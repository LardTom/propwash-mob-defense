package de.lardtom.mobdefense.rules.core;

import de.lardtom.propwash.api.worldrule.WorldRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleDefaultsTest {
    @Test
    void ownerDecisions() {
        assertFalse(RuleDefaults.ALLOW_FLUID_BUCKETS.defaultFlag());
        assertTrue(RuleDefaults.ALLOW_TNT.defaultFlag());
        assertTrue(RuleDefaults.MOBS_REACT_TO_DRONES.defaultFlag());
        assertTrue(RuleDefaults.REAL_AMMO.defaultFlag());
        assertEquals(4, RuleDefaults.DISPENSER_COOLDOWN_TICKS.defaultValue());
        assertEquals(100, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
    }

    @Test
    void designTable() {
        assertEquals(List.of("mobs_react_to_drones", "mob_reaction_range", "mob_damage_to_drones_percent", "payloads_hit_players", "allow_tnt",
                "drone_griefing", "allow_fluid_buckets", "real_ammo", "dispenser_cooldown_ticks", "spear_damage_percent", "sculk_hears_drones"),
                RuleDefaults.ALL.stream().map(RuleSpec::path).toList());
        assertEquals(List.of(24, 8, 64), List.of(RuleDefaults.MOB_REACTION_RANGE.defaultValue(), RuleDefaults.MOB_REACTION_RANGE.min(),
                RuleDefaults.MOB_REACTION_RANGE.max()));
        assertEquals(List.of(100, 0, 400), List.of(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.defaultValue(),
                RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.min(), RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.max()));
        assertEquals(List.of(4, 0, 100), List.of(RuleDefaults.DISPENSER_COOLDOWN_TICKS.defaultValue(), RuleDefaults.DISPENSER_COOLDOWN_TICKS.min(),
                RuleDefaults.DISPENSER_COOLDOWN_TICKS.max()));
        assertEquals(List.of(100, 0, 100), List.of(RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue(), RuleDefaults.SPEAR_DAMAGE_PERCENT.min(),
                RuleDefaults.SPEAR_DAMAGE_PERCENT.max()));
        assertFalse(RuleDefaults.PAYLOADS_HIT_PLAYERS.defaultFlag());
        assertTrue(RuleDefaults.DRONE_GRIEFING.defaultFlag());
        assertTrue(RuleDefaults.SCULK_HEARS_DRONES.defaultFlag());
    }

    @Test
    void pathsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (RuleSpec spec : RuleDefaults.ALL) {
            assertTrue(seen.add(spec.path()), spec.path());
        }
    }

    @Test
    void coreIsEverythingButTheOptionalSculkRule() {
        List<RuleSpec> expected = new ArrayList<>(RuleDefaults.ALL);
        expected.remove(RuleDefaults.SCULK_HEARS_DRONES);
        assertEquals(expected, RuleDefaults.CORE);
        assertEquals(List.of(RuleDefaults.SCULK_HEARS_DRONES), RuleDefaults.OPTIONAL);
    }

    @Test
    void trainingSwitchesMobReactionsOffAndTheOtherPresetsBackOn() {
        assertEquals(List.of("realistic=1", "training=0", "arcade=1"), RuleDefaults.PRESET_VALUES.stream()
                .filter(value -> value.rule() == RuleDefaults.MOBS_REACT_TO_DRONES).map(value -> value.preset() + "=" + value.value()).toList());
        assertEquals(3, RuleDefaults.PRESET_VALUES.size(), "andere Regeln bleiben beim Anwenden einer Voreinstellung unverändert");
        for (PresetValue value : RuleDefaults.PRESET_VALUES) {
            assertTrue(RuleDefaults.CORE.contains(value.rule()), value + ": nur angemeldete Regeln");
        }
    }

    @Test
    void presetIdsAreThePropwashPresets() {
        assertEquals(WorldRules.PRESETS, PresetValue.PRESETS);
        assertEquals(WorldRules.PRESET_TRAINING, PresetValue.TRAINING);
    }

    @Test
    void presetValuesAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new PresetValue("race", RuleDefaults.ALLOW_TNT, 1));
        assertThrows(IllegalArgumentException.class, () -> new PresetValue(PresetValue.TRAINING, RuleDefaults.MOB_REACTION_RANGE, 65));
        assertThrows(IllegalArgumentException.class, () -> PresetValue.flag(PresetValue.TRAINING, RuleDefaults.MOB_REACTION_RANGE, true));
        assertFalse(PresetValue.flag(PresetValue.TRAINING, RuleDefaults.ALLOW_TNT, false).flagValue());
    }
}
