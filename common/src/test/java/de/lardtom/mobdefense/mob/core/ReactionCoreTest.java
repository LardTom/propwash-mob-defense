package de.lardtom.mobdefense.mob.core;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionCoreTest {
    @Test
    void designNumbers() {
        assertEquals(10, ReactionLimits.SEARCH_PERIOD);
        assertEquals(12, ReactionLimits.MOBS_PER_DRONE);
        assertEquals(64, ReactionLimits.EVALUATIONS_PER_TICK);
        assertEquals(0.5, ReactionLimits.SWITCH_FACTOR);
        assertTrue(ReactionLimits.HOLD_TICKS > ReactionLimits.SEARCH_PERIOD, "Zuordnung hält länger als ein Suchtakt");
    }

    @Test
    void searchIsStaggeredByEntityId() {
        Map<Long, Integer> perTick = new HashMap<>();
        for (int id = 0; id < 100; id++) {
            int due = 0;
            for (long tick = 1000; tick < 1000 + ReactionLimits.SEARCH_PERIOD; tick++) {
                if (ReactionLimits.due(tick, id, ReactionLimits.SEARCH_PERIOD)) {
                    due++;
                    perTick.merge(tick, 1, Integer::sum);
                }
            }
            assertEquals(1, due, "Drohne " + id + " genau einmal je 10 Ticks");
        }
        perTick.values().forEach(count -> assertEquals(10, count, "100 Drohnen gleichmäßig auf 10 Ticks verteilt"));
        assertTrue(ReactionLimits.due(-3, 3, 10));
        assertThrows(IllegalArgumentException.class, () -> ReactionLimits.due(0, 0, 0));
    }

    @Test
    void playersStayFirstTarget() {
        assertTrue(ReactionLimits.prefersDrone(4.0, 10.0));
        assertFalse(ReactionLimits.prefersDrone(5.0, 10.0), "genau halb so weit reicht nicht");
        assertFalse(ReactionLimits.prefersDrone(8.0, 10.0));
        assertTrue(ReactionLimits.prefersDrone(1.0, Double.POSITIVE_INFINITY));
        assertFalse(ReactionLimits.prefersDrone(Double.NaN, 10.0));
    }

    @Test
    void rangeIsTheSmallerOfRuleAndKind() {
        assertEquals(24.0, ReactionLimits.range(24, KindWindows.GHAST_MAX));
        assertEquals(16.0, ReactionLimits.range(24, KindWindows.BLAZE_MAX));
        assertEquals(64.0, ReactionLimits.range(64, Double.MAX_VALUE));
        assertEquals(8.0, ReactionLimits.range(8, KindWindows.ARCHER_MAX));
    }

    @Test
    void memoryLastsHundredTicks() {
        assertTrue(ReactionLimits.remembers(200, 100));
        assertFalse(ReactionLimits.remembers(201, 100));
    }

    @Test
    void budgetStopsAtSixtyFour() {
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        int taken = 0;
        while (budget.take()) {
            taken++;
        }
        assertEquals(64, taken);
        assertTrue(budget.exhausted());
        assertEquals(0, budget.left());
        assertEquals(64, budget.used());
        assertThrows(IllegalArgumentException.class, () -> new Budget(-1));
    }

    @Test
    void chaseStrikesOnlyLowOrLandedDrones() {
        assertTrue(KindWindows.chaseStrike(4.45, 1.95, false));
        assertFalse(KindWindows.chaseStrike(4.46, 1.95, false));
        assertTrue(KindWindows.chaseStrike(20.0, 1.95, true));
    }

    @Test
    void archerWindow() {
        assertFalse(KindWindows.archer(3.9));
        assertTrue(KindWindows.archer(4.0));
        assertTrue(KindWindows.archer(24.0));
        assertFalse(KindWindows.archer(24.1));
    }

    @Test
    void creeperSwellsCloseAndDefusesFar() {
        assertTrue(KindWindows.creeperLow(4.0));
        assertFalse(KindWindows.creeperLow(4.1));
        assertEquals(1, KindWindows.creeperSwell(3.0, -1, true));
        assertEquals(-1, KindWindows.creeperSwell(3.1, -1, true));
        assertEquals(1, KindWindows.creeperSwell(6.9, 1, true), "einmal gezündet bis 7 Blöcke weiter");
        assertEquals(-1, KindWindows.creeperSwell(7.1, 1, true));
        assertEquals(-1, KindWindows.creeperSwell(1.0, 1, false), "ohne Sichtlinie aus");
    }

    @Test
    void phantomNeedsHeightOrNight() {
        assertFalse(KindWindows.phantom(10.0, false));
        assertTrue(KindWindows.phantom(10.1, false));
        assertTrue(KindWindows.phantom(2.0, true));
    }

    @Test
    void landedNearGround() {
        assertTrue(KindWindows.landed(true, 3.0));
        assertTrue(KindWindows.landed(false, 0.1));
        assertFalse(KindWindows.landed(false, 0.3));
    }
}
