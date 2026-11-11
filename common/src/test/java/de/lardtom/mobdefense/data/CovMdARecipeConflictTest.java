package de.lardtom.mobdefense.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdARecipeConflictTest {
    static final String SNAPSHOT = "cov/cov-md-a/justmoreparts-1.2.0-recipes.json";
    static final String RECIPES_IN_JAR = "data/justmoreparts/recipe";
    static final int JMP_RECIPES = 146;

    static List<JsonObject> snapshot() throws IOException {
        try (InputStream in = CovMdARecipeConflictTest.class.getClassLoader().getResourceAsStream(SNAPSHOT)) {
            assertNotNull(in, SNAPSHOT + " fehlt");
            JsonObject root = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            List<JsonObject> out = new ArrayList<>();
            root.getAsJsonArray("recipes").forEach(element -> out.add(element.getAsJsonObject()));
            return out;
        }
    }

    @Test
    void snapshotHasEveryCraftingRecipeOfJustMoreParts120() throws IOException {
        List<JsonObject> recipes = snapshot();
        assertEquals(JMP_RECIPES, recipes.size(), "Werkbank-Rezepte von Just More Parts 1.2.0");
        String jar = System.getenv(CovMdAJustMorePartsTest.JAR_ENV);
        if (jar == null || jar.isBlank()) {
            return;
        }
        TreeSet<String> fromJar = new TreeSet<>();
        try (FileSystem fs = FileSystems.newFileSystem(Path.of(jar), Map.of()); Stream<Path> files = Files.walk(fs.getPath(RECIPES_IN_JAR))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                String type = json.getAsJsonObject().get("type").getAsString();
                if (type.equals("minecraft:crafting_shaped") || type.equals("minecraft:crafting_shapeless")) {
                    String relative = fs.getPath(RECIPES_IN_JAR).relativize(file).toString().replace('\\', '/');
                    fromJar.add("justmoreparts:" + relative.substring(0, relative.length() - ".json".length()));
                }
            }
        }
        TreeSet<String> ids = new TreeSet<>();
        recipes.forEach(recipe -> ids.add(recipe.get("id").getAsString()));
        assertEquals(fromJar, ids, "Abzug weicht vom Jar " + jar + " ab");
    }

    @Test
    void ownPatternsDoNotCollideWithJustMoreParts120() throws IOException {
        List<RecipeUniquenessTest.Recipe> own = new ArrayList<>();
        for (Path file : DataFiles.json(DataFiles.data().resolve("recipe"))) {
            own.add(RecipeUniquenessTest.recipe(MobDefense.id(DataFiles.stem(file)).toString(), DataFiles.object(file)));
        }
        assertEquals(3, own.size());
        List<String> conflicts = new ArrayList<>();
        for (JsonObject entry : snapshot()) {
            RecipeUniquenessTest.Recipe other = RecipeUniquenessTest.recipe(entry.get("id").getAsString(), entry);
            for (RecipeUniquenessTest.Recipe mine : own) {
                if (RecipeUniquenessTest.conflict(mine, other)) {
                    conflicts.add(mine.id() + " ↔ " + other.id());
                }
            }
        }
        assertTrue(conflicts.isEmpty(), "Muster gleich wie in Just More Parts 1.2.0: " + conflicts);
    }
}
