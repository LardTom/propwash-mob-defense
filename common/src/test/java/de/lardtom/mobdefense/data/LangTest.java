package de.lardtom.mobdefense.data;

import com.google.gson.JsonElement;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.MobDefenseTab;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangTest {
    static final List<String> LANGUAGES = List.of("en_us", "de_de");
    private static final Set<String> UMLAUT_LOOKALIKES = Set.of("aero", "aloe", "oboe", "poesie", "israel", "michael", "true", "blue", "queue");
    private static final List<String> SHARP_S = List.of("gross", "grosse", "grossen", "grosser", "strasse", "fuss", "heiss", "heisst", "schliesslich",
            "ausser", "ausserdem", "ausserhalb", "weiss", "massstab", "gruss", "draussen", "spass", "stoss", "gemass");

    private static Map<String, Map<String, Map<String, String>>> modules() throws IOException {
        Map<String, Map<String, Map<String, String>>> out = new TreeMap<>();
        try (Stream<Path> dirs = Files.list(DataFiles.lang())) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Map<String, Map<String, String>> languages = new LinkedHashMap<>();
                for (String language : LANGUAGES) {
                    Path file = dir.resolve(language + ".json");
                    assertTrue(Files.isRegularFile(file), dir.getFileName() + "/" + language + ".json fehlt");
                    Map<String, String> entries = new LinkedHashMap<>();
                    for (Map.Entry<String, JsonElement> entry : DataFiles.object(file).entrySet()) {
                        entries.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    languages.put(language, entries);
                }
                out.put(dir.getFileName().toString(), languages);
            }
        }
        return out;
    }

    private static Map<String, String> merged(String language) throws IOException {
        Map<String, String> out = new HashMap<>();
        for (Map<String, Map<String, String>> module : modules().values()) {
            out.putAll(module.get(language));
        }
        return out;
    }

    @Test
    void sameKeysInGermanAndEnglishAndNoKeyInTwoModules() throws IOException {
        Map<String, String> owner = new HashMap<>();
        for (Map.Entry<String, Map<String, Map<String, String>>> module : modules().entrySet()) {
            Set<String> english = new TreeSet<>(module.getValue().get("en_us").keySet());
            Set<String> german = new TreeSet<>(module.getValue().get("de_de").keySet());
            assertEquals(english, german, module.getKey() + ": Schlüssel EN und DE");
            for (String key : english) {
                String before = owner.put(key, module.getKey());
                assertTrue(before == null, key + " steht in " + before + " und " + module.getKey());
                assertTrue(key.startsWith(MobDefense.MOD_ID + ".") || key.contains("." + MobDefense.MOD_ID + ".") || key.endsWith("." + MobDefense.MOD_ID),
                        key + ": nur im Namensraum mobdefense");
            }
        }
    }

    @Test
    void valuesAreTrimmedAndNotEmpty() throws IOException {
        for (String language : LANGUAGES) {
            for (Map.Entry<String, String> entry : merged(language).entrySet()) {
                String value = entry.getValue();
                assertFalse(value.isBlank(), language + " " + entry.getKey() + " leer");
                assertEquals(value.strip(), value, language + " " + entry.getKey() + ": Leerzeichen am Rand");
            }
        }
    }

    @Test
    void percentSignsAreFormatCodes() throws IOException {
        for (String language : LANGUAGES) {
            for (Map.Entry<String, String> entry : merged(language).entrySet()) {
                String rest = entry.getValue().replaceAll("%(\\d+\\$)?[sd]|%%", "");
                assertFalse(rest.contains("%"), language + " " + entry.getKey() + ": einzelnes % zerstört den Text, „%%“ oder „Prozent“ schreiben");
            }
        }
    }

    @Test
    void germanUsesUmlautsSharpSAndTypographicQuotes() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> entry : merged("de_de").entrySet()) {
            String value = entry.getValue();
            for (String word : value.toLowerCase(Locale.ROOT).split("[^a-zäöüß]+")) {
                if (word.isEmpty() || UMLAUT_LOOKALIKES.contains(word)) {
                    continue;
                }
                if (word.contains("ae") || word.contains("oe") || replacedUe(word)) {
                    problems.add(entry.getKey() + ": „" + word + "“ – Umlaut?");
                }
                if (SHARP_S.contains(word)) {
                    problems.add(entry.getKey() + ": „" + word + "“ – ß?");
                }
            }
            if (value.contains("\"") || value.contains("“") && !value.contains("„")) {
                problems.add(entry.getKey() + ": deutsche Anführungszeichen „…“");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    static boolean replacedUe(String word) {
        for (int i = word.indexOf("ue"); i >= 0; i = word.indexOf("ue", i + 1)) {
            char before = i == 0 ? ' ' : word.charAt(i - 1);
            if (before != 'e' && before != 'a' && before != 'q') {
                return true;
            }
        }
        return false;
    }

    @Test
    void umlautCheckSeesReplacements() {
        assertTrue(replacedUe("fuer"));
        assertTrue(replacedUe("muenition"));
        assertFalse(replacedUe("feuer"));
        assertFalse(replacedUe("neue"));
        assertFalse(replacedUe("dauer"));
    }

    @Test
    void osdTextsArePrintableAscii() throws IOException {
        for (String language : LANGUAGES) {
            for (Map.Entry<String, String> entry : merged(language).entrySet()) {
                if (!List.of(entry.getKey().split("\\.")).contains("osd")) {
                    continue;
                }
                for (char c : entry.getValue().toCharArray()) {
                    assertTrue(c >= 0x20 && c <= 0x7e, language + " " + entry.getKey() + ": OSD-Text nur ASCII, gefunden „" + c + "“");
                }
            }
        }
    }

    static String propwashSectionFallback(String modName) {
        String name = modName.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("propwash fpv:", "propwash:")) {
            if (lower.startsWith(prefix)) {
                return name.substring(prefix.length()).strip();
            }
        }
        return name;
    }

    @Test
    void coreTextsArePresentAndTranslated() throws IOException {
        Map<String, String> english = merged("en_us");
        Map<String, String> german = merged("de_de");
        List<String> keys = new ArrayList<>(List.of(MobDefense.MOD_ID + ".name", MobDefense.MOD_ID + ".description",
                MobDefense.MOD_ID + ".world_rules.section", MobDefenseTab.NAME_KEY));
        MobDefense.PARTS.forEach(part -> keys.add("part." + part.getNamespace() + "." + part.getPath()));
        MobDefense.PARTS.forEach(part -> keys.add(part.getNamespace() + ".help." + part.getPath() + ".summary"));
        for (RuleSpec spec : RuleDefaults.CORE) {
            keys.add("gamerule." + MobDefense.MOD_ID + "." + spec.path());
            keys.add("gamerule." + MobDefense.MOD_ID + "." + spec.path() + ".description");
        }
        for (String key : keys) {
            assertTrue(english.containsKey(key), key + " fehlt auf Englisch");
            assertTrue(german.containsKey(key), key + " fehlt auf Deutsch");
        }
        String fallback = propwashSectionFallback(MobDefense.MOD_NAME);
        assertEquals("Mob Defense", fallback, "Propwash zeigt ohne Schlüssel den Mod-Namen ohne „Propwash:“");
        assertEquals(fallback, english.get(MobDefense.MOD_ID + ".world_rules.section"), "Abschnitt nur mit dem Namen, wie ohne Schlüssel");
        assertEquals(fallback, german.get(MobDefense.MOD_ID + ".world_rules.section"), "Abschnitt nur mit dem Namen, wie ohne Schlüssel");
        assertEquals("Mob Defense", english.get(MobDefenseTab.NAME_KEY), "Propwash setzt „Propwash: “ davor");
        for (RuleSpec spec : RuleDefaults.CORE) {
            String key = "gamerule." + MobDefense.MOD_ID + "." + spec.path() + ".description";
            assertNotEquals(english.get(key), german.get(key), key + " nicht übersetzt");
        }
    }
}
