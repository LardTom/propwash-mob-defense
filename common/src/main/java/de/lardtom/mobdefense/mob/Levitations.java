package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.mob.core.Levitation;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.event.DroneDamage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class Levitations {
    private static final Map<Entity, Long> LIFTING = new WeakHashMap<>();

    private Levitations() {
    }

    public static void onDamaged(DroneDamage damage) {
        if (damage.cancelled() || !(damage.source().getDirectEntity() instanceof ShulkerBullet)) {
            return;
        }
        long now = damage.level().getGameTime();
        DroneState drone = DroneState.of(damage.drone(), now);
        if (drone != null && DroneReactions.active(drone)) {
            start(damage.drone(), now);
        }
    }

    public static void tick(MinecraftServer server) {
        for (Map.Entry<Entity, Long> entry : snapshot()) {
            Entity drone = entry.getKey();
            long now = drone.level().getGameTime();
            DroneState state = drone.isRemoved() || !Levitation.lifting(now, entry.getValue()) ? null : DroneState.of(drone, now);
            if (state == null || !DroneReactions.active(state)) {
                end(drone, entry.getValue());
                continue;
            }
            double lift = Levitation.lift(state.view().massKg(), state.view().velocity().y);
            if (lift > 0.0) {
                PropwashApi.drones().physics(drone).ifPresent(physics -> physics.push(new Vec3(0.0, lift, 0.0), false, Vec3.ZERO, Levitation.TICK_SECONDS));
            }
        }
    }

    public static synchronized boolean lifting(Entity drone) {
        Long endsAt = LIFTING.get(drone);
        return endsAt != null && Levitation.lifting(drone.level().getGameTime(), endsAt);
    }

    private static synchronized void start(Entity drone, long now) {
        LIFTING.put(drone, Levitation.endsAt(now));
    }

    private static synchronized void end(Entity drone, long endsAt) {
        LIFTING.remove(drone, endsAt);
    }

    private static synchronized List<Map.Entry<Entity, Long>> snapshot() {
        if (LIFTING.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<Entity, Long>> out = new ArrayList<>(LIFTING.size());
        for (Map.Entry<Entity, Long> entry : LIFTING.entrySet()) {
            Entity drone = entry.getKey();
            if (drone != null) {
                out.add(Map.entry(drone, entry.getValue()));
            }
        }
        return out;
    }
}
