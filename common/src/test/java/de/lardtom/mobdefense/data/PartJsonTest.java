package de.lardtom.mobdefense.data;

import com.google.gson.JsonObject;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartJsonTest {
    private static final double EPSILON = 1.0e-9;

    record Expected(String id, AccessorySlot slot, double grams, double widthMm, double heightMm, double depthMm, double frontalAreaM2,
                    double watts) {
    }

    static final List<Expected> DESIGN = List.of(
            new Expected("dispenser_mount", AccessorySlot.FRONT, 150.0, 62.0, 58.0, 64.0, 0.0036, 1.2),
            new Expected("spear_mount", AccessorySlot.BOTTOM, 40.0, 24.0, 18.0, 90.0, 0.0004, 0.0),
            new Expected("rocket_booster", AccessorySlot.TOP, 28.0, 30.0, 22.0, 70.0, 0.0005, 0.2));

    private static Path parts() {
        return DataFiles.data().resolve("propwash_parts");
    }

    private static AccessoryDefinition parse(String id) {
        Path file = parts().resolve("accessory").resolve(id + ".json");
        assertTrue(Files.isRegularFile(file), file + " fehlt");
        JsonObject json = DataFiles.object(file);
        assertEquals(1, json.get("format").getAsInt(), id + ": format");
        PartDefinition definition = PropwashApi.parts().fromJson(PartKind.ACCESSORY, MobDefense.id(id), json);
        return assertInstanceOf(AccessoryDefinition.class, definition, id);
    }

    @Test
    void exactlyTheThreeAccessories() throws Exception {
        Set<String> kinds = new TreeSet<>();
        try (Stream<Path> dirs = Files.list(parts())) {
            dirs.filter(Files::isDirectory).forEach(dir -> kinds.add(dir.getFileName().toString()));
        }
        assertEquals(Set.of("accessory"), kinds);
        Set<String> files = new TreeSet<>();
        for (Path file : DataFiles.json(parts())) {
            files.add(DataFiles.stem(file));
        }
        Set<String> expected = new TreeSet<>();
        DESIGN.forEach(part -> expected.add(part.id()));
        assertEquals(expected, files);
        List<Identifier> ids = new ArrayList<>();
        DESIGN.forEach(part -> ids.add(MobDefense.id(part.id())));
        assertEquals(ids, MobDefense.PARTS);
    }

    @Test
    void valuesMatchTheDesign() {
        for (Expected part : DESIGN) {
            AccessoryDefinition definition = parse(part.id());
            assertEquals(MobDefense.id(part.id()), definition.id());
            assertEquals(part.slot(), definition.slot(), part.id() + ": Halterung");
            assertEquals(part.grams(), definition.basics().massGrams(), EPSILON, part.id() + ": Masse");
            assertEquals(part.widthMm(), definition.widthMm(), EPSILON, part.id() + ": Breite");
            assertEquals(part.heightMm(), definition.heightMm(), EPSILON, part.id() + ": Höhe");
            assertEquals(part.depthMm(), definition.depthMm(), EPSILON, part.id() + ": Tiefe");
            assertEquals(part.frontalAreaM2(), definition.frontalAreaM2(), EPSILON, part.id() + ": Stirnfläche");
            assertEquals(part.watts(), definition.flightBatteryWatts(), EPSILON, part.id() + ": Strom");
            assertEquals(0, definition.ledCount(), part.id() + ": LEDs");
            assertTrue(definition.actionCam().isEmpty(), part.id() + ": keine Action-Cam");
            assertTrue(definition.basics().creativeListed(), part.id() + ": im Kreativ-Tab");
        }
    }

    @Test
    void frontalAreaFitsTheFrontFace() {
        for (Expected part : DESIGN) {
            AccessoryDefinition definition = parse(part.id());
            double face = definition.widthMm() * definition.heightMm() / 1.0e6;
            assertTrue(definition.frontalAreaM2() > 0.0, part.id() + ": Stirnfläche fehlt");
            assertTrue(definition.frontalAreaM2() <= face + 1.0e-5, part.id() + ": Stirnfläche " + definition.frontalAreaM2()
                    + " m² größer als B × H = " + face + " m²");
        }
    }

    @Test
    void sortFollowsTheMounts() {
        List<Integer> sort = new ArrayList<>();
        for (Expected part : DESIGN) {
            sort.add(parse(part.id()).basics().sortOrder());
        }
        assertEquals(List.of(0, 1, 2), sort);
    }

    @Test
    void jsonRoundTripKeepsEveryValue() {
        for (Expected part : DESIGN) {
            AccessoryDefinition definition = parse(part.id());
            JsonObject encoded = PropwashApi.parts().toJson(definition);
            PartDefinition again = PropwashApi.parts().fromJson(PartKind.ACCESSORY, definition.id(), encoded);
            assertEquals(definition, again, part.id());
        }
    }

    @Test
    void everyPartHasItemModelRenderModelAndNames() {
        JsonObject english = DataFiles.object(DataFiles.lang().resolve("core/en_us.json"));
        JsonObject german = DataFiles.object(DataFiles.lang().resolve("core/de_de.json"));
        for (Expected part : DESIGN) {
            String id = part.id();
            for (String file : List.of("items/" + id + ".json", "items/drone_render/accessory/" + id + ".json",
                    "items/drone_render/accessory/" + id + "_paint.json")) {
                assertTrue(Files.isRegularFile(DataFiles.assets().resolve(file)), "assets/mobdefense/" + file + " fehlt");
            }
            String key = "part." + MobDefense.MOD_ID + "." + id;
            assertTrue(english.has(key), key + " fehlt auf Englisch");
            assertTrue(german.has(key), key + " fehlt auf Deutsch");
        }
    }
}
