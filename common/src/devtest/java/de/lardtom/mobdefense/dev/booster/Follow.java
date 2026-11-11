package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Follow {
    public static final double TARGET_SIDE = 1.8;
    public static final double TARGET_DOWN = 1.6;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile @Nullable Entity drone;
    private static volatile @Nullable LivingEntity target;
    private static volatile double lastDistance = Double.NaN;

    private Follow() {
    }

    public static void start(Entity followed, LivingEntity victim) {
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(Follow::tick);
        }
        drone = followed;
        target = victim;
        lastDistance = Double.NaN;
    }

    public static void stop() {
        drone = null;
        target = null;
    }

    public static double lastDistance() {
        return lastDistance;
    }

    private static void tick(MinecraftServer server) {
        Entity followed = drone;
        if (followed == null) {
            return;
        }
        if (followed.isRemoved()) {
            stop();
            return;
        }
        Optional<DroneView> view = PropwashApi.drones().view(followed);
        if (view.isEmpty()) {
            return;
        }
        Vec3 center = view.get().center();
        Vec3 forward = new Vec3(view.get().forward().x, 0.0, view.get().forward().z);
        forward = forward.lengthSqr() > 1.0E-9 ? forward.normalize() : new Vec3(0.0, 0.0, -1.0);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 ahead = view.get().velocity().scale(0.05);
        LivingEntity victim = target;
        if (victim != null && !victim.isRemoved()) {
            Vec3 at = center.add(ahead).add(right.scale(-TARGET_SIDE)).add(0.0, -TARGET_DOWN, 0.0);
            victim.teleportTo(at.x, at.y, at.z);
            victim.setDeltaMovement(Vec3.ZERO);
            lastDistance = center.add(ahead).distanceTo(at);
        }
    }
}
