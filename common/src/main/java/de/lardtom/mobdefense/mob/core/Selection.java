package de.lardtom.mobdefense.mob.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public final class Selection<T> {
    private final List<T> order;
    private final int limit;
    private int next;
    private int accepted;

    public Selection(Collection<? extends T> candidates, ToDoubleFunction<? super T> distance, int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Grenze darf nicht negativ sein: " + limit);
        }
        List<Ranked<T>> ranked = new ArrayList<>(candidates.size());
        for (T candidate : candidates) {
            ranked.add(new Ranked<>(candidate, distance.applyAsDouble(candidate)));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::distance));
        List<T> sorted = new ArrayList<>(ranked.size());
        for (Ranked<T> entry : ranked) {
            sorted.add(entry.item());
        }
        this.order = sorted;
        this.limit = limit;
    }

    public static <T> Selection<T> nearest(Collection<? extends T> candidates, ToDoubleFunction<? super T> distance) {
        return new Selection<>(candidates, distance, ReactionLimits.MOBS_PER_DRONE);
    }

    public boolean run(Budget budget, Predicate<? super T> accept) {
        while (!this.finished()) {
            if (!budget.take()) {
                return false;
            }
            if (accept.test(this.order.get(this.next++))) {
                this.accepted++;
            }
        }
        return true;
    }

    public boolean finished() {
        return this.next >= this.order.size() || this.accepted >= this.limit;
    }

    public int accepted() {
        return this.accepted;
    }

    public int evaluated() {
        return this.next;
    }

    public int size() {
        return this.order.size();
    }

    public int limit() {
        return this.limit;
    }

    private record Ranked<T>(T item, double distance) {
    }

    public static final class Pending<K, T> {
        private final Deque<Waiting<K, T>> waiting = new ArrayDeque<>();

        public boolean start(K key, Selection<T> selection, LongFunction<Optional<Predicate<T>>> accept, Budget budget, long tick) {
            if (this.waiting(key)) {
                throw new IllegalStateException("Suche für " + key + " wartet schon");
            }
            Waiting<K, T> search = new Waiting<>(key, selection, accept);
            if (search.run(budget, tick)) {
                return true;
            }
            this.waiting.addLast(search);
            return false;
        }

        public int resume(Budget budget, long tick) {
            int ended = 0;
            Iterator<Waiting<K, T>> searches = this.waiting.iterator();
            while (searches.hasNext() && !budget.exhausted()) {
                if (searches.next().run(budget, tick)) {
                    searches.remove();
                    ended++;
                }
            }
            return ended;
        }

        public boolean waiting(K key) {
            for (Waiting<K, T> search : this.waiting) {
                if (Objects.equals(search.key(), key)) {
                    return true;
                }
            }
            return false;
        }

        public int size() {
            return this.waiting.size();
        }

        public void clear() {
            this.waiting.clear();
        }

        private record Waiting<K, T>(K key, Selection<T> selection, LongFunction<Optional<Predicate<T>>> accept) {
            private boolean run(Budget budget, long tick) {
                Optional<Predicate<T>> test = this.accept.apply(tick);
                return test.isEmpty() || this.selection.run(budget, test.get());
            }
        }
    }
}
