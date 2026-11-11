package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.KindWindows;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

public final class CreeperReaction implements Reaction {
    public static final String KIND = "creeper";
    public static final double SPEED = 1.0;
    public static final double CLOSE_ENOUGH = 1.2;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        return KindWindows.creeperLow(drone.height());
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.look(mob, drone);
        double distance = mob.getBoundingBox().getCenter().distanceTo(drone.center());
        double horizontal = Math.hypot(mob.getX() - drone.center().x, mob.getZ() - drone.center().z);
        if (horizontal > CLOSE_ENOUGH) {
            Moves.walk(mob, drone.ground(), SPEED, memory);
        } else {
            Moves.halt(mob, memory);
        }
        if (mob instanceof Creeper creeper) {
            boolean sees = mob.getSensing().hasLineOfSight(drone.entity());
            creeper.setSwellDir(KindWindows.creeperSwell(distance, creeper.getSwellDir(), sees));
        }
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob instanceof Creeper creeper) {
            creeper.setSwellDir(-1);
        }
        Moves.stop(mob, memory);
    }
}
