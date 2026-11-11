package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.dispenser.CadencePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

public final class CadenceSync {
    public static final int RETRY_TICKS = 20;
    public static final int MAX_TRIES = 10;
    private static volatile Predicate<CustomPacketPayload> sender = payload -> false;
    private static @Nullable ClientPacketListener connection;
    private static boolean sent;
    private static boolean dirty;
    private static int wait;
    private static int tries;
    private static int count;

    private CadenceSync() {
    }

    public static void install(Predicate<CustomPacketPayload> loaderSender) {
        sender = Objects.requireNonNull(loaderSender, "loaderSender");
    }

    public static synchronized void changed() {
        dirty = true;
        tries = 0;
        wait = 0;
    }

    public static synchronized int sentCount() {
        return count;
    }

    public static synchronized void tick(Minecraft minecraft) {
        ClientPacketListener now = minecraft.getConnection();
        if (now == null) {
            connection = null;
            return;
        }
        if (now != connection) {
            connection = now;
            sent = false;
            tries = 0;
            wait = 0;
        }
        if ((sent && !dirty) || tries >= MAX_TRIES) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        tries++;
        if (send()) {
            sent = true;
            dirty = false;
            tries = 0;
        } else {
            wait = RETRY_TICKS;
        }
    }

    private static boolean send() {
        try {
            if (sender.test(CadencePayload.of(ClientSettings.get().cadence()))) {
                count++;
                return true;
            }
        } catch (RuntimeException e) {
            return false;
        }
        return false;
    }
}
