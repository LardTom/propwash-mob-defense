package de.lardtom.mobdefense.spear.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpearRamTest {
    @Test
    void villagerHitHeadOnByTheSevenInchHullIsAhead() {
        assertTrue(SpearRam.ahead(6.14, -58.90, -23.18, 5.465, -59.795, -23.975, 6.065, -57.845, -23.375, 0.0, 0.0, -1.0));
        assertTrue(SpearRam.cosine(6.14, -58.90, -23.18, 5.465, -59.795, -23.975, 6.065, -57.845, -23.375, 0.0, 0.0, -1.0) > 0.9);
    }

    @Test
    void noseTiltedDownInFastFlightStillCountsAsAhead() {
        double down = Math.toRadians(40.0);
        assertTrue(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, -1.0, -1.2, 0.3, 1.0, -0.6, 0.0, -Math.sin(down), -Math.cos(down)));
    }

    @Test
    void targetBesideAboveOrBehindIsNotAhead() {
        assertFalse(SpearRam.ahead(0.0, 0.0, 0.0, 0.3, -1.0, -0.1, 0.9, 1.0, 0.5, 0.0, 0.0, -1.0), "seitlich");
        assertFalse(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, 0.2, -0.3, 0.3, 2.0, 0.3, 0.0, 0.0, -1.0), "oben");
        assertFalse(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, -1.0, 0.2, 0.3, 1.0, 0.8, 0.0, 0.0, -1.0), "hinten");
    }

    @Test
    void coneEndsSixtyDegreesFromTheNose() {
        double inside = Math.toRadians(59.0);
        double outside = Math.toRadians(61.0);
        assertTrue(SpearRam.ahead(0.0, 0.0, 0.0, Math.sin(inside), 0.0, -Math.cos(inside), Math.sin(inside), 0.0, -Math.cos(inside), 0.0, 0.0, -1.0));
        assertFalse(SpearRam.ahead(0.0, 0.0, 0.0, Math.sin(outside), 0.0, -Math.cos(outside), Math.sin(outside), 0.0, -Math.cos(outside), 0.0, 0.0, -1.0));
        assertEquals(0.5, SpearRam.FRONT_COSINE);
    }

    @Test
    void centreInsideTheTargetUsesTheTargetCentre() {
        assertTrue(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, -1.0, -0.8, 0.3, 1.0, 0.1, 0.0, 0.0, -1.0), "Mitte vorn");
        assertFalse(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, -1.0, -0.1, 0.3, 1.0, 0.8, 0.0, 0.0, -1.0), "Mitte hinten");
        assertTrue(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, -0.3, -0.3, 0.3, 0.3, 0.3, 0.0, 0.0, -1.0), "genau auf der Mitte");
    }

    @Test
    void withoutNoseNothingIsAhead() {
        assertFalse(SpearRam.ahead(0.0, 0.0, 0.0, -0.3, -1.0, -1.2, 0.3, 1.0, -0.6, 0.0, 0.0, 0.0));
    }
}
