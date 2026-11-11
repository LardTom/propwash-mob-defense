package de.lardtom.mobdefense.mob;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

public final class Moves {
    public static final int REPATH_TICKS = 10;
    public static final double REPATH_DISTANCE_SQR = 2.25;
    public static final float LOOK_SPEED = 30.0F;

    private Moves() {
    }

    public static boolean brain(Mob mob) {
        return mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED);
    }

    public static void walk(Mob mob, Vec3 target, double speed, ReactionMemory memory) {
        boolean brain = brain(mob);
        boolean idle = brain ? !mob.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) : mob.getNavigation().isDone();
        if (memory.path != null && memory.repath > 0 && memory.path.distanceToSqr(target) < REPATH_DISTANCE_SQR && !idle) {
            memory.repath--;
            return;
        }
        memory.path = target;
        memory.repath = REPATH_TICKS;
        if (brain) {
            WalkTarget walk = new WalkTarget(target, (float) speed, 1);
            memory.walk = walk;
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walk);
        } else {
            mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }

    public static void halt(Mob mob, ReactionMemory memory) {
        if (brain(mob)) {
            eraseOwnWalk(mob.getBrain(), memory);
        } else if (!mob.getNavigation().isDone()) {
            mob.getNavigation().stop();
        }
        memory.path = null;
        memory.repath = 0;
    }

    public static void look(Mob mob, DroneState drone) {
        Brain<?> brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) {
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(drone.entity(), true));
        }
        mob.getLookControl().setLookAt(drone.center().x, drone.center().y, drone.center().z, LOOK_SPEED, LOOK_SPEED);
    }

    public static void face(Mob mob, Vec3 at) {
        double dx = at.x - mob.getX();
        double dz = at.z - mob.getZ();
        if (dx * dx + dz * dz < 1.0E-6) {
            return;
        }
        float yaw = (float) (-Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
        mob.setYRot(yaw);
        mob.yBodyRot = yaw;
        mob.setYHeadRot(yaw);
    }

    public static void stop(Mob mob, ReactionMemory memory) {
        Brain<?> brain = mob.getBrain();
        if (brain(mob)) {
            eraseOwnWalk(brain, memory);
            if (brain.checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) {
                brain.getMemory(MemoryModuleType.LOOK_TARGET).filter(EntityTracker.class::isInstance).map(EntityTracker.class::cast)
                        .filter(tracker -> !(tracker.getEntity() instanceof LivingEntity))
                        .ifPresent(tracker -> brain.eraseMemory(MemoryModuleType.LOOK_TARGET));
            }
        } else {
            mob.getNavigation().stop();
        }
        memory.path = null;
        memory.repath = 0;
    }

    private static void eraseOwnWalk(Brain<?> brain, ReactionMemory memory) {
        WalkTarget own = memory.walk;
        if (own != null && brain.getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk == own).isPresent()) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        memory.walk = null;
    }
}
