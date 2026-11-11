package de.lardtom.mobdefense.spear.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdBContactCooldownTest {
    static final int COOLDOWN = 10;

    static List<Long> hits(long from, int contactTicks, int cooldown) {
        ContactMemory memory = new ContactMemory();
        List<Long> out = new ArrayList<>();
        for (long tick = from; tick < from + contactTicks; tick++) {
            if (!memory.recent(42, tick, cooldown)) {
                memory.remember(42, tick);
                out.add(tick);
            }
        }
        return out;
    }

    @Test
    void pushingThroughAZombieAtFiveMetresPerSecondHitsOnce() {
        int contact = (int) Math.ceil((0.6 + 2 * 0.125) / (5.0 / 20.0));
        assertEquals(List.of(1000L), hits(1000L, contact, COOLDOWN), "Kontakt " + contact + " Ticks");
    }

    @Test
    void longContactHitsAtMostOncePerHalfSecond() {
        for (int contact = 1; contact <= 60; contact++) {
            List<Long> hits = hits(500L, contact, COOLDOWN);
            assertEquals((contact + COOLDOWN - 1) / COOLDOWN, hits.size(), "Kontakt " + contact + " Ticks");
            for (int i = 1; i < hits.size(); i++) {
                assertTrue(hits.get(i) - hits.get(i - 1) >= COOLDOWN, "Abstand " + (hits.get(i) - hits.get(i - 1)));
            }
        }
    }

    @Test
    void otherTargetsAreNotBlocked() {
        ContactMemory memory = new ContactMemory();
        memory.remember(1, 100L);
        for (int target = 2; target < 12; target++) {
            assertTrue(!memory.recent(target, 101L, COOLDOWN), "Ziel " + target);
            memory.remember(target, 101L);
        }
        assertTrue(memory.recent(1, 109L, COOLDOWN));
        assertTrue(!memory.recent(1, 110L, COOLDOWN));
    }
}
