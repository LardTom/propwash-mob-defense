package de.lardtom.mobdefense.dispenser.core;

public final class SlotCycle {
    public static final int SLOTS = 4;

    private SlotCycle() {
    }

    public static int firing(int selected, boolean[] filled) {
        check(filled);
        int start = Math.floorMod(selected, SLOTS);
        for (int step = 0; step < SLOTS; step++) {
            int candidate = (start + step) % SLOTS;
            if (filled[candidate]) {
                return candidate;
            }
        }
        return -1;
    }

    public static int next(int current, boolean[] filled) {
        check(filled);
        int start = Math.floorMod(current, SLOTS);
        for (int step = 1; step <= SLOTS; step++) {
            int candidate = (start + step) % SLOTS;
            if (filled[candidate]) {
                return candidate;
            }
        }
        return start;
    }

    private static void check(boolean[] filled) {
        if (filled == null || filled.length != SLOTS) {
            throw new IllegalArgumentException("Der Dispenser hat genau " + SLOTS + " Slots");
        }
    }
}
