package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.net.NoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class OsdNotices {
    private static final Map<Notice, Shown> SHOWN = new EnumMap<>(Notice.class);
    private static final AtomicInteger RECEIVED = new AtomicInteger();
    private static int level;

    private OsdNotices() {
    }

    private record Shown(int drone, long until) {
    }

    public static void accept(NoticePayload payload) {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null) {
            return;
        }
        show(payload.notice(), payload.droneId(), world.getGameTime() + payload.ticks(), System.identityHashCode(world));
        RECEIVED.incrementAndGet();
    }

    static synchronized void show(Notice notice, int drone, long until, int levelId) {
        if (levelId != level) {
            SHOWN.clear();
            level = levelId;
        }
        SHOWN.put(notice, new Shown(drone, until));
    }

    public static synchronized boolean active(Notice notice, int drone, long gameTime) {
        Shown shown = SHOWN.get(notice);
        return shown != null && shown.drone() == drone && gameTime < shown.until();
    }

    public static synchronized void clear() {
        SHOWN.clear();
    }

    public static int received() {
        return RECEIVED.get();
    }
}
