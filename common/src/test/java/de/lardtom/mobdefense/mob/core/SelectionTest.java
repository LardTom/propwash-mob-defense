package de.lardtom.mobdefense.mob.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectionTest {
    private record Mob(int drone, int rank, double distance) {
    }

    private static List<Mob> ring(int drone, int count, long seed) {
        List<Mob> out = new ArrayList<>();
        for (int rank = 0; rank < count; rank++) {
            out.add(new Mob(drone, rank, 3.0 + rank * 0.5));
        }
        Collections.shuffle(out, new Random(seed));
        return out;
    }

    private static List<Integer> ranks(List<Mob> mobs) {
        return mobs.stream().map(Mob::rank).toList();
    }

    private static Predicate<Mob> recording(List<Mob> seen, Predicate<Mob> accepts) {
        return mob -> {
            seen.add(mob);
            return accepts.test(mob);
        };
    }

    private static Optional<Predicate<Mob>> always(Predicate<Mob> accept) {
        return Optional.of(accept);
    }

    @Test
    void thirtyMobsOnlyTheTwelveNearestAreAccepted() {
        List<Mob> seen = new ArrayList<>();
        List<Mob> chosen = new ArrayList<>();
        Selection<Mob> selection = Selection.nearest(ring(1, 30, 7L), Mob::distance);
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertTrue(selection.run(budget, recording(seen, mob -> chosen.add(mob))));
        assertEquals(30, selection.size());
        assertEquals(ReactionLimits.MOBS_PER_DRONE, selection.limit());
        assertEquals(12, selection.accepted());
        assertEquals(12, selection.evaluated());
        assertEquals(12, budget.used(), "eine Auswertung je zugeordnetem Mob, die 18 entfernten werden nicht angesehen");
        assertTrue(selection.finished());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), ranks(chosen), "genau die 12 nächsten, nächste zuerst");
        assertEquals(ranks(chosen), ranks(seen));
    }

    @Test
    void rejectedMobsCostAnEvaluationButNoSlot() {
        List<Mob> seen = new ArrayList<>();
        Selection<Mob> selection = Selection.nearest(ring(1, 30, 11L), Mob::distance);
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertTrue(selection.run(budget, recording(seen, mob -> mob.rank() % 3 != 0)));
        assertEquals(12, selection.accepted());
        assertEquals(18, selection.evaluated(), "sechs ohne Sichtlinie kosten je eine Auswertung");
        assertEquals(18, budget.used());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17), ranks(seen));
    }

    @Test
    void fewerMobsThanSlots() {
        Selection<Mob> selection = Selection.nearest(ring(1, 5, 3L), Mob::distance);
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertTrue(selection.run(budget, mob -> true));
        assertEquals(5, selection.accepted());
        assertEquals(59, budget.left());
        assertTrue(selection.finished());
    }

    @Test
    void noCandidatesNeedNoBudget() {
        Selection<Mob> selection = Selection.nearest(List.of(), Mob::distance);
        Budget budget = new Budget(0);
        assertTrue(selection.finished());
        assertTrue(selection.run(budget, mob -> true));
        assertEquals(0, budget.used());
    }

    @Test
    void exhaustedBudgetResumesWhereItStopped() {
        List<Mob> seen = new ArrayList<>();
        Selection<Mob> selection = Selection.nearest(ring(1, 30, 5L), Mob::distance);
        Predicate<Mob> accept = recording(seen, mob -> true);
        Budget first = new Budget(5);
        assertFalse(selection.run(first, accept), "Budget leer, Suche offen");
        assertFalse(selection.finished());
        assertEquals(5, selection.accepted());
        assertEquals(5, selection.evaluated());
        assertFalse(selection.run(new Budget(0), accept), "ohne Budget keine Auswertung");
        assertEquals(5, selection.evaluated());
        Budget second = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertTrue(selection.run(second, accept));
        assertEquals(7, second.used(), "weiter beim sechsten Mob, keiner doppelt");
        assertEquals(12, selection.accepted());
        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), ranks(seen));
        assertTrue(selection.run(new Budget(0), accept), "fertige Suche braucht kein Budget");
    }

    @Test
    void equalDistancesKeepTheOrderOfTheCandidates() {
        List<Mob> mobs = List.of(new Mob(1, 2, 4.0), new Mob(1, 0, 4.0), new Mob(1, 3, 2.0), new Mob(1, 1, 4.0));
        List<Mob> seen = new ArrayList<>();
        assertTrue(Selection.nearest(mobs, Mob::distance).run(new Budget(64), recording(seen, mob -> true)));
        assertEquals(List.of(3, 2, 0, 1), ranks(seen));
    }

    @Test
    void limitAndBudgetGuards() {
        assertThrows(IllegalArgumentException.class, () -> new Selection<>(ring(1, 3, 1L), Mob::distance, -1));
        Selection<Mob> none = new Selection<>(ring(1, 3, 1L), Mob::distance, 0);
        assertTrue(none.finished());
        assertTrue(none.run(new Budget(0), mob -> true));
        assertEquals(0, none.evaluated());
    }

    @Test
    void searchesThatExceedTheBudgetResumeFirstInTheNextTick() {
        Selection.Pending<Integer, Mob> pending = new Selection.Pending<>();
        List<Selection<Mob>> searches = new ArrayList<>();
        List<Mob> seen = new ArrayList<>();
        Budget first = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        for (int drone = 0; drone < 6; drone++) {
            Selection<Mob> selection = Selection.nearest(ring(drone, 30, drone), Mob::distance);
            searches.add(selection);
            boolean done = pending.start(drone, selection, tick -> always(recording(seen, mob -> true)), first, 0L);
            assertEquals(drone < 5, done, "Drohne " + drone);
        }
        assertEquals(64, first.used(), "5 × 12 + 4 Auswertungen im ersten Tick");
        assertEquals(1, pending.size());
        assertTrue(pending.waiting(5));
        assertFalse(pending.waiting(4));
        assertEquals(4, searches.get(5).accepted());
        seen.clear();
        Budget second = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertEquals(1, pending.resume(second, 1L));
        Selection<Mob> fresh = Selection.nearest(ring(9, 30, 9L), Mob::distance);
        assertTrue(pending.start(9, fresh, tick -> always(recording(seen, mob -> true)), second, 1L));
        assertEquals(8 + 12, second.used());
        assertEquals(0, pending.size());
        assertEquals(List.of(5, 5, 5, 5, 5, 5, 5, 5), seen.subList(0, 8).stream().map(Mob::drone).toList(), "die offene Suche zuerst");
        searches.forEach(search -> assertEquals(12, search.accepted()));
    }

    @Test
    void acceptorSeesTheTickOfEachRun() {
        Selection.Pending<Integer, Mob> pending = new Selection.Pending<>();
        Map<Long, Integer> perTick = new HashMap<>();
        Selection<Mob> selection = Selection.nearest(ring(1, 30, 2L), Mob::distance);
        for (long tick = 10L; tick < 13L; tick++) {
            Budget budget = new Budget(5);
            if (tick == 10L) {
                assertFalse(pending.start(1, selection, now -> always(mob -> {
                    perTick.merge(now, 1, Integer::sum);
                    return true;
                }), budget, tick));
            } else {
                pending.resume(budget, tick);
            }
        }
        assertEquals(Map.of(10L, 5, 11L, 5, 12L, 2), perTick);
        assertEquals(0, pending.size());
    }

    @Test
    void searchWithoutAcceptorIsDroppedWithoutBudget() {
        Selection.Pending<Integer, Mob> pending = new Selection.Pending<>();
        Selection<Mob> selection = Selection.nearest(ring(1, 30, 4L), Mob::distance);
        assertFalse(pending.start(1, selection, tick -> tick == 0L ? always(mob -> true) : Optional.empty(), new Budget(3), 0L));
        assertTrue(pending.waiting(1));
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertEquals(1, pending.resume(budget, 1L), "Drohne weg oder geparkt: Suche endet");
        assertEquals(0, budget.used());
        assertEquals(0, pending.size());
        assertEquals(3, selection.evaluated());
        assertTrue(pending.start(2, Selection.nearest(ring(2, 30, 4L), Mob::distance), tick -> Optional.empty(), new Budget(0), 2L));
        assertEquals(0, pending.size());
    }

    @Test
    void resumeStopsWhenTheBudgetIsEmptyAndKeepsTheOrder() {
        Selection.Pending<Integer, Mob> pending = new Selection.Pending<>();
        List<Integer> order = new ArrayList<>();
        Budget first = new Budget(0);
        for (int drone = 0; drone < 3; drone++) {
            int id = drone;
            assertFalse(pending.start(drone, Selection.nearest(ring(drone, 30, drone), Mob::distance), tick -> always(mob -> order.add(id)), first, 0L));
        }
        assertEquals(3, pending.size());
        Budget second = new Budget(20);
        assertEquals(1, pending.resume(second, 1L));
        assertTrue(second.exhausted());
        assertEquals(2, pending.size());
        assertFalse(pending.waiting(0));
        assertTrue(pending.waiting(1));
        assertTrue(pending.waiting(2));
        assertEquals(0, pending.resume(new Budget(0), 2L));
        assertEquals(2, pending.resume(new Budget(64), 3L));
        assertEquals(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2), order);
    }

    @Test
    void oneWaitingSearchPerDrone() {
        Selection.Pending<Integer, Mob> pending = new Selection.Pending<>();
        assertFalse(pending.start(1, Selection.nearest(ring(1, 30, 1L), Mob::distance), tick -> always(mob -> true), new Budget(0), 0L));
        assertThrows(IllegalStateException.class,
                () -> pending.start(1, Selection.nearest(ring(1, 30, 1L), Mob::distance), tick -> always(mob -> true), new Budget(64), 0L));
        assertEquals(1, pending.size());
        pending.clear();
        assertEquals(0, pending.size());
        assertFalse(pending.waiting(1));
    }

    @Test
    void staggeredDronesNeverExceedSixtyFourPerTick() {
        int drones = 100;
        Selection.Pending<Integer, Mob> pending = new Selection.Pending<>();
        Map<Integer, Integer> searched = new HashMap<>();
        int started = 0;
        int ended = 0;
        int maxPerTick = 0;
        int maxPending = 0;
        for (long tick = 0; tick < 400; tick++) {
            Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
            ended += pending.resume(budget, tick);
            for (int drone = 0; drone < drones; drone++) {
                if (!ReactionLimits.due(tick, drone, ReactionLimits.SEARCH_PERIOD) || pending.waiting(drone)) {
                    continue;
                }
                int id = drone;
                searched.merge(drone, 1, Integer::sum);
                started++;
                if (pending.start(drone, Selection.nearest(ring(drone, 20, drone), Mob::distance), now -> always(mob -> {
                    assertTrue(mob.drone() == id && mob.rank() < ReactionLimits.MOBS_PER_DRONE, "nur die 12 nächsten der eigenen Drohne");
                    return true;
                }), budget, tick)) {
                    ended++;
                }
            }
            maxPerTick = Math.max(maxPerTick, budget.used());
            maxPending = Math.max(maxPending, pending.size());
            assertTrue(pending.size() == 0 || budget.exhausted(), "offene Suchen nur bei leerem Budget (Tick " + tick + ")");
        }
        assertEquals(ReactionLimits.EVALUATIONS_PER_TICK, maxPerTick, "höchstens 64 Auswertungen je Tick, und das Budget wird genutzt");
        assertEquals(drones, searched.size(), "jede Drohne wird gesucht");
        assertTrue(maxPending <= drones, "höchstens eine offene Suche je Drohne");
        assertEquals(started, ended + pending.size(), "jede Suche endet oder wartet");
        int least = searched.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int expected = 400 * ReactionLimits.EVALUATIONS_PER_TICK / ReactionLimits.MOBS_PER_DRONE / drones;
        assertTrue(least >= expected / 2, "keine Drohne verhungert: mindestens " + least + " Suchen, Mittel etwa " + expected);
    }
}
