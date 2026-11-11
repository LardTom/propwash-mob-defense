package de.lardtom.mobdefense.mob.core;

public final class KindWindows {
    public static final double CHASE_REACH_ABOVE_HEAD = 2.5;
    public static final double ARCHER_MIN = 4.0;
    public static final double ARCHER_MAX = 24.0;
    public static final double BLAZE_MAX = 16.0;
    public static final double GHAST_MAX = 64.0;
    public static final double CREEPER_MAX_HEIGHT = 4.0;
    public static final double CREEPER_IGNITE = 3.0;
    public static final double CREEPER_DEFUSE = 7.0;
    public static final double PHANTOM_MIN_HEIGHT = 10.0;
    public static final double RAVAGER_ROAR = 4.0;
    public static final double LANDED_HEIGHT = 0.25;

    private KindWindows() {
    }

    public static boolean chaseStrike(double droneBottom, double mobTop, boolean landed) {
        return landed || droneBottom - mobTop <= CHASE_REACH_ABOVE_HEAD;
    }

    public static boolean archer(double distance) {
        return distance >= ARCHER_MIN && distance <= ARCHER_MAX;
    }

    public static boolean creeperLow(double heightAboveGround) {
        return heightAboveGround <= CREEPER_MAX_HEIGHT;
    }

    public static int creeperSwell(double distance, int currentDirection, boolean sees) {
        if (!sees) {
            return -1;
        }
        if (currentDirection > 0) {
            return distance > CREEPER_DEFUSE ? -1 : 1;
        }
        return distance <= CREEPER_IGNITE ? 1 : -1;
    }

    public static boolean phantom(double heightAboveGround, boolean nightInOpen) {
        return heightAboveGround > PHANTOM_MIN_HEIGHT || nightInOpen;
    }

    public static boolean landed(boolean onGround, double heightAboveGround) {
        return onGround || heightAboveGround < LANDED_HEIGHT;
    }
}
