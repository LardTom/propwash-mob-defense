package de.lardtom.mobdefense.booster;

import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.dev.booster.CovMdBElytraReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdBElytraReferenceTest {
    @Test
    void rocketLifetimeLikeVanilla() {
        assertEquals(20, CovMdBElytraReference.lifetime(1, 0, 0));
        assertEquals(31, CovMdBElytraReference.lifetime(1, 5, 6));
        assertEquals(30, CovMdBElytraReference.lifetime(2, 0, 0));
        CovMdBElytraReference.Result result = CovMdBElytraReference.rocket(1, 0.0);
        assertEquals(26.5 / 20.0, result.boostSeconds(), 1.0E-9);
    }

    @Test
    void rocketApproachesItsTargetSpeed() {
        double target = (CovMdBElytraReference.ROCKET_ADD + CovMdBElytraReference.ROCKET_POWER * CovMdBElytraReference.ROCKET_BLEND)
                * CovMdBElytraReference.DRAG_HORIZONTAL / (1.0 - (1.0 - CovMdBElytraReference.ROCKET_BLEND) * CovMdBElytraReference.DRAG_HORIZONTAL) * 20.0;
        double gain = CovMdBElytraReference.peakGain(0.0, 40);
        assertTrue(gain > 0.9 * target && gain < 1.05 * target, gain + " gegen " + target);
        assertTrue(CovMdBElytraReference.peakGain(10.0, 26) < CovMdBElytraReference.peakGain(0.0, 26));
    }

    @Test
    void boosterBurnIsWithinThirtyPercentOfTheVanillaRocket() {
        double rocket = CovMdBElytraReference.rocket(1, 0.0).boostSeconds();
        double booster = BoostProfile.burnSeconds(1);
        assertTrue(Math.abs(booster / rocket - 1.0) <= 0.30, booster + " s gegen " + rocket + " s");
    }
}
