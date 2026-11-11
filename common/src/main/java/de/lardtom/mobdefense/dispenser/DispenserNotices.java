package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.net.Notices;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class DispenserNotices {
    private DispenserNotices() {
    }

    public static Notice notice(FireGate.Notice notice) {
        return switch (notice) {
            case NO_AMMO -> Notice.NO_AMMO;
            case BUCKET_LOCKED -> Notice.BUCKET_LOCKED;
            case TNT_LOCKED -> Notice.TNT_LOCKED;
            case BARREL_BLOCKED -> Notice.BARREL_BLOCKED;
            case NO_PERMISSION -> Notice.NO_PERMISSION;
            case NONE -> throw new IllegalArgumentException("kein Hinweis");
        };
    }

    static boolean publish(ServerPlayer pilot, Entity drone, FireGate.Notice notice) {
        return notice != FireGate.Notice.NONE && Notices.send(pilot, drone, notice(notice));
    }
}
