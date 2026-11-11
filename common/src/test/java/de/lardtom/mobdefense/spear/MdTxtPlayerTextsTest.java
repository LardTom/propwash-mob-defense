package de.lardtom.mobdefense.spear;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MdTxtPlayerTextsTest {
    private static final List<String> ENGLISH_DOCS = List.of("README.md", "CHANGELOG.md", "docs/guide.md", "docs/modrinth/description.md",
            "docs/modrinth/listing.md");
    private static final List<String> GERMAN_DOCS = List.of("docs/guide.de.md", "docs/modrinth/description_de.md");
    private static final List<String> REDUCED_SPEAR_DAMAGE = List.of("half the vanilla spear damage", "half of the vanilla spear damage",
            "half vanilla spear damage", "weaker than in the hand", "weaker than in your hand", "halber vanilla-speerschaden",
            "halben vanilla-speerschaden", "halbe vanilla-speerschaden", "schwächer als in der hand");
    private static final List<String> TNT_OFF_BY_DEFAULT = List.of("tnt needs allow_tnt", "tnt only with", "only with `allow_tnt`",
            "tnt (off", "tnt braucht", "tnt nur mit", "nur mit `allow_tnt`", "tnt (standard aus");
    private static final Pattern DRONE_JOUST = Pattern.compile("(?i)drone\\s+joust");
    private static final String SPEAR_CALLOUT = "mobdefense.guide.scene.spear.callout.speed";
    private static final String RULES_CALLOUT = "mobdefense.guide.scene.dispenser.callout.rules";
    private static final String TNT_RULE = "gamerule.mobdefense.allow_tnt";
    private static final String JOUST_TITLE = "advancements.mobdefense.lance_flight.title";

    static Path root() {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        return resources.getParent().getParent().getParent().getParent();
    }

    static String raw(String relative) throws IOException {
        return Files.readString(root().resolve(relative), StandardCharsets.UTF_8);
    }

    static String doc(String relative) throws IOException {
        return raw(relative).replaceAll("\\s+", " ");
    }

    static Map<String, String> lang(String language) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (Stream<Path> dirs = Files.list(root().resolve("common/src/lang"))) {
            for (Path dir : dirs.filter(Files::isDirectory).sorted().toList()) {
                Path file = dir.resolve(language + ".json");
                if (Files.isRegularFile(file)) {
                    for (Map.Entry<String, JsonElement> entry : JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                            .getAsJsonObject().entrySet()) {
                        out.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        }
        return out;
    }

    static Map<String, String> playerTexts() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String language : List.of("en_us", "de_de")) {
            lang(language).forEach((key, value) -> out.put(language + " " + key, value));
        }
        for (String file : ENGLISH_DOCS) {
            out.put(file, doc(file));
        }
        for (String file : GERMAN_DOCS) {
            out.put(file, doc(file));
        }
        return out;
    }

    static List<String> containing(Map<String, String> texts, List<String> phrases) {
        List<String> hits = new ArrayList<>();
        texts.forEach((where, text) -> {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String phrase : phrases) {
                if (lower.contains(phrase)) {
                    hits.add(where + ": „" + phrase + "“");
                }
            }
        });
        return hits;
    }

    static String tableRow(String text, String id) {
        for (String line : text.split("\n")) {
            if (line.startsWith("|") && line.contains("| `" + id + "` |")) {
                return line;
            }
        }
        return "";
    }

    @Test
    void spearTextsNameTheFullVanillaDamage() throws IOException {
        assertEquals(100, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
        List<String> hits = containing(playerTexts(), REDUCED_SPEAR_DAMAGE);
        assertTrue(hits.isEmpty(), "Texte nennen weniger als den Vanilla-Speerschaden: " + hits);
        String english = lang("en_us").get(SPEAR_CALLOUT);
        String german = lang("de_de").get(SPEAR_CALLOUT);
        assertTrue(english.contains("vanilla spear damage") && !english.toLowerCase(Locale.ROOT).contains("half"), english);
        assertTrue(german.contains("Vanilla-Speerschaden") && !german.toLowerCase(Locale.ROOT).contains("halb"), german);
        assertTrue(doc("docs/guide.md").contains("*Spear damage* (100 % by default)"));
        assertTrue(doc("docs/guide.de.md").contains("„Speerschaden“ (Standard 100 %)"));
        assertTrue(doc("README.md").contains("vanilla spear damage (world rule, 100 % by default)"));
        assertTrue(doc("docs/modrinth/description.md").contains("with vanilla spear damage"));
        assertTrue(doc("docs/modrinth/description_de.md").contains("mit Vanilla-Speerschaden"));
    }

    @Test
    void theSpearAdvancementIsCalledDroneJoustEverywhere() throws IOException {
        assertEquals("Drone Joust", lang("en_us").get(JOUST_TITLE));
        assertEquals("Lanzenflug", lang("de_de").get(JOUST_TITLE));
        List<String> wrong = new ArrayList<>();
        playerTexts().forEach((where, text) -> {
            if (text.toLowerCase(Locale.ROOT).contains("lance flight")) {
                wrong.add(where + ": „Lance flight“");
            }
            Matcher matcher = DRONE_JOUST.matcher(text);
            while (matcher.find()) {
                if (!matcher.group().equals("Drone Joust")) {
                    wrong.add(where + ": „" + matcher.group() + "“");
                }
            }
        });
        assertTrue(wrong.isEmpty(), "Fortschritt heißt „Drone Joust“: " + wrong);
        assertTrue(doc("docs/guide.md").contains("*Drone Joust* (defeat a mob with the spear mount)"));
        assertTrue(doc("CHANGELOG.md").contains("*Drone Joust*"));
        assertTrue(doc("docs/guide.de.md").contains("„Lanzenflug“ (Mob mit der Speer-Halterung besiegen)"));
    }

    @Test
    void tntIsAllowedByDefaultAndTheWorldRuleTurnsItOff() throws IOException {
        assertTrue(RuleDefaults.ALLOW_TNT.defaultFlag());
        Map<String, String> english = lang("en_us");
        Map<String, String> german = lang("de_de");
        List<String> hits = containing(playerTexts(), TNT_OFF_BY_DEFAULT);
        assertTrue(hits.isEmpty(), "Texte nennen TNT als standardmäßig aus: " + hits);
        assertTrue(english.get(TNT_RULE + ".description").contains("Off: the dispenser refuses TNT."), english.get(TNT_RULE + ".description"));
        assertTrue(german.get(TNT_RULE + ".description").contains("Aus: Der Dispenser verweigert TNT."), german.get(TNT_RULE + ".description"));
        assertTrue(english.get(RULES_CALLOUT).contains("TNT is allowed by default"), english.get(RULES_CALLOUT));
        assertTrue(german.get(RULES_CALLOUT).contains("TNT ist standardmäßig erlaubt"), german.get(RULES_CALLOUT));
        String guide = doc("docs/guide.md");
        String guideDe = doc("docs/guide.de.md");
        assertTrue(guide.contains("TNT is allowed by default; the world rule *TNT from the dispenser* turns it off"));
        assertTrue(guideDe.contains("TNT ist standardmäßig erlaubt; die Welt-Regel „TNT aus dem Dispenser“ schaltet es ab"));
        String row = tableRow(raw("docs/guide.md"), "allow_tnt");
        assertTrue(row.startsWith("| " + english.get(TNT_RULE) + " | `allow_tnt` | on | – | " + english.get(TNT_RULE + ".description") + " |"), row);
        String rowDe = tableRow(raw("docs/guide.de.md"), "allow_tnt");
        assertTrue(rowDe.startsWith("| " + german.get(TNT_RULE) + " | `allow_tnt` | an | – | " + german.get(TNT_RULE + ".description") + " |"), rowDe);
        assertTrue(doc("README.md").contains("TNT (allowed by default)"));
        assertTrue(doc("docs/modrinth/description.md").contains("TNT (allowed by default)"));
        assertTrue(doc("docs/modrinth/description_de.md").contains("TNT (standardmäßig erlaubt)"));
    }
}
