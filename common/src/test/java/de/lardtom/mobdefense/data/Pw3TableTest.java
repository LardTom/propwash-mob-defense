package de.lardtom.mobdefense.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.share.ShareTableCheck;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Pw3TableTest {
    static final List<String> ACCESSORIES = List.of("dispenser_mount", "spear_mount", "rocket_booster");
    private static final String BASELINE = "sharecode/mobdefense-baseline.json";

    private static Path table() {
        return DataFiles.data().resolve("propwash_share/table.json");
    }

    private static Path baseline() throws URISyntaxException {
        URL url = Pw3TableTest.class.getClassLoader().getResource(BASELINE);
        assertNotNull(url, BASELINE + " fehlt");
        return Path.of(url.toURI());
    }

    @Test
    void propwashCheckFindsNothingAgainstTheBaseline() throws IOException, URISyntaxException {
        List<ShareTableCheck.Finding> findings = ShareTableCheck.check(DataFiles.resources(), DataFiles.version(), baseline());
        assertTrue(findings.isEmpty(), "ShareTableCheck: " + findings);
    }

    @Test
    void numbersOneTwoThree() {
        JsonObject root = DataFiles.object(table());
        assertEquals(ShareTableCheck.FORMAT, root.get("format").getAsInt());
        assertEquals(MobDefense.MOD_ID, root.get("namespace").getAsString());
        JsonObject parts = root.getAsJsonObject("parts");
        assertEquals(List.of("accessory"), new ArrayList<>(parts.keySet()));
        List<String> ids = new ArrayList<>();
        for (JsonElement element : parts.getAsJsonArray("accessory")) {
            ids.add(element.getAsJsonObject().get("id").getAsString());
        }
        assertEquals(ACCESSORIES, ids.subList(0, Math.min(ids.size(), ACCESSORIES.size())), "Nummern 1 bis 3 bleiben für immer");
    }

    @Test
    void noFallbackPartAndNoShortIdWithoutTheDirectory() {
        JsonObject root = DataFiles.object(table());
        assertFalse(root.has("short_id"), "Kurzkennung erst nach Aufnahme ins offizielle Verzeichnis");
        for (Map.Entry<String, JsonElement> kind : root.getAsJsonObject("parts").entrySet()) {
            for (JsonElement element : kind.getValue().getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                assertFalse(entry.has("fallback"), entry + ": kein Ersatzteil, die Halterung bleibt ohne Mob Defense leer");
                assertFalse(entry.get("id").getAsString().contains(":"), entry + ": Id ohne Namensraum");
            }
        }
    }

    @Test
    void baselineIsAPrefixOfTheTable() throws IOException, URISyntaxException {
        Map<String, Map<String, List<String>>> frozen = ShareTableCheck.readBaseline(baseline());
        List<String> before = frozen.getOrDefault(MobDefense.MOD_ID, Map.of()).getOrDefault("accessory", List.of());
        assertEquals(ACCESSORIES, before);
        JsonArray now = DataFiles.object(table()).getAsJsonObject("parts").getAsJsonArray("accessory");
        assertTrue(now.size() >= before.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.get(i), now.get(i).getAsJsonObject().get("id").getAsString(), "Eintrag " + (i + 1));
        }
    }

    @Test
    void checkReportsInsertedAndMovedEntries(@TempDir Path temp) throws IOException, URISyntaxException {
        Path data = temp.resolve("data/mobdefense");
        Files.createDirectories(data.resolve("propwash_share"));
        Path parts = data.resolve("propwash_parts/accessory");
        Files.createDirectories(parts);
        for (String id : ACCESSORIES) {
            Files.copy(DataFiles.data().resolve("propwash_parts/accessory/" + id + ".json"), parts.resolve(id + ".json"));
        }
        Files.writeString(data.resolve("propwash_share/table.json"), """
                {"format": 1, "namespace": "mobdefense", "parts": {"accessory": [
                  {"id": "spear_mount", "since": "1.0.0"},
                  {"id": "dispenser_mount", "since": "1.0.0"},
                  {"id": "rocket_booster", "since": "1.0.0"}
                ]}}
                """);
        List<ShareTableCheck.Finding> findings = ShareTableCheck.check(temp, DataFiles.version(), baseline());
        assertTrue(findings.stream().anyMatch(finding -> finding.code().equals("ENTRY_MOVED")), "Verschieben bleibt unbemerkt: " + findings);
    }
}
