package de.lardtom.mobdefense;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.propwash.api.registry.ExtensionTab;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideCreativeTabTest {
    private static final Pattern TAB_EN = Pattern.compile("(?i)\\btabs?\\b");
    private static final Pattern TAB_DE = Pattern.compile("(?i)reiter\\b|\\btabs?\\b");
    private static final Pattern PROPWASH_TAB_EN = Pattern.compile("(?i)propwash's (?:own |main )?(?:creative )?tab\\b|propwash (?:fpv )?tab\\b");
    private static final Pattern PROPWASH_TAB_DE = Pattern.compile(
            "(?i)propwashs (?:eigene[mnr]? )?(?:kreativ-)?(?:reiter|tab)\\b|propwash-(?:kreativ-)?(?:reiter|tab)\\b");

    record Guide(String file, String language, String heading, String marker, String open, String close, Pattern tab, Pattern propwashTab) {
    }

    private static final List<Guide> GUIDES = List.of(
            new Guide("docs/guide.md", "en_us", "## 1. Getting started", "In creative mode", "*", "*", TAB_EN, PROPWASH_TAB_EN),
            new Guide("docs/guide.de.md", "de_de", "## 1. Erste Schritte", "Im Kreativmodus", "„", "“", TAB_DE, PROPWASH_TAB_DE));

    static Path root() {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        return resources.getParent().getParent().getParent().getParent();
    }

    static String modText(String language, String key) throws IOException {
        String value = null;
        try (Stream<Path> dirs = Files.list(root().resolve("common/src/lang"))) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path file = dir.resolve(language + ".json");
                if (Files.isRegularFile(file)) {
                    JsonElement entry = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().get(key);
                    if (entry != null) {
                        value = entry.getAsString();
                    }
                }
            }
        }
        assertNotNull(value, "Mob Defense " + language + ": " + key + " fehlt");
        return value;
    }

    static String propwashText(String language, String key) throws IOException {
        String path = "assets/propwash/lang/" + language + ".json";
        try (InputStream in = GuideCreativeTabTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, "Propwash " + path + " nicht im Klassenpfad");
            JsonObject lang = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(lang.has(key), "Propwash " + path + ": " + key + " fehlt");
            return lang.get(key).getAsString();
        }
    }

    static String tabTitle(String language) throws IOException {
        String pattern = propwashText(language, ExtensionTab.TITLE_KEY);
        assertTrue(pattern.contains("%s") || pattern.contains("%1$s"), ExtensionTab.TITLE_KEY + " ohne Platzhalter: " + pattern);
        String name = modText(language, MobDefenseTab.NAME_KEY);
        return pattern.replace("%1$s", name).replace("%s", name);
    }

    static String creativeSentence(Guide guide) throws IOException {
        String text = Files.readString(root().resolve(guide.file()), StandardCharsets.UTF_8);
        int start = text.indexOf(guide.heading());
        assertTrue(start >= 0, guide.file() + ": Abschnitt „" + guide.heading() + "“ fehlt");
        int end = text.indexOf("\n---", start);
        String section = text.substring(start, end < 0 ? text.length() : end).replaceAll("\\s+", " ");
        int from = section.indexOf(guide.marker());
        assertTrue(from >= 0, guide.file() + ": Satz „" + guide.marker() + " …“ fehlt in „" + guide.heading() + "“");
        int to = section.indexOf(". ", from);
        return section.substring(from, to < 0 ? section.length() : to + 1);
    }

    static int count(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    @Test
    void creativeTabTitleIsPropwashPrefixPlusOwnName() throws IOException {
        assertEquals("Mob Defense", modText("en_us", MobDefenseTab.NAME_KEY));
        assertEquals("Mob Defense", modText("de_de", MobDefenseTab.NAME_KEY));
        assertEquals("Propwash: Mob Defense", tabTitle("en_us"));
        assertEquals("Propwash: Mob Defense", tabTitle("de_de"));
    }

    @Test
    void guideNamesExactlyTheTabTheGameShows() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Guide guide : GUIDES) {
            String sentence = creativeSentence(guide);
            String title = guide.open() + tabTitle(guide.language()) + guide.close();
            if (!sentence.contains(title)) {
                problems.add(guide.file() + ": Reiter " + title + " fehlt in „" + sentence + "“");
            }
            int tabs = count(guide.tab(), sentence);
            if (tabs != 1) {
                problems.add(guide.file() + ": " + tabs + " Reiter statt genau einem in „" + sentence + "“");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void guideNeverPutsThePartsIntoPropwashsOwnTab() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Guide guide : GUIDES) {
            String text = Files.readString(root().resolve(guide.file()), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
            Matcher matcher = guide.propwashTab().matcher(text);
            while (matcher.find()) {
                problems.add(guide.file() + ": „" + matcher.group() + "“ – Propwash zeigt Teile von Add-ons mit eigenem Reiter nicht in seinem Reiter");
            }
            String main = guide.open() + propwashText(guide.language(), "itemGroup.propwash.main") + guide.close();
            assertFalse(creativeSentence(guide).contains(main), guide.file() + ": Propwash-Reiter " + main + " im Satz zum Kreativmodus");
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
