package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.Strikes;
import de.lardtom.mobdefense.mob.core.KindWindows;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.HoglinBase;

public final class BruteReaction extends ChaseReaction {
    public static final String KIND = "brute";
    public static final int ROAR_INTERVAL = 100;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return !(mob instanceof NeutralMob neutral) || neutral.isAngry();
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        chase(mob, drone, memory);
        if (mob instanceof Ravager) {
            if (memory.special > 0) {
                memory.special--;
            } else if (Strikes.touches(mob, drone, KindWindows.RAVAGER_ROAR)) {
                Strikes.roar(mob, drone.entity());
                memory.special = ROAR_INTERVAL;
            }
        }
    }

    @Override
    protected void strike(Mob mob, DroneState drone, ReactionMemory memory) {
        if (mob instanceof HoglinBase) {
            Strikes.bite(mob, drone.entity(), mob instanceof Zoglin ? SoundEvents.ZOGLIN_ATTACK : SoundEvents.HOGLIN_ATTACK);
        } else {
            Strikes.melee(mob, drone.entity());
        }
    }
}
