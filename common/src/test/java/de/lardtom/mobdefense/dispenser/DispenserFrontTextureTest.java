package de.lardtom.mobdefense.dispenser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispenserFrontTextureTest {
    static final String VANILLA_FRONT = "minecraft:block/dispenser_front";
    static final List<String> MODELS = List.of("dispenser_mount", "dispenser_mount_paint");

    static Path resources() {
        return Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
    }

    static JsonObject model(String name) throws IOException {
        Path file = resources().resolve("assets/mobdefense/models/part/accessory/" + name + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    static JsonObject element(JsonObject model, String name) {
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("name") && name.equals(object.get("name").getAsString())) {
                return object;
            }
        }
        return null;
    }

    static double[] vector(JsonObject element, String key) {
        JsonArray array = element.getAsJsonArray(key);
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    @Test
    void frontPlateShowsTheVanillaDispenserFront() throws IOException {
        for (String name : MODELS) {
            JsonObject model = model(name);
            JsonObject plate = element(model, "front_plate");
            assertNotNull(plate, name + ": Element front_plate fehlt");
            JsonObject north = plate.getAsJsonObject("faces").getAsJsonObject("north");
            assertNotNull(north, name + ": front_plate ohne Vorderseite");
            String reference = north.get("texture").getAsString();
            assertTrue(reference.startsWith("#"), name + ": Vorderseite ohne Textur-Variable: " + reference);
            JsonObject textures = model.getAsJsonObject("textures");
            String key = reference.substring(1);
            assertTrue(textures.has(key), name + ": Textur-Variable " + key + " fehlt");
            assertEquals(VANILLA_FRONT, textures.get(key).getAsString(), name + ": Vorderseite zeigt nicht die Vanilla-Textur des Dispensers");
            JsonArray uv = north.getAsJsonArray("uv");
            assertEquals(List.of(0.0, 0.0, 16.0, 16.0), List.of(uv.get(0).getAsDouble(), uv.get(1).getAsDouble(), uv.get(2).getAsDouble(),
                    uv.get(3).getAsDouble()), name + ": die Vorderseite zeigt die ganze Textur");
            assertFalse(north.has("tintindex"), name + ": die Vanilla-Vorderseite wird nicht lackiert");
        }
    }

    @Test
    void allTexturesShareTheBlockAtlas() throws IOException {
        for (String name : MODELS) {
            JsonObject textures = model(name).getAsJsonObject("textures");
            for (String key : textures.keySet()) {
                String sprite = textures.get(key).getAsString();
                String path = sprite.substring(sprite.indexOf(':') + 1);
                assertTrue(path.startsWith("block/"), name + ": Textur " + key + " = " + sprite
                        + " liegt nicht im Block-Atlas (Minecraft backt ein Item-Modell nur aus einem Atlas)");
                if (sprite.startsWith("mobdefense:")) {
                    assertTrue(Files.isRegularFile(resources().resolve("assets/mobdefense/textures/" + path + ".png")), sprite + " fehlt");
                }
            }
        }
    }

    @Test
    void nothingCoversTheVanillaFront() throws IOException {
        for (String name : MODELS) {
            JsonObject model = model(name);
            JsonObject plate = element(model, "front_plate");
            assertNotNull(plate, name + ": Element front_plate fehlt");
            double[] from = vector(plate, "from");
            double[] to = vector(plate, "to");
            List<String> covering = new ArrayList<>();
            for (JsonElement element : model.getAsJsonArray("elements")) {
                JsonObject other = element.getAsJsonObject();
                if (other == plate) {
                    continue;
                }
                double[] a = vector(other, "from");
                double[] b = vector(other, "to");
                boolean inFront = Math.min(a[2], b[2]) < from[2] - 1.0E-6;
                boolean overlaps = Math.max(a[0], b[0]) > from[0] && Math.min(a[0], b[0]) < to[0] && Math.max(a[1], b[1]) > from[1]
                        && Math.min(a[1], b[1]) < to[1];
                if (inFront && overlaps) {
                    covering.add(other.has("name") ? other.get("name").getAsString() : other.toString());
                }
            }
            assertTrue(covering.isEmpty(), name + ": vor der Vorderseite liegen " + covering);
        }
    }

    @Test
    void theVanillaTextureIsReferencedNotCopied() throws IOException {
        assertFalse(Files.exists(resources().resolve("assets/minecraft")), "keine Dateien im Namensraum minecraft (Verweis statt Kopie)");
        try (Stream<Path> files = Files.walk(resources().resolve("assets/mobdefense/textures"))) {
            List<String> copies = files.map(path -> path.getFileName().toString()).filter(file -> file.contains("dispenser_front")).toList();
            assertTrue(copies.isEmpty(), "eigene Kopie der Vorderseite: " + copies);
        }
    }
}
