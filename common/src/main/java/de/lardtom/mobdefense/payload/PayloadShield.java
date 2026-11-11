package de.lardtom.mobdefense.payload;

import de.lardtom.mobdefense.dispenser.DispenserRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class PayloadShield {
    private PayloadShield() {
    }

    public static boolean allowDamage(LivingEntity target, DamageSource source) {
        if (!(target instanceof Player) || !(target.level() instanceof ServerLevel level)) {
            return true;
        }
        if (!Payloads.is(source.getDirectEntity())) {
            return true;
        }
        return !shields(level);
    }

    public static boolean shields(ServerLevel level) {
        return !DispenserRules.payloadsHitPlayers(level) || !level.isPvpAllowed();
    }
}
