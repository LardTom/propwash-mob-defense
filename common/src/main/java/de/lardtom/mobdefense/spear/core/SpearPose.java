package de.lardtom.mobdefense.spear.core;

public final class SpearPose {
    public static final double ITEM_MM = 160.0;
    public static final double ITEM_PIXELS = 16.0;
    public static final double TIP_PIXELS = 7.5 * Math.sqrt(2.0);
    public static final float SCALE = 0.6F;
    public static final double AXIS_X_MM = 0.0;
    public static final double AXIS_Y_MM = -10.34;
    public static final double CENTER_Z_MM = -36.0;
    public static final float YAW = -90.0F;
    public static final float PITCH = 0.0F;
    public static final float ROLL = 45.0F;
    public static final double HALF_LENGTH_MM = TIP_PIXELS * ITEM_MM / ITEM_PIXELS * SCALE;
    public static final double TIP_FORWARD_MM = -CENTER_Z_MM + HALF_LENGTH_MM;
    public static final double TAIL_FORWARD_MM = -CENTER_Z_MM - HALF_LENGTH_MM;
    public static final double TIP_UP_MM = AXIS_Y_MM;

    private SpearPose() {
    }

    public static double tipForwardMetres() {
        return TIP_FORWARD_MM / 1000.0;
    }

    public static double tipUpMetres() {
        return TIP_UP_MM / 1000.0;
    }

    public static double lengthMm() {
        return 2.0 * HALF_LENGTH_MM;
    }
}
