package de.lardtom.mobdefense.dev.spear.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.spear.SpearChecks;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class SpearFpvSteps {
    public static final double CHANGED = 0.005;
    public static final double NOISE_FACTOR = 3.0;
    public static final int CHANNEL = 24;
    public static final int SETTLE_TICKS = 15;
    public static final int GRAB_TICKS = 200;
    public static final double LEFT = 0.3;
    public static final double RIGHT = 0.7;
    public static final double TOP = 0.5;
    public static final double BOTTOM = 1.0;
    public static final int WALL_AHEAD = 3;
    public static final int WALL_HALF_WIDTH = 4;
    public static final int WALL_HEIGHT = 7;
    private static final String WITH = "fpv.with";
    private static final String WITHOUT = "fpv.without";
    private static final String AGAIN = "fpv.again";
    private static final String CONTROL = "fpv.control";
    private static final String PENDING = "fpv.pending";
    private static final String TIP = "fpv.tip";
    private static boolean hudHidden;

    private SpearFpvSteps() {
    }

    private record Frame(int width, int height, int[] pixels) {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("S8 Speer im FPV-Bild – 7″ mit Netheritspeer steht im FPV vor einer Wand: mit Speer ändert sich das Bild unter der Mitte, ein "
                + "Speer-Item an der Speerspitze zur Kontrolle auch", 2400, Script.create()
                .server("7″ mit Netheritspeer vor einer Wand", SpearFpvSteps::rig)
                .append(Pilot.fly())
                .run("HUD aus", SpearFpvSteps::hideHud)
                .sleep(SETTLE_TICKS)
                .server("Speerspitze merken", SpearFpvSteps::rememberTip)
                .then("Bild mit Speer", (ctx, t) -> grab(ctx, t, WITH))
                .server("Speer heraus", (server, state) -> inventory(Pilot.serverDrone(server, state)).set(0, ItemStack.EMPTY))
                .sleep(SETTLE_TICKS)
                .then("Bild ohne Speer", (ctx, t) -> grab(ctx, t, WITHOUT))
                .sleep(5)
                .then("Bild ohne Speer, noch einmal", (ctx, t) -> grab(ctx, t, AGAIN))
                .server("Kontrolle: Speer-Item an der Speerspitze", SpearFpvSteps::control)
                .sleep(SETTLE_TICKS)
                .then("Bild mit Kontrolle", (ctx, t) -> grab(ctx, t, CONTROL))
                .then("Ergebnis", SpearFpvSteps::result), SpearFpvSteps::cleanup);
    }

    private static AccessoryInventory inventory(Entity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM)
                .orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"));
    }

    private static void rig(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.spawn(server, state, SpearFeedbackSteps.sevenInch(), "S8 FPV 7", Arena.ground(0, -6), 180.0F);
        inventory(drone).set(0, new ItemStack(Items.NETHERITE_SPEAR));
        ServerLevel level = Arena.level(server);
        Vec3 nose = SpearChecks.nose(drone);
        Vec3 side = new Vec3(-nose.z, 0.0, nose.x);
        Vec3 base = drone.position().add(nose.scale(WALL_AHEAD));
        BlockPos from = BlockPos.containing(base.subtract(side.scale(WALL_HALF_WIDTH)));
        BlockPos to = BlockPos.containing(base.add(side.scale(WALL_HALF_WIDTH))).above(WALL_HEIGHT - 1);
        Arena.fill(level, from, to, Blocks.SMOOTH_STONE.defaultBlockState());
    }

    private static void rememberTip(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        Optional<Vec3> tip = SpearStrikes.lastTip(drone);
        if (tip.isEmpty()) {
            throw new IllegalStateException("keine Speerspitze am Server");
        }
        state.put(TIP, tip.get());
    }

    private static void control(MinecraftServer server, Map<String, Object> state) {
        Vec3 tip = (Vec3) state.get(TIP);
        ServerLevel level = Arena.level(server);
        ItemEntity item = new ItemEntity(level, tip.x, tip.y, tip.z, new ItemStack(Items.NETHERITE_SPEAR), 0.0, 0.0, 0.0);
        item.setNoGravity(true);
        item.setNeverPickUp();
        item.setUnlimitedLifetime();
        Arena.spawn(level, item);
    }

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hudHidden = true;
        }
    }

    private static void restoreHud() {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hudHidden = false;
    }

    private static StepResult grab(SelfTest.StepContext ctx, int ticks, String key) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get(PENDING);
        if (pending == null) {
            CompletableFuture<Frame> future = new CompletableFuture<>();
            Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), 1, image -> {
                try (image) {
                    future.complete(new Frame(image.getWidth(), image.getHeight(), image.getPixels()));
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                }
            });
            state.put(PENDING, future);
            return StepResult.pending();
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Frame> future = (CompletableFuture<Frame>) pending;
        if (!future.isDone()) {
            return ticks > GRAB_TICKS ? StepResult.fail("Bild hängt") : StepResult.pending();
        }
        state.remove(PENDING);
        if (future.isCompletedExceptionally()) {
            return StepResult.fail("Bild: " + future.exceptionNow());
        }
        state.put(key, future.join());
        return StepResult.pass();
    }

    public static double changed(int width, int height, int[] a, int[] b) {
        int x0 = (int) Math.floor(width * LEFT);
        int x1 = (int) Math.ceil(width * RIGHT);
        int y0 = (int) Math.floor(height * TOP);
        int y1 = (int) Math.ceil(height * BOTTOM);
        int total = 0;
        int differ = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int i = y * width + x;
                total++;
                if (channel(a[i], b[i], 0) > CHANNEL || channel(a[i], b[i], 8) > CHANNEL || channel(a[i], b[i], 16) > CHANNEL) {
                    differ++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) differ / total;
    }

    private static int channel(int a, int b, int shift) {
        return Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF));
    }

    private static double compare(Frame a, Frame b) {
        if (a.width() != b.width() || a.height() != b.height()) {
            throw new IllegalStateException("Bildgröße geändert: " + a.width() + "x" + a.height() + " und " + b.width() + "x" + b.height());
        }
        return changed(a.width(), a.height(), a.pixels(), b.pixels());
    }

    private static StepResult result(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Frame with = (Frame) state.get(WITH);
        Frame without = (Frame) state.get(WITHOUT);
        Frame again = (Frame) state.get(AGAIN);
        Frame control = (Frame) state.get(CONTROL);
        double noise = compare(without, again);
        double spear = compare(with, without);
        double check = compare(control, again);
        double limit = Math.max(CHANGED, NOISE_FACTOR * noise);
        String summary = String.format(Locale.ROOT, "Bild %dx%d, Bereich unter der Mitte: mit Speer %.2f %% anders, Kontrolle %.2f %%, Rauschen %.2f %%, "
                + "Grenze %.2f %%", with.width(), with.height(), spear * 100.0, check * 100.0, noise * 100.0, limit * 100.0);
        if (check < limit) {
            return StepResult.fail("Messung ungültig: das Speer-Item an der Speerspitze ist nicht im Bild | " + summary);
        }
        if (spear < limit) {
            return StepResult.fail("Speer im eigenen FPV-Bild nicht gezeichnet | " + summary);
        }
        return StepResult.pass(summary);
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("HUD zurück, landen", () -> {
                restoreHud();
                Pilot.land(Minecraft.getInstance());
            })
            .server("Flug beenden", (server, state) -> Pilot.release(server));

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }
}
