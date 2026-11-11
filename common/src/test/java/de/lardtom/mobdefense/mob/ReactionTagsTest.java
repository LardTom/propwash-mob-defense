package de.lardtom.mobdefense.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionTagsTest {
    private static final Map<String, String> DESIGN = Map.ofEntries(
            entry("zombie", "chase"), entry("husk", "chase"), entry("drowned", "chase"), entry("zombie_villager", "chase"),
            entry("zombie_nautilus", "chase"),
            entry("zombified_piglin", "brute"), entry("vindicator", "brute"), entry("piglin_brute", "brute"), entry("ravager", "brute"),
            entry("hoglin", "brute"), entry("zoglin", "brute"), entry("wither_skeleton", "brute"),
            entry("skeleton", "archer"), entry("stray", "archer"), entry("bogged", "archer"), entry("parched", "archer"),
            entry("illusioner", "archer"), entry("pillager", "archer"), entry("piglin", "archer"),
            entry("blaze", "fireball"), entry("ghast", "fireball"),
            entry("breeze", "wind"),
            entry("shulker", "bullet"),
            entry("witch", "potion"),
            entry("evoker", "caster"),
            entry("phantom", "flyer"), entry("vex", "flyer"),
            entry("creeper", "creeper"),
            entry("slime", "jumper"), entry("magma_cube", "jumper"), entry("sulfur_cube", "jumper"), entry("spider", "jumper"),
            entry("cave_spider", "jumper"),
            entry("guardian", "beam"), entry("elder_guardian", "beam"),
            entry("enderman", "stare"), entry("creaking", "stare"),
            entry("wither", "boss"),
            entry("silverfish", "ground"), entry("endermite", "ground"),
            entry("warden", "warden"));
    private static final Set<String> EXCEPTIONS = Set.of("ender_dragon", "giant", "camel_husk", "zombie_horse");
    private static final Set<String> OPTIONAL = Set.of("warden");
    private static final Map<String, List<String>> VARIANTS = Map.of("chase", List.of("trident"));
    private static final String FALLBACK = "ground";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Path tags() {
        return Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).resolve("data").resolve(MobDefense.MOD_ID)
                .resolve("tags").resolve("entity_type");
    }

    private static Set<String> values(Path file) {
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : root.getAsJsonArray("values")) {
            String value = element.getAsString();
            assertTrue(value.startsWith("minecraft:"), file.getFileName() + ": nur Vanilla-Ids ohne Tag-Verweis, nicht " + value);
            assertTrue(ids.add(value.substring("minecraft:".length())), file.getFileName() + ": doppelt " + value);
        }
        return ids;
    }

    private static Map<String, Set<String>> reactsTags() {
        Map<String, Set<String>> out = new TreeMap<>();
        Path dir = tags().resolve("reacts");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                out.put(name.substring(0, name.length() - ".json".length()), values(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static Set<String> hostileVanilla() {
        Set<String> out = new TreeSet<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if ("minecraft".equals(id.getNamespace()) && type.getCategory() == MobCategory.MONSTER) {
                out.add(id.getPath());
            }
        }
        return out;
    }

    @Test
    void everyHostileVanillaMobIsPlannedExactlyOnce() {
        Set<String> hostile = hostileVanilla();
        assertTrue(hostile.size() > 40, "zu wenige feindliche Vanilla-Mobs gefunden: " + hostile);
        List<String> problems = new ArrayList<>();
        for (String id : hostile) {
            boolean planned = DESIGN.containsKey(id);
            boolean excepted = EXCEPTIONS.contains(id);
            if (planned == excepted) {
                problems.add(id + (planned ? " steht in der Tabelle und auf der Ausnahmeliste" : " fehlt in Tabelle und Ausnahmeliste"));
            }
        }
        for (String id : DESIGN.keySet()) {
            if (!hostile.contains(id)) {
                problems.add(id + " ist kein feindlicher Vanilla-Mob in 26.3");
            }
        }
        assertEquals(List.of(), problems);
    }

    private static Set<String> waiting() {
        Set<String> registered = new TreeSet<>(ReactionKinds.names());
        Set<String> out = new TreeSet<>();
        DESIGN.forEach((id, kind) -> {
            if (OPTIONAL.contains(kind) && !registered.contains(kind)) {
                out.add(id);
            }
        });
        return out;
    }

    @Test
    void everyHostileVanillaMobIsInExactlyOneTagOrIgnored() {
        Map<String, Set<String>> tags = reactsTags();
        Set<String> ignored = values(tags().resolve("ignores_drones.json"));
        Set<String> expectedIgnored = new TreeSet<>(EXCEPTIONS);
        expectedIgnored.addAll(waiting());
        assertEquals(expectedIgnored, new TreeSet<>(ignored), "ignores_drones = Ausnahmeliste und Mobs offener Zusatz-Arten");
        List<String> problems = new ArrayList<>();
        for (String id : hostileVanilla()) {
            List<String> in = new ArrayList<>();
            tags.forEach((kind, ids) -> {
                if (ids.contains(id)) {
                    in.add(kind);
                }
            });
            boolean excepted = ignored.contains(id);
            int places = in.size() + (excepted ? 1 : 0);
            if (places != 1) {
                problems.add(id + ": Tags " + in + (excepted ? ", ignores_drones" : ""));
            }
        }
        assertEquals(List.of(), problems);
    }

    @Test
    void tagsFollowTheDesignTable() {
        List<String> problems = new ArrayList<>();
        reactsTags().forEach((kind, ids) -> {
            for (String id : ids) {
                if (!kind.equals(DESIGN.get(id))) {
                    problems.add(id + " steht in reacts/" + kind + ", laut Design " + DESIGN.get(id));
                }
            }
        });
        assertEquals(List.of(), problems);
    }

    @Test
    void everyDesignKindIsImplementedWithACompleteTag() {
        Map<String, Set<String>> tags = reactsTags();
        Set<String> registered = new TreeSet<>(ReactionKinds.names());
        assertEquals(registered, tags.keySet(), "je umgesetzter Art genau ein Tag reacts/<art>");
        Set<String> missing = new TreeSet<>(DESIGN.values());
        missing.removeAll(registered);
        missing.removeAll(OPTIONAL);
        assertEquals(Set.of(), missing, "Arten aus dem Design ohne Umsetzung");
        Map<String, Set<String>> expected = new HashMap<>();
        DESIGN.forEach((id, kind) -> expected.computeIfAbsent(kind, key -> new TreeSet<>()).add(id));
        for (String kind : registered) {
            assertEquals(expected.getOrDefault(kind, Set.of()), new TreeSet<>(tags.get(kind)), "reacts/" + kind + " vollständig");
        }
    }

    @Test
    void kindsAreRegisteredInOrder() {
        assertEquals(List.of("chase", "brute", "archer", "fireball", "creeper", "flyer"), ReactionKinds.names().subList(0, 6));
        assertEquals(List.of("stare", "beam", "caster", "boss", "ground", "wind", "bullet", "potion", "jumper"), ReactionKinds.names().subList(6, 15));
        for (String kind : ReactionKinds.names()) {
            assertEquals(MobDefense.id("reacts/" + kind), ReactionTags.reacts(kind).location());
        }
        assertEquals(MobDefense.id("ignores_drones"), ReactionTags.IGNORES_DRONES.location());
    }

    @Test
    void tridentIsAVariantOfChaseAndGroundCatchesUntaggedEnemies() {
        assertEquals(VARIANTS, ReactionKinds.variants());
        for (List<String> variants : VARIANTS.values()) {
            for (String variant : variants) {
                assertFalse(ReactionKinds.names().contains(variant), variant + " ist Variante, keine eigene Art mit Tag");
            }
        }
        Reaction fallback = ReactionKinds.fallback();
        assertTrue(fallback != null && FALLBACK.equals(fallback.kind()), "Auffang-Art für Enemy ohne Tag: " + FALLBACK);
        assertTrue(ReactionKinds.find(FALLBACK).filter(found -> found == fallback).isPresent(), "Auffang-Art ist angemeldet");
    }
}
