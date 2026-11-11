package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.SelfTestRunner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

public final class WorldRestart {
    private static final String WORLD = "restart.world";
    private static final String SERVER = "restart.server";
    private static final String LEFT = "restart.left";
    private static final String OPENED = "restart.opened";
    private static final String JOINED = "restart.joined";
    private static final int LEAVE_TICKS = 20 * 60;
    private static final int OPEN_TICKS = 20 * 180;
    private static final int SETTLE_TICKS = 40;

    private WorldRestart() {
    }

    public static Script script() {
        return Script.create()
                .server("Weltordner merken", (server, state) -> {
                    Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
                    if (folder == null) {
                        throw new IllegalStateException("Weltordner unbekannt");
                    }
                    state.put(WORLD, folder.toString());
                    state.put(SERVER, server);
                })
                .then("Welt speichern und verlassen", WorldRestart::leave)
                .then("Welt neu öffnen", WorldRestart::open);
    }

    private static StepResult leave(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (!ctx.state().containsKey(LEFT)) {
            ctx.state().put(LEFT, Boolean.TRUE);
            mc.schedule(() -> mc.disconnectFromWorld(Component.literal("Mob-Defense-Selbsttest: Neustart der Welt")));
            return StepResult.pending();
        }
        if (mc.level == null && mc.player == null && mc.getSingleplayerServer() == null && ticks > 5) {
            return StepResult.pass("Welt gespeichert, integrierter Server beendet nach " + ticks + " Ticks");
        }
        return ticks > LEAVE_TICKS ? StepResult.fail("Welt nicht verlassen (Bildschirm: " + SelfTestRunner.screenName(mc) + ")") : StepResult.pending();
    }

    private static StepResult open(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        String world = String.valueOf(ctx.state().get(WORLD));
        if (!ctx.state().containsKey(OPENED)) {
            ctx.state().put(OPENED, Boolean.TRUE);
            mc.schedule(() -> mc.createWorldOpenFlows().openWorld(world, () -> mc.gui.setScreen(new TitleScreen())));
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen instanceof ConfirmScreen confirm && ticks % 20 == 0) {
            ctx.log("bestätige " + confirm.getTitle().getString());
            if (!SelfTestRunner.pressButton(confirm, CommonComponents.GUI_YES.getString())) {
                SelfTestRunner.pressButton(confirm, CommonComponents.GUI_PROCEED.getString());
            }
        }
        boolean inside = mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && screen == null;
        if (!inside) {
            ctx.state().remove(JOINED);
            return ticks > OPEN_TICKS ? StepResult.fail("Welt „" + world + "“ nicht wieder geöffnet (Bildschirm: " + SelfTestRunner.screenName(mc) + ")")
                    : StepResult.pending();
        }
        int joined = (int) ctx.state().computeIfAbsent(JOINED, key -> ticks);
        if (ticks - joined < SETTLE_TICKS) {
            return StepResult.pending();
        }
        if (ctx.state().remove(SERVER) == mc.getSingleplayerServer()) {
            return StepResult.fail("derselbe integrierte Server läuft noch");
        }
        return StepResult.pass("Welt „" + world + "“ neu geöffnet nach " + joined + " Ticks, neuer integrierter Server");
    }
}
