package de.lardtom.mobdefense.rules.core;

import java.util.List;

public final class RuleDefaults {
    public static final RuleSpec MOBS_REACT_TO_DRONES = RuleSpec.flag("mobs_react_to_drones", true);
    public static final RuleSpec MOB_REACTION_RANGE = RuleSpec.number("mob_reaction_range", 24, 8, 64);
    public static final RuleSpec MOB_DAMAGE_TO_DRONES_PERCENT = RuleSpec.number("mob_damage_to_drones_percent", 100, 0, 400);
    public static final RuleSpec PAYLOADS_HIT_PLAYERS = RuleSpec.flag("payloads_hit_players", false);
    public static final RuleSpec ALLOW_TNT = RuleSpec.flag("allow_tnt", true);
    public static final RuleSpec DRONE_GRIEFING = RuleSpec.flag("drone_griefing", true);
    public static final RuleSpec ALLOW_FLUID_BUCKETS = RuleSpec.flag("allow_fluid_buckets", false);
    public static final RuleSpec REAL_AMMO = RuleSpec.flag("real_ammo", true);
    public static final RuleSpec DISPENSER_COOLDOWN_TICKS = RuleSpec.number("dispenser_cooldown_ticks", 4, 0, 100);
    public static final RuleSpec SPEAR_DAMAGE_PERCENT = RuleSpec.number("spear_damage_percent", 100, 0, 100);
    public static final RuleSpec SCULK_HEARS_DRONES = RuleSpec.flag("sculk_hears_drones", true);

    public static final List<RuleSpec> ALL = List.of(MOBS_REACT_TO_DRONES, MOB_REACTION_RANGE, MOB_DAMAGE_TO_DRONES_PERCENT,
            PAYLOADS_HIT_PLAYERS, ALLOW_TNT, DRONE_GRIEFING, ALLOW_FLUID_BUCKETS, REAL_AMMO, DISPENSER_COOLDOWN_TICKS,
            SPEAR_DAMAGE_PERCENT, SCULK_HEARS_DRONES);
    public static final List<RuleSpec> OPTIONAL = List.of(SCULK_HEARS_DRONES);
    public static final List<RuleSpec> CORE = ALL.stream().filter(spec -> !OPTIONAL.contains(spec)).toList();
    public static final List<PresetValue> PRESET_VALUES = List.of(
            PresetValue.flag(PresetValue.REALISTIC, MOBS_REACT_TO_DRONES, true),
            PresetValue.flag(PresetValue.TRAINING, MOBS_REACT_TO_DRONES, false),
            PresetValue.flag(PresetValue.ARCADE, MOBS_REACT_TO_DRONES, true));

    private RuleDefaults() {
    }
}
