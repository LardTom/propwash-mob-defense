package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mixin.PhantomAccessor;
import de.lardtom.mobdefense.mob.Access;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Failures;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.Strikes;
import de.lardtom.mobdefense.mob.core.KindWindows;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class FlyerReaction implements Reaction {
    public static final String KIND = "flyer";
    public static final double CIRCLE_RADIUS = 8.0;
    public static final double CIRCLE_HEIGHT = 6.0;
    public static final double CIRCLE_STEP = 0.12;
    public static final int CIRCLE_MIN = 40;
    public static final int CIRCLE_SPREAD = 40;
    public static final int SWOOP_MAX = 100;
    public static final int VEX_REST = 20;
    private static final int CIRCLE = 0;
    private static final int SWOOP = 1;
    private static final int LEVEL_EVENT_PHANTOM_BITE = 1039;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        if (mob instanceof Phantom) {
            return KindWindows.phantom(drone.height(), nightInOpen(mob.level(), drone));
        }
        return true;
    }

    public static boolean nightInOpen(Level level, DroneState drone) {
        return level.isDarkOutside() && level.canSeeSky(BlockPos.containing(drone.center()));
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.step = mob instanceof Phantom ? CIRCLE : SWOOP;
        memory.cooldown = CIRCLE_MIN / 2;
        memory.angle = Math.atan2(mob.getZ() - drone.center().z, mob.getX() - drone.center().x);
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        if (mob instanceof Phantom phantom) {
            phantom(phantom, drone, memory);
        } else if (mob instanceof Vex vex) {
            vex(vex, drone, memory);
        } else {
            hover(mob, drone, memory);
        }
    }

    private static void phantom(Phantom phantom, DroneState drone, ReactionMemory memory) {
        if (!Access.PHANTOM) {
            Failures.disableQuietly(phantom);
            return;
        }
        PhantomAccessor access = (PhantomAccessor) phantom;
        memory.ticks++;
        if (memory.step == CIRCLE) {
            memory.angle += CIRCLE_STEP;
            Vec3 point = drone.center().add(Math.cos(memory.angle) * CIRCLE_RADIUS, CIRCLE_HEIGHT, Math.sin(memory.angle) * CIRCLE_RADIUS);
            access.mobdefense$setMoveTargetPoint(point);
            if (memory.ticks >= memory.cooldown) {
                memory.step = SWOOP;
                memory.ticks = 0;
            }
            return;
        }
        access.mobdefense$setMoveTargetPoint(drone.center());
        if (Strikes.touches(phantom, drone, 0.2)) {
            Strikes.melee(phantom, drone.entity());
            memory.shots++;
            if (!phantom.isSilent()) {
                phantom.level().levelEvent(null, LEVEL_EVENT_PHANTOM_BITE, phantom.blockPosition(), 0);
            }
            circle(phantom, memory);
        } else if (phantom.horizontalCollision || memory.ticks > SWOOP_MAX) {
            circle(phantom, memory);
        }
    }

    private static void circle(Mob mob, ReactionMemory memory) {
        memory.step = CIRCLE;
        memory.ticks = 0;
        memory.cooldown = CIRCLE_MIN + mob.getRandom().nextInt(CIRCLE_SPREAD);
    }

    private static void vex(Vex vex, DroneState drone, ReactionMemory memory) {
        if (memory.special > 0) {
            memory.special--;
            Vec3 rest = memory.point == null ? drone.center().add(0.0, 2.0, 0.0) : memory.point;
            vex.getMoveControl().setWantedPosition(rest.x, rest.y, rest.z, 0.25);
            vex.setIsCharging(false);
            return;
        }
        Vec3 center = drone.center();
        vex.getMoveControl().setWantedPosition(center.x, center.y, center.z, 1.0);
        vex.setIsCharging(true);
        if (Strikes.touches(vex, drone, 0.0)) {
            Strikes.melee(vex, drone.entity());
            memory.shots++;
            vex.setIsCharging(false);
            memory.special = VEX_REST;
            memory.point = center.add(vex.getRandom().nextInt(7) - 3, 2.0 + vex.getRandom().nextInt(2), vex.getRandom().nextInt(7) - 3);
        }
    }

    private static void hover(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.look(mob, drone);
        Vec3 center = drone.center();
        mob.getMoveControl().setWantedPosition(center.x, center.y, center.z, 1.0);
        if (memory.cooldown > 0) {
            memory.cooldown--;
        } else if (Strikes.touches(mob, drone, 0.2)) {
            Strikes.melee(mob, drone.entity());
            memory.shots++;
            memory.cooldown = VEX_REST;
        }
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob instanceof Vex vex) {
            vex.setIsCharging(false);
        }
        if (mob instanceof Phantom phantom && Access.PHANTOM) {
            ((PhantomAccessor) phantom).mobdefense$setAnchorPoint(phantom.blockPosition().above(5));
        }
    }
}
