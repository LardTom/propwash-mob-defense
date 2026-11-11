package de.lardtom.mobdefense.dev.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class Shots {
    public static final String PROPERTY = "mobdefense.screenshots";
    public static final String RUN_FOLDER = "screenshots/selftest";
    public static final String ENGLISH = "en_us";
    public static final String GERMAN = "de_de";
    private static final String SHOT = "shots.future";
    private static final int SETTLE_TICKS = 8;
    private static final int WINDOW_TICKS = 80;
    private static final List<Path> TAKEN = new ArrayList<>();
    private static int @Nullable [] savedWindow;
    private static @Nullable String savedLanguage;

    private Shots() {
    }

    public static boolean enabled() {
        String value = System.getProperty(PROPERTY, "");
        return SelfTest.enabled() && !value.isBlank() && !value.equalsIgnoreCase("false");
    }

    public static Path dir() {
        String value = System.getProperty(PROPERTY, "").trim();
        if (runFolder(value)) {
            return gameDir().resolve(RUN_FOLDER).toAbsolutePath().normalize();
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    public static String describe() {
        String value = System.getProperty(PROPERTY, "").trim();
        return runFolder(value) ? "Run-Ordner/" + RUN_FOLDER : Path.of(value).toAbsolutePath().normalize().toString();
    }

    private static boolean runFolder(String value) {
        return value.isEmpty() || value.equalsIgnoreCase("true");
    }

    private static Path gameDir() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null ? mc.gameDirectory.toPath() : Services.PLATFORM.gameDir();
    }

    public static synchronized List<Path> taken() {
        return List.copyOf(TAKEN);
    }

    public static void clearOverlays(Minecraft mc) {
        mc.gui.hud.getChat().clearMessages(false);
        mc.gui.hud.setOverlayMessage(Component.empty(), false);
        mc.gui.hud.clearTitles();
        mc.gui.toastManager().clear();
    }

    public static CompletableFuture<String> capture(String name) {
        CompletableFuture<String> done = new CompletableFuture<>();
        Path file = dir().resolve(name + ".png").normalize();
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            done.completeExceptionally(e);
            return done;
        }
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        String size = target.width + "x" + target.height;
        Screenshot.takeScreenshot(target, 1, image -> Util.ioPool().execute(() -> {
            try (image) {
                image.writeToFile(file);
                synchronized (Shots.class) {
                    TAKEN.add(file);
                }
                done.complete(size);
            } catch (Exception e) {
                done.completeExceptionally(e);
            }
        }));
        return done;
    }

    public static StepResult shot(SelfTest.StepContext ctx, int ticks, String name, Runnable before) {
        if (!enabled()) {
            return StepResult.pass();
        }
        Minecraft mc = Minecraft.getInstance();
        Object pending = ctx.state().get(SHOT);
        if (ticks == 0 && pending == null) {
            clearOverlays(mc);
            before.run();
        }
        if (ticks < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (pending == null) {
            ctx.state().put(SHOT, capture(name));
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<String> future = (CompletableFuture<String>) pending;
        if (!future.isDone()) {
            return ticks > 400 ? StepResult.fail("Bildschirmfoto " + name + " hängt") : StepResult.pending();
        }
        ctx.state().remove(SHOT);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Bildschirmfoto " + name + ": " + future.exceptionNow());
        }
        ctx.log("Bildschirmfoto " + dir().resolve(name + ".png") + " (" + future.join() + ")");
        return StepResult.pass();
    }

    public static Script photos(String name) {
        return photos(name, () -> {
        });
    }

    public static Script photos(String name, Runnable before) {
        if (!enabled()) {
            return Script.create();
        }
        return Script.create()
                .run("Sprache Englisch", () -> language(Minecraft.getInstance(), ENGLISH))
                .then("Bildschirmfoto " + name, (ctx, ticks) -> shot(ctx, ticks, name, before))
                .run("Sprache Deutsch", () -> language(Minecraft.getInstance(), GERMAN))
                .then("Bildschirmfoto " + name + "_de", (ctx, ticks) -> shot(ctx, ticks, name + "_de", before))
                .run("Sprache zurück", () -> restoreLanguage(Minecraft.getInstance()));
    }

    public static void language(Minecraft mc, String code) {
        if (savedLanguage == null) {
            savedLanguage = mc.getLanguageManager().getSelected();
        }
        apply(mc, code);
    }

    public static void restoreLanguage(Minecraft mc) {
        String saved = savedLanguage;
        if (saved == null) {
            return;
        }
        savedLanguage = null;
        apply(mc, saved);
    }

    private static void apply(Minecraft mc, String code) {
        if (code.equals(mc.getLanguageManager().getSelected())) {
            return;
        }
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        mc.getLanguageManager().onResourceManagerReload(mc.getResourceManager());
        if (mc.gui.screen() != null) {
            mc.resizeGui();
        }
    }

    public static StepResult window(SelfTest.StepContext ctx, int ticks, int width, int height, int scale) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (savedWindow == null) {
            savedWindow = new int[]{window.getScreenWidth(), window.getScreenHeight(), mc.options.guiScale().get()};
        }
        if (ticks == 0) {
            float density = Math.max(1.0F, window.getPixelDensity());
            window.setWindowed(Math.round(width / density), Math.round(height / density));
            return StepResult.pending();
        }
        boolean sized = window.getWidth() == width && window.getHeight() == height;
        if (!sized && ticks < WINDOW_TICKS) {
            return StepResult.pending();
        }
        if (!sized) {
            return StepResult.fail("Fenster wird nicht " + width + "x" + height + ", sondern " + window.getWidth() + "x" + window.getHeight());
        }
        mc.options.guiScale().set(scale);
        mc.resizeGui();
        return StepResult.pass();
    }

    public static void restoreWindow(Minecraft mc) {
        int[] saved = savedWindow;
        if (saved == null) {
            return;
        }
        savedWindow = null;
        mc.options.guiScale().set(saved[2]);
        mc.getWindow().setWindowed(saved[0], saved[1]);
        mc.resizeGui();
    }
}
