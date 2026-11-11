package de.lardtom.mobdefense.mob.core;

public final class Levitation {
    public static final int TICKS = 200;
    public static final double TICK_SECONDS = 0.05;
    public static final double GRAVITY = 9.81;
    public static final double RISE = 1.0;
    public static final double RATE = 4.0;
    public static final double MAX_ACCELERATION = 3.0 * GRAVITY;

    private Levitation() {
    }

    public static long endsAt(long now) {
        return now + TICKS;
    }

    public static boolean lifting(long now, long endsAt) {
        return now < endsAt;
    }

    public static double acceleration(double verticalSpeed) {
        if (!Double.isFinite(verticalSpeed)) {
            return 0.0;
        }
        return Math.clamp(GRAVITY + RATE * (RISE - verticalSpeed), 0.0, MAX_ACCELERATION);
    }

    public static double lift(double massKg, double verticalSpeed) {
        if (!(massKg > 0.0) || !Double.isFinite(massKg)) {
            return 0.0;
        }
        return massKg * acceleration(verticalSpeed);
    }
}
