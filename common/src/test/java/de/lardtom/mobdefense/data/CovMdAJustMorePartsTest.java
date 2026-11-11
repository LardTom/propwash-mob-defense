package de.lardtom.mobdefense.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdAJustMorePartsTest {
    static final String SNAPSHOT = "cov/cov-md-a/justmoreparts-1.2.0-frames.json";
    static final String JAR_ENV = "MOBDEFENSE_JMP_JAR";
    static final String FRAMES_IN_JAR = "data/justmoreparts/propwash_parts/frame";
    static final int JMP_FRAMES = 22;
    static final Map<AccessorySlot, Identifier> PARTS = Map.of(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT, AccessorySlot.BOTTOM,
            MobDefense.SPEAR_MOUNT, AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER);
    static final Map<AccessorySlot, Double> GRAMS = Map.of(AccessorySlot.FRONT, 150.0, AccessorySlot.BOTTOM, 40.0, AccessorySlot.TOP, 28.0);
    private static boolean ready;

    @BeforeAll
    static synchronized void boot() {
        if (ready) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(DataComponentInitializers.PendingComponents::apply);
        for (Identifier part : MobDefense.PARTS) {
            register(PartKind.ACCESSORY, part, DataFiles.object(DataFiles.data().resolve("propwash_parts/accessory/" + part.getPath() + ".json")));
        }
        for (Map.Entry<String, JsonObject> frame : snapshot().entrySet()) {
            register(PartKind.FRAME, Identifier.parse(frame.getKey()), frame.getValue());
        }
        ready = true;
    }

    private static void register(PartKind kind, Identifier id, JsonObject json) {
        if (PropwashApi.parts().get(id).isEmpty()) {
            PropwashApi.parts().register(PropwashApi.parts().fromJson(kind, id, json));
        }
    }

    static Map<String, JsonObject> snapshot() {
        try (InputStream in = CovMdAJustMorePartsTest.class.getClassLoader().getResourceAsStream(SNAPSHOT)) {
            assertNotNull(in, SNAPSHOT + " fehlt");
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("frames").entrySet()) {
                out.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
            return out;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static FrameDefinition frame(String id) {
        PartDefinition definition = PropwashApi.parts().get(Identifier.parse(id)).orElseThrow(() -> new AssertionError(id + " nicht registriert"));
        assertTrue(definition instanceof FrameDefinition, id + " ist kein Rahmen");
        return (FrameDefinition) definition;
    }

    private static BuildSpec build(String frame) {
        return BuildSpec.of(Identifier.parse(frame), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300"));
    }

    private static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    private static List<String> added(BuildCheck without, BuildCheck with) {
        List<String> out = new ArrayList<>(with.problems());
        out.removeAll(without.problems());
        return out;
    }

    @Test
    void snapshotHasEveryFrameOfJustMoreParts120() throws IOException {
        Map<String, JsonObject> snapshot = snapshot();
        assertEquals(JMP_FRAMES, snapshot.size(), "Rahmen in Just More Parts 1.2.0");
        String jar = System.getenv(JAR_ENV);
        if (jar == null || jar.isBlank()) {
            System.out.println("[cov-md-a] " + JAR_ENV + " nicht gesetzt: Abzug " + SNAPSHOT + " ohne Vergleich mit dem Jar");
            return;
        }
        Map<String, JsonObject> fromJar = new LinkedHashMap<>();
        try (FileSystem fs = FileSystems.newFileSystem(Path.of(jar), Map.of()); Stream<Path> files = Files.list(fs.getPath(FRAMES_IN_JAR))) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                fromJar.put("justmoreparts:" + name.substring(0, name.length() - ".json".length()),
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        assertEquals(fromJar, snapshot, "Abzug weicht vom Jar " + jar + " ab");
        System.out.println("[cov-md-a] Abzug stimmt mit " + jar + " überein (" + fromJar.size() + " Rahmen)");
    }

    @Test
    void everyFrameWithAFrontMountTakesTheDispenserWith150Grams() {
        List<String> front = new ArrayList<>();
        for (String id : snapshot().keySet()) {
            if (!frame(id).accessoryMounts().contains(AccessorySlot.FRONT)) {
                continue;
            }
            front.add(id);
            BuildSpec base = build(id);
            BuildSpec mounted = base.withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
            assertTrue(added(PropwashApi.builds().check(base), PropwashApi.builds().check(mounted)).isEmpty(), id + ": Dispenser vorn abgelehnt");
            BuildStats without = PropwashApi.builds().analyze(base).orElseThrow();
            BuildStats with = PropwashApi.builds().analyze(mounted).orElseThrow();
            assertEquals(150.0, with.massGrams() - without.massGrams(), 0.5, id + ": Analyse-Masse");
        }
        System.out.println("[cov-md-a] Just-More-Parts-Rahmen mit Halterung vorn: " + front.size() + " " + front);
    }

    @Test
    void everyFrameTakesTheMobDefensePartsExactlyOnItsOwnMounts() {
        List<String> notes = new ArrayList<>();
        for (String id : snapshot().keySet()) {
            FrameDefinition frame = frame(id);
            BuildSpec base = build(id);
            BuildCheck plain = PropwashApi.builds().check(base);
            BuildStats without = PropwashApi.builds().analyze(base).orElseThrow(() -> new AssertionError(id + ": keine Analyse"));
            StringBuilder note = new StringBuilder(id.substring(id.indexOf(':') + 1)).append(' ').append(frame.accessoryMounts());
            for (Map.Entry<AccessorySlot, Identifier> part : PARTS.entrySet()) {
                BuildSpec mounted = base.withAccessory(part.getKey(), part.getValue());
                List<String> added = added(plain, PropwashApi.builds().check(mounted));
                if (frame.accessoryMounts().contains(part.getKey())) {
                    assertTrue(added.isEmpty(), id + ": " + part.getValue() + " passt, trotzdem " + added);
                    BuildStats with = PropwashApi.builds().analyze(mounted).orElseThrow();
                    assertEquals(GRAMS.get(part.getKey()), with.massGrams() - without.massGrams(), 0.5, id + ": Analyse-Masse mit " + part.getValue());
                    note.append(String.format(Locale.ROOT, " +%s %.0f g", part.getValue().getPath(), with.massGrams() - without.massGrams()));
                } else {
                    assertTrue(!added.isEmpty(), id + ": " + part.getValue() + " ohne Halterung " + part.getKey() + " angenommen");
                    note.append(" −").append(part.getValue().getPath()).append(' ').append(added);
                }
            }
            notes.add(note.toString());
        }
        System.out.println("[cov-md-a] " + String.join("; ", notes));
    }
}
