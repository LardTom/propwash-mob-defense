package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.net.NoticePayload;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public final class MobDefenseClient {
    private static final String DEV_CLIENT = "de.lardtom.mobdefense.dev.client.DevClientBootstrap";
    private static boolean initialized;
    private static volatile Consumer<Minecraft> clientTick = minecraft -> {
    };

    private MobDefenseClient() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MobDefenseOsd.register();
        MobDefenseGuide.register();
        MobDefenseSettingsTab.register();
        MobDefense.LOG.info("{}: Client bereit", MobDefense.MOD_NAME);
        devtest();
    }

    public static void notice(NoticePayload payload) {
        OsdNotices.accept(payload);
    }

    public static void onClientTick(Consumer<Minecraft> listener) {
        clientTick = clientTick.andThen(listener);
    }

    public static void tick(Minecraft minecraft) {
        clientTick.accept(minecraft);
    }

    private static void devtest() {
        if (!Boolean.getBoolean("mobdefense.selftest") && !Boolean.getBoolean("mobdefense.smoketest")) {
            return;
        }
        Class<?> type;
        try {
            type = Class.forName(DEV_CLIENT, true, MobDefenseClient.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            MobDefense.LOG.error("{}: Selbsttest angefordert, aber {} fehlt im Klassenpfad", MobDefense.MOD_NAME, DEV_CLIENT);
            return;
        }
        try {
            type.getMethod("init").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(DEV_CLIENT + " startet nicht", e);
        }
    }
}
