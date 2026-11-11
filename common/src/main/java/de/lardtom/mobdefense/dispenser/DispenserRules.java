package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class DispenserRules {
    private DispenserRules() {
    }

    public static FireGate.Rules of(@Nullable Level level) {
        return new FireGate.Rules(
                MobDefenseRules.flag(level, RuleDefaults.REAL_AMMO),
                MobDefenseRules.flag(level, RuleDefaults.ALLOW_TNT),
                MobDefenseRules.flag(level, RuleDefaults.ALLOW_FLUID_BUCKETS),
                MobDefenseRules.flag(level, RuleDefaults.DRONE_GRIEFING),
                MobDefenseRules.number(level, RuleDefaults.DISPENSER_COOLDOWN_TICKS));
    }

    public static boolean payloadsHitPlayers(@Nullable Level level) {
        return MobDefenseRules.flag(level, RuleDefaults.PAYLOADS_HIT_PLAYERS);
    }

    public static boolean droneGriefing(@Nullable Level level) {
        return MobDefenseRules.flag(level, RuleDefaults.DRONE_GRIEFING);
    }
}
