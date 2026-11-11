package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.propwash.accessory.dispense.DispenseMath;
import de.lardtom.propwash.api.accessory.DispenseOptions;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;

public final class ExpectedRecoil {
    public static final double NOTICEABLE_SHARE = 0.5;

    private ExpectedRecoil() {
    }

    public static double windChargeNs() {
        ProjectileItem.DispenseConfig config = ((ProjectileItem) Items.WIND_CHARGE).createDispenseConfig();
        return DispenseMath.recoil(DispenseMath.Kind.WIND_CHARGE, config.power(), DispenseOptions.defaults().speedScale());
    }

    public static boolean matches(double measuredNs, double expectedNs) {
        return Math.abs(measuredNs - expectedNs) <= 0.05 * expectedNs + 0.01;
    }

    public static double idealBackMps(double recoilNs, double massKg) {
        return massKg > 0.0 && recoilNs > 0.0 ? recoilNs / massKg : 0.0;
    }

    public static double noticeableBackMps(double recoilNs, double massKg) {
        return NOTICEABLE_SHARE * idealBackMps(recoilNs, massKg);
    }

    public static boolean noticeable(double backMps, double recoilNs, double massKg) {
        double minimum = noticeableBackMps(recoilNs, massKg);
        return minimum > 0.0 && backMps >= minimum;
    }
}
