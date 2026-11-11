package de.lardtom.mobdefense.mob.core;

public final class Gaze {
    public static final double ENDERMAN_CONE = 0.025;
    public static final double CREAKING_CONE = 0.5;

    private Gaze() {
    }

    public static double threshold(double cone, double distance, boolean scaleByDistance) {
        if (!(cone >= 0.0)) {
            throw new IllegalArgumentException("Kegel muss mindestens 0 sein: " + cone);
        }
        double divisor = scaleByDistance ? Math.max(distance, 1.0E-6) : 1.0;
        return 1.0 - cone / divisor;
    }

    public static double fovThreshold(double verticalFovDegrees) {
        if (!(verticalFovDegrees > 0.0 && verticalFovDegrees < 180.0)) {
            throw new IllegalArgumentException("Sichtfeld ungültig: " + verticalFovDegrees);
        }
        return Math.cos(Math.toRadians(verticalFovDegrees / 2.0));
    }

    public static boolean inCone(double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ, double targetX, double targetY,
                                 double targetZ, double cone, boolean scaleByDistance, double verticalFovDegrees) {
        double dx = targetX - eyeX;
        double dy = targetY - eyeY;
        double dz = targetZ - eyeZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double look = Math.sqrt(lookX * lookX + lookY * lookY + lookZ * lookZ);
        if (distance < 1.0E-6 || look < 1.0E-9 || !Double.isFinite(distance) || !Double.isFinite(look)) {
            return false;
        }
        double dot = (lookX * dx + lookY * dy + lookZ * dz) / (look * distance);
        double limit = Math.max(threshold(cone, distance, scaleByDistance), fovThreshold(verticalFovDegrees));
        return dot > limit;
    }
}
