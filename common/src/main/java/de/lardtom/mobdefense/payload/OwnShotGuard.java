package de.lardtom.mobdefense.payload;

import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class OwnShotGuard {
    private static final Map<Entity, UUID> FIRED_FROM = new WeakHashMap<>();
    private static int blocked;

    private OwnShotGuard() {
    }

    static void register() {
        PropwashEvents.DRONE_DAMAGED.register(OwnShotGuard::onDamaged);
    }

    static synchronized void fired(Projectile projectile, Entity drone) {
        FIRED_FROM.put(projectile, drone.getUUID());
    }

    public static synchronized boolean firedFrom(@Nullable Entity shot, Entity drone) {
        if (shot == null) {
            return false;
        }
        UUID from = FIRED_FROM.get(shot);
        return from != null && from.equals(drone.getUUID());
    }

    public static synchronized int blocked() {
        return blocked;
    }

    private static void onDamaged(DroneDamage damage) {
        if (!firedFrom(damage.source().getDirectEntity(), damage.drone())) {
            return;
        }
        damage.cancel();
        synchronized (OwnShotGuard.class) {
            blocked++;
        }
    }
}
