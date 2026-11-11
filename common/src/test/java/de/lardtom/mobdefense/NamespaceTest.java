package de.lardtom.mobdefense;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamespaceTest {
    private static final Set<String> ALLOWED = Set.of(MobDefense.MOD_ID, "minecraft", "c");
    private static final Set<String> REFERENCED = Set.of(MobDefense.MOD_ID, "minecraft", "c", "propwash");
    private static final Pattern ID = Pattern.compile("#?([a-z0-9_.-]+):[a-z0-9_./-]+");

    private static Path resources() {
        return Path.of(System.getProperty("mobdefense.resources", "src/main/resources"));
    }

    @Test
    void modIdIsOwnNamespace() {
        assertEquals("mobdefense", MobDefense.MOD_ID);
        assertNotEquals("propwash", MobDefense.MOD_ID);
    }

    @Test
    void resourcesUseOnlyOwnNamespace() throws IOException {
        Path resources = resources();
        for (String root : List.of("assets", "data")) {
            Path dir = resources.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(dir)) {
                for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                    String name = namespace.getFileName().toString();
                    assertTrue(ALLOWED.contains(name), root + "/" + name + " liegt nicht im eigenen Namensraum");
                }
            }
        }
    }

    @Test
    void ownIdsLiveInTheOwnNamespace() {
        for (var part : MobDefense.PARTS) {
            assertEquals(MobDefense.MOD_ID, part.getNamespace(), part.toString());
        }
        assertEquals(MobDefense.MOD_ID, MobDefense.id("allow_tnt").getNamespace());
        assertEquals(MobDefense.DISPENSER_MOUNT, MobDefenseTab.ICON);
    }

    @Test
    void dataReferencesOnlyKnownNamespacesAndCreatesNothingForPropwash() throws IOException {
        Path data = resources().resolve("data").resolve(MobDefense.MOD_ID);
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(data)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                scan(data.relativize(file).toString(), "", json, problems);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static void scan(String file, String field, JsonElement json, List<String> problems) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> scan(file, entry.getKey(), entry.getValue(), problems));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> scan(file, field, element, problems));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            var matcher = ID.matcher(json.getAsString());
            if (!matcher.matches()) {
                return;
            }
            String namespace = matcher.group(1);
            if (!REFERENCED.contains(namespace)) {
                problems.add(file + ": fremder Namensraum in " + json.getAsString());
            }
            boolean created = Set.of("id", "recipes").contains(field) && (file.startsWith("recipe") || file.startsWith("advancement"));
            if (created && json.getAsString().startsWith("propwash:")) {
                problems.add(file + ": " + field + " " + json.getAsString() + " im gesperrten Namensraum propwash");
            }
        }
    }
}
