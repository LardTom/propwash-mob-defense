package de.lardtom.mobdefense.net.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class RateGate<K> {
    private static final int PRUNE_AT = 64;

    private final int limit;
    private final long window;
    private final Map<K, ArrayDeque<Long>> recent = new HashMap<>();

    public RateGate(int limit, long window) {
        if (limit < 1 || window < 1) {
            throw new IllegalArgumentException("Grenze und Fenster müssen mindestens 1 sein");
        }
        this.limit = limit;
        this.window = window;
    }

    public int limit() {
        return this.limit;
    }

    public long window() {
        return this.window;
    }

    public synchronized boolean allow(K key, long now) {
        return this.allow(key, now, 0);
    }

    public synchronized boolean allow(K key, long now, int reserve) {
        if (reserve < 0 || reserve >= this.limit) {
            throw new IllegalArgumentException("Vorbehalt muss zwischen 0 und Grenze - 1 liegen: " + reserve);
        }
        if (this.recent.size() > PRUNE_AT) {
            this.prune(now);
        }
        ArrayDeque<Long> times = this.recent.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        this.drop(times, now);
        if (times.size() >= this.limit - reserve) {
            return false;
        }
        times.addLast(now);
        return true;
    }

    public synchronized int size() {
        return this.recent.size();
    }

    public synchronized void clear() {
        this.recent.clear();
    }

    private void prune(long now) {
        Iterator<ArrayDeque<Long>> it = this.recent.values().iterator();
        while (it.hasNext()) {
            ArrayDeque<Long> times = it.next();
            this.drop(times, now);
            if (times.isEmpty()) {
                it.remove();
            }
        }
    }

    private void drop(ArrayDeque<Long> times, long now) {
        if (!times.isEmpty() && times.peekLast() > now) {
            times.clear();
            return;
        }
        long oldest = now - this.window;
        while (!times.isEmpty() && times.peekFirst() <= oldest) {
            times.removeFirst();
        }
    }
}
