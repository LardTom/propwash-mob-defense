package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dev.dispenser.ExpectedRecoil;
import de.lardtom.propwash.accessory.dispense.DispenseMath;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindChargeRecoilTest {
    private static final double MASS_KG = 0.5;

    @BeforeAll
    static void bootstrap() {
        Vanilla.boot();
    }

    static double reported() {
        ProjectileItem.DispenseConfig config = ((ProjectileItem) Items.WIND_CHARGE).createDispenseConfig();
        return DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, config.power(), DispenseOptions.defaults().speedScale());
    }

    @Test
    void theSelftestsExpectTheRecoilPropwashReports() {
        double reported = reported();
        assertEquals(reported, ExpectedRecoil.windChargeNs(), 1.0E-9,
                "S2 und S4 erwarten den Rückstoß, den Propwash für eine Windkugel aus dispense meldet");
        assertTrue(ExpectedRecoil.matches(reported, ExpectedRecoil.windChargeNs()));
    }

    @Test
    void theToleranceStaysFivePercentPlusOneHundredth() {
        double expected = ExpectedRecoil.windChargeNs();
        double band = 0.05 * expected + 0.01;
        assertTrue(ExpectedRecoil.matches(expected + band * 0.99, expected));
        assertTrue(ExpectedRecoil.matches(expected - band * 0.99, expected));
        assertFalse(ExpectedRecoil.matches(expected + band * 1.01, expected));
        assertFalse(ExpectedRecoil.matches(expected - band * 1.01, expected));
    }

    @Test
    void s4FailsWithoutAKick() {
        double recoil = ExpectedRecoil.windChargeNs();
        assertFalse(ExpectedRecoil.noticeable(0.0, recoil, MASS_KG), "ohne Stoß (Impuls abgeschaltet) muss S4 scheitern");
        assertFalse(ExpectedRecoil.noticeable(0.01, recoil, MASS_KG), "Messrauschen ist kein spürbarer Rückstoß");
        assertFalse(ExpectedRecoil.noticeable(0.49 * recoil / MASS_KG, recoil, MASS_KG));
    }

    @Test
    void s4NeedsHalfOfImpulsePerMass() {
        double recoil = ExpectedRecoil.windChargeNs();
        double ideal = recoil / MASS_KG;
        assertEquals(ideal, ExpectedRecoil.idealBackMps(recoil, MASS_KG), 1.0E-12);
        assertEquals(ExpectedRecoil.NOTICEABLE_SHARE * ideal, ExpectedRecoil.noticeableBackMps(recoil, MASS_KG), 1.0E-12);
        assertEquals(0.5, ExpectedRecoil.NOTICEABLE_SHARE, 0.0);
        assertTrue(ExpectedRecoil.noticeable(0.5 * ideal * 1.01, recoil, MASS_KG));
        assertFalse(ExpectedRecoil.noticeable(0.5 * ideal * 0.99, recoil, MASS_KG));
        assertTrue(ExpectedRecoil.noticeable(ideal, recoil, MASS_KG));
    }

    @Test
    void theThresholdFollowsTheReportedImpulseAndTheMass() {
        assertEquals(2.0 * ExpectedRecoil.noticeableBackMps(1.0, MASS_KG), ExpectedRecoil.noticeableBackMps(2.0, MASS_KG), 1.0E-12);
        assertEquals(0.5 * ExpectedRecoil.noticeableBackMps(1.0, MASS_KG), ExpectedRecoil.noticeableBackMps(1.0, 2.0 * MASS_KG), 1.0E-12);
        assertFalse(ExpectedRecoil.noticeable(5.0, 0.0, MASS_KG), "ohne gemeldeten Impuls keine Schwelle, S4 scheitert");
        assertFalse(ExpectedRecoil.noticeable(5.0, ExpectedRecoil.windChargeNs(), 0.0), "ohne Masse keine Schwelle, S4 scheitert");
    }
}
