package de.lardtom.mobdefense.mob.core;

import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.net.Notices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatPulseTest {
    private static final int LATENCY = 4;

    @Test
    void numbersFitTheNoticeLimits() {
        assertEquals(Notice.DEFAULT_TICKS, ThreatPulse.SHOW_TICKS, "BEDROHUNG steht wie jede Warnung 2 s");
        assertTrue(ThreatPulse.SHOW_TICKS <= Notice.MAX_TICKS);
        assertTrue(ThreatPulse.REFRESH_TICKS + LATENCY < ThreatPulse.SHOW_TICKS, "Auffrischen vor dem Ablauf");
        assertTrue(ThreatPulse.RETRY_TICKS < ThreatPulse.REFRESH_TICKS);
        assertTrue(Notices.WINDOW_TICKS / ThreatPulse.REFRESH_TICKS < Notices.PER_SECOND, "Platz für die Hinweise des Dispensers");
        assertTrue(Notices.WINDOW_TICKS / ThreatPulse.REFRESH_TICKS <= Notices.THREAT_RESERVE, "BEDROHUNG passt in den freigehaltenen Platz");
        assertTrue(Notices.THREAT_RESERVE < Notices.PER_SECOND);
    }

    @Test
    void firstThreatIsSentAtOnce() {
        assertTrue(ThreatPulse.due(0, ThreatPulse.NEVER));
        assertTrue(ThreatPulse.due(123_456, ThreatPulse.NEVER));
        assertTrue(ThreatPulse.due(-5, ThreatPulse.NEVER));
    }

    @Test
    void refreshOnlyAfterTheInterval() {
        long last = ThreatPulse.mark(1000, true);
        assertEquals(1000, last);
        assertFalse(ThreatPulse.due(1000, last));
        assertFalse(ThreatPulse.due(1000 + ThreatPulse.REFRESH_TICKS - 1, last));
        assertTrue(ThreatPulse.due(1000 + ThreatPulse.REFRESH_TICKS, last));
        assertTrue(ThreatPulse.due(999, last), "Uhr zurückgestellt");
    }

    @Test
    void droppedPacketIsRetriedSoon() {
        long last = ThreatPulse.mark(500, false);
        assertFalse(ThreatPulse.due(500 + ThreatPulse.RETRY_TICKS - 1, last));
        assertTrue(ThreatPulse.due(500 + ThreatPulse.RETRY_TICKS, last));
    }

    @Test
    void continuousThreatStaysVisibleWithOnePacketPerSecond() {
        List<Long> sends = run(0, 600, tick -> true, tick -> true);
        assertEquals(0L, sends.getFirst(), "sofort beim ersten Ziel");
        for (long tick = LATENCY; tick < 600; tick++) {
            assertTrue(visible(sends, tick), "BEDROHUNG sichtbar in Tick " + tick);
        }
        for (long start = 0; start < 600; start++) {
            long from = start;
            long inWindow = sends.stream().filter(send -> send >= from && send < from + Notices.WINDOW_TICKS).count();
            assertTrue(inWindow <= 1, inWindow + " Pakete ab Tick " + start);
        }
    }

    @Test
    void warningEndsWithinShowTicksAfterTheLastThreat() {
        List<Long> sends = run(0, 400, tick -> tick < 100, tick -> true);
        assertTrue(sends.stream().allMatch(send -> send < 100));
        assertTrue(visible(sends, 100));
        assertFalse(visible(sends, 99 + LATENCY + ThreatPulse.SHOW_TICKS));
    }

    @Test
    void fullGateDelaysButDoesNotSpam() {
        List<Long> attempts = new ArrayList<>();
        List<Long> sends = run(0, 200, tick -> true, tick -> {
            attempts.add(tick);
            return tick >= 12;
        });
        assertEquals(List.of(0L, 5L, 10L, 15L), attempts.subList(0, 4));
        assertEquals(15L, sends.getFirst());
        assertTrue(attempts.size() <= sends.size() + 3);
    }

    private static List<Long> run(long from, long to, LongPredicate threatened, LongPredicate gate) {
        List<Long> sends = new ArrayList<>();
        long last = ThreatPulse.NEVER;
        for (long tick = from; tick < to; tick++) {
            if (!threatened.test(tick) || !ThreatPulse.due(tick, last)) {
                continue;
            }
            boolean sent = gate.test(tick);
            if (sent) {
                sends.add(tick);
            }
            last = ThreatPulse.mark(tick, sent);
        }
        return sends;
    }

    private static boolean visible(List<Long> sends, long tick) {
        return sends.stream().anyMatch(send -> send + LATENCY <= tick && tick < send + LATENCY + ThreatPulse.SHOW_TICKS);
    }
}
