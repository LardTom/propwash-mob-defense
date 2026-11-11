package de.lardtom.mobdefense.net;

import de.lardtom.mobdefense.dispenser.core.FireCadence;
import de.lardtom.mobdefense.mob.core.ThreatPulse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatUnderFireTest {
    private static final int LATENCY = 4;
    private static final int TICKS = 426;

    @Test
    void threatAppearsWhileFiringIntoAnEmptyDispenser() {
        Run run = run(FireCadence.DEFAULT_DELAY_TICKS, FireCadence.DEFAULT_INTERVAL_TICKS, 30);
        assertTrue(!run.threats.isEmpty(), "BEDROHUNG nie gesendet: 0 von " + run.threatAttempts + " Versuchen, KEINE MUNITION "
                + run.noAmmo.size() + " von " + run.noAmmoAttempts);
        assertEquals(30L, run.threats.getFirst(), "BEDROHUNG sofort beim ersten Mob");
        int hidden = 0;
        for (long tick = 30 + LATENCY; tick < TICKS; tick++) {
            if (!visible(run.threats, tick, ThreatPulse.SHOW_TICKS)) {
                hidden++;
            }
        }
        assertEquals(0, hidden, hidden + " von " + (TICKS - 30 - LATENCY) + " Ticks ohne BEDROHUNG");
    }

    @Test
    void everyCadenceAndStartKeepsBothWarningsVisibleWithinTheLimit() {
        for (int interval = FireCadence.MIN_TICKS; interval <= FireCadence.MAX_TICKS; interval++) {
            for (int start = 0; start < 2 * Notices.WINDOW_TICKS; start++) {
                Run run = run(FireCadence.DEFAULT_DELAY_TICKS, interval, start);
                String where = "Abstand " + interval + ", Bedrohung ab Tick " + start;
                assertTrue(!run.threats.isEmpty() && run.threats.getFirst() == start, where + ": BEDROHUNG nicht sofort " + run.threats);
                for (long tick = start + LATENCY; tick < TICKS; tick++) {
                    assertTrue(visible(run.threats, tick, ThreatPulse.SHOW_TICKS), where + ": BEDROHUNG fehlt in Tick " + tick);
                }
                for (long tick = LATENCY; tick < TICKS; tick++) {
                    assertTrue(visible(run.noAmmo, tick, Notice.DEFAULT_TICKS), where + ": KEINE MUNITION fehlt in Tick " + tick);
                }
                List<Long> all = new ArrayList<>(run.noAmmo);
                all.addAll(run.threats);
                for (long from = 0; from < TICKS; from++) {
                    long first = from;
                    long inWindow = all.stream().filter(send -> send >= first && send < first + Notices.WINDOW_TICKS).count();
                    assertTrue(inWindow <= Notices.PER_SECOND, where + ": " + inWindow + " Pakete ab Tick " + from);
                }
            }
        }
    }

    @Test
    void threatBurstStillHitsTheFullLimit() {
        UUID pilot = UUID.randomUUID();
        int sent = 0;
        for (int i = 0; i < 10; i++) {
            if (Notices.admit(pilot, Notice.THREAT, 1000)) {
                sent++;
            }
        }
        assertEquals(Notices.PER_SECOND, sent);
    }

    private static Run run(int delay, int interval, long threatFrom) {
        UUID pilot = UUID.randomUUID();
        Run run = new Run();
        long last = ThreatPulse.NEVER;
        long nextShot = 0;
        for (long tick = 0; tick < TICKS; tick++) {
            if (tick == nextShot) {
                run.noAmmoAttempts++;
                if (Notices.admit(pilot, Notice.NO_AMMO, tick)) {
                    run.noAmmo.add(tick);
                }
                nextShot = tick + (tick == 0 ? delay : interval);
            }
            if (tick < threatFrom || !ThreatPulse.due(tick, last)) {
                continue;
            }
            run.threatAttempts++;
            boolean sent = Notices.admit(pilot, Notice.THREAT, tick);
            if (sent) {
                run.threats.add(tick);
            }
            last = ThreatPulse.mark(tick, sent);
        }
        return run;
    }

    private static boolean visible(List<Long> sends, long tick, int show) {
        return sends.stream().anyMatch(send -> send + LATENCY <= tick && tick < send + LATENCY + show);
    }

    private static final class Run {
        final List<Long> noAmmo = new ArrayList<>();
        final List<Long> threats = new ArrayList<>();
        int noAmmoAttempts;
        int threatAttempts;
    }
}
