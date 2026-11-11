package de.lardtom.mobdefense.booster.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoostProfileTest {
    private static final double EPS = 1.0E-9;

    @Test
    void burnTimeFollowsFlightDurationOneTwoThree() {
        assertEquals(25, BoostProfile.burnTicks(1));
        assertEquals(35, BoostProfile.burnTicks(2));
        assertEquals(45, BoostProfile.burnTicks(3));
        assertEquals(1.25, BoostProfile.burnSeconds(1), EPS);
        assertEquals(1.75, BoostProfile.burnSeconds(2), EPS);
        assertEquals(2.25, BoostProfile.burnSeconds(3), EPS);
    }

    @Test
    void flightOutsideOneToThreeIsClamped() {
        assertEquals(1, BoostProfile.flight(0));
        assertEquals(1, BoostProfile.flight(-4));
        assertEquals(3, BoostProfile.flight(4));
        assertEquals(3, BoostProfile.flight(255));
        assertEquals(25, BoostProfile.burnTicks(0));
        assertEquals(45, BoostProfile.burnTicks(127));
    }

    @Test
    void thrustIsMassTimesNineteenPointSix() {
        assertEquals(19.6, BoostProfile.NEWTONS_PER_KG, EPS);
        assertEquals(0.03 * 19.6, BoostProfile.thrustNewtons(0.03), EPS);
        assertEquals(0.65 * 19.6, BoostProfile.thrustNewtons(0.65), EPS);
        assertEquals(1.5 * 19.6, BoostProfile.thrustNewtons(1.5), EPS);
        assertEquals(3.0 * 19.6, BoostProfile.thrustNewtons(3.0), EPS);
    }

    @Test
    void sameAccelerationForEveryDroneSizeBelowTheLimit() {
        for (double mass : new double[]{0.025, 0.12, 0.65, 1.1, 2.4, 3.0}) {
            assertEquals(2.0 * BoostProfile.GRAVITY, BoostProfile.acceleration(mass), 1.0E-9, "Masse " + mass);
            assertFalse(BoostProfile.capped(mass), "Masse " + mass);
        }
    }

    @Test
    void thrustStopsAtSixtyNewtons() {
        double limitMass = 60.0 / 19.6;
        assertEquals(60.0, BoostProfile.thrustNewtons(limitMass), 1.0E-9);
        assertEquals(60.0, BoostProfile.thrustNewtons(4.5), EPS);
        assertEquals(60.0, BoostProfile.thrustNewtons(12.0), EPS);
        assertTrue(BoostProfile.capped(4.5));
        assertEquals(60.0 / 4.5, BoostProfile.acceleration(4.5), EPS);
        assertTrue(BoostProfile.acceleration(4.5) < 2.0 * BoostProfile.GRAVITY);
    }

    @Test
    void fiveInchGetsAboutTwentyFiveMetresPerSecondFromAShortRocket() {
        assertEquals(24.5, BoostProfile.deltaV(0.65, 1), 1.0E-9);
        assertEquals(34.3, BoostProfile.deltaV(0.65, 2), 1.0E-9);
        assertEquals(44.1, BoostProfile.deltaV(0.65, 3), 1.0E-9);
        assertEquals(0.65 * 24.5, BoostProfile.impulse(BoostProfile.thrustNewtons(0.65), BoostProfile.burnTicks(1)), 1.0E-9);
    }

    @Test
    void invalidMassGivesNoThrust() {
        assertEquals(0.0, BoostProfile.thrustNewtons(0.0));
        assertEquals(0.0, BoostProfile.thrustNewtons(-1.0));
        assertEquals(0.0, BoostProfile.thrustNewtons(Double.NaN));
        assertEquals(0.0, BoostProfile.thrustNewtons(Double.POSITIVE_INFINITY));
        assertEquals(0.0, BoostProfile.acceleration(0.0));
        assertEquals(0.0, BoostProfile.impulse(-5.0, 10));
    }

    @Test
    void burningAndRemainingTime() {
        assertTrue(BoostProfile.burning(125L, 100L));
        assertFalse(BoostProfile.burning(100L, 100L));
        assertFalse(BoostProfile.burning(0L, 100L));
        assertEquals(1.25, BoostProfile.remainingSeconds(125L, 100L), EPS);
        assertEquals(0.0, BoostProfile.remainingSeconds(90L, 100L), EPS);
    }

    @Test
    void starsExplodeLikeAnElytraRocket() {
        assertEquals(0.0F, BoostProfile.blastDamage(0));
        assertEquals(7.0F, BoostProfile.blastDamage(1));
        assertEquals(11.0F, BoostProfile.blastDamage(3));
        assertEquals(7.0F, BoostProfile.blastDamageAt(1, 0.0), 1.0E-6);
        assertEquals(7.0F * (float) Math.sqrt(0.5), BoostProfile.blastDamageAt(1, 2.5), 1.0E-5);
        assertEquals(0.0F, BoostProfile.blastDamageAt(1, 5.0), 1.0E-6);
        assertEquals(0.0F, BoostProfile.blastDamageAt(1, 5.01));
        assertEquals(0.0F, BoostProfile.blastDamageAt(0, 1.0));
        assertEquals(0.0F, BoostProfile.blastDamageAt(2, Double.NaN));
    }

    @Test
    void rocketWeighsSixtyGrams() {
        assertEquals(60.0, BoostProfile.payloadGrams(1), EPS);
        assertEquals(0.0, BoostProfile.payloadGrams(0), EPS);
        assertEquals(0.0, BoostProfile.payloadGrams(-2), EPS);
    }
}
