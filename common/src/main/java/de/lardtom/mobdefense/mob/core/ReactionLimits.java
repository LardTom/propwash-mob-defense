package de.lardtom.mobdefense.mob.core;

public final class ReactionLimits {
    public static final int SEARCH_PERIOD = 10;
    public static final int MOBS_PER_DRONE = 12;
    public static final int EVALUATIONS_PER_TICK = 64;
    public static final int HOLD_TICKS = 25;
    public static final int MEMORY_TICKS = 100;
    public static final double SWITCH_FACTOR = 0.5;

    private ReactionLimits() {
    }

    public static boolean due(long tick, int id, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("Takt muss größer als 0 sein: " + period);
        }
        return Math.floorMod(tick + id, (long) period) == 0L;
    }

    public static boolean prefersDrone(double droneDistance, double targetDistance) {
        if (!Double.isFinite(droneDistance) || droneDistance < 0.0) {
            return false;
        }
        if (!Double.isFinite(targetDistance)) {
            return true;
        }
        return droneDistance < SWITCH_FACTOR * targetDistance;
    }

    public static double range(int ruleRange, double kindMax) {
        double rule = Math.max(0, ruleRange);
        return Double.isNaN(kindMax) ? rule : Math.min(rule, Math.max(0.0, kindMax));
    }

    public static boolean remembers(long now, long lastSeen) {
        return now - lastSeen <= MEMORY_TICKS;
    }
}
