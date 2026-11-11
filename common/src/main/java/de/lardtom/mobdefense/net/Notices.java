package de.lardtom.mobdefense.net;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.net.core.RateGate;
import de.lardtom.propwash.api.accessory.DispenseResult;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class Notices {
    public static final int PER_SECOND = 4;
    public static final int WINDOW_TICKS = 20;
    public static final int THREAT_RESERVE = 1;
    private static final RateGate<UUID> GATE = new RateGate<>(PER_SECOND, WINDOW_TICKS);
    private static final AtomicInteger SENT = new AtomicInteger();
    private static final AtomicInteger DROPPED = new AtomicInteger();
    private static volatile BiConsumer<ServerPlayer, CustomPacketPayload> sender = (player, payload) -> {
    };
    private static volatile boolean installed;

    private Notices() {
    }

    public static synchronized void install(BiConsumer<ServerPlayer, CustomPacketPayload> loaderSender) {
        sender = Objects.requireNonNull(loaderSender, "loaderSender");
        if (!installed) {
            MobDefense.onServerStarted(server -> GATE.clear());
        }
        installed = true;
    }

    public static boolean installed() {
        return installed;
    }

    public static boolean send(ServerPlayer pilot, Entity drone, Notice notice) {
        return send(pilot, drone, notice, Notice.DEFAULT_TICKS);
    }

    public static boolean send(ServerPlayer pilot, Entity drone, Notice notice, int ticks) {
        Objects.requireNonNull(pilot, "pilot");
        Objects.requireNonNull(drone, "drone");
        Objects.requireNonNull(notice, "notice");
        if (!admit(pilot.getUUID(), notice, pilot.level().getServer().getTickCount())) {
            DROPPED.incrementAndGet();
            return false;
        }
        try {
            sender.accept(pilot, new NoticePayload(drone.getId(), notice, ticks));
        } catch (RuntimeException e) {
            MobDefense.LOG.warn("{}: OSD-Hinweis {} an {} nicht gesendet", MobDefense.MOD_NAME, notice, pilot.getScoreboardName(), e);
            return false;
        }
        SENT.incrementAndGet();
        return true;
    }

    static boolean admit(UUID pilot, Notice notice, long tick) {
        return GATE.allow(pilot, tick, notice == Notice.THREAT ? 0 : THREAT_RESERVE);
    }

    public static boolean dispense(ServerPlayer pilot, Entity drone, DispenseResult result) {
        return Notice.of(result.status()).map(notice -> send(pilot, drone, notice)).orElse(false);
    }

    public static boolean threat(ServerPlayer pilot, Entity drone, int ticks) {
        return send(pilot, drone, Notice.THREAT, ticks);
    }

    public static int sent() {
        return SENT.get();
    }

    public static int dropped() {
        return DROPPED.get();
    }
}
