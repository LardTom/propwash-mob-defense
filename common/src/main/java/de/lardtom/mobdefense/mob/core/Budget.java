package de.lardtom.mobdefense.mob.core;

public final class Budget {
    private final int limit;
    private int used;

    public Budget(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Budget darf nicht negativ sein: " + limit);
        }
        this.limit = limit;
    }

    public boolean take() {
        if (this.used >= this.limit) {
            return false;
        }
        this.used++;
        return true;
    }

    public int used() {
        return this.used;
    }

    public int left() {
        return this.limit - this.used;
    }

    public boolean exhausted() {
        return this.used >= this.limit;
    }
}
