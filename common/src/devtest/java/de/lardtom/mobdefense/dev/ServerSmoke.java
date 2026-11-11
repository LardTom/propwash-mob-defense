package de.lardtom.mobdefense.dev;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.PartDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerSmoke {
    public static final String PROPERTY = "mobdefense.serversmoke";
    public static final String HOLD_ENV = "MOBDEFENSE_JOINTEST_HOLD";
    public static final String STOP_FILE = "jointest.stop";
    public static final int RUN_TICKS = 100;
    public static final Identifier PW3_TABLE = MobDefense.id("propwash_share/table.json");
    public static final Set<String> OPTIONAL_RULES = Set.of(RuleDefaults.SCULK_HEARS_DRONES.path());
    private static final String TAG = "[Mob-Defense-Server-Rauchtest]";

    private static final List<String> NOTES = new ArrayList<>();
    private static final Set<String> PLAYERS = new HashSet<>();
    private static final AtomicBoolean FINISHED = new AtomicBoolean();
    private static List<ServerChecks.Check> checks = List.of();
    private static int ticksSinceStart = -1;
    private static int joins;
    private static volatile @Nullable String failure;

    private ServerSmoke() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static int holdSeconds() {
        String value = System.getenv(HOLD_ENV);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static void init() {
        if (!enabled()) {
            return;
        }
        ServerChecks registry = new ServerChecks();
        for (DevModule module : DevModules.all()) {
            module.serverChecks(registry.module(module.name()));
        }
        checks = registry.all();
        MobDefense.onServerStarted(ServerSmoke::onStarted);
        MobDefense.onServerTick(ServerSmoke::onTick);
        MobDefense.LOG.info("{} aktiv, {} Prüfungen aus den Modulen{}", TAG, checks.size(),
                holdSeconds() > 0 ? ", Beitrittstest hält " + holdSeconds() + " s" : "");
    }

    private static void onStarted(MinecraftServer server) {
        ticksSinceStart = 0;
        if (!server.isDedicatedServer()) {
            fail("kein dedizierter Server");
        }
        if (holdSeconds() > 0) {
            server.setUsingWhitelist(false);
            try {
                Files.deleteIfExists(stopFile());
            } catch (IOException e) {
                fail("alte " + STOP_FILE + " nicht gelöscht: " + e);
            }
            watch(server);
        }
        check("Add-on", ServerSmoke::addon, server);
        check("Teile", ServerSmoke::parts, server);
        check("Welt-Regeln", ServerSmoke::rules, server);
        check("PW3-Tabelle", ServerSmoke::shareTable, server);
        runChecks(server, 0);
        MobDefense.LOG.info("{} gestartet, läuft {}", TAG, holdSeconds() > 0
                ? "bis " + STOP_FILE + " im Run-Ordner liegt, höchstens " + holdSeconds() + " s (Beitrittstest)" : RUN_TICKS + " Ticks");
    }

    private static void onTick(MinecraftServer server) {
        if (ticksSinceStart < 0 || FINISHED.get()) {
            return;
        }
        ticksSinceStart++;
        runChecks(server, ticksSinceStart);
        if (holdSeconds() > 0) {
            players(server);
            return;
        }
        if (ticksSinceStart >= RUN_TICKS) {
            finish(server);
        }
    }

    private static void runChecks(MinecraftServer server, int tick) {
        for (ServerChecks.Check entry : checks) {
            if (entry.tick() == tick) {
                check(entry.module() + ": " + entry.name(), entry.body(), server);
            }
        }
    }

    private static void check(String name, ServerChecks.ServerCheck body, MinecraftServer server) {
        try {
            String summary = body.run(server);
            String line = name + ": " + (summary == null || summary.isBlank() ? "ok" : summary);
            NOTES.add(line);
            MobDefense.LOG.info("{} {}", TAG, line);
        } catch (Exception e) {
            MobDefense.LOG.error("{} {} fehlgeschlagen", TAG, name, e);
            fail(name + ": " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private static String addon(MinecraftServer server) {
        if (!MobDefense.initialized()) {
            throw new IllegalStateException("MobDefense.init() lief nicht");
        }
        String version = Services.PLATFORM.modVersion(MobDefense.MOD_ID).orElseThrow(() -> new IllegalStateException("Mod mobdefense nicht geladen"));
        String propwash = Services.PLATFORM.modVersion(PropwashApi.MOD_ID).orElseThrow(() -> new IllegalStateException("Propwash nicht geladen"));
        if (!PropwashApi.isCompatible(MobDefense.REQUIRED_API)) {
            throw new IllegalStateException("Propwash-API " + PropwashApi.API_VERSION + " erfüllt " + MobDefense.REQUIRED_API + " nicht");
        }
        return "Mob Defense " + version + " mit Propwash " + propwash + " (API " + PropwashApi.API_VERSION + ", verlangt " + MobDefense.REQUIRED_API
                + "), " + Services.PLATFORM.name();
    }

    private static List<PartDefinition> ownParts() {
        return PropwashApi.parts().all().stream().filter(part -> MobDefense.MOD_ID.equals(part.id().getNamespace())).toList();
    }

    private static String parts(MinecraftServer server) {
        if (!PropwashApi.parts().isFrozen()) {
            throw new IllegalStateException("Propwash-Teileregister nach dem Start nicht eingefroren");
        }
        List<PartDefinition> own = ownParts();
        List<String> withoutItem = new ArrayList<>();
        for (PartDefinition part : own) {
            if (PropwashApi.partItems().item(part.id()).isEmpty()) {
                withoutItem.add(part.id().toString());
            }
        }
        if (!withoutItem.isEmpty()) {
            throw new IllegalStateException("Teile ohne Item: " + withoutItem);
        }
        return own.size() + " Teile von Mob Defense, alle mit Item";
    }

    private static String rules(MinecraftServer server) {
        Map<String, RuleSpec> specs = new HashMap<>();
        for (RuleSpec spec : RuleDefaults.ALL) {
            specs.put(spec.path(), spec);
        }
        GameRules values = server.getGameRules();
        Set<String> registered = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (Identifier id : BuiltInRegistries.GAME_RULE.keySet()) {
            if (!MobDefense.MOD_ID.equals(id.getNamespace())) {
                continue;
            }
            registered.add(id.getPath());
            RuleSpec spec = specs.get(id.getPath());
            GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
            if (spec == null) {
                problems.add(id + " steht nicht in RuleDefaults");
                continue;
            }
            if (rule == null) {
                problems.add(id + " ohne Eintrag im Register");
                continue;
            }
            String problem = compare(spec, rule, values);
            if (problem != null) {
                problems.add(id + ": " + problem);
            }
        }
        if (!registered.isEmpty()) {
            for (RuleSpec spec : RuleDefaults.ALL) {
                if (!registered.contains(spec.path()) && !OPTIONAL_RULES.contains(spec.path())) {
                    problems.add(MobDefense.id(spec.path()) + " fehlt");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        if (registered.isEmpty()) {
            return "noch keine Welt-Regel von Mob Defense angemeldet";
        }
        return registered.size() + " von " + RuleDefaults.ALL.size() + " Welt-Regeln als Gamerules angemeldet, Typ, Bereich, Standard und Wert in der Welt wie RuleDefaults";
    }

    private static <T> @Nullable String compare(RuleSpec spec, GameRule<T> rule, GameRules values) {
        Object expected = spec.flag() ? (Object) spec.defaultFlag() : (Object) spec.defaultValue();
        if (spec.flag() != (rule.valueClass() == Boolean.class)) {
            return "Typ " + rule.valueClass().getSimpleName() + " statt " + (spec.flag() ? "Schalter" : "Zahl");
        }
        if (!expected.equals(rule.defaultValue())) {
            return "Standard " + rule.defaultValue() + " statt " + expected;
        }
        if (!rule.serialize(values.get(rule)).equals(String.valueOf(expected))) {
            return "Wert in der neuen Welt " + values.getAsString(rule) + " statt " + expected;
        }
        ArgumentType<T> argument = rule.argument();
        if (!spec.flag() && argument instanceof IntegerArgumentType integer
                && (integer.getMinimum() != spec.min() || integer.getMaximum() != spec.max())) {
            return "Bereich " + integer.getMinimum() + " bis " + integer.getMaximum() + " statt " + spec.min() + " bis " + spec.max();
        }
        return null;
    }

    private static String shareTable(MinecraftServer server) throws IOException {
        Optional<Resource> resource = server.getResourceManager().getResource(PW3_TABLE);
        List<PartDefinition> own = ownParts();
        if (resource.isEmpty()) {
            if (!own.isEmpty()) {
                throw new IllegalStateException(own.size() + " Teile, aber keine Tabelle data/mobdefense/propwash_share/table.json");
            }
            return "keine Tabelle (noch keine Teile)";
        }
        JsonObject table;
        try (Reader reader = resource.get().openAsReader()) {
            table = JsonParser.parseReader(reader).getAsJsonObject();
        }
        if (!table.has("format") || table.get("format").getAsInt() != 1) {
            throw new IllegalStateException("format ist nicht 1");
        }
        if (!table.has("namespace") || !MobDefense.MOD_ID.equals(table.get("namespace").getAsString())) {
            throw new IllegalStateException("namespace ist nicht mobdefense");
        }
        Map<String, Set<String>> entries = new HashMap<>();
        int count = 0;
        JsonObject parts = table.has("parts") ? table.getAsJsonObject("parts") : new JsonObject();
        for (Map.Entry<String, JsonElement> kind : parts.entrySet()) {
            JsonArray list = kind.getValue().getAsJsonArray();
            for (JsonElement element : list) {
                String id = element.getAsJsonObject().get("id").getAsString();
                if (id.contains(":")) {
                    throw new IllegalStateException("Eintrag mit Namensraum: " + id);
                }
                entries.computeIfAbsent(kind.getKey(), key -> new HashSet<>()).add(id);
                count++;
            }
        }
        List<String> missing = new ArrayList<>();
        for (PartDefinition part : own) {
            if (!entries.getOrDefault(part.kind().key(), Set.of()).contains(part.id().getPath())) {
                missing.add(part.id().toString());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Teile ohne Eintrag: " + missing);
        }
        return "gelesen, " + count + " Einträge, alle " + own.size() + " Teile drin";
    }

    private static void players(MinecraftServer server) {
        Set<String> now = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            now.add(player.getGameProfile().name());
        }
        for (String name : now) {
            if (PLAYERS.add(name)) {
                joins++;
                MobDefense.LOG.info("[Beitrittstest] {} beigetreten ({} Beitritte)", name, joins);
            }
        }
        if (PLAYERS.retainAll(now)) {
            MobDefense.LOG.info("[Beitrittstest] Spieler verlassen, noch {}", PLAYERS.size());
        }
    }

    private static Path stopFile() {
        return Services.PLATFORM.gameDir().resolve(STOP_FILE);
    }

    private static void watch(MinecraftServer server) {
        long end = System.currentTimeMillis() + holdSeconds() * 1000L;
        Thread watcher = new Thread(() -> {
            while (!FINISHED.get()) {
                if (Files.exists(stopFile()) || System.currentTimeMillis() > end) {
                    MobDefense.LOG.info("[Beitrittstest] {} – Server endet", Files.exists(stopFile()) ? STOP_FILE + " gefunden" : "Haltezeit um");
                    finish(server);
                    return;
                }
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Mob-Defense-Beitrittstest-Wächter");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void finish(MinecraftServer server) {
        if (!FINISHED.compareAndSet(false, true)) {
            return;
        }
        ClassLoadScan.Result scan = ClassLoadScan.scan();
        if (!scan.ok()) {
            fail(scan.message());
        } else {
            NOTES.add("Client-Klassen: " + scan.message());
            MobDefense.LOG.info("{} Client-Klassen: {}", TAG, scan.message());
        }
        String problem = failure;
        String result = problem == null
                ? "PASS dedizierter Server, " + Math.max(0, ticksSinceStart) + " Ticks, " + NOTES.size() + " Prüfungen"
                + (holdSeconds() > 0 ? ", " + joins + " Beitritte" : "") + " | " + String.join(" | ", NOTES)
                : "FAIL " + problem;
        if (problem == null) {
            MobDefense.LOG.info("{} {}", TAG, result);
        } else {
            MobDefense.LOG.error("{} {}", TAG, result);
        }
        DevResult.write(result);
        server.halt(false);
    }

    private static void fail(String reason) {
        if (failure == null) {
            failure = reason;
        }
    }
}
