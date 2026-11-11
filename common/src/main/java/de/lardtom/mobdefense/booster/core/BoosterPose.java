package de.lardtom.mobdefense.booster.core;

public final class BoosterPose {
    public static final double ITEM_MM = 160.0;
    public static final double ITEM_PIXELS = 16.0;
    public static final float SCALE = 0.43F;
    public static final double MM_PER_PIXEL = ITEM_MM / ITEM_PIXELS * SCALE;
    public static final double CRADLE_AXIS_UP_MM = 15.9;
    public static final double NOSE_STOP_MM = -29.0;
    public static final double IGNITER_MM = 27.2;
    public static final double SPRITE_TIP_PIXELS = 6.0;
    public static final double SPRITE_TAIL_PIXELS = 7.0;
    public static final double SPRITE_AXIS_PIXELS = 0.5;
    public static final double CENTER_Z_MM = NOSE_STOP_MM + SPRITE_TIP_PIXELS * MM_PER_PIXEL;
    public static final double CENTER_UP_MM = CRADLE_AXIS_UP_MM - SPRITE_AXIS_PIXELS * MM_PER_PIXEL;
    public static final double CENTER_X_MM = 0.0;
    public static final float YAW = 90.0F;
    public static final float PITCH = 0.0F;
    public static final float ROLL = -90.0F;
    public static final double TIP_Z_MM = CENTER_Z_MM - SPRITE_TIP_PIXELS * MM_PER_PIXEL;
    public static final double TAIL_Z_MM = CENTER_Z_MM + SPRITE_TAIL_PIXELS * MM_PER_PIXEL;
    public static final double ROCKET_MIDDLE_Z_MM = (TIP_Z_MM + TAIL_Z_MM) / 2.0;

    private BoosterPose() {
    }

    public static double lengthMm() {
        return TAIL_Z_MM - TIP_Z_MM;
    }

    public static double axisUpMetres() {
        return CRADLE_AXIS_UP_MM / 1000.0;
    }

    public static double tailForwardMetres() {
        return -TAIL_Z_MM / 1000.0;
    }

    public static double middleForwardMetres() {
        return -ROCKET_MIDDLE_Z_MM / 1000.0;
    }
}
