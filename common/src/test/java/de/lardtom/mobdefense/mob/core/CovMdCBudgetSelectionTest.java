package de.lardtom.mobdefense.mob.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.DoublePredicate;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdCBudgetSelectionTest {
    private static final class Search {
        private final int drone;
        private final Selection<Double> selection;
        private final DoublePredicate accepts;
        private final List<Double> chosen = new ArrayList<>();
        private int evaluations;

        private Search(int drone, List<Double> distances, DoublePredicate accepts) {
            this.drone = drone;
            this.selection = Selection.nearest(distances, Double::doubleValue);
            this.accepts = accepts;
        }

        private boolean accept(double distance) {
            this.evaluations++;
            if (this.accepts.test(distance)) {
                this.chosen.add(distance);
                return true;
            }
            return false;
        }

        private Optional<Predicate<Double>> acceptor(long tick) {
            return Optional.of(this::accept);
        }

        private boolean run(Budget budget) {
            return this.selection.run(budget, this::accept);
        }
    }

    private static List<Double> ring(int count, long seed) {
        List<Double> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(3.0 + i * 0.5);
        }
        Collections.shuffle(out, new Random(seed));
        return out;
    }

    @Test
    void thirtyMobsOnlyTheTwelveNearestReact() {
        Search search = new Search(1, ring(30, 7L), distance -> true);
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertTrue(search.run(budget));
        assertEquals(12, search.chosen.size());
        assertEquals(12, budget.used(), "eine Auswertung je zugeordnetem Mob, die übrigen 18 werden nicht angesehen");
        List<Double> expected = new ArrayList<>(ring(30, 7L));
        Collections.sort(expected);
        assertEquals(expected.subList(0, 12), search.chosen);
    }

    @Test
    void mobsWithoutSightDoNotTakeASlot() {
        Search search = new Search(1, ring(30, 11L), distance -> Math.round(distance * 2.0) % 3 != 0);
        assertTrue(search.run(new Budget(ReactionLimits.EVALUATIONS_PER_TICK)));
        assertEquals(12, search.chosen.size());
        assertTrue(search.evaluations > 12, "abgelehnte Mobs kosten eine Auswertung, aber keinen Platz");
        for (int i = 1; i < search.chosen.size(); i++) {
            assertTrue(search.chosen.get(i - 1) < search.chosen.get(i), "nächste zuerst");
        }
    }

    @Test
    void fewerMobsThanSlots() {
        Search search = new Search(1, ring(5, 3L), distance -> true);
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertTrue(search.run(budget));
        assertEquals(5, search.chosen.size());
        assertEquals(59, budget.left());
    }

    @Test
    void searchesThatExceedTheBudgetResumeNextTick() {
        List<Search> due = new ArrayList<>();
        for (int drone = 0; drone < 6; drone++) {
            due.add(new Search(drone, ring(30, drone), distance -> true));
        }
        Selection.Pending<Integer, Double> pending = new Selection.Pending<>();
        Budget first = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        for (Search search : due) {
            pending.start(search.drone, search.selection, search::acceptor, first, 0L);
        }
        assertEquals(64, first.used());
        assertEquals(1, pending.size(), "5 × 12 + 4 Auswertungen im ersten Tick");
        assertTrue(pending.waiting(5));
        assertEquals(4, due.get(5).chosen.size());
        Budget second = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        assertEquals(1, pending.resume(second, 1L));
        assertEquals(8, second.used());
        assertEquals(0, pending.size());
        due.forEach(search -> assertEquals(12, search.chosen.size()));
    }

    @Test
    void staggeredSearchesNeverExceedSixtyFourPerTick() {
        int drones = 100;
        Map<Integer, Integer> searched = new HashMap<>();
        Selection.Pending<Integer, Double> pending = new Selection.Pending<>();
        int maxPerTick = 0;
        int maxPending = 0;
        for (long tick = 0; tick < 400; tick++) {
            Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
            pending.resume(budget, tick);
            for (int drone = 0; drone < drones; drone++) {
                int id = drone;
                if (!ReactionLimits.due(tick, id, ReactionLimits.SEARCH_PERIOD) || pending.waiting(id)) {
                    continue;
                }
                Search search = new Search(id, ring(20, id), distance -> true);
                searched.merge(id, 1, Integer::sum);
                pending.start(id, search.selection, search::acceptor, budget, tick);
            }
            maxPerTick = Math.max(maxPerTick, budget.used());
            maxPending = Math.max(maxPending, pending.size());
        }
        assertTrue(maxPerTick <= ReactionLimits.EVALUATIONS_PER_TICK, "höchstens 64 Auswertungen je Tick: " + maxPerTick);
        assertEquals(drones, searched.size(), "jede Drohne wird gesucht");
        assertTrue(maxPending <= drones, "höchstens eine offene Suche je Drohne");
        assertFalse(searched.values().stream().anyMatch(count -> count == 0));
    }
}
