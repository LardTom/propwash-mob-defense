package de.lardtom.mobdefense.mob.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageTwoCoreTest {
    private static final double FOV = 90.0;

    @Test
    void endermanConeShrinksWithDistance() {
        assertTrue(Gaze.inCone(0, 0, 0, 0, 0, 1, 0, 0, 10, Gaze.ENDERMAN_CONE, true, FOV));
        double offAt10 = Math.tan(Math.acos(1.0 - Gaze.ENDERMAN_CONE / 10.0)) * 10.0;
        assertTrue(Gaze.inCone(0, 0, 0, 0, 0, 1, offAt10 * 0.9, 0, 10, Gaze.ENDERMAN_CONE, true, FOV));
        assertFalse(Gaze.inCone(0, 0, 0, 0, 0, 1, offAt10 * 1.1, 0, 10, Gaze.ENDERMAN_CONE, true, FOV));
        assertTrue(Gaze.threshold(Gaze.ENDERMAN_CONE, 40.0, true) > Gaze.threshold(Gaze.ENDERMAN_CONE, 4.0, true));
        assertFalse(Gaze.inCone(0, 0, 0, 0, 0, 1, 0, 0, -10, Gaze.ENDERMAN_CONE, true, FOV));
    }

    @Test
    void creakingConeIsLimitedByTheCameraFieldOfView() {
        assertEquals(0.5, Gaze.threshold(Gaze.CREAKING_CONE, 3.0, false), 1.0E-12);
        assertTrue(Gaze.inCone(0, 0, 0, 0, 0, 1, 0, 3.9, 4, Gaze.CREAKING_CONE, false, FOV));
        assertFalse(Gaze.inCone(0, 0, 0, 0, 0, 1, 0, 4.1, 4, Gaze.CREAKING_CONE, false, FOV));
        assertTrue(Gaze.inCone(0, 0, 0, 0, 0, 1, 0, 6.0, 4, Gaze.CREAKING_CONE, false, 120.0));
        assertFalse(Gaze.inCone(0, 0, 0, 0, 0, 0, 0, 0, 4, Gaze.CREAKING_CONE, false, FOV));
        assertFalse(Gaze.inCone(1, 1, 1, 0, 0, 1, 1, 1, 1, Gaze.CREAKING_CONE, false, FOV));
        assertThrows(IllegalArgumentException.class, () -> Gaze.fovThreshold(180.0));
        assertThrows(IllegalArgumentException.class, () -> Gaze.threshold(-1.0, 1.0, false));
    }

    @Test
    void windowsFollowTheDesignTable() {
        assertTrue(StageTwoWindows.stareLow(4.0));
        assertFalse(StageTwoWindows.stareLow(4.1));
        assertTrue(StageTwoWindows.fangs(2.0));
        assertFalse(StageTwoWindows.fangs(2.01));
        assertTrue(StageTwoWindows.ground(1.0, false));
        assertFalse(StageTwoWindows.ground(1.2, false));
        assertTrue(StageTwoWindows.ground(5.0, true));
        assertTrue(StageTwoWindows.jumper(3.0, false, false));
        assertFalse(StageTwoWindows.jumper(6.0, true, false));
        assertTrue(StageTwoWindows.jumper(6.0, true, true));
        assertFalse(StageTwoWindows.jumper(6.0, false, true));
        assertEquals(16.0, StageTwoWindows.BEAM_MAX);
        assertEquals(12.0, StageTwoWindows.CASTER_MAX);
        assertEquals(32.0, StageTwoWindows.BOSS_MAX);
        assertEquals(16.0, StageTwoWindows.WIND_MAX);
        assertEquals(16.0, StageTwoWindows.BULLET_MAX);
        assertEquals(10.0, StageTwoWindows.POTION_MAX);
        assertEquals(16.0, StageTwoWindows.TRIDENT_MAX);
        assertTrue(StageTwoWindows.stareTeleport(3.5));
        assertFalse(StageTwoWindows.stareTeleport(3.0));
    }

    @Test
    void potionHurtsOnlyWithinTwoBlocks() {
        assertEquals(6.0, StageTwoWindows.potionDamage(0.0), 1.0E-12);
        assertEquals(3.0, StageTwoWindows.potionDamage(2.0), 1.0E-12);
        assertEquals(0.0, StageTwoWindows.potionDamage(2.01));
        assertEquals(0.0, StageTwoWindows.potionDamage(Double.NaN));
        assertEquals(0.0, StageTwoWindows.potionDamage(-1.0));
        assertEquals(1.0, StageTwoWindows.segmentDistance(0, 0, 0, 2, 0, 0, 1, 1, 0), 1.0E-12);
        assertEquals(Math.sqrt(2.0), StageTwoWindows.segmentDistance(0, 0, 0, 2, 0, 0, 3, 1, 0), 1.0E-12);
        assertEquals(1.0, StageTwoWindows.segmentDistance(0, 0, 0, 0, 0, 0, 0, 1, 0), 1.0E-12);
    }

    @Test
    void leapReachesTheDroneWithinLimits() {
        for (double rise = 0.5; rise <= 3.0; rise += 0.5) {
            double speed = StageTwoWindows.leapSpeed(rise);
            double height = 0.0;
            double v = speed;
            while (v > 0.0) {
                height += v;
                v = (v - StageTwoWindows.GRAVITY) * StageTwoWindows.DRAG;
            }
            assertTrue(height >= rise || speed == StageTwoWindows.LEAP_MAX, "Sprung auf " + rise + " m erreicht nur " + height);
        }
        assertEquals(StageTwoWindows.LEAP_MIN, StageTwoWindows.leapSpeed(0.0));
        assertEquals(StageTwoWindows.LEAP_MAX, StageTwoWindows.leapSpeed(50.0));
        assertEquals(StageTwoWindows.LEAP_MIN, StageTwoWindows.leapSpeed(Double.NaN));
    }

    @Test
    void angerLastsTwentyToThirtyNineSeconds() {
        assertEquals(400, StageTwoWindows.angerTicks(0.0));
        assertTrue(StageTwoWindows.angerTicks(0.999) <= 779);
        assertEquals(400, StageTwoWindows.angerTicks(-3.0));
    }
}
