package de.lardtom.mobdefense.dev.booster;

public final class CovMdBElytraReference {
    public static final double TICKS_PER_SECOND = 20.0;
    public static final double GRAVITY = 0.08;
    public static final double ROCKET_POWER = 1.5;
    public static final double ROCKET_ADD = 0.1;
    public static final double ROCKET_BLEND = 0.5;
    public static final double LIFT = 0.75;
    public static final double DRAG_HORIZONTAL = 0.99;
    public static final double DRAG_VERTICAL = 0.98;
    public static final int RANDOM_A = 6;
    public static final int RANDOM_B = 7;

    private CovMdBElytraReference() {
    }

    public record Result(double startMetresPerSecond, double gainMetresPerSecond, double boostSeconds, double minGain, double maxGain) {
    }

    public static int lifetime(int flightDuration, int a, int b) {
        return 10 * (flightDuration + 1) + a + b;
    }

    public static double peakGain(double startMetresPerSecond, int boostTicks) {
        double vx = startMetresPerSecond / TICKS_PER_SECOND;
        double vy = 0.0;
        double start = vx;
        double peak = vx;
        for (int tick = 0; tick < boostTicks + 20; tick++) {
            if (tick < boostTicks) {
                vx += ROCKET_ADD + (ROCKET_POWER - vx) * ROCKET_BLEND;
                vy += (0.0 - vy) * ROCKET_BLEND;
            }
            double horizontal = Math.abs(vx);
            vy += GRAVITY * (-1.0 + LIFT);
            if (vy < 0.0) {
                double convert = vy * -0.1;
                vx += convert;
                vy += convert;
            }
            vx += (horizontal - vx) * 0.1;
            vx *= DRAG_HORIZONTAL;
            vy *= DRAG_VERTICAL;
            peak = Math.max(peak, vx);
        }
        return (peak - start) * TICKS_PER_SECOND;
    }

    public static Result rocket(int flightDuration, double startMetresPerSecond) {
        double sumGain = 0.0;
        double sumTicks = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (int a = 0; a < RANDOM_A; a++) {
            for (int b = 0; b < RANDOM_B; b++) {
                int boost = lifetime(flightDuration, a, b) + 1;
                double gain = peakGain(startMetresPerSecond, boost);
                sumGain += gain;
                sumTicks += boost;
                min = Math.min(min, gain);
                max = Math.max(max, gain);
                count++;
            }
        }
        return new Result(startMetresPerSecond, sumGain / count, sumTicks / count / TICKS_PER_SECOND, min, max);
    }
}
