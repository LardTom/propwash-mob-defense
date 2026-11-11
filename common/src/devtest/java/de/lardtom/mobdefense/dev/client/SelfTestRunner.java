package de.lardtom.mobdefense.dev.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.MobDefenseClient;
import de.lardtom.mobdefense.dev.DevResult;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class SelfTestRunner {
    public static final String WORLD_NAME = "Mob Defense Selftest";
    public static final String REPORT_FILE = "mobdefense-selftest-steps.txt";
    private static final String TAG = "[Mob-Defense-Selbsttest]";
    private static final long START_MILLIS = 10L * 60L * 1000L;
    private static final long SMOKE_MILLIS = 5L * 60L * 1000L;
    private static final long SPARE_MILLIS = 5L * 60L * 1000L;
    private static final int SETTLE_TICKS = 60;
    private static final int TITLE_TIMEOUT_TICKS = 20 * 90;
    private static final int WORLD_TIMEOUT_TICKS = 20 * 180;
    private static final int LEAVE_TIMEOUT_TICKS = 20 * 60;
    private static final int CLEANUP_TICKS = 20 * 30;

    private enum Phase { WAIT_TITLE, WAIT_WORLD, SETTLE, RUN, WAIT_LEFT, FINISH, DONE }

    private enum StepPhase { BODY, CLEANUP, RESET }

    private static final List<String> FAILURES = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static final AtomicLong DEADLINE = new AtomicLong(Long.MAX_VALUE);
    private static Phase phase = Phase.WAIT_TITLE;
    private static int phaseTicks;
    private static int runTicks;
    private static int runLimit = Integer.MAX_VALUE;
    private static List<SelfTest.Step> steps = List.of();
    private static int stepIndex;
    private static int stepTicks;
    private static StepPhase stepPhase = StepPhase.BODY;
    private static Map<String, Object> stepState = SelfTest.newState();
    private static Map<String, Object> cleanupState = SelfTest.newState();
    private static int passed;
    private static volatile boolean finished;
    private static boolean smoke;
    private static boolean installed;
    private static long started;

    private SelfTestRunner() {
    }

    public static void install() {
        if (SelfTest.enabled()) {
            start(false);
        }
    }

    public static void installSmoke() {
        if (SelfTest.smokeEnabled()) {
            start(true);
        }
    }

    public static boolean running() {
        return phase == Phase.RUN;
    }

    private static void start(boolean smokeMode) {
        if (installed) {
            return;
        }
        installed = true;
        smoke = smokeMode;
        started = System.currentTimeMillis();
        DEADLINE.set(started + (smoke ? SMOKE_MILLIS : START_MILLIS));
        MobDefense.LOG.info("{} {} aktiv ({}), Auswahl: {}, Bildschirmfotos: {}", TAG, smoke ? "Rauchtest" : "Selbsttest", Services.PLATFORM.name(),
                SelfTest.filter() == null ? "alle Schritte" : SelfTest.filter(), Shots.enabled() ? Shots.describe() : "aus");
        MobDefenseClient.onClientTick(SelfTestRunner::tick);
        Thread watchdog = new Thread(() -> {
            while (!finished) {
                if (System.currentTimeMillis() > DEADLINE.get()) {
                    String where = phase == Phase.RUN && stepIndex < steps.size() ? " in Schritt " + steps.get(stepIndex).name() : "";
                    MobDefense.LOG.error("{} FAIL Wächter: Client hängt (Phase {}{})", marker(), phase, where);
                    DevResult.write("FAIL Wächter: Client hängt in Phase " + phase + where);
                    Runtime.getRuntime().halt(3);
                }
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "Mob-Defense-Selbsttest-Wächter");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static String marker() {
        return smoke ? "MOBDEFENSE SMOKE" : "MOBDEFENSE SELFTEST";
    }

    private static void tick(Minecraft mc) {
        phaseTicks++;
        if (phase == Phase.RUN) {
            runTicks++;
        }
        try {
            if (phase == Phase.RUN && runTicks > runLimit) {
                FAILURES.add("Zeitgrenze von " + runLimit / 20 + " s für alle Schritte überschritten");
                leaveWorld(mc);
            }
            switch (phase) {
                case WAIT_TITLE -> {
                    if (mc.gui.screen() instanceof TitleScreen && phaseTicks > 10) {
                        createWorld(mc);
                        setPhase(Phase.WAIT_WORLD);
                    } else if (phaseTicks > TITLE_TIMEOUT_TICKS) {
                        FAILURES.add("Titelbildschirm nicht erreicht, aktueller Bildschirm: " + screenName(mc));
                        setPhase(Phase.FINISH);
                    }
                }
                case WAIT_WORLD -> {
                    Screen screen = mc.gui.screen();
                    if (screen instanceof ConfirmScreen confirm && phaseTicks % 20 == 0) {
                        MobDefense.LOG.info("{} bestätige {}", TAG, confirm.getTitle().getString());
                        pressButton(confirm, CommonComponents.GUI_YES.getString());
                    }
                    if (mc.player != null && mc.level != null && screen == null) {
                        MobDefense.LOG.info("{} Welt geladen nach {} Ticks", TAG, phaseTicks);
                        setPhase(Phase.SETTLE);
                    } else if (phaseTicks > WORLD_TIMEOUT_TICKS) {
                        FAILURES.add("Welt nicht geladen (Bildschirm: " + screenName(mc) + ")");
                        leaveWorld(mc);
                    }
                }
                case SETTLE -> {
                    if (phaseTicks >= SETTLE_TICKS && smoke) {
                        MobDefense.LOG.info("{} Rauchtest: Welt betreten, keine Schritte", TAG);
                        leaveWorld(mc);
                    } else if (phaseTicks >= SETTLE_TICKS) {
                        begin(mc);
                    }
                }
                case RUN -> {
                    if (runStep(mc)) {
                        leaveWorld(mc);
                    }
                }
                case WAIT_LEFT -> {
                    if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && phaseTicks > 5) {
                        MobDefense.LOG.info("{} Welt verlassen nach {} Ticks", TAG, phaseTicks);
                        setPhase(Phase.FINISH);
                    } else if (phaseTicks > LEAVE_TIMEOUT_TICKS) {
                        FAILURES.add("Welt nicht verlassen (Bildschirm: " + screenName(mc) + ")");
                        setPhase(Phase.FINISH);
                    }
                }
                case FINISH -> finish(mc);
                case DONE -> {
                }
            }
        } catch (Throwable t) {
            MobDefense.LOG.error("{} Fehler im Läufer (Phase {})", TAG, phase, t);
            if (phase.ordinal() < Phase.WAIT_LEFT.ordinal()) {
                FAILURES.add("Läufer-Fehler in Phase " + phase + ": " + t);
                leaveWorld(mc);
            } else if (phase.ordinal() < Phase.FINISH.ordinal()) {
                FAILURES.add("Läufer-Fehler in Phase " + phase + ": " + t);
                setPhase(Phase.FINISH);
            } else if (phase != Phase.DONE) {
                setPhase(Phase.DONE);
                mc.stop();
            }
        }
    }

    private static void begin(Minecraft mc) {
        Arena.remember(mc);
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> Arena.rememberRules(server));
        }
        steps = SelfTest.startSteps();
        int budget = 0;
        for (SelfTest.Step step : steps) {
            budget += step.maxTicks() + 2 * CLEANUP_TICKS;
        }
        runLimit = budget + 20 * 60;
        DEADLINE.set(System.currentTimeMillis() + runLimit * 50L * 2L + SPARE_MILLIS);
        MobDefense.LOG.info("{} starte {} von {} Schritten, Zeitgrenze {} s in der Welt, Wächter nach {} min", TAG, steps.size(), SelfTest.all().size(),
                runLimit / 20, (DEADLINE.get() - System.currentTimeMillis()) / 60000L);
        if (steps.isEmpty()) {
            FAILURES.add("kein Schritt passt zur Auswahl " + SelfTest.filter());
        }
        startStep(0);
        setPhase(Phase.RUN);
    }

    private static void leaveWorld(Minecraft mc) {
        setPhase(Phase.WAIT_LEFT);
        if (mc.level != null) {
            MobDefense.LOG.info("{} verlasse die Welt", TAG);
            mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Mob-Defense-Selbsttest beendet")));
        }
    }

    private static void createWorld(Minecraft mc) {
        deleteOldWorlds(mc);
        CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(new TitleScreen()));
        if (!(mc.gui.screen() instanceof CreateWorldScreen screen)) {
            throw new IllegalStateException("CreateWorldScreen wurde nicht geöffnet, sondern " + screenName(mc));
        }
        WorldCreationUiState ui = screen.getUiState();
        ui.setName(WORLD_NAME);
        Holder<WorldPreset> flat = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT);
        ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(flat));
        ui.setSeed("1");
        ui.setGenerateStructures(false);
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        ui.getGameRules().set(GameRules.ADVANCE_TIME, false, null);
        ui.getGameRules().set(GameRules.ADVANCE_WEATHER, false, null);
        ui.getGameRules().set(GameRules.SPAWN_MOBS, false, null);
        MobDefense.LOG.info("{} lege flache Kreativwelt '{}' an", TAG, ui.getTargetFolder());
        if (!pressButton(screen, Component.translatable("selectWorld.create").getString())) {
            throw new IllegalStateException("Knopf 'selectWorld.create' nicht gefunden");
        }
    }

    private static void deleteOldWorlds(Minecraft mc) {
        Path saves = mc.getLevelSource().getBaseDir();
        if (!Files.isDirectory(saves)) {
            return;
        }
        try (Stream<Path> worlds = Files.list(saves)) {
            for (Path world : worlds.filter(p -> p.getFileName().toString().startsWith(WORLD_NAME)).toList()) {
                try (Stream<Path> files = Files.walk(world)) {
                    for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        } catch (IOException e) {
            MobDefense.LOG.warn("{} alte Welten nicht gelöscht: {}", TAG, e.toString());
        }
    }

    public static boolean pressButton(Screen screen, String text) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && text.equals(button.getMessage().getString())) {
                button.onPress(new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
                return true;
            }
        }
        return false;
    }

    private static void startStep(int index) {
        stepIndex = index;
        stepTicks = 0;
        stepPhase = StepPhase.BODY;
        stepState = SelfTest.newState();
        cleanupState = SelfTest.newState();
        if (index < steps.size()) {
            SelfTest.Step step = steps.get(index);
            MobDefense.LOG.info("{} Schritt {}/{} ({}): {}", TAG, index + 1, steps.size(), step.module(), step.name());
        }
    }

    private static boolean runStep(Minecraft mc) {
        if (stepIndex >= steps.size()) {
            return true;
        }
        SelfTest.Step step = steps.get(stepIndex);
        MinecraftServer server = mc.getSingleplayerServer();
        switch (stepPhase) {
            case BODY -> {
                StepResult result = call(step.body(), new Context(stepTicks, server, stepState, step.name()), step.name());
                stepTicks++;
                if (!result.done() && stepTicks >= step.maxTicks()) {
                    result = StepResult.fail("Zeitüberschreitung nach " + step.maxTicks() + " Ticks");
                }
                if (result.done()) {
                    String line = (result.failed() ? "FAIL " : "PASS ") + step.name() + " (" + stepTicks + " Ticks)"
                            + (result.message().isEmpty() ? "" : ": " + result.message());
                    REPORT.add(line);
                    if (result.failed()) {
                        FAILURES.add(step.name() + ": " + result.message());
                        MobDefense.LOG.error("{} FAIL {}: {}", TAG, step.name(), result.message());
                    } else {
                        passed++;
                        MobDefense.LOG.info("{} PASS {} ({} Ticks){}", TAG, step.name(), stepTicks, result.message().isEmpty() ? "" : ": " + result.message());
                    }
                    stepPhase = StepPhase.CLEANUP;
                    stepTicks = 0;
                }
            }
            case CLEANUP -> {
                StepResult result = call(step.cleanup(), new Context(stepTicks, server, cleanupState, step.name() + " – aufräumen"), step.name());
                stepTicks++;
                if (result.done() || stepTicks >= CLEANUP_TICKS) {
                    if (!result.done() || result.failed()) {
                        String why = result.done() ? result.message() : "Zeitüberschreitung";
                        FAILURES.add(step.name() + ": Aufräumen des Schritts fehlgeschlagen: " + why);
                        REPORT.add("FAIL " + step.name() + ": Aufräumen des Schritts: " + why);
                    }
                    stepPhase = StepPhase.RESET;
                    stepTicks = 0;
                    cleanupState = SelfTest.newState();
                }
            }
            case RESET -> {
                StepResult result = call(Arena::reset, new Context(stepTicks, server, cleanupState, step.name() + " – Arena"), step.name());
                stepTicks++;
                if (result.done() || stepTicks >= CLEANUP_TICKS) {
                    if (!result.done() || result.failed()) {
                        String why = result.done() ? result.message() : "Zeitüberschreitung";
                        FAILURES.add(step.name() + ": Aufräumen nach dem Schritt fehlgeschlagen: " + why);
                        REPORT.add("FAIL " + step.name() + ": Aufräumen danach: " + why);
                    }
                    startStep(stepIndex + 1);
                }
            }
        }
        return stepIndex >= steps.size();
    }

    private static StepResult call(SelfTest.ClientStep body, Context context, String name) {
        try {
            return body.tick(context);
        } catch (Throwable t) {
            MobDefense.LOG.error("{} Schritt '{}' warf", TAG, name, t);
            return StepResult.fail("Ausnahme: " + t);
        }
    }

    private static void finish(Minecraft mc) {
        finished = true;
        long seconds = (System.currentTimeMillis() - started) / 1000L;
        String result;
        if (smoke && FAILURES.isEmpty()) {
            result = "PASS Rauchtest (" + Services.PLATFORM.name() + ", " + seconds + " s)";
            MobDefense.LOG.info("MOBDEFENSE SMOKE PASS (Welt angelegt, betreten und verlassen, {})", Services.PLATFORM.name());
        } else if (!smoke && FAILURES.isEmpty() && passed > 0) {
            result = "PASS " + passed + " Schritte (" + Services.PLATFORM.name() + ", " + seconds + " s, " + Shots.taken().size() + " Bildschirmfotos)";
            MobDefense.LOG.info("MOBDEFENSE SELFTEST PASS ({} Schritte, {}, {} s)", passed, Services.PLATFORM.name(), seconds);
        } else {
            if (!smoke && passed == 0 && FAILURES.isEmpty()) {
                FAILURES.add("keine Schritte ausgeführt");
            }
            result = "FAIL " + String.join(" | ", FAILURES);
            MobDefense.LOG.error("{} {} ({})", marker(), result, Services.PLATFORM.name());
        }
        if (!smoke) {
            writeReport(result);
        }
        DevResult.write(result);
        setPhase(Phase.DONE);
        MobDefense.LOG.info("{} beende das Spiel", TAG);
        mc.stop();
    }

    private static void writeReport(String result) {
        List<String> lines = new ArrayList<>();
        lines.add("Mob-Defense-Selbsttest (" + Services.PLATFORM.name() + ")");
        lines.add("Auswahl: " + (SelfTest.filter() == null ? "alle Schritte" : SelfTest.filter()));
        lines.add("Bildschirmfotos: " + (Shots.enabled() ? Shots.dir() + " (" + Shots.taken().size() + ")" : "aus"));
        lines.addAll(REPORT);
        lines.add(result);
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(REPORT_FILE), String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            MobDefense.LOG.warn("{} Schrittliste nicht geschrieben: {}", TAG, e.toString());
        }
    }

    private static void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    public static String screenName(Minecraft mc) {
        Screen s = mc.gui.screen();
        return s == null ? "keiner" : s.getClass().getName();
    }

    private record Context(int tick, @Nullable MinecraftServer server, Map<String, Object> state, String name) implements SelfTest.StepContext {
        @Override
        public CompletableFuture<Void> onServer(Consumer<MinecraftServer> action) {
            MinecraftServer s = this.server;
            if (s == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("kein integrierter Server"));
            }
            return s.submit(() -> action.accept(s)).whenComplete((v, t) -> {
                if (t != null) {
                    MobDefense.LOG.error("{} Server-Aktion in '{}' fehlgeschlagen", TAG, this.name, t);
                }
            });
        }

        @Override
        public void log(String message) {
            MobDefense.LOG.info("{} {}: {}", TAG, this.name, message);
        }
    }
}
