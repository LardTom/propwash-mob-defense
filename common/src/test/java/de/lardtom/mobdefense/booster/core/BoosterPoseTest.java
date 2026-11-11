package de.lardtom.mobdefense.booster.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoosterPoseTest {
    @Test
    void rocketLiesInTheCradleWithTheTipAgainstTheNoseStop() {
        assertEquals(BoosterPose.NOSE_STOP_MM, BoosterPose.TIP_Z_MM, 1.0E-9);
        assertTrue(BoosterPose.TAIL_Z_MM <= BoosterPose.IGNITER_MM, "Heck der Rakete vor dem Zünder: " + BoosterPose.TAIL_Z_MM);
        assertTrue(BoosterPose.IGNITER_MM - BoosterPose.TAIL_Z_MM < 1.0, "Zündschnur am Zünder");
        assertTrue(BoosterPose.TIP_Z_MM < BoosterPose.TAIL_Z_MM, "Spitze vorn (−z), Heck hinten");
        assertEquals(13.0 * BoosterPose.MM_PER_PIXEL, BoosterPose.lengthMm(), 1.0E-9);
    }

    @Test
    void axisOfTheRocketIsTheAxisOfTheCradle() {
        assertEquals(BoosterPose.CRADLE_AXIS_UP_MM, BoosterPose.CENTER_UP_MM + BoosterPose.SPRITE_AXIS_PIXELS * BoosterPose.MM_PER_PIXEL, 1.0E-9);
        assertEquals(0.0159, BoosterPose.axisUpMetres(), 1.0E-12);
    }

    @Test
    void thrustPointIsTheTailBehindTheMiddle() {
        assertTrue(BoosterPose.tailForwardMetres() < BoosterPose.middleForwardMetres());
        assertEquals(-BoosterPose.TAIL_Z_MM / 1000.0, BoosterPose.tailForwardMetres(), 1.0E-12);
    }

    @Test
    void displayStaysWithinTheLimitsOfItemDisplay() {
        assertTrue(BoosterPose.SCALE > 0.0F && BoosterPose.SCALE <= 4.0F);
        assertTrue(Math.abs(BoosterPose.CENTER_Z_MM) <= 500.0 && Math.abs(BoosterPose.CENTER_UP_MM) <= 500.0);
        assertEquals(160.0 / 16.0 * BoosterPose.SCALE, BoosterPose.MM_PER_PIXEL, 1.0E-6);
    }
}
