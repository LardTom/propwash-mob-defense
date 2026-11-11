package de.lardtom.mobdefense.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTextsTest {
    private static final Map<String, List<String>> SCENE_KEYS = Map.of(
            "dispenser", List.of("step.load.title", "step.load.text", "callout.slots", "callout.service", "step.fire.title", "step.fire.text",
                    "hint.fire", "hint.exit", "step.slots.title", "step.slots.text", "hint.next", "callout.display", "callout.rules"),
            "spear", List.of("step.mount.title", "step.mount.text", "callout.only", "step.joust.title", "step.joust.text", "callout.speed",
                    "callout.durability"),
            "booster", List.of("step.load.title", "step.load.text", "callout.one", "step.ignite.title", "step.ignite.text", "hint.ignite",
                    "callout.boost", "callout.stars"));
    private static final Map<String, String> WARNINGS_EN = Map.of("no_ammo", "NO AMMO", "bucket_locked", "BUCKET LOCKED", "tnt_locked", "TNT LOCKED",
            "barrel_blocked", "BARREL BLOCKED", "no_permission", "NO PERMISSION", "threat", "THREAT", "hit", "HIT");
    private static final Map<String, String> WARNINGS_DE = Map.of("no_ammo", "KEINE MUNITION", "bucket_locked", "EIMER GESPERRT", "tnt_locked",
            "TNT GESPERRT", "barrel_blocked", "LAUF BLOCKIERT", "no_permission", "KEIN RECHT", "threat", "BEDROHUNG", "hit", "TREFFER");

    static Path module() {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        return resources.getParent().getParent().resolve("lang").resolve("ui");
    }

    static Map<String, String> texts(String language) throws IOException {
        JsonObject object = JsonParser.parseString(Files.readString(module().resolve(language + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    @Test
    void osdNamesAndWarningsAsInTheDesign() throws IOException {
        Map<String, String> english = texts("en_us");
        Map<String, String> german = texts("de_de");
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            assertTrue(english.containsKey(spec.nameKey()) && german.containsKey(spec.nameKey()), spec.nameKey());
            assertTrue(OsdLines.printableAscii(german.get(spec.nameKey())), spec.nameKey());
        }
        for (Notice notice : Notice.values()) {
            assertEquals(WARNINGS_EN.get(notice.path()), english.get(notice.translationKey()), notice.translationKey());
            assertEquals(WARNINGS_DE.get(notice.path()), german.get(notice.translationKey()), notice.translationKey());
            assertTrue(german.get(notice.translationKey()).length() <= OsdElementSpec.MAX_CHARS);
        }
    }

    @Test
    void scenesHaveAllTextsWithinTheLimits() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(language);
            for (String scene : MobDefenseGuide.SCENES) {
                String name = scene.substring(scene.indexOf(':') + 1);
                String prefix = "mobdefense.guide.scene." + name + ".";
                List<String> keys = new ArrayList<>(List.of("title", "description"));
                keys.addAll(SCENE_KEYS.get(name));
                for (String key : keys) {
                    String value = texts.get(prefix + key);
                    assertTrue(value != null, language + " " + prefix + key + " fehlt");
                    int limit = key.equals("title") ? 38 : key.equals("description") ? 64 : key.endsWith(".text") ? 150 : 90;
                    assertTrue(value.length() <= limit, language + " " + prefix + key + ": " + value.length() + " > " + limit);
                }
                long extra = texts.keySet().stream().filter(key -> key.startsWith(prefix)).count();
                assertEquals(keys.size(), extra, language + " " + scene + ": Schlüssel ohne Verwendung in der Szene");
            }
        }
    }

    @Test
    void scenesAndHintsNameActionOneAndRightClick() throws IOException {
        Map<String, List<String>> phrases = Map.of(
                "en_us", List.of("accessory action 1", "right-click ends the flight"),
                "de_de", List.of("zubehör-aktion 1", "rechtsklick beendet den flug"));
        for (Map.Entry<String, List<String>> language : phrases.entrySet()) {
            Map<String, String> texts = texts(language.getKey());
            for (String scene : MobDefenseGuide.SCENES) {
                String prefix = "mobdefense.guide.scene." + scene.substring(scene.indexOf(':') + 1) + ".";
                String all = joined(texts, prefix);
                for (String phrase : language.getValue()) {
                    assertTrue(all.contains(phrase), language.getKey() + " " + scene + " nennt nicht „" + phrase + "“");
                }
            }
            String hints = joined(texts, "mobdefense.help.dispenser_mount.use.");
            for (String phrase : language.getValue()) {
                assertTrue(hints.contains(phrase), language.getKey() + " Hinweis Dispenser nennt nicht „" + phrase + "“");
            }
            assertTrue(joined(texts, "mobdefense.help.rocket_booster.use.").contains(language.getKey().equals("en_us") ? "accessory action 3" : "zubehör-aktion 3"));
        }
    }

    @Test
    void advancementTextsForAllFive() throws IOException {
        for (String language : List.of("en_us", "de_de")) {
            Map<String, String> texts = texts(language);
            for (String id : List.of("root", "air_support", "lance_flight", "afterburner", "recoil")) {
                assertTrue(texts.containsKey("advancements.mobdefense." + id + ".title"), language + " " + id);
                assertTrue(texts.containsKey("advancements.mobdefense." + id + ".description"), language + " " + id);
            }
        }
        Map<String, String> german = texts("de_de");
        assertEquals("Luftunterstützung", german.get("advancements.mobdefense.air_support.title"));
        assertEquals("Lanzenflug", german.get("advancements.mobdefense.lance_flight.title"));
        assertEquals("Nachbrenner", german.get("advancements.mobdefense.afterburner.title"));
        assertEquals("Rückstoß", german.get("advancements.mobdefense.recoil.title"));
    }

    private static String joined(Map<String, String> texts, String prefix) {
        StringBuilder out = new StringBuilder();
        texts.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                out.append(value.toLowerCase(Locale.ROOT)).append('\n');
            }
        });
        return out.toString();
    }
}
