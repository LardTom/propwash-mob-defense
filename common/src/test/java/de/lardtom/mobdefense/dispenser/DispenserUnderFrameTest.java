package de.lardtom.mobdefense.dispenser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.dev.dispenser.UnderFrame;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartBasics;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispenserUnderFrameTest {
    static final String PART = "data/mobdefense/propwash_parts/accessory/dispenser_mount.json";
    static final String SPEAR = "data/mobdefense/propwash_parts/accessory/spear_mount.json";
    static final String MODEL = "assets/mobdefense/models/part/accessory/dispenser_mount.json";
    static final String PROPWASH_FRAME = "data/propwash/propwash_parts/frame/freestyle_5.json";
    static final String JMP_ENV = "MOBDEFENSE_JMP_JAR";
    static final String CAMDRONES_ENV = "MOBDEFENSE_CAMDRONES_JAR";
    static final int PROPWASH_FRONT_FRAMES = 4;

    record Source(String namespace, ZipFile zip) {
        Optional<JsonObject> json(String path) throws IOException {
            ZipEntry entry = this.zip.getEntry(path);
            if (entry == null) {
                return Optional.empty();
            }
            try (InputStream in = this.zip.getInputStream(entry)) {
                return Optional.of(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }

        List<String> frames() {
            String prefix = "data/" + this.namespace + "/propwash_parts/frame/";
            List<String> out = new ArrayList<>();
            for (ZipEntry entry : Collections.list(this.zip.entries())) {
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(".json") && name.indexOf('/', prefix.length()) < 0) {
                    out.add(name.substring(prefix.length(), name.length() - ".json".length()));
                }
            }
            Collections.sort(out);
            return out;
        }
    }

    record Frame(String id, UnderFrame.Anchor front, Optional<UnderFrame.Anchor> bottom, List<UnderFrame.Part> body) {
    }

    static Path resources() {
        return Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
    }

    static JsonObject own(String path) throws IOException {
        return JsonParser.parseString(Files.readString(resources().resolve(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static double[] size(JsonObject part) {
        JsonArray size = part.getAsJsonArray("size_mm");
        return new double[]{size.get(0).getAsDouble(), size.get(1).getAsDouble(), size.get(2).getAsDouble()};
    }

    static double field(JsonObject part, String name) {
        return part.has(name) ? part.get(name).getAsDouble() : 0.0;
    }

    static Path propwashJar() throws IOException {
        URL url = DispenserUnderFrameTest.class.getClassLoader().getResource(PROPWASH_FRAME);
        assertTrue(url != null, PROPWASH_FRAME + " nicht im Klassenpfad");
        assertEquals("jar", url.getProtocol(), "Propwash liegt nicht als Jar im Klassenpfad: " + url);
        try {
            return Path.of(((JarURLConnection) url.openConnection()).getJarFileURL().toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    static List<Source> sources() throws IOException {
        List<Source> out = new ArrayList<>();
        out.add(new Source("propwash", new ZipFile(propwashJar().toFile())));
        for (String[] addon : new String[][]{{JMP_ENV, "justmoreparts"}, {CAMDRONES_ENV, "camdrones"}}) {
            String jar = System.getenv(addon[0]);
            if (jar == null || jar.isBlank()) {
                System.out.println("[dispenser] " + addon[0] + " nicht gesetzt: Rahmen von " + addon[1] + " nicht gemessen");
                continue;
            }
            out.add(new Source(addon[1], new ZipFile(Path.of(jar).toFile())));
        }
        return out;
    }

    static JsonObject model(List<Source> sources, String id) throws IOException {
        Identifier model = Identifier.parse(id);
        String path = "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json";
        for (Source source : sources) {
            Optional<JsonObject> json = source.json(path);
            if (json.isPresent()) {
                return json.get();
            }
        }
        throw new AssertionError(path + " in keinem Jar");
    }

    static List<Frame> frontFrames(List<Source> sources) throws IOException {
        List<Frame> out = new ArrayList<>();
        for (Source source : sources) {
            for (String name : source.frames()) {
                JsonObject frame = source.json("data/" + source.namespace() + "/propwash_parts/frame/" + name + ".json").orElseThrow();
                boolean front = false;
                for (JsonElement mount : frame.getAsJsonArray("accessory_mounts")) {
                    front |= "front".equals(mount.getAsString());
                }
                if (!front) {
                    continue;
                }
                String id = source.namespace() + ":" + name;
                JsonObject anchors = frame.getAsJsonObject("accessory_anchors");
                assertTrue(anchors != null && anchors.has("front"), id + ": Halterung vorn ohne Anker");
                JsonObject item = source.json("assets/" + source.namespace() + "/items/drone_render/frame/" + name + ".json")
                        .orElseThrow(() -> new AssertionError(id + ": kein Rahmenmodell")).getAsJsonObject("model");
                assertEquals("minecraft:model", item.get("type").getAsString(), id);
                List<UnderFrame.Part> body = UnderFrame.parts(model(sources, item.get("model").getAsString()), UnderFrame.transform(item));
                assertTrue(!body.isEmpty(), id + ": Rahmenmodell ohne Elemente");
                Optional<UnderFrame.Anchor> bottom = anchors.has("bottom") ? Optional.of(UnderFrame.Anchor.of(anchors.getAsJsonObject("bottom")))
                        : Optional.empty();
                out.add(new Frame(id, UnderFrame.Anchor.of(anchors.getAsJsonObject("front")), bottom, body));
            }
        }
        return out;
    }

    @Test
    void theDispenserIsRecessedByNineTenthsOfItsDepthAndHangsAtMostOneMillimetreBelowTheAnchor() throws IOException {
        JsonObject part = own(PART);
        double[] size = size(part);
        double recess = field(part, "recess_mm");
        double drop = field(part, "drop_mm");
        assertEquals("front", part.get("slot").getAsString());
        assertEquals(Math.round(UnderFrame.RECESS_SHARE * size[2]), recess, 0.0, "recess_mm = 0,9 × Tiefe " + size[2] + " mm");
        assertTrue(drop > 0.0 && drop <= UnderFrame.MAX_GAP_MM, "drop_mm " + drop + " mm: über 0 und höchstens " + UnderFrame.MAX_GAP_MM + " mm");
        AccessoryDefinition definition = AccessoryDefinition.builder(Identifier.fromNamespaceAndPath("mobdefense", "dispenser_mount"))
                .basics(PartBasics.of(part.get("mass_g").getAsDouble())).slot(AccessorySlot.FRONT).size(size[0], size[1], size[2])
                .frontalArea(part.get("frontal_area_m2").getAsDouble()).recess(recess).drop(drop).build();
        assertEquals(recess, definition.recessMm(), 0.0);
        assertEquals(drop, definition.dropMm(), 0.0);
    }

    @Test
    void theHousingSitsUnderEveryFrameWithAFrontMountAndOnlyTheStoneFrontSticksOut() throws IOException {
        JsonObject part = own(PART);
        double[] size = size(part);
        double recess = field(part, "recess_mm");
        double drop = field(part, "drop_mm");
        List<UnderFrame.Part> housing = UnderFrame.parts(own(MODEL));
        JsonObject spear = own(SPEAR);
        double[] spearSize = size(spear);
        UnderFrame.Box box = UnderFrame.Box.of(size[0], size[1], size[2], recess, drop, 1.0);
        List<Source> sources = sources();
        List<String> misses = new ArrayList<>();
        int propwash = 0;
        try {
            List<Frame> frames = frontFrames(sources);
            for (Frame frame : frames) {
                UnderFrame.Fit fit = UnderFrame.measure(frame.body(), frame.front(), housing, size[0], size[1], size[2], recess, drop);
                String line = frame.id() + ": " + fit.describe();
                if (frame.bottom().isPresent()) {
                    UnderFrame.Anchor bottom = frame.bottom().get();
                    double stretch = bottom.lengthMm() > 0.0 ? bottom.lengthMm() / spearSize[0] : 1.0;
                    UnderFrame.Box mount = UnderFrame.Box.bottom(spearSize[0], spearSize[1], spearSize[2], stretch);
                    line += String.format(Locale.ROOT, ", Speer-Halterung %.1f mm tief im Gehäuse", UnderFrame.overlap(box, frame.front(), mount, bottom));
                }
                System.out.println("[dispenser] " + line);
                if (frame.id().startsWith("propwash:")) {
                    propwash++;
                }
                if (!fit.underFrame(size[2])) {
                    misses.add(line);
                }
            }
        } finally {
            for (Source source : sources) {
                source.zip().close();
            }
        }
        assertTrue(propwash >= PROPWASH_FRONT_FRAMES, propwash + " Propwash-Rahmen mit Halterung vorn");
        assertTrue(misses.isEmpty(), "Dispenser nicht unter dem Rahmen: " + String.join("; ", misses));
    }
}
