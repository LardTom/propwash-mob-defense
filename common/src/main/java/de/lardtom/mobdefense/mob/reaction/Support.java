package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Knockbacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class Support {
    static final double WALL_GAP = 0.35;

    private Support() {
    }

    static @Nullable ServerLevel level(Mob mob) {
        return mob.level() instanceof ServerLevel level ? level : null;
    }

    static boolean sees(Mob mob, DroneState drone) {
        return mob.getSensing().hasLineOfSight(drone.entity());
    }

    static double flat(Mob mob, Vec3 point) {
        return Math.hypot(mob.getX() - point.x, mob.getZ() - point.z);
    }

    static Vec3 toward(Mob mob, Vec3 point) {
        Vec3 flat = new Vec3(point.x - mob.getX(), 0.0, point.z - mob.getZ());
        double length = flat.length();
        return length < 1.0E-4 ? Vec3.ZERO : flat.scale(1.0 / length);
    }

    static float yaw(Mob mob, Vec3 point) {
        return (float) (Math.atan2(point.z - mob.getZ(), point.x - mob.getX()) * (180.0 / Math.PI)) - 90.0F;
    }

    static boolean clear(Level level, Vec3 from, Vec3 to, Entity viewer, ClipContext.Block block) {
        return level.clip(new ClipContext(from, to, block, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }

    static boolean hurt(ServerLevel level, Entity drone, DamageSource source, float amount) {
        return Knockbacks.scoped(drone, () -> drone.hurtServer(level, source, amount));
    }

    static boolean atWall(Level level, DroneState drone) {
        AABB box = drone.box();
        double reach = Math.max(box.getXsize(), box.getZsize()) / 2.0 + WALL_GAP;
        Vec3 center = drone.center();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = BlockPos.containing(center.x + direction.getStepX() * reach, center.y, center.z + direction.getStepZ() * reach);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double step) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-3) {
            return;
        }
        int points = Math.max(1, (int) Math.ceil(length / step));
        for (int i = 1; i <= points; i++) {
            Vec3 at = from.add(delta.scale(i / (double) points));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
