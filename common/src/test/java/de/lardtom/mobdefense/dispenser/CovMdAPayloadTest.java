package de.lardtom.mobdefense.dispenser;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdAPayloadTest {
    static final double ARROW = 20.0;
    static final double POTION = 350.0;
    static final double TNT = 600.0;
    static final double WIND_CHARGE = 50.0;
    static final double ROCKET = 60.0;
    static final double MOUNT_GRAMS = 150.0;
    static final double MOUNT_AREA_M2 = 0.0036;
    static final double MIN_TOP_SPEED_LOSS_KMH = 1.0;
    static final double SLUGGISH_FACTOR = 0.5;

    @BeforeAll
    static void bootstrap() {
        Vanilla.boot();
        register(MobDefense.DISPENSER_MOUNT);
    }

    static void register(Identifier id) {
        if (PropwashApi.parts().get(id).isPresent()) {
            return;
        }
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        Path file = resources.resolve("data/" + id.getNamespace() + "/propwash_parts/accessory/" + id.getPath() + ".json");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            PropwashApi.parts().register(PropwashApi.parts().fromJson(PartKind.ACCESSORY, id, json));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double payload(List<ItemStack> contents) {
        return DispenserBehavior.INSTANCE.payloadGrams(contents).orElseThrow(() -> new AssertionError("Dispenser meldet keine Beladung"));
    }

    private static List<ItemStack> slots(ItemStack... stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SlotCycle.SLOTS; i++) {
            out.add(i < stacks.length ? stacks[i] : ItemStack.EMPTY);
        }
        return out;
    }

    @Test
    void payloadGramsPerItemAsInDesignTable214() {
        assertEquals(ARROW, payload(slots(new ItemStack(Items.ARROW))), 1.0E-9, "Pfeil");
        assertEquals(ARROW, payload(slots(new ItemStack(Items.SPECTRAL_ARROW))), 1.0E-9, "Spektralpfeil");
        assertEquals(POTION, payload(slots(new ItemStack(Items.SPLASH_POTION))), 1.0E-9, "Wurftrank");
        assertEquals(POTION, payload(slots(new ItemStack(Items.LINGERING_POTION))), 1.0E-9, "Verweiltrank");
        assertEquals(TNT, payload(slots(new ItemStack(Items.TNT))), 1.0E-9, "TNT");
        assertEquals(WIND_CHARGE, payload(slots(new ItemStack(Items.WIND_CHARGE))), 1.0E-9, "Windkugel");
        assertEquals(ROCKET, payload(slots(new ItemStack(Items.FIREWORK_ROCKET))), 1.0E-9, "Rakete im Dispenser");
        assertEquals(0.0, payload(slots()), 1.0E-9, "leer");
    }

    @Test
    void fourSlotsWithSixteenArrowsWeigh1280Grams() {
        double grams = payload(slots(new ItemStack(Items.ARROW, 16), new ItemStack(Items.ARROW, 16), new ItemStack(Items.ARROW, 16),
                new ItemStack(Items.ARROW, 16)));
        assertEquals(1280.0, grams, 1.0E-9);
        assertEquals(4 * 16, 1280.0 / ARROW, 1.0E-9);
    }

    @Test
    void mixedLoadAddsUpPerSlot() {
        double grams = payload(slots(new ItemStack(Items.ARROW, 16), new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.WIND_CHARGE, 16),
                new ItemStack(Items.TNT, 4)));
        assertEquals(16 * ARROW + POTION + 16 * WIND_CHARGE + 4 * TNT, grams, 1.0E-9);
    }

    @Test
    void boosterRocketWeighs60Grams() {
        assertEquals(ROCKET, BoostProfile.payloadGrams(1), 1.0E-9);
        assertEquals(0.0, BoostProfile.payloadGrams(0), 1.0E-9);
    }

    private static BuildStats stats(String name, BuildSpec spec) {
        return PropwashApi.builds().analyze(spec).orElseThrow(() -> new AssertionError(name + ": keine Analyse"));
    }

    private static double thrustToWeight(BuildStats stats, double payload) {
        return stats.thrustToWeight() * stats.massGrams() / (stats.massGrams() + MOUNT_GRAMS + payload);
    }

    private static double fourPotions() {
        return payload(slots(new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.LINGERING_POTION),
                new ItemStack(Items.SPLASH_POTION)));
    }

    private static double fourTnt() {
        return payload(slots(new ItemStack(Items.TNT, 4)));
    }

    static BuildSpec sevenInchLipo() {
        return BuildSpec.of(propwash("long_range_7"), propwash("stack_30"), propwash("m2807_1300"), propwash("p7_2b"), propwash("digital_hd"),
                propwash("b6s_1300"));
    }

    static String heavyLoads() {
        BuildStats five = stats("5″", PayloadMassBenchTest.fiveInch());
        BuildStats seven = stats("7″ Li-Ion", PayloadMassBenchTest.sevenInch());
        BuildStats lipo = stats("7″ LiPo", sevenInchLipo());
        return String.format(Locale.ROOT,
                "Schub/Gewicht mit leerer Halterung: 5″ %.2f, 7″ Li-Ion %.2f, 7″ LiPo %.2f; mit vier Tränken (%.0f g): 5″ %.2f, 7″ Li-Ion %.2f, 7″ LiPo %.2f; "
                        + "mit vier TNT (%.0f g): 5″ %.2f, 7″ Li-Ion %.2f, 7″ LiPo %.2f",
                thrustToWeight(five, 0.0), thrustToWeight(seven, 0.0), thrustToWeight(lipo, 0.0), fourPotions(), thrustToWeight(five, fourPotions()),
                thrustToWeight(seven, fourPotions()), thrustToWeight(lipo, fourPotions()), fourTnt(), thrustToWeight(five, fourTnt()),
                thrustToWeight(seven, fourTnt()), thrustToWeight(lipo, fourTnt()));
    }

    @Test
    void fourPotionsWeigh1400GramsAndASevenInchCarriesThem() {
        assertEquals(1400.0, fourPotions(), 1.0E-9);
        assertEquals(2400.0, fourTnt(), 1.0E-9);
        BuildStats five = stats("5″", PayloadMassBenchTest.fiveInch());
        BuildStats seven = stats("7″", PayloadMassBenchTest.sevenInch());
        String text = heavyLoads();
        System.out.println("[cov-md-a] " + text);
        assertTrue(thrustToWeight(five, fourPotions()) > 1.0, text + " – die 5″ hebt mit vier Tränken ab");
        assertTrue(thrustToWeight(seven, fourPotions()) >= PayloadMassBenchTest.HOVER_MARGIN, text + " – die 7″ fliegt brauchbar");
    }

    @Test
    void fourPotionsOrFourTntSlowEveryDroneAndASevenInchWithLipoCarriesThemBest() {
        BuildSpec lipoMounted = sevenInchLipo().withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
        assertTrue(PropwashApi.builds().check(lipoMounted).buildable(), "7″ LiPo mit Dispenser: " + PropwashApi.builds().check(lipoMounted).problems());
        BuildStats five = stats("5″", PayloadMassBenchTest.fiveInch());
        BuildStats seven = stats("7″ Li-Ion", PayloadMassBenchTest.sevenInch());
        BuildStats lipo = stats("7″ LiPo", sevenInchLipo());
        String text = heavyLoads();
        System.out.println("[cov-md-a] " + text);
        for (BuildStats drone : List.of(five, seven, lipo)) {
            for (double load : new double[] {fourPotions(), fourTnt()}) {
                assertTrue(thrustToWeight(drone, load) <= SLUGGISH_FACTOR * thrustToWeight(drone, 0.0),
                        text + " – jede Drohne wird mit " + Math.round(load) + " g deutlich träger");
            }
        }
        for (double load : new double[] {fourPotions(), fourTnt()}) {
            String with = text + " – mit " + Math.round(load) + " g";
            assertTrue(thrustToWeight(lipo, load) > thrustToWeight(five, load), with + " trägt die 7″ mit LiPo mehr als die 5″");
            assertTrue(thrustToWeight(lipo, load) > thrustToWeight(seven, load), with + " trägt die 7″ mit LiPo mehr als die Long-Range-7″");
            assertTrue(thrustToWeight(lipo, load) >= PayloadMassBenchTest.HOVER_MARGIN, with + " fliegt die 7″ mit LiPo brauchbar");
            assertTrue(thrustToWeight(seven, load) < thrustToWeight(five, load), with + " trägt die Long-Range-7″ mit Li-Ion weniger als die 5″");
        }
    }

    static BuildSpec whoop() {
        return BuildSpec.of(propwash("whoop_65"), propwash("aio_whoop"), propwash("m0802_22000"), propwash("p31_4b"), propwash("analog_nano"),
                propwash("b1s_300_hv"));
    }

    private static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    @Test
    void aWhoopCannotLiftAnEmptyDispenser() {
        BuildStats whoop = stats("Whoop", whoop());
        BuildStats mounted = stats("Whoop mit Dispenser", whoop().withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT));
        String text = String.format(Locale.ROOT, "Whoop %.0f g, Schub/Gewicht %.2f; mit leerem Dispenser %.0f g, Schub/Gewicht %.2f", whoop.massGrams(),
                whoop.thrustToWeight(), mounted.massGrams(), mounted.thrustToWeight());
        System.out.println("[cov-md-a] " + text);
        assertEquals(MOUNT_GRAMS, mounted.massGrams() - whoop.massGrams(), 0.5, text);
        assertTrue(mounted.thrustToWeight() < 1.0, text + " – eine Whoop hebt den Dispenser nicht");
    }

    @Test
    void anEmptyDispenserLowersTheTopSpeedOfAFiveInch() {
        BuildSpec base = PayloadMassBenchTest.fiveInch();
        BuildSpec mounted = base.withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
        assertTrue(PropwashApi.builds().check(mounted).buildable(), "5″ mit Dispenser: " + PropwashApi.builds().check(mounted).problems());
        BuildStats without = stats("5″", base);
        BuildStats with = stats("5″ mit Dispenser", mounted);
        assertEquals(MOUNT_GRAMS, with.massGrams() - without.massGrams(), 0.5, "Analyse-Masse");
        double loss = without.topSpeedKmh() - with.topSpeedKmh();
        String text = String.format(Locale.ROOT, "Spitzentempo 5″ ohne %.1f km/h, mit leerem Dispenser %.1f km/h (−%.1f km/h, +%.0f g, +%.4f m²)",
                without.topSpeedKmh(), with.topSpeedKmh(), loss, MOUNT_GRAMS, MOUNT_AREA_M2);
        System.out.println("[cov-md-a] " + text);
        assertTrue(loss >= MIN_TOP_SPEED_LOSS_KMH, text);
        assertTrue(with.topSpeedKmh() > 0.5 * without.topSpeedKmh(), text + " – nur etwas niedriger");
    }
}
