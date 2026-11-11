package de.lardtom.mobdefense.dev.join;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.GenericWaitingScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class JoinProbe {
    public static final String TARGET = "MOBDEFENSE_JOINTEST";
    public static final String LABEL = "MOBDEFENSE_JOINTEST_LABEL";
    public static final String EXPECT = "MOBDEFENSE_JOINTEST_EXPECT";
    public static final String RESULT_FILE = "mobdefense-selftest-result.txt";
    public static final String FOLDER = "jointest";
    public static final String JOINED = "joined";
    public static final String ADDON_CLASS = "de.lardtom.mobdefense.MobDefense";
    private static final Logger LOG = LoggerFactory.getLogger("Propwash: Mob Defense Beitrittstest");
    private static final String TAG = "[Mob-Defense-Beitrittstest]";
    private static final int TITLE_TICKS = 20 * 90;
    private static final int MAX_TICKS = 20 * 150;
    private static final int STABLE_TICKS = 30;
    private static final long WATCHDOG_MILLIS = 6L * 60L * 1000L;
    private static final int STAY_TICKS = 20 * 240;

    private enum Phase { WAIT_TITLE, OBSERVE, CAPTURE, STAY, LEAVE, FINISH, DONE }

    private static Phase phase = Phase.WAIT_TITLE;
    private static int ticks;
    private static int phaseTicks;
    private static int since;
    private static String screenKey = "";
    private static String text = "";
    private static boolean addonExpected;
    private static boolean installed;
    private static volatile boolean finished;
    private static @Nullable CompletableFuture<String> capture;
    private static @Nullable BooleanSupplier stay;
    private static String stayReason = "";
    private static @Nullable String failure;

    private JoinProbe() {
    }

    public static boolean requested() {
        return target() != null;
    }

    public static @Nullable String target() {
        String value = System.getenv(TARGET);
        return value == null || value.isBlank() ? null : value.trim();
    }

    public static String label() {
        String value = System.getenv(LABEL);
        return value == null || value.isBlank() ? "join" : value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public static List<String> expectations() {
        List<String> out = new ArrayList<>();
        String value = System.getenv(EXPECT);
        if (value == null) {
            return out;
        }
        for (String part : value.split("\\|")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    public static boolean addonPresent() {
        try {
            Class.forName(ADDON_CLASS, false, JoinProbe.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static void stayUntil(String reason, BooleanSupplier done) {
        stayReason = reason;
        stay = done;
    }

    public static synchronized void install(boolean withAddon) {
        if (installed || !requested()) {
            return;
        }
        installed = true;
        addonExpected = withAddon;
        LOG.info("{} aktiv: Ziel {}, Fall {}, erwartet {}, Mob Defense im Client {}", TAG, target(), label(), expectations(),
                addonPresent() ? "vorhanden" : "nicht vorhanden");
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(WATCHDOG_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            if (!finished) {
                LOG.error("{} FAIL Wächter: Client hängt in Phase {}", TAG, phase);
                result("FAIL Wächter: Beitrittstest hängt in Phase " + phase);
                Runtime.getRuntime().halt(3);
            }
        }, "Mob-Defense-Beitrittstest-Wächter");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static void tick(Minecraft mc) {
        if (!installed || phase == Phase.DONE) {
            return;
        }
        ticks++;
        phaseTicks++;
        try {
            step(mc);
        } catch (Throwable t) {
            LOG.error("{} Fehler in Phase {}", TAG, phase, t);
            text = "Fehler: " + t;
            finish(mc, "FAIL Fehler im Beitrittstest: " + t);
        }
    }

    private static void step(Minecraft mc) {
        switch (phase) {
            case WAIT_TITLE -> {
                if (mc.gui.screen() instanceof TitleScreen && phaseTicks > 10) {
                    String target = target();
                    LOG.info("{} verbinde mit {}", TAG, target);
                    ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(target),
                            new ServerData("Mob Defense join test", target, ServerData.Type.OTHER), false, null);
                    next(Phase.OBSERVE);
                } else if (phaseTicks > TITLE_TICKS) {
                    finish(mc, "FAIL Titelbildschirm nicht erreicht");
                }
            }
            case OBSERVE -> {
                Screen screen = mc.gui.screen();
                boolean joined = mc.level != null && mc.player != null && screen == null;
                boolean settled = joined || (mc.level == null && screen != null && !transientScreen(screen));
                String key = joined ? "world" : screen == null ? "none" : screen.getClass().getName();
                if (!key.equals(screenKey)) {
                    screenKey = key;
                    since = phaseTicks;
                    return;
                }
                if (!settled || phaseTicks - since < STABLE_TICKS) {
                    if (phaseTicks > MAX_TICKS) {
                        text = describe(screen);
                        finish(mc, "FAIL kein Ergebnis nach " + MAX_TICKS / 20 + " s (" + key + ")");
                    }
                    return;
                }
                text = (joined ? JOINED + "\n" : describe(screen)) + "mobdefense_client=" + (addonPresent() ? "present" : "absent") + "\n";
                write(label() + ".txt", text);
                LOG.info("{} {}: {}", TAG, label(), text.replace("\n", " | "));
                capture = capture(mc, label());
                next(Phase.CAPTURE);
            }
            case CAPTURE -> {
                CompletableFuture<String> pending = capture;
                if (pending != null && !pending.isDone() && phaseTicks < 200) {
                    return;
                }
                if (stay != null && mc.level != null) {
                    LOG.info("{} bleibt in der Welt, bis {} fertig ist (höchstens {} s)", TAG, stayReason, STAY_TICKS / 20);
                    next(Phase.STAY);
                    return;
                }
                leave(mc);
            }
            case STAY -> {
                BooleanSupplier done = stay;
                if (mc.level == null) {
                    failure = "FAIL " + stayReason + ": Verbindung nach " + phaseTicks / 20 + " s getrennt, bevor der Ablauf fertig war";
                    next(Phase.LEAVE);
                    return;
                }
                if (done != null && !done.getAsBoolean() && phaseTicks <= STAY_TICKS) {
                    return;
                }
                if (phaseTicks > STAY_TICKS) {
                    failure = "FAIL " + stayReason + ": nach " + STAY_TICKS / 20 + " s nicht fertig";
                } else {
                    LOG.info("{} {} fertig nach {} s, Client verlässt die Welt", TAG, stayReason, phaseTicks / 20);
                }
                leave(mc);
            }
            case LEAVE -> {
                if (mc.level == null || phaseTicks > 20 * 30) {
                    String failed = failure;
                    finish(mc, failed != null ? failed : evaluate(text));
                }
            }
            case FINISH, DONE -> {
            }
        }
    }

    private static void next(Phase value) {
        phase = value;
        phaseTicks = 0;
    }

    private static void leave(Minecraft mc) {
        if (mc.level != null) {
            mc.disconnectFromWorld(Component.literal("Mob Defense join test"));
        }
        next(Phase.LEAVE);
    }

    static String evaluate(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (addonExpected != addonPresent()) {
            return "FAIL Mob Defense im Client " + (addonPresent() ? "vorhanden" : "nicht vorhanden") + ", erwartet "
                    + (addonExpected ? "vorhanden" : "nicht vorhanden");
        }
        for (String expected : expectations()) {
            boolean negate = expected.startsWith("!");
            String needle = (negate ? expected.substring(1) : expected).toLowerCase(Locale.ROOT);
            if (lower.contains(needle) == negate) {
                return "FAIL " + label() + ": " + (negate ? "unerwartet: " : "fehlt: ") + needle;
            }
        }
        return "PASS Beitrittstest " + label() + " (" + (lower.startsWith(JOINED) ? "beigetreten" : "abgelehnt") + ", Mob Defense im Client "
                + (addonPresent() ? "vorhanden" : "nicht vorhanden") + ", " + expectations().size() + " Erwartungen)";
    }

    private static void finish(Minecraft mc, String result) {
        finished = true;
        phase = Phase.DONE;
        if (result.startsWith("PASS")) {
            LOG.info("{} {}", TAG, result);
        } else {
            LOG.error("{} {}", TAG, result);
        }
        result(result);
        mc.stop();
    }

    private static void result(String result) {
        try {
            Files.writeString(gameDir().resolve(RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("{} Ergebnisdatei nicht geschrieben", TAG, e);
        }
    }

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    private static boolean transientScreen(Screen screen) {
        return screen instanceof ConnectScreen || screen instanceof GenericMessageScreen || screen instanceof GenericWaitingScreen
                || screen instanceof LevelLoadingScreen || screen instanceof ProgressScreen;
    }

    public static String describe(@Nullable Screen screen) {
        if (screen == null) {
            return "kein Bildschirm\n";
        }
        StringBuilder out = new StringBuilder();
        out.append("screen=").append(screen.getClass().getName()).append('\n');
        out.append("title=").append(screen.getTitle().getString()).append('\n');
        for (Method method : screen.getClass().getMethods()) {
            if (method.getName().equals("detailsText") && method.getParameterCount() == 0 && method.getReturnType() == String.class) {
                try {
                    out.append("details=\n").append(method.invoke(screen)).append('\n');
                } catch (ReflectiveOperationException e) {
                    out.append("details: ").append(e).append('\n');
                }
            }
        }
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    append(out, type.getSimpleName() + "." + field.getName(), field.get(screen));
                } catch (ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
            }
        }
        return out.toString();
    }

    private static void append(StringBuilder out, String name, @Nullable Object value) {
        switch (value) {
            case null -> {
            }
            case Component component -> out.append(name).append('=').append(component.getString()).append('\n');
            case DisconnectionDetails details -> out.append(name).append(".reason=").append(details.reason().getString()).append('\n');
            case Map<?, ?> map -> {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getValue() instanceof Component component) {
                        out.append(name).append('[').append(entry.getKey()).append("]=").append(component.getString()).append('\n');
                    }
                }
            }
            case Collection<?> list -> {
                int index = 0;
                for (Object item : list) {
                    if (item instanceof Component component) {
                        out.append(name).append('[').append(index).append("]=").append(component.getString()).append('\n');
                    }
                    index++;
                }
            }
            default -> {
            }
        }
    }

    private static CompletableFuture<String> capture(Minecraft mc, String name) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = gameDir().resolve(FOLDER).resolve(name + ".png");
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.complete("kein Ordner: " + e);
            return done;
        }
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                done.complete(file.toString());
            } catch (Exception e) {
                done.complete("Bildschirmfoto fehlgeschlagen: " + e);
            }
        }));
        return done;
    }

    private static void write(String name, String content) {
        try {
            Path dir = gameDir().resolve(FOLDER);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.error("{} {} nicht geschrieben", TAG, name, e);
        }
    }
}
