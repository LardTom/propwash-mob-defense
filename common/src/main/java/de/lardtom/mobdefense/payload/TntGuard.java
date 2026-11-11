package de.lardtom.mobdefense.payload;

import de.lardtom.mobdefense.dispenser.DispenserRules;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;

public final class TntGuard {
    private static int tamed;

    private TntGuard() {
    }

    public static Level.ExplosionInteraction interaction(PrimedTnt tnt, Level.ExplosionInteraction original) {
        if (!Payloads.is(tnt) || DispenserRules.droneGriefing(tnt.level())) {
            return original;
        }
        tamed++;
        return Level.ExplosionInteraction.NONE;
    }

    public static int tamed() {
        return tamed;
    }
}
