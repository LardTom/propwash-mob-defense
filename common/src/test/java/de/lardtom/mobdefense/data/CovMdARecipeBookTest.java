package de.lardtom.mobdefense.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdARecipeBookTest {
    record Design(String id, List<String> pattern, Map<String, String> key) {
    }

    static final List<Design> DESIGN = List.of(
            new Design("dispenser_mount", List.of(" B ", "NDN", " C "), ordered("B", "propwash:circuit_board", "N", "minecraft:iron_nugget", "D",
                    "minecraft:dispenser", "C", "propwash:copper_coil")),
            new Design("spear_mount", List.of("N N", "PPP"), ordered("N", "minecraft:iron_nugget", "P", "propwash:carbon_plate")),
            new Design("rocket_booster", List.of("NFN", " P "), ordered("N", "minecraft:copper_nugget", "F", "minecraft:flint_and_steel", "P",
                    "propwash:carbon_plate")));
    static final List<String> PROPWASH_ACCESSORIES = List.of("gripper", "led_strip");
    static final String GROUP = "propwash_accessory";
    static final String CATEGORY = "equipment";

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static JsonObject recipe(String id) {
        return DataFiles.object(DataFiles.data().resolve("recipe/" + id + ".json"));
    }

    private static JsonObject unlock(String id) {
        Path file = DataFiles.data().resolve("advancement/recipes/tools/" + id + ".json");
        assertTrue(Files.isRegularFile(file), id + ": Freischalt-Fortschritt fehlt");
        return DataFiles.object(file);
    }

    private static JsonObject propwashRecipe(String id) throws IOException {
        String path = "data/propwash/recipe/" + id + ".json";
        try (InputStream in = CovMdARecipeBookTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path + " nicht im Propwash-Jar auf dem Klassenpfad");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Map<String, String> key(JsonObject recipe) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : recipe.getAsJsonObject("key").entrySet()) {
            out.put(entry.getKey(), entry.getValue().getAsString());
        }
        return out;
    }

    @Test
    void patternsAndIngredientsAsInDesignTable22() {
        for (Design design : DESIGN) {
            JsonObject recipe = recipe(design.id());
            List<String> rows = new ArrayList<>();
            recipe.getAsJsonArray("pattern").forEach(row -> rows.add(row.getAsString()));
            assertEquals(design.pattern(), rows, design.id() + ": Muster");
            assertEquals(new TreeMap<>(design.key()), key(recipe), design.id() + ": Schlüssel");
            assertEquals(MobDefense.id(design.id()).toString(), recipe.getAsJsonObject("result").get("id").getAsString(), design.id() + ": Ergebnis");
            int count = recipe.getAsJsonObject("result").has("count") ? recipe.getAsJsonObject("result").get("count").getAsInt() : 1;
            assertEquals(1, count, design.id() + ": ein Teil je Rezept");
        }
    }

    @Test
    void sameRecipeBookGroupAndCategoryAsGripperAndLedStrip() throws IOException {
        for (String id : PROPWASH_ACCESSORIES) {
            JsonObject propwash = propwashRecipe(id);
            assertEquals(GROUP, propwash.get("group").getAsString(), "propwash:" + id + ": Gruppe");
            assertEquals(CATEGORY, propwash.get("category").getAsString(), "propwash:" + id + ": Kategorie");
        }
        for (Design design : DESIGN) {
            JsonObject recipe = recipe(design.id());
            assertEquals(GROUP, recipe.get("group").getAsString(), design.id() + ": Gruppe wie Greifer und LED-Streifen");
            assertEquals(CATEGORY, recipe.get("category").getAsString(), design.id() + ": Kategorie wie Greifer und LED-Streifen");
        }
    }

    @Test
    void unlockAdvancementHasOneHasItemCriterionPerIngredient() {
        for (Design design : DESIGN) {
            JsonObject json = unlock(design.id());
            assertEquals("minecraft:recipes/root", json.get("parent").getAsString(), design.id() + ": Eltern-Fortschritt");
            JsonObject criteria = json.getAsJsonObject("criteria");
            Set<String> expected = new TreeSet<>();
            for (String item : design.key().values()) {
                String name = "has_" + item.substring(item.indexOf(':') + 1);
                expected.add(name);
                assertTrue(criteria.has(name), design.id() + ": Kriterium " + name + " fehlt");
                JsonObject criterion = criteria.getAsJsonObject(name);
                assertEquals("minecraft:inventory_changed", criterion.get("trigger").getAsString(), design.id() + ": " + name);
                JsonArray items = criterion.getAsJsonObject("conditions").getAsJsonArray("items");
                assertEquals(1, items.size(), design.id() + ": " + name);
                assertEquals(item, items.get(0).getAsJsonObject().get("items").getAsString(), design.id() + ": " + name);
            }
            expected.add("has_the_recipe");
            assertEquals(expected, criteria.keySet(), design.id() + ": genau ein has_-Kriterium je Zutat und has_the_recipe");
            JsonArray requirements = json.getAsJsonArray("requirements");
            assertEquals(1, requirements.size(), design.id() + ": eine Zutat reicht");
            Set<String> any = new TreeSet<>();
            requirements.get(0).getAsJsonArray().forEach(name -> any.add(name.getAsString()));
            assertEquals(expected, any, design.id() + ": jede Zutat schaltet frei");
            assertEquals(List.of(MobDefense.id(design.id()).toString()), List.of(json.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0)
                    .getAsString()), design.id() + ": Belohnung");
        }
    }

    @Test
    void anIronNuggetUnlocksDispenserAndSpearMountButNotTheBooster() {
        Set<String> unlocked = new TreeSet<>();
        for (Design design : DESIGN) {
            if (unlock(design.id()).getAsJsonObject("criteria").has("has_iron_nugget")) {
                unlocked.add(design.id());
            }
        }
        assertEquals(Set.of("dispenser_mount", "spear_mount"), unlocked);
    }
}
