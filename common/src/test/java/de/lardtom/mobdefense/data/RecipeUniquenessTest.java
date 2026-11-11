package de.lardtom.mobdefense.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeUniquenessTest {
    private static final String SHAPED = "minecraft:crafting_shaped";
    private static final String SHAPELESS = "minecraft:crafting_shapeless";
    private static final String JMP_SNAPSHOT = "recipes/justmoreparts-1.1.1.json";

    record Recipe(String id, boolean shaped, List<List<Set<String>>> grid, List<Set<String>> ingredients) {
    }

    @Test
    void ownRecipesFollowThePropwashStyle() {
        List<Path> files = DataFiles.json(DataFiles.data().resolve("recipe"));
        Set<String> results = new TreeSet<>();
        for (Path file : files) {
            String id = DataFiles.stem(file);
            JsonObject recipe = DataFiles.object(file);
            assertEquals(SHAPED, recipe.get("type").getAsString(), id + ": geformtes Rezept");
            assertEquals("equipment", recipe.get("category").getAsString(), id + ": Kategorie");
            assertEquals("propwash_accessory", recipe.get("group").getAsString(), id + ": Gruppe wie Greifer und LED-Streifen");
            String result = recipe.getAsJsonObject("result").get("id").getAsString();
            assertEquals(MobDefense.id(id).toString(), result, id + ": Ergebnis");
            results.add(result);
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            JsonObject key = recipe.getAsJsonObject("key");
            Set<String> used = new HashSet<>();
            int width = pattern.get(0).getAsString().length();
            assertTrue(pattern.size() <= 3 && width <= 3, id + ": höchstens 3 × 3");
            for (JsonElement row : pattern) {
                assertEquals(width, row.getAsString().length(), id + ": Zeilen gleich lang");
                for (char c : row.getAsString().toCharArray()) {
                    if (c != ' ') {
                        assertTrue(key.has(String.valueOf(c)), id + ": Zeichen " + c + " ohne Schlüssel");
                        used.add(String.valueOf(c));
                    }
                }
            }
            assertEquals(key.keySet(), used, id + ": jeder Schlüssel im Muster");
            for (String symbol : key.keySet()) {
                String item = key.get(symbol).getAsString();
                String namespace = item.substring(0, item.indexOf(':'));
                assertTrue(namespace.equals("minecraft") || namespace.equals("propwash"), id + ": Zutat " + item + " nur aus Vanilla und Propwash");
                assertNotNull(RecipeUniquenessTest.class.getClassLoader().getResource("assets/" + namespace + "/items/" + item.substring(namespace.length() + 1)
                        + ".json"), id + ": Item " + item + " gibt es nicht");
            }
        }
        Set<String> expected = new TreeSet<>();
        MobDefense.PARTS.forEach(part -> expected.add(part.toString()));
        assertEquals(expected, results, "genau ein Rezept je Teil");
    }

    @Test
    void designPatterns() {
        assertEquals(List.of(" B ", "NDN", " C "), pattern("dispenser_mount"));
        assertEquals(List.of("N N", "PPP"), pattern("spear_mount"));
        assertEquals(List.of("NFN", " P "), pattern("rocket_booster"));
    }

    @Test
    void ownRecipesUnlockWithAnyIngredient() {
        for (Path file : DataFiles.json(DataFiles.data().resolve("recipe"))) {
            String id = DataFiles.stem(file);
            Path advancement = DataFiles.data().resolve("advancement/recipes/tools/" + id + ".json");
            assertTrue(Files.isRegularFile(advancement), id + ": Freischaltung fehlt");
            JsonObject json = DataFiles.object(advancement);
            JsonObject criteria = json.getAsJsonObject("criteria");
            Set<String> items = new TreeSet<>();
            for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
                JsonObject value = criterion.getValue().getAsJsonObject();
                if ("minecraft:inventory_changed".equals(value.get("trigger").getAsString())) {
                    items.add(value.getAsJsonObject("conditions").getAsJsonArray("items").get(0).getAsJsonObject().get("items").getAsString());
                } else {
                    assertEquals("minecraft:recipe_unlocked", value.get("trigger").getAsString());
                    assertEquals(MobDefense.id(id).toString(), value.getAsJsonObject("conditions").get("recipes").getAsString());
                }
            }
            Set<String> ingredients = new TreeSet<>();
            DataFiles.object(file).getAsJsonObject("key").entrySet().forEach(entry -> ingredients.add(entry.getValue().getAsString()));
            assertEquals(ingredients, items, id + ": je Zutat ein Kriterium");
            JsonArray requirements = json.getAsJsonArray("requirements");
            assertEquals(1, requirements.size(), id + ": eine Zutat reicht");
            assertEquals(criteria.size(), requirements.get(0).getAsJsonArray().size(), id + ": alle Kriterien in einer Gruppe");
            assertEquals(MobDefense.id(id).toString(), json.getAsJsonObject("rewards").getAsJsonArray("recipes").get(0).getAsString());
        }
    }

    @Test
    void patternsAreUniqueAgainstPropwashJustMorePartsAndVanilla() throws IOException {
        List<Recipe> own = new ArrayList<>();
        for (Path file : DataFiles.json(DataFiles.data().resolve("recipe"))) {
            own.add(recipe(MobDefense.id(DataFiles.stem(file)).toString(), DataFiles.object(file)));
        }
        List<Recipe> others = new ArrayList<>();
        int propwash = load(others, "data/propwash/recipe/gripper.json", "data/propwash/recipe", "propwash");
        int vanilla = load(others, "data/minecraft/recipe/dispenser.json", "data/minecraft/recipe", "minecraft");
        int jmp = 0;
        URL snapshot = RecipeUniquenessTest.class.getClassLoader().getResource(JMP_SNAPSHOT);
        assertNotNull(snapshot, JMP_SNAPSHOT + " fehlt");
        try (var in = snapshot.openStream()) {
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : root.getAsJsonArray("recipes")) {
                JsonObject entry = element.getAsJsonObject();
                others.add(recipe(entry.get("id").getAsString(), entry));
                jmp++;
            }
        }
        assertTrue(propwash >= 50, "Propwash-Rezepte nicht gefunden: " + propwash);
        assertTrue(vanilla >= 500, "Vanilla-Rezepte nicht gefunden: " + vanilla);
        assertEquals(143, jmp, "Rezepte von Just More Parts 1.1.1");
        List<String> conflicts = new ArrayList<>();
        for (int i = 0; i < own.size(); i++) {
            for (int j = i + 1; j < own.size(); j++) {
                if (conflict(own.get(i), own.get(j))) {
                    conflicts.add(own.get(i).id() + " ↔ " + own.get(j).id());
                }
            }
            for (Recipe other : others) {
                if (conflict(own.get(i), other)) {
                    conflicts.add(own.get(i).id() + " ↔ " + other.id());
                }
            }
        }
        assertTrue(conflicts.isEmpty(), "Muster nicht eindeutig: " + conflicts);
    }

    @Test
    void conflictCheckFindsCopies() {
        JsonObject gripperLike = JsonParser.parseString("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [\"NFN\", \" P \"],"
                + " \"key\": {\"N\": [\"minecraft:copper_nugget\", \"minecraft:gold_nugget\"], \"F\": \"minecraft:flint_and_steel\","
                + " \"P\": \"propwash:carbon_plate\"}}").getAsJsonObject();
        Recipe booster = recipe("mobdefense:rocket_booster", DataFiles.object(DataFiles.data().resolve("recipe/rocket_booster.json")));
        assertTrue(conflict(booster, recipe("copy", gripperLike)));
        JsonObject mirrored = JsonParser.parseString("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [\"   \", \"BN \", \"D  \"],"
                + " \"key\": {\"B\": \"propwash:circuit_board\", \"N\": \"minecraft:iron_nugget\", \"D\": \"minecraft:dispenser\"}}").getAsJsonObject();
        JsonObject original = JsonParser.parseString("{\"type\": \"minecraft:crafting_shaped\", \"pattern\": [\"NB\", \" D\"],"
                + " \"key\": {\"B\": \"propwash:circuit_board\", \"N\": \"minecraft:iron_nugget\", \"D\": \"minecraft:dispenser\"}}").getAsJsonObject();
        assertTrue(conflict(recipe("a", original), recipe("b", mirrored)));
        JsonObject shapeless = JsonParser.parseString("{\"type\": \"minecraft:crafting_shapeless\", \"ingredients\": [\"minecraft:iron_nugget\","
                + " \"minecraft:iron_nugget\", \"propwash:carbon_plate\", \"propwash:carbon_plate\", \"propwash:carbon_plate\"]}").getAsJsonObject();
        Recipe spear = recipe("mobdefense:spear_mount", DataFiles.object(DataFiles.data().resolve("recipe/spear_mount.json")));
        assertTrue(conflict(spear, recipe("loose", shapeless)));
    }

    private static List<String> pattern(String id) {
        List<String> rows = new ArrayList<>();
        DataFiles.object(DataFiles.data().resolve("recipe/" + id + ".json")).getAsJsonArray("pattern").forEach(row -> rows.add(row.getAsString()));
        return rows;
    }

    private static int load(List<Recipe> out, String probe, String folder, String namespace) throws IOException {
        URL url = RecipeUniquenessTest.class.getClassLoader().getResource(probe);
        assertNotNull(url, probe + " nicht auf dem Klassenpfad");
        Path root = root(url, probe);
        Path dir = root.resolve(folder);
        int count = 0;
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject json = element.getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "";
                if (!type.equals(SHAPED) && !type.equals(SHAPELESS)) {
                    continue;
                }
                String relative = dir.relativize(file).toString().replace('\\', '/');
                out.add(recipe(namespace + ":" + relative.substring(0, relative.length() - ".json".length()), json));
                count++;
            }
        }
        return count;
    }

    private static Path root(URL url, String probe) throws IOException {
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                FileSystem fs;
                try {
                    fs = FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException e) {
                    fs = FileSystems.getFileSystem(uri);
                }
                return fs.getPath("/");
            }
            Path file = Path.of(uri);
            Path root = file;
            for (int i = 0; i < probe.split("/").length; i++) {
                root = root.getParent();
            }
            return root;
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    static Recipe recipe(String id, JsonObject json) {
        String type = json.get("type").getAsString();
        if (type.equals(SHAPELESS)) {
            List<Set<String>> ingredients = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("ingredients")) {
                ingredients.add(ingredient(element));
            }
            return new Recipe(id, false, List.of(), ingredients);
        }
        JsonObject key = json.getAsJsonObject("key");
        Map<Character, Set<String>> symbols = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            symbols.put(entry.getKey().charAt(0), ingredient(entry.getValue()));
        }
        List<List<Set<String>>> grid = new ArrayList<>();
        for (JsonElement row : json.getAsJsonArray("pattern")) {
            List<Set<String>> cells = new ArrayList<>();
            for (char c : row.getAsString().toCharArray()) {
                cells.add(c == ' ' ? Set.of() : symbols.getOrDefault(c, Set.of("?" + c)));
            }
            grid.add(cells);
        }
        List<List<Set<String>>> shrunk = shrink(grid);
        List<Set<String>> ingredients = new ArrayList<>();
        shrunk.forEach(row -> row.stream().filter(cell -> !cell.isEmpty()).forEach(ingredients::add));
        return new Recipe(id, true, shrunk, ingredients);
    }

    private static Set<String> ingredient(JsonElement element) {
        Set<String> out = new TreeSet<>();
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(item -> out.addAll(ingredient(item)));
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("item")) {
                out.add(object.get("item").getAsString());
            } else if (object.has("tag")) {
                out.add("#" + object.get("tag").getAsString());
            }
        } else {
            out.add(element.getAsString());
        }
        return out;
    }

    private static List<List<Set<String>>> shrink(List<List<Set<String>>> grid) {
        int top = grid.size();
        int bottom = -1;
        int left = Integer.MAX_VALUE;
        int right = -1;
        for (int y = 0; y < grid.size(); y++) {
            for (int x = 0; x < grid.get(y).size(); x++) {
                if (!grid.get(y).get(x).isEmpty()) {
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        List<List<Set<String>>> out = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            out.add(new ArrayList<>(grid.get(y).subList(left, right + 1)));
        }
        return out;
    }

    static boolean conflict(Recipe a, Recipe b) {
        if (a.shaped() && b.shaped()) {
            return sameGrid(a.grid(), b.grid(), false) || sameGrid(a.grid(), b.grid(), true);
        }
        return a.ingredients().size() == b.ingredients().size() && matches(a.ingredients(), b.ingredients(), 0, new boolean[b.ingredients().size()]);
    }

    private static boolean sameGrid(List<List<Set<String>>> a, List<List<Set<String>>> b, boolean mirror) {
        if (a.size() != b.size() || a.isEmpty() || a.get(0).size() != b.get(0).size()) {
            return false;
        }
        int width = a.get(0).size();
        for (int y = 0; y < a.size(); y++) {
            for (int x = 0; x < width; x++) {
                Set<String> left = a.get(y).get(x);
                Set<String> right = b.get(y).get(mirror ? width - 1 - x : x);
                if (left.isEmpty() != right.isEmpty() || !left.isEmpty() && !overlap(left, right)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matches(List<Set<String>> a, List<Set<String>> b, int index, boolean[] taken) {
        if (index == a.size()) {
            return true;
        }
        for (int i = 0; i < b.size(); i++) {
            if (!taken[i] && overlap(a.get(index), b.get(i))) {
                taken[i] = true;
                if (matches(a, b, index + 1, taken)) {
                    return true;
                }
                taken[i] = false;
            }
        }
        return false;
    }

    private static boolean overlap(Set<String> a, Set<String> b) {
        for (String item : a) {
            if (b.contains(item)) {
                return true;
            }
        }
        return false;
    }
}
