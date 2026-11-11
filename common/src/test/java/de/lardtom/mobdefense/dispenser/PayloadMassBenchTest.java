package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadMassBenchTest {
    static final double MOUNT_GRAMS = 150.0;
    static final double HOVER_MARGIN = 1.5;
    static final double CANNOT_HOVER_BELOW = 1.15;

    @BeforeAll
    static void bootstrap() {
        Vanilla.boot();
    }

    private static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    static BuildSpec fiveInch() {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300"));
    }

    static BuildSpec sevenInch() {
        return BuildSpec.of(propwash("long_range_7"), propwash("stack_30"), propwash("m2807_1300"), propwash("p7_2b"), propwash("digital_hd"),
                propwash("b6s_4000_lion"));
    }

    static BuildSpec cinewhoop() {
        return BuildSpec.of(propwash("cinewhoop_3"), propwash("aio_20"), propwash("m1404_4600"), propwash("p3_3b"), propwash("digital_lite"),
                propwash("b4s_650"));
    }

    record Bench(String name, double massGrams, double thrustGrams) {
        double thrustToWeight(double payloadGrams) {
            return this.thrustGrams / (this.massGrams + MOUNT_GRAMS + payloadGrams);
        }
    }

    static Bench bench(String name, BuildSpec spec) {
        BuildStats stats = PropwashApi.builds().analyze(spec).orElseThrow(() -> new AssertionError(name + ": keine Analyse"));
        assertTrue(stats.massGrams() > 0.0 && stats.thrustToWeight() > 0.0, name + ": " + stats.massGrams() + " g, T/W " + stats.thrustToWeight());
        return new Bench(name, stats.massGrams(), stats.thrustToWeight() * stats.massGrams());
    }

    static List<ItemStack> full(ItemStack ammo) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SlotCycle.SLOTS; i++) {
            out.add(ammo.copyWithCount(DispenserAmmo.limit(ammo)));
        }
        return out;
    }

    static String describe(Bench bench, String load, double payload) {
        return String.format(Locale.ROOT, "%s mit %s (%.0f g): %.0f g Schub für %.0f g, Schub/Gewicht %.2f", bench.name(), load, payload,
                bench.thrustGrams(), bench.massGrams() + MOUNT_GRAMS + payload, bench.thrustToWeight(payload));
    }

    @Test
    void fullOfArrowsAFiveInchStillFliesWell() {
        double arrows = DispenserAmmo.grams(full(new ItemStack(Items.ARROW)));
        assertEquals(4 * 16 * 20.0, arrows, 1.0E-9);
        Bench five = bench("5″", fiveInch());
        assertTrue(five.thrustToWeight(arrows) >= HOVER_MARGIN, describe(five, "64 Pfeilen", arrows));
    }

    @Test
    void fourTntMakeAFiveInchSluggish() {
        double tnt = DispenserAmmo.grams(new ItemStack(Items.TNT).copyWithCount(AmmoTable.TNT_LIMIT));
        assertEquals(2400.0, tnt, 1.0E-9);
        Bench five = bench("5″", fiveInch());
        assertTrue(five.thrustToWeight(tnt) < 2.0, describe(five, "vier TNT", tnt));
        assertTrue(five.thrustToWeight(tnt) < five.thrustToWeight(0.0) / 4.0, describe(five, "vier TNT", tnt));
        double fullTnt = SlotCycle.SLOTS * tnt;
        assertTrue(five.thrustToWeight(fullTnt) < 1.0, describe(five, "16 TNT", fullTnt));
    }

    @Test
    void theTntLoadOfS7StillLetsAFiveInchHover() {
        List<ItemStack> load = DispenserRig.tntRun();
        double grams = DispenserAmmo.grams(load);
        Bench five = bench("5″", fiveInch());
        assertTrue(five.thrustToWeight(grams) >= CANNOT_HOVER_BELOW, describe(five, DispenserRig.counts(load), grams));
    }

    @Test
    void aSevenInchStillHoversWithFourTntOrFourPotions() {
        Bench seven = bench("7″", sevenInch());
        double tnt = DispenserAmmo.grams(new ItemStack(Items.TNT).copyWithCount(AmmoTable.TNT_LIMIT));
        double potions = DispenserAmmo.grams(full(new ItemStack(Items.SPLASH_POTION)));
        assertEquals(1400.0, potions, 1.0E-9);
        assertTrue(seven.thrustToWeight(tnt) > 1.2, describe(seven, "vier TNT", tnt));
        assertTrue(seven.thrustToWeight(potions) >= HOVER_MARGIN, describe(seven, "vier Tränken", potions));
    }

    @Test
    void aCinewhoopCannotLiftAFullLoad() {
        Bench cine = bench("3″ Cinewhoop", cinewhoop());
        double arrows = DispenserAmmo.grams(full(new ItemStack(Items.ARROW)));
        double tnt = DispenserAmmo.grams(new ItemStack(Items.TNT).copyWithCount(AmmoTable.TNT_LIMIT));
        assertTrue(cine.thrustToWeight(arrows) < 1.0, describe(cine, "64 Pfeilen", arrows));
        assertTrue(cine.thrustToWeight(tnt) < 1.0, describe(cine, "vier TNT", tnt));
        assertTrue(cine.thrustToWeight(4 * 20.0) >= HOVER_MARGIN, describe(cine, "vier Pfeilen", 80.0));
    }

    @Test
    void anEmptyMountKeepsACinewhoopFlying() {
        Bench cine = bench("3″ Cinewhoop", cinewhoop());
        assertTrue(cine.thrustToWeight(0.0) > 1.0, describe(cine, "leerer Halterung", 0.0));
    }
}
