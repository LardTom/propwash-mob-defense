package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class GroundReaction extends ChaseReaction {
    public static final String KIND = "ground";

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return (!(mob instanceof NeutralMob neutral) || neutral.isAngry()) && mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        return StageTwoWindows.ground(drone.height(), drone.landed());
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        chase(mob, drone, memory);
    }
}
