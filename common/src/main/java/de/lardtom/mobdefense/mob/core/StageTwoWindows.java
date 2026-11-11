package de.lardtom.mobdefense.mob.core;

public final class StageTwoWindows {
    public static final double STARE_LOW = 4.0;
    public static final double STARE_TELEPORT_MIN = 3.0;
    public static final int STARE_ANGER_MIN = 400;
    public static final int STARE_ANGER_SPREAD = 380;
    public static final double BEAM_MAX = 16.0;
    public static final double BEAM_MIN = 3.0;
    public static final double CASTER_MAX = 12.0;
    public static final double CASTER_FANGS_HEIGHT = 2.0;
    public static final double BOSS_MAX = 32.0;
    public static final double GROUND_HEIGHT = 1.0;
    public static final double WIND_MAX = 16.0;
    public static final double BULLET_MAX = 16.0;
    public static final double POTION_MAX = 10.0;
    public static final double POTION_RADIUS = 2.0;
    public static final double POTION_HARM = 6.0;
    public static final double POTION_FALLOFF = 4.0;
    public static final double TRIDENT_MAX = 16.0;
    public static final double JUMPER_HEIGHT = 3.0;
    public static final double GRAVITY = 0.08;
    public static final double DRAG = 0.98;
    public static final double LEAP_MIN = 0.42;
    public static final double LEAP_MAX = 1.1;
    public static final double LEAP_MARGIN = 0.4;

    private StageTwoWindows() {
    }

    public static boolean stareLow(double heightAboveGround) {
        return heightAboveGround <= STARE_LOW;
    }

    public static boolean stareTeleport(double horizontalDistance) {
        return horizontalDistance > STARE_TELEPORT_MIN;
    }

    public static boolean beam(double distance, boolean elder) {
        return elder || distance > BEAM_MIN;
    }

    public static boolean fangs(double heightAboveGround) {
        return heightAboveGround <= CASTER_FANGS_HEIGHT;
    }

    public static boolean ground(double heightAboveGround, boolean landed) {
        return landed || heightAboveGround <= GROUND_HEIGHT;
    }

    public static boolean jumper(double heightAboveGround, boolean atWall, boolean climber) {
        return heightAboveGround <= JUMPER_HEIGHT || climber && atWall;
    }

    public static double potionDamage(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0 || distance > POTION_RADIUS) {
            return 0.0;
        }
        return POTION_HARM * (1.0 - distance / POTION_FALLOFF);
    }

    public static double segmentDistance(double ax, double ay, double az, double bx, double by, double bz, double px, double py, double pz) {
        double sx = bx - ax;
        double sy = by - ay;
        double sz = bz - az;
        double length = sx * sx + sy * sy + sz * sz;
        double t = length < 1.0E-12 ? 0.0 : ((px - ax) * sx + (py - ay) * sy + (pz - az) * sz) / length;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = ax + sx * t - px;
        double cy = ay + sy * t - py;
        double cz = az + sz * t - pz;
        return Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    public static double leapSpeed(double rise) {
        if (!Double.isFinite(rise) || rise <= 0.0) {
            return LEAP_MIN;
        }
        double speed = Math.sqrt(2.0 * GRAVITY * (rise + LEAP_MARGIN)) / DRAG;
        return Math.max(LEAP_MIN, Math.min(LEAP_MAX, speed));
    }

    public static int angerTicks(double random) {
        double r = Math.max(0.0, Math.min(1.0, random));
        return STARE_ANGER_MIN + (int) Math.floor(r * STARE_ANGER_SPREAD);
    }
}
