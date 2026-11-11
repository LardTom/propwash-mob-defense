package de.lardtom.mobdefense.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancementFilesTest {
    static final List<String> AWARDED = List.of("air_support", "lance_flight", "afterburner", "recoil");

    private static JsonObject read(String id) throws IOException {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        Path file = resources.resolve("data/mobdefense/advancement/" + id + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void fourImpossibleAdvancementsUnderTheRoot() throws IOException {
        for (String id : AWARDED) {
            JsonObject json = read(id);
            assertEquals("mobdefense:root", json.get("parent").getAsString(), id);
            JsonObject criteria = json.getAsJsonObject("criteria");
            assertEquals(1, criteria.size(), id);
            assertEquals("minecraft:impossible", criteria.getAsJsonObject("done").get("trigger").getAsString(), id);
            JsonObject display = json.getAsJsonObject("display");
            assertEquals("advancements.mobdefense." + id + ".title", display.getAsJsonObject("title").get("translate").getAsString());
            assertEquals("advancements.mobdefense." + id + ".description", display.getAsJsonObject("description").get("translate").getAsString());
            assertTrue(display.getAsJsonObject("icon").get("id").getAsString().startsWith("minecraft:"), id);
        }
    }

    @Test
    void rootOpensTheTabWithTheFirstAccessory() throws IOException {
        JsonObject root = read("root");
        assertTrue(!root.has("parent"));
        String items = root.getAsJsonObject("criteria").getAsJsonObject("has_accessory").getAsJsonObject("conditions").getAsJsonArray("items")
                .get(0).getAsJsonObject().get("items").toString();
        for (String part : List.of("mobdefense:dispenser_mount", "mobdefense:spear_mount", "mobdefense:rocket_booster")) {
            assertTrue(items.contains(part), part);
        }
        assertEquals("minecraft:gui/advancements/backgrounds/adventure", root.getAsJsonObject("display").get("background").getAsString());
    }
}
