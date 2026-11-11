package de.lardtom.mobdefense.mob.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdCSwitchRuleTest {
    private static final double PLAYER = 20.0;

    @Test
    void zombieChasingFromTwentyBlocksStaysAtFifteen() {
        assertFalse(ReactionLimits.prefersDrone(15.0, PLAYER), "Drohne in 15 m, Spieler in 20 m: Zombie bleibt beim Spieler");
        assertFalse(ReactionLimits.prefersDrone(10.0, PLAYER), "genau halb so weit reicht nicht");
    }

    @Test
    void zombieSwitchesBelowHalfTheDistance() {
        assertTrue(ReactionLimits.prefersDrone(7.9, PLAYER), "Drohne unter 8 m, Spieler in 20 m: Zombie wechselt");
        assertTrue(ReactionLimits.prefersDrone(9.99, PLAYER));
        assertTrue(ReactionLimits.prefersDrone(0.0, PLAYER));
    }

    @Test
    void ruleIsHalfTheTargetDistanceForEveryTarget() {
        for (int target = 1; target <= 64; target++) {
            double half = target * ReactionLimits.SWITCH_FACTOR;
            assertTrue(ReactionLimits.prefersDrone(Math.nextDown(half), target), "knapp unter der Hälfte von " + target);
            assertFalse(ReactionLimits.prefersDrone(half, target), "genau die Hälfte von " + target);
            assertFalse(ReactionLimits.prefersDrone(half + 0.01, target), "knapp über der Hälfte von " + target);
        }
    }

    @Test
    void closerDroneNeverLosesAgainstFartherDrone() {
        for (double target = 2.0; target <= 40.0; target += 0.5) {
            boolean switched = false;
            for (double drone = 40.0; drone >= 0.0; drone -= 0.25) {
                boolean prefers = ReactionLimits.prefersDrone(drone, target);
                assertTrue(!switched || prefers, "einmal gewechselt, bleibt es beim Näherkommen beim Wechsel (Ziel " + target + ", Drohne " + drone + ")");
                switched |= prefers;
            }
            assertTrue(switched);
        }
    }

    @Test
    void withoutTargetTheDroneWinsAndInvalidDistancesNever() {
        assertTrue(ReactionLimits.prefersDrone(23.0, Double.POSITIVE_INFINITY));
        assertTrue(ReactionLimits.prefersDrone(23.0, Double.NaN));
        assertFalse(ReactionLimits.prefersDrone(-1.0, PLAYER));
        assertFalse(ReactionLimits.prefersDrone(Double.POSITIVE_INFINITY, PLAYER));
        assertEquals(0.5, ReactionLimits.SWITCH_FACTOR);
    }
}
