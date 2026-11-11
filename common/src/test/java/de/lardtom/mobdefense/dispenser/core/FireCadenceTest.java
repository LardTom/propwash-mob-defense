package de.lardtom.mobdefense.dispenser.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireCadenceTest {
    private static final AmmoTable.Kind P = AmmoTable.Kind.PROJECTILE;

    private static AmmoTable.Kind[] slots() {
        AmmoTable.Kind[] out = new AmmoTable.Kind[SlotCycle.SLOTS];
        out[0] = P;
        return out;
    }

    private static FireGate.Rules rules(int cooldown) {
        return new FireGate.Rules(true, true, false, true, cooldown);
    }

    private static List<Long> hold(long start, long readyAt, int heldTicks, FireCadence.Setting setting, int cooldown) {
        FireGate.Rules rules = rules(cooldown);
        List<Long> shots = new ArrayList<>();
        long ready = readyAt;
        boolean attempted = FireGate.decide(start, ready, 0, slots(), rules).outcome() != FireGate.Outcome.COOLDOWN;
        if (attempted) {
            shots.add(start);
            ready = FireCadence.nextReady(start, setting, cooldown);
        }
        FireCadence.Hold hold = FireCadence.press(start, attempted, ready, setting, cooldown);
        for (long tick = start; tick < start + heldTicks; tick++) {
            if (!FireCadence.due(tick, hold)) {
                continue;
            }
            if (FireGate.decide(tick, ready, 0, slots(), rules).outcome() != FireGate.Outcome.COOLDOWN) {
                shots.add(tick);
                ready = FireCadence.nextReady(tick, setting, cooldown);
                hold = FireCadence.after(tick, hold, setting, cooldown);
            }
        }
        return shots;
    }

    private static List<Long> triggers(int every, int ticks, FireCadence.Setting setting, int cooldown) {
        FireGate.Rules rules = rules(cooldown);
        List<Long> shots = new ArrayList<>();
        long ready = 0;
        for (long tick = 0; tick < ticks; tick += every) {
            if (FireGate.decide(tick, ready, 0, slots(), rules).outcome() != FireGate.Outcome.COOLDOWN) {
                shots.add(tick);
                ready = FireCadence.nextReady(tick, setting, cooldown);
            }
        }
        return shots;
    }

    private static void allGaps(long expected, List<Long> shots, String label) {
        assertTrue(shots.size() > 2, label + ": zu wenige Schüsse " + shots);
        for (long gap : gaps(shots)) {
            assertEquals(expected, gap, label + ": Abstände " + gaps(shots));
        }
    }

    private static List<Long> gaps(List<Long> shots) {
        List<Long> out = new ArrayList<>();
        for (int i = 1; i < shots.size(); i++) {
            out.add(shots.get(i) - shots.get(i - 1));
        }
        return out;
    }

    @Test
    void defaultsGiveTheSecondShotMoreTime() {
        FireCadence.Setting defaults = FireCadence.Setting.DEFAULT;
        assertEquals(4, FireCadence.MIN_TICKS, "feste Mindest-Abschusszeit wie der Vanilla-Dispenser");
        assertEquals(40, FireCadence.MAX_TICKS);
        assertEquals(10, defaults.delayTicks());
        assertEquals(4, defaults.intervalTicks());
        assertTrue(defaults.delayTicks() > FireGate.Rules.DEFAULTS.cooldownTicks(), "Anlauf länger als die Abklingzeit");
    }

    @Test
    void aShortPressFiresExactlyOnce() {
        for (int held = 1; held <= 10; held++) {
            assertEquals(List.of(0L), hold(0, 0, held, FireCadence.Setting.DEFAULT, 4), held + " Ticks gehalten");
            assertEquals(1, FireCadence.shots(held, FireCadence.Setting.DEFAULT, 4));
        }
        assertEquals(List.of(0L, 10L), hold(0, 0, 11, FireCadence.Setting.DEFAULT, 4));
    }

    @Test
    void holdingWaitsForTheDelayThenFiresAtTheInterval() {
        List<Long> shots = hold(100, 0, 40, FireCadence.Setting.DEFAULT, 4);
        assertEquals(List.of(100L, 110L, 114L, 118L, 122L, 126L, 130L, 134L, 138L), shots);
        assertEquals(shots.size(), FireCadence.shots(40, FireCadence.Setting.DEFAULT, 4));
        List<Long> gaps = gaps(hold(0, 0, 100, new FireCadence.Setting(15, 10), 4));
        assertEquals(15L, gaps.getFirst());
        for (long gap : gaps.subList(1, gaps.size())) {
            assertEquals(10L, gap);
        }
    }

    @Test
    void worldRuleIsTheLowerLimit() {
        assertEquals(List.of(0L, 20L), hold(0, 0, 40, FireCadence.Setting.DEFAULT, 20));
        FireCadence.Setting setting = new FireCadence.Setting(12, 6);
        assertEquals(12, setting.effectiveDelay(4));
        assertEquals(6, setting.effectiveInterval(4));
        assertEquals(30, setting.effectiveDelay(30));
        assertEquals(30, setting.effectiveInterval(30));
        assertEquals(4, FireCadence.Setting.DEFAULT.effectiveInterval(0), "Welt-Regel 0 hebt die feste Mindestzeit nicht auf");
        assertEquals(gaps(hold(0, 0, 200, setting, 25)), List.of(25L, 25L, 25L, 25L, 25L, 25L, 25L));
    }

    @Test
    void settingsAreClampedToTheFixedRange() {
        assertEquals(new FireCadence.Setting(4, 40), new FireCadence.Setting(0, 1000));
        assertEquals(new FireCadence.Setting(4, 4), new FireCadence.Setting(-5, 3));
        assertEquals(new FireCadence.Setting(40, 4), new FireCadence.Setting(Integer.MAX_VALUE, Integer.MIN_VALUE));
    }

    @Test
    void aPressDuringTheCooldownFiresAsSoonAsReadyThenWaitsForTheDelay() {
        assertEquals(List.of(5L, 15L, 19L), hold(2, 5, 20, FireCadence.Setting.DEFAULT, 4));
        FireCadence.Hold hold = FireCadence.press(2, false, 5, FireCadence.Setting.DEFAULT, 4);
        assertEquals(5L, hold.nextAt());
        assertTrue(!hold.fired());
    }

    @Test
    void tappingAtTheIntervalFiresEveryTime() {
        assertEquals(10, triggers(4, 40, FireCadence.Setting.DEFAULT, 4).size(), "zehn Drücke im Abstand von 4 Ticks feuern zehnmal");
        assertEquals(10, triggers(6, 60, new FireCadence.Setting(10, 6), 1).size(), "zehn Drücke im eingestellten Abstand feuern zehnmal");
    }

    @Test
    void afterEveryAttemptTheLargestOfWorldRuleFixedMinimumAndSettingApplies() {
        assertEquals(104L, FireCadence.nextReady(100, FireCadence.Setting.DEFAULT, 1), "Welt-Regel 1 unterschreitet die feste Mindestzeit nicht");
        assertEquals(104L, FireCadence.nextReady(100, FireCadence.Setting.DEFAULT, 0));
        assertEquals(106L, FireCadence.nextReady(100, new FireCadence.Setting(10, 6), 1), "eingestellter Abstand gilt auch für einzelne Auslöser");
        assertEquals(108L, FireCadence.nextReady(100, new FireCadence.Setting(10, 6), 8), "Welt-Regel über der Einstellung");
        assertEquals(140L, FireCadence.nextReady(100, new FireCadence.Setting(4, 40), 4));
        assertEquals(200L, FireCadence.nextReady(100, new FireCadence.Setting(4, 4), 100));
    }

    @Test
    void timedTriggersNeverFireFasterThanAllowed() {
        for (int cooldown = 0; cooldown <= FireCadence.MIN_TICKS; cooldown++) {
            allGaps(FireCadence.MIN_TICKS, triggers(2, 80, FireCadence.Setting.DEFAULT, cooldown), "2er-Takt, Welt-Regel " + cooldown);
            allGaps(FireCadence.MIN_TICKS, triggers(1, 80, FireCadence.Setting.DEFAULT, cooldown), "jeden Tick, Welt-Regel " + cooldown);
        }
        allGaps(6, triggers(2, 80, new FireCadence.Setting(10, 6), 1), "2er-Takt, Abstand 6, Welt-Regel 1");
        allGaps(7, triggers(1, 80, new FireCadence.Setting(10, 7), 1), "jeden Tick, Abstand 7, Welt-Regel 1");
        allGaps(8, triggers(2, 80, new FireCadence.Setting(10, 6), 8), "2er-Takt, Abstand 6, Welt-Regel 8");
        allGaps(40, triggers(2, 200, new FireCadence.Setting(4, 40), 1), "2er-Takt, Abstand 40, Welt-Regel 1");
        for (int every = 1; every <= 20; every++) {
            for (int interval = FireCadence.MIN_TICKS; interval <= 12; interval++) {
                FireCadence.Setting setting = new FireCadence.Setting(10, interval);
                for (long gap : gaps(triggers(every, 200, setting, 1))) {
                    assertTrue(gap >= interval, "Takt " + every + ", Abstand " + interval + ": " + gap);
                }
            }
        }
    }

    @Test
    void holdingNeverFiresTheSecondShotSoonerThanTheInterval() {
        FireCadence.Setting setting = new FireCadence.Setting(4, 10);
        assertEquals(10, setting.effectiveDelay(1), "Anlauf unter dem Abstand gilt als Abstand");
        allGaps(10, hold(0, 0, 60, setting, 1), "Anlauf 4, Abstand 10, gehalten");
        assertEquals(1 + (60 - 1) / 10, FireCadence.shots(60, setting, 1));
        assertEquals(hold(0, 0, 60, setting, 1).size(), FireCadence.shots(60, setting, 1));
    }
}
