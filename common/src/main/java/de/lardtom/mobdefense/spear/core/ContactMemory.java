package de.lardtom.mobdefense.spear.core;

import java.util.HashMap;
import java.util.Map;

public final class ContactMemory {
    public static final int KEEP_TICKS = 200;
    public static final int PRUNE_SIZE = 32;

    private final Map<Integer, Long> last = new HashMap<>();

    public boolean recent(int target, long now, int cooldownTicks) {
        Long tick = this.last.get(target);
        return tick != null && now - tick < cooldownTicks;
    }

    public void remember(int target, long now) {
        if (this.last.size() >= PRUNE_SIZE) {
            this.last.values().removeIf(tick -> now - tick >= KEEP_TICKS || tick > now);
        }
        this.last.put(target, now);
    }

    public int size() {
        return this.last.size();
    }

    public void clear() {
        this.last.clear();
    }
}
