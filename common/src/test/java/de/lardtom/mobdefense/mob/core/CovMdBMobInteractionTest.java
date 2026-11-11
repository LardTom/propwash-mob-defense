package de.lardtom.mobdefense.mob.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdBMobInteractionTest {
    private static final double VANILLA_RISE_PER_TICK = 0.05;
    private static final double VANILLA_RELAX = 0.2;
    private static final double PROPWASH_MAX_FORCE = 500.0;

    @Test
    void guardianKeepsThreeBlocksLikeWithPlayersElderDoesNotLetGo() {
        assertFalse(StageTwoWindows.beam(StageTwoWindows.BEAM_MIN, false), "Wächter: genau 3 Blöcke reichen nicht");
        assertTrue(StageTwoWindows.beam(StageTwoWindows.BEAM_MIN + 0.01, false));
        assertTrue(StageTwoWindows.beam(StageTwoWindows.BEAM_MAX, false));
        assertTrue(StageTwoWindows.beam(1.0, true), "Großer Wächter bleibt auch nah dran");
        assertEquals(3.0, StageTwoWindows.BEAM_MIN);
    }

    @Test
    void levitationLastsTwoHundredTicksLikeTheVanillaEffect() {
        long hit = 1000L;
        long ends = Levitation.endsAt(hit);
        assertEquals(hit + 200L, ends);
        assertTrue(Levitation.lifting(hit, ends));
        assertTrue(Levitation.lifting(ends - 1L, ends));
        assertFalse(Levitation.lifting(ends, ends));
        assertEquals(1.0 / 20.0, Levitation.TICK_SECONDS, 1.0E-12);
    }

    @Test
    void withoutThrustTheDroneRisesExactlyLikeALevitatingPlayer() {
        double speed = 0.0;
        double vanilla = 0.0;
        for (int tick = 0; tick < 60; tick++) {
            double net = Levitation.acceleration(speed) - Levitation.GRAVITY;
            speed += net * Levitation.TICK_SECONDS;
            vanilla += (VANILLA_RISE_PER_TICK - vanilla) * VANILLA_RELAX;
            assertEquals(vanilla * 20.0, speed, 1.0E-9, "Tick " + tick);
        }
        assertEquals(Levitation.RISE, speed, 0.01);
        assertEquals(Levitation.GRAVITY, Levitation.acceleration(Levitation.RISE), 1.0E-12, "steigt mit 1 m/s, Schwerkraft aufgehoben");
    }

    @Test
    void ownThrustAddsOnTopAndTheLiftIsBounded() {
        double hoverSteady = Levitation.RISE + Levitation.GRAVITY / Levitation.RATE;
        assertEquals(0.0, Levitation.acceleration(hoverSteady), 1.0E-12, "mit Schwebeschub steigt sie mit gut 3,4 m/s");
        assertEquals(0.0, Levitation.acceleration(10.0));
        assertEquals(Levitation.MAX_ACCELERATION, Levitation.acceleration(-20.0));
        assertEquals(0.0, Levitation.acceleration(Double.NaN));
        assertEquals(0.0, Levitation.acceleration(Double.POSITIVE_INFINITY));
        assertEquals(0.7 * Levitation.GRAVITY, Levitation.lift(0.7, Levitation.RISE), 1.0E-12);
        assertEquals(0.0, Levitation.lift(0.0, 0.0));
        assertEquals(0.0, Levitation.lift(-1.0, 0.0));
        assertEquals(0.0, Levitation.lift(Double.NaN, 0.0));
        assertTrue(Levitation.lift(5.0, -50.0) < PROPWASH_MAX_FORCE, "unter der Grenze von DronePhysics#push");
    }
}
