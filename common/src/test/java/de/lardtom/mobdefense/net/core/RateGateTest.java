package de.lardtom.mobdefense.net.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateGateTest {
    @Test
    void atMostFourPerSecondAndPilot() {
        RateGate<String> gate = new RateGate<>(4, 20);
        int allowed = 0;
        for (int i = 0; i < 50; i++) {
            if (gate.allow("pilot", 100)) {
                allowed++;
            }
        }
        assertEquals(4, allowed);
        assertTrue(gate.allow("other", 100));
        assertFalse(gate.allow("pilot", 119));
        assertTrue(gate.allow("pilot", 120));
    }

    @Test
    void noWindowEverHoldsMoreThanTheLimit() {
        RateGate<String> gate = new RateGate<>(4, 20);
        int[] sent = new int[400];
        for (int tick = 0; tick < 400; tick++) {
            for (int burst = 0; burst < 3; burst++) {
                if (gate.allow("pilot", tick)) {
                    sent[tick]++;
                }
            }
        }
        for (int start = 0; start + 20 <= 400; start++) {
            int sum = 0;
            for (int tick = start; tick < start + 20; tick++) {
                sum += sent[tick];
            }
            assertTrue(sum <= 4, "Fenster ab " + start + ": " + sum);
        }
    }

    @Test
    void reserveKeepsTheLastPlaceForTheWarning() {
        RateGate<String> gate = new RateGate<>(4, 20);
        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (gate.allow("pilot", 100, 1)) {
                allowed++;
            }
        }
        assertEquals(3, allowed);
        assertTrue(gate.allow("pilot", 101, 0));
        assertFalse(gate.allow("pilot", 102, 0));
        assertFalse(gate.allow("pilot", 119, 1));
        assertTrue(gate.allow("pilot", 120, 1));
        assertTrue(gate.allow("pilot", 120, 1));
        assertFalse(gate.allow("pilot", 120, 1));
        assertTrue(gate.allow("pilot", 121, 0));
        assertThrows(IllegalArgumentException.class, () -> gate.allow("pilot", 130, 4));
        assertThrows(IllegalArgumentException.class, () -> gate.allow("pilot", 130, -1));
    }

    @Test
    void earlierClockAfterAWorldChangeStartsFresh() {
        RateGate<String> gate = new RateGate<>(4, 20);
        for (int i = 0; i < 3; i++) {
            assertTrue(gate.allow("pilot", 5000, 1));
        }
        assertFalse(gate.allow("pilot", 5000, 1));
        for (long tick : new long[] {100, 200, 300, 4000}) {
            assertTrue(gate.allow("pilot", tick, 1), "Tick " + tick + " der neuen Welt verworfen, Ticks der alten Welt zählen noch");
        }
        assertTrue(gate.allow("pilot", 4000, 1));
        assertTrue(gate.allow("pilot", 4000, 1));
        assertFalse(gate.allow("pilot", 4000, 1));
        assertTrue(gate.allow("pilot", 4001, 0));
        assertFalse(gate.allow("pilot", 4019, 0));
        assertTrue(gate.allow("pilot", 4020, 1));
    }

    @Test
    void prunesPilotsOfAnEarlierClock() {
        RateGate<Integer> gate = new RateGate<>(4, 20);
        for (int i = 0; i < 100; i++) {
            gate.allow(i, 5000);
        }
        gate.allow(-1, 10);
        gate.allow(-2, 11);
        assertTrue(gate.size() <= 2, "Einträge der alten Welt bleiben liegen: " + gate.size());
    }

    @Test
    void prunesOldPilots() {
        RateGate<Integer> gate = new RateGate<>(4, 20);
        for (int i = 0; i < 200; i++) {
            gate.allow(i, i);
        }
        assertTrue(gate.size() < 100, "alte Einträge bleiben liegen: " + gate.size());
        assertThrows(IllegalArgumentException.class, () -> new RateGate<String>(0, 20));
    }
}
