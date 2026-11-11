package de.lardtom.mobdefense.booster.core;

public final class BoostProfile {
    public static final double GRAVITY = 9.8;
    public static final double G_LOAD = 2.0;
    public static final double NEWTONS_PER_KG = GRAVITY * G_LOAD;
    public static final double MAX_NEWTONS = 60.0;
    public static final int MIN_FLIGHT = 1;
    public static final int MAX_FLIGHT = 3;
    public static final int TICKS_PER_SECOND = 20;
    public static final double TICK_SECONDS = 1.0 / TICKS_PER_SECOND;
    public static final int BASE_TICKS = 15;
    public static final int TICKS_PER_FLIGHT = 10;
    public static final double ROCKET_GRAMS = 60.0;
    public static final double BLAST_RADIUS = 5.0;
    public static final float BLAST_BASE = 5.0F;
    public static final float BLAST_PER_STAR = 2.0F;

    private BoostProfile() {
    }

    public static int flight(int flightDuration) {
        return Math.max(MIN_FLIGHT, Math.min(MAX_FLIGHT, flightDuration));
    }

    public static int burnTicks(int flightDuration) {
        return BASE_TICKS + TICKS_PER_FLIGHT * flight(flightDuration);
    }

    public static double burnSeconds(int flightDuration) {
        return burnTicks(flightDuration) * TICK_SECONDS;
    }

    public static double thrustNewtons(double massKg) {
        if (!(massKg > 0.0) || !Double.isFinite(massKg)) {
            return 0.0;
        }
        return Math.min(massKg * NEWTONS_PER_KG, MAX_NEWTONS);
    }

    public static boolean capped(double massKg) {
        return massKg > 0.0 && massKg * NEWTONS_PER_KG > MAX_NEWTONS;
    }

    public static double acceleration(double massKg) {
        double thrust = thrustNewtons(massKg);
        return thrust > 0.0 ? thrust / massKg : 0.0;
    }

    public static double deltaV(double massKg, int flightDuration) {
        return acceleration(massKg) * burnSeconds(flightDuration);
    }

    public static double impulse(double thrustNewtons, int ticks) {
        return Math.max(0.0, thrustNewtons) * Math.max(0, ticks) * TICK_SECONDS;
    }

    public static boolean burning(long endsAt, long gameTime) {
        return endsAt > gameTime;
    }

    public static double remainingSeconds(long endsAt, long gameTime) {
        return Math.max(0L, endsAt - gameTime) * TICK_SECONDS;
    }

    public static float blastDamage(int stars) {
        return stars > 0 ? BLAST_BASE + BLAST_PER_STAR * stars : 0.0F;
    }

    public static float blastDamageAt(int stars, double distance) {
        float base = blastDamage(stars);
        if (base <= 0.0F || !(distance >= 0.0) || distance > BLAST_RADIUS) {
            return 0.0F;
        }
        return base * (float) Math.sqrt((BLAST_RADIUS - distance) / BLAST_RADIUS);
    }

    public static double payloadGrams(int rockets) {
        return Math.max(0, rockets) * ROCKET_GRAMS;
    }
}
