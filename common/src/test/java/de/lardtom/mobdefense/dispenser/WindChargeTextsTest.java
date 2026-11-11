package de.lardtom.mobdefense.dispenser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WindChargeTextsTest {
    private static final List<String> PLAYER_TEXTS = List.of("README.md", "CHANGELOG.md", "docs/guide.md", "docs/guide.de.md", "docs/modrinth/description.md",
            "docs/modrinth/description_de.md", "docs/modrinth/listing.md", "common/src/lang/dispenser/en_us.json", "common/src/lang/dispenser/de_de.json");
    private static final Pattern WIND_CHARGE = Pattern.compile("(?i)wind ?charge|windkugel");
    private static final Pattern FLIPS = Pattern.compile("(?iu)\\b(flip\\w*|tips? over|tipping over|topples?|overturns?|kipp\\w*|überschl\\w*)");

    static Path root() {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        return resources.getParent().getParent().getParent().getParent();
    }

    static String text(String relative) throws IOException {
        return Files.readString(root().resolve(relative), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
    }

    @Test
    void noPlayerTextSaysAWindChargeFlipsTheDrone() throws IOException {
        List<String> stale = new ArrayList<>();
        for (String file : PLAYER_TEXTS) {
            for (String sentence : text(file).split("(?<=[.!?;])\\s|\\s-\\s|\\|")) {
                if (WIND_CHARGE.matcher(sentence).find() && FLIPS.matcher(sentence).find()) {
                    stale.add(file + ": " + sentence.trim());
                }
            }
        }
        assertTrue(stale.isEmpty(), "Windkugel kippt die Drohne nicht mehr (E27), veraltet: " + stale);
    }

    @Test
    void theModrinthTextsSayTheDroneStaysFlyable() throws IOException {
        assertTrue(text("docs/modrinth/description.md").toLowerCase(Locale.ROOT).contains("flyable"), "description.md");
        assertTrue(text("docs/modrinth/description_de.md").toLowerCase(Locale.ROOT).contains("fliegbar"), "description_de.md");
    }

    @Test
    void decisionE26PointsToE27ForTheWindCharge() throws IOException {
        String row = Files.readAllLines(root().resolve("docs/design.md"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("| E26 |")).findFirst().orElseThrow();
        assertTrue(row.contains("E27"), "E26 nennt den Hebel der Windkugel ohne Verweis auf E27: " + row);
    }
}
