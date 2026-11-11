package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.mob.core.KindWindows;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public record DroneState(Entity entity, DroneView view, Vec3 center, double bottom, double height, boolean landed) {
    public static final double GROUND_PROBE = 32.0;
    private static final Vec3 DOWN = new Vec3(0.0, -1.0, 0.0);
    private static final double PROBE_LIFT = 0.05;
    private static final Map<Entity, Stamped> CACHE = new WeakHashMap<>();

    private record Stamped(long tick, @Nullable DroneState state) {
    }

    public static synchronized @Nullable DroneState of(Entity drone, long tick) {
        Stamped cached = CACHE.get(drone);
        if (cached != null && cached.tick() == tick) {
            return cached.state();
        }
        DroneState state = compute(drone);
        CACHE.put(drone, new Stamped(tick, state));
        return state;
    }

    private static @Nullable DroneState compute(Entity drone) {
        if (drone.isRemoved()) {
            return null;
        }
        DroneView view = PropwashApi.drones().view(drone).orElse(null);
        if (view == null) {
            return null;
        }
        AABB box = drone.getBoundingBox();
        Vec3 center = view.center();
        Vec3 probe = new Vec3(center.x, box.minY + PROBE_LIFT, center.z);
        double height = Math.max(0.0, PropwashApi.drones().obstacleDistance(drone.level(), probe, DOWN, GROUND_PROBE, false, drone) - PROBE_LIFT);
        return new DroneState(drone, view, center, box.minY, height, KindWindows.landed(drone.onGround(), height));
    }

    public Vec3 ground() {
        return new Vec3(this.center.x, this.bottom - this.height, this.center.z);
    }

    public double distanceFrom(Mob mob) {
        return mob.getEyePosition().distanceTo(this.center);
    }

    public AABB box() {
        return this.entity.getBoundingBox();
    }
}
