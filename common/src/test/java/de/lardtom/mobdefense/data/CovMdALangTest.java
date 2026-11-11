package de.lardtom.mobdefense.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.MobDefenseTab;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.client.MobDefenseGuide;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.dispenser.DispenserBehavior;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdALangTest {
    static final List<String> LANGUAGES = List.of("en_us", "de_de");
    static final Map<String, List<String>> ADVANCEMENT_TITLES = Map.of(
            "air_support", List.of("Air Support", "Luftunterstützung"),
            "lance_flight", List.of("Drone Joust", "Lanzenflug"),
            "afterburner", List.of("Afterburner", "Nachbrenner"),
            "recoil", List.of("Recoil", "Rückstoß"));

    static Map<String, String> merged(String language) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (Stream<Path> dirs = Files.list(DataFiles.lang())) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path file = dir.resolve(language + ".json");
                if (Files.isRegularFile(file)) {
                    for (Map.Entry<String, JsonElement> entry : DataFiles.object(file).entrySet()) {
                        out.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        return out;
    }

    private static void translates(JsonElement element, Set<String> out) {
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("translate") && object.get("translate").isJsonPrimitive()) {
                out.add(object.get("translate").getAsString());
            }
            object.entrySet().forEach(entry -> translates(entry.getValue(), out));
        } else if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> translates(child, out));
        }
    }

    static Set<String> usedKeys() {
        Set<String> keys = new TreeSet<>();
        keys.add(MobDefenseTab.NAME_KEY);
        keys.add(DispenserBehavior.FIRE_KEY);
        keys.add(DispenserBehavior.NEXT_SLOT_KEY);
        keys.add(BoosterBehavior.IGNITE_KEY);
        for (Identifier part : MobDefense.PARTS) {
            keys.add("part." + part.getNamespace() + "." + part.getPath());
            keys.add(part.getNamespace() + ".help." + part.getPath() + ".summary");
            keys.add(part.getNamespace() + ".help." + part.getPath() + ".use.1");
        }
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            keys.add(spec.nameKey());
        }
        for (Notice notice : Notice.values()) {
            keys.add(notice.translationKey());
        }
        for (String scene : MobDefenseGuide.SCENES) {
            keys.add(MobDefense.MOD_ID + ".guide.scene." + scene.substring(scene.indexOf(':') + 1) + ".title");
        }
        for (Path file : DataFiles.json(DataFiles.data())) {
            translates(DataFiles.element(file), keys);
        }
        return keys;
    }

    @Test
    void everyKeyTheCodeAndDataUseExistsInBothLanguages() throws IOException {
        Set<String> keys = usedKeys();
        assertTrue(keys.size() >= 30, "nur " + keys.size() + " Schlüssel gefunden: " + keys);
        for (String language : LANGUAGES) {
            Map<String, String> texts = merged(language);
            List<String> missing = new ArrayList<>();
            for (String key : keys) {
                String value = texts.get(key);
                if (value == null || value.isBlank() || value.equals(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), language + ": roher Schlüssel bei " + missing);
        }
    }

    @Test
    void partNamesDifferBetweenTheLanguagesAndGermanUsesUmlautsWhereNeeded() throws IOException {
        Map<String, String> english = merged("en_us");
        Map<String, String> german = merged("de_de");
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("part.mobdefense.dispenser_mount", "Dispenser-Halterung|Dispenser Mount");
        expected.put("part.mobdefense.spear_mount", "Speer-Halterung|Spear Mount");
        expected.put("part.mobdefense.rocket_booster", "Raketen-Booster|Rocket Booster");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String[] names = entry.getValue().split("\\|");
            assertEquals(names[0], german.get(entry.getKey()), entry.getKey() + " Deutsch");
            assertEquals(names[1], english.get(entry.getKey()), entry.getKey() + " Englisch");
        }
        assertTrue(german.get(DispenserBehavior.FIRE_KEY).equals("Dispenser feuern"), german.get(DispenserBehavior.FIRE_KEY));
        assertTrue(german.get(BoosterBehavior.IGNITE_KEY).equals("Booster zünden"), german.get(BoosterBehavior.IGNITE_KEY));
    }

    @Test
    void advancementTitlesInEnglishAndGerman() throws IOException {
        Map<String, String> english = merged("en_us");
        Map<String, String> german = merged("de_de");
        for (Map.Entry<String, List<String>> entry : ADVANCEMENT_TITLES.entrySet()) {
            String key = "advancements.mobdefense." + entry.getKey() + ".title";
            assertEquals(entry.getValue().get(0), english.get(key), key + " Englisch");
            assertEquals(entry.getValue().get(1), german.get(key), key + " Deutsch");
            JsonObject json = DataFiles.object(DataFiles.data().resolve("advancement/" + entry.getKey() + ".json"));
            assertEquals("minecraft:impossible", json.getAsJsonObject("criteria").getAsJsonObject("done").get("trigger").getAsString(), entry.getKey());
            assertEquals(key, json.getAsJsonObject("display").getAsJsonObject("title").get("translate").getAsString(), entry.getKey());
        }
    }
}
