package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.Strikes;
import de.lardtom.mobdefense.mob.core.KindWindows;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public class ChaseReaction implements Reaction {
    public static final String KIND = "chase";
    public static final double SPEED = 1.0;
    public static final int ATTACK_INTERVAL = 20;
    public static final double CLOSE_IN = 2.5;
    public static final double UNDER = 0.35;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        mob.setAggressive(true);
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        chase(mob, drone, memory);
    }

    protected final void chase(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.look(mob, drone);
        double flat = Math.hypot(mob.getX() - drone.center().x, mob.getZ() - drone.center().z);
        if (flat < CLOSE_IN && mob.getNavigation().isDone()) {
            if (flat > UNDER) {
                mob.getMoveControl().setWantedPosition(drone.center().x, mob.getY(), drone.center().z, SPEED);
            }
        } else {
            Moves.walk(mob, destination(mob, drone), SPEED, memory);
        }
        if (memory.cooldown > 0) {
            memory.cooldown--;
            return;
        }
        if (canStrike(mob, drone)) {
            this.strike(mob, drone, memory);
            memory.cooldown = ATTACK_INTERVAL;
        }
    }

    public static boolean canStrike(Mob mob, DroneState drone) {
        return KindWindows.chaseStrike(drone.bottom(), mob.getY() + mob.getBbHeight(), drone.landed())
                && Strikes.inReach(mob, drone, KindWindows.CHASE_REACH_ABOVE_HEAD);
    }

    protected void strike(Mob mob, DroneState drone, ReactionMemory memory) {
        Strikes.melee(mob, drone.entity());
    }

    protected static Vec3 destination(Mob mob, DroneState drone) {
        return mob.isInWater() ? drone.center() : drone.ground();
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        mob.setAggressive(false);
        Moves.stop(mob, memory);
    }
}
