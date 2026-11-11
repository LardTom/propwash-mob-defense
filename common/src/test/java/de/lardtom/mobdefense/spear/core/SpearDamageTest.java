package de.lardtom.mobdefense.spear.core;

import de.lardtom.mobdefense.rules.core.RuleDefaults;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpearDamageTest {
    static final Map<String, SpearDamage.Stats> VANILLA = vanilla();

    private static Map<String, SpearDamage.Stats> vanilla() {
        Map<String, SpearDamage.Stats> out = new LinkedHashMap<>();
        out.put("wooden", stats(0.7F, 14.0F, 5.1F, 4.6F));
        out.put("stone", stats(0.82F, 13.0F, 5.1F, 4.6F));
        out.put("copper", stats(0.82F, 12.0F, 5.1F, 4.6F));
        out.put("iron", stats(0.95F, 11.0F, 5.1F, 4.6F));
        out.put("golden", stats(0.7F, 13.0F, 5.1F, 4.6F));
        out.put("diamond", stats(1.075F, 10.0F, 5.1F, 4.6F));
        out.put("netherite", stats(1.2F, 9.0F, 5.1F, 4.6F));
        return out;
    }

    static SpearDamage.Stats stats(float multiplier, float dismount, float knockback, float damage) {
        return new SpearDamage.Stats(multiplier, Optional.of(new SpearDamage.Threshold(0.0F, damage)),
                Optional.of(new SpearDamage.Threshold(knockback, 0.0F)), Optional.of(new SpearDamage.Threshold(dismount, 0.0F)));
    }

    private static SpearDamage.Stats iron() {
        return VANILLA.get("iron");
    }

    @Test
    void vanillaFormulaIsOnePlusFloorOfSpeedTimesMultiplier() {
        assertEquals(15, SpearDamage.vanilla(15.0, 0.95F));
        assertEquals(29, SpearDamage.vanilla(30.0, 0.95F));
        assertEquals(4, SpearDamage.vanilla(4.6, 0.7F));
        assertEquals(1, SpearDamage.vanilla(0.0, 1.2F));
        assertEquals(1, SpearDamage.vanilla(-5.0, 1.2F));
    }

    @Test
    void floatMultiplierRoundsLikeVanilla() {
        assertEquals(1 + (int) Math.floor(20.0 * 0.95F), SpearDamage.vanilla(20.0, 0.95F));
        assertEquals(19, SpearDamage.vanilla(20.0, 0.95F));
    }

    @Test
    void designExamplesForTheIronSpear() {
        assertEquals(7.0F, SpearDamage.scaled(15.0, 0.95F, 50));
        assertEquals(14.0F, SpearDamage.scaled(30.0, 0.95F, 50));
        assertEquals(10.0F, SpearDamage.scaled(10.0, 0.95F, SpearDamage.DEFAULT_PERCENT));
        assertEquals(15.0F, SpearDamage.scaled(15.0, 0.95F, SpearDamage.DEFAULT_PERCENT));
        assertEquals(15.0F, SpearDamage.scaled(30.0, 0.95F, SpearDamage.DEFAULT_PERCENT));
    }

    @Test
    void vanillaIsTheDefault() {
        assertEquals(100, SpearDamage.DEFAULT_PERCENT);
        assertEquals(SpearDamage.DEFAULT_PERCENT, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
        assertEquals(SpearDamage.MAX_PERCENT, RuleDefaults.SPEAR_DAMAGE_PERCENT.max());
        assertEquals(0, RuleDefaults.SPEAR_DAMAGE_PERCENT.min());
    }

    @Test
    void damageNeverExceedsFifteen() {
        assertEquals(15.0F, SpearDamage.scaled(40.0, 1.2F, 50));
        assertEquals(15.0F, SpearDamage.scaled(20.0, 0.95F, 100));
        assertEquals(15.0F, SpearDamage.scaled(1000.0, 1.2F, 100));
        for (SpearDamage.Stats stats : VANILLA.values()) {
            for (double speed = 0.0; speed <= 80.0; speed += 0.25) {
                for (int percent = 0; percent <= 100; percent += 5) {
                    float amount = SpearDamage.scaled(speed, stats.damageMultiplier(), percent);
                    assertTrue(amount >= 0.0F && amount <= SpearDamage.MAX_DAMAGE, speed + " m/s, " + percent + " %: " + amount);
                    assertEquals(Math.floor(amount), amount, 0.0);
                }
            }
        }
    }

    @Test
    void percentScalesAndIsClamped() {
        assertEquals(0.0F, SpearDamage.scaled(15.0, 0.95F, 0));
        assertEquals(0.0F, SpearDamage.scaled(15.0, 0.95F, -20));
        assertEquals(15.0F, SpearDamage.scaled(15.0, 0.95F, 100));
        assertEquals(15.0F, SpearDamage.scaled(15.0, 0.95F, 250));
        assertEquals(3.0F, SpearDamage.scaled(15.0, 0.95F, 25));
        assertEquals(1.0F, SpearDamage.scaled(15.0, 0.95F, 10));
    }

    @Test
    void lessDamageThanVanillaBelowHundredPercent() {
        for (Map.Entry<String, SpearDamage.Stats> entry : VANILLA.entrySet()) {
            for (double speed = 4.6; speed <= 60.0; speed += 0.5) {
                float drone = SpearDamage.scaled(speed, entry.getValue().damageMultiplier(), 50);
                assertTrue(drone < SpearDamage.vanilla(speed, entry.getValue().damageMultiplier()), entry.getKey() + " " + speed);
            }
        }
    }

    @Test
    void defaultDealsVanillaDamageUpToTheCap() {
        for (Map.Entry<String, SpearDamage.Stats> entry : VANILLA.entrySet()) {
            for (double speed = 4.6; speed <= 60.0; speed += 0.5) {
                float multiplier = entry.getValue().damageMultiplier();
                float drone = SpearDamage.scaled(speed, multiplier, SpearDamage.DEFAULT_PERCENT);
                assertEquals(Math.min(SpearDamage.MAX_DAMAGE, SpearDamage.vanilla(speed, multiplier)), drone, entry.getKey() + " " + speed);
            }
        }
    }

    @Test
    void noDamageBelowTheDamageThreshold() {
        SpearDamage.Outcome slow = SpearDamage.evaluate(iron(), 4.5, 4.5, 50);
        assertSame(SpearDamage.Outcome.NONE, slow);
        assertFalse(slow.any());
        SpearDamage.Outcome stop = SpearDamage.evaluate(iron(), 0.0, 0.0, 50);
        assertSame(SpearDamage.Outcome.NONE, stop);
        SpearDamage.Outcome edge = SpearDamage.evaluate(iron(), 4.6F, 4.6F, 50);
        assertTrue(edge.damage());
        assertFalse(edge.knockback());
        assertEquals(2.0F, edge.amount());
    }

    @Test
    void thresholdsOfTheComponentDecideKnockbackAndDismount() {
        SpearDamage.Outcome fifteen = SpearDamage.evaluate(iron(), 15.0, 15.0, 50);
        assertTrue(fifteen.damage());
        assertTrue(fifteen.knockback());
        assertTrue(fifteen.dismount());
        assertEquals(7.0F, fifteen.amount());
        SpearDamage.Outcome eight = SpearDamage.evaluate(iron(), 8.0, 8.0, 50);
        assertTrue(eight.damage());
        assertTrue(eight.knockback());
        assertFalse(eight.dismount());
        SpearDamage.Outcome five = SpearDamage.evaluate(iron(), 5.0, 5.0, 50);
        assertTrue(five.damage());
        assertFalse(five.knockback());
        assertFalse(five.dismount());
        SpearDamage.Outcome wood = SpearDamage.evaluate(VANILLA.get("wooden"), 13.0, 13.0, 50);
        assertFalse(wood.dismount());
        assertTrue(SpearDamage.evaluate(VANILLA.get("netherite"), 9.0, 9.0, 50).dismount());
    }

    @Test
    void relativeSpeedDecidesDamageAttackerSpeedDecidesKnockback() {
        SpearDamage.Outcome fleeing = SpearDamage.evaluate(iron(), 8.0, 3.0, 50);
        assertFalse(fleeing.damage());
        assertTrue(fleeing.knockback());
        assertEquals(0.0F, fleeing.amount());
        SpearDamage.Outcome charging = SpearDamage.evaluate(iron(), 2.0, 9.0, 50);
        assertTrue(charging.damage());
        assertFalse(charging.knockback());
        assertEquals(4.0F, charging.amount());
        SpearDamage.Outcome negative = SpearDamage.evaluate(iron(), 8.0, -4.0, 50);
        assertFalse(negative.damage());
    }

    @Test
    void zeroPercentKeepsTheHitButDealsNoDamage() {
        SpearDamage.Outcome outcome = SpearDamage.evaluate(iron(), 15.0, 15.0, 0);
        assertTrue(outcome.damage());
        assertEquals(0.0F, outcome.amount());
        assertTrue(outcome.knockback());
    }

    @Test
    void missingConditionsMeanNoEffect() {
        SpearDamage.Stats none = new SpearDamage.Stats(1.0F, Optional.empty(), Optional.empty(), Optional.empty());
        assertSame(SpearDamage.Outcome.NONE, SpearDamage.evaluate(none, 50.0, 50.0, 100));
        SpearDamage.Stats knockbackOnly = new SpearDamage.Stats(1.0F, Optional.empty(), Optional.of(new SpearDamage.Threshold(5.1F, 0.0F)),
                Optional.empty());
        SpearDamage.Outcome outcome = SpearDamage.evaluate(knockbackOnly, 20.0, 20.0, 100);
        assertFalse(outcome.damage());
        assertTrue(outcome.knockback());
        assertEquals(0.0F, outcome.amount());
    }

    @Test
    void brokenNumbersAreIgnored() {
        assertSame(SpearDamage.Outcome.NONE, SpearDamage.evaluate(iron(), Double.NaN, 15.0, 50));
        assertSame(SpearDamage.Outcome.NONE, SpearDamage.evaluate(iron(), 15.0, Double.POSITIVE_INFINITY, 50));
        assertEquals(1, SpearDamage.vanilla(Double.NaN, 0.95F));
    }

    @Test
    void droneCountsAsPlayerForTheThresholds() {
        assertEquals(1.0F, SpearDamage.ENTITY_FACTOR);
        assertTrue(new SpearDamage.Threshold(5.1F, 0.0F).met(5.1F, 0.0));
        assertFalse(new SpearDamage.Threshold(5.1F, 0.0F).met(5.0, 100.0));
    }
}
