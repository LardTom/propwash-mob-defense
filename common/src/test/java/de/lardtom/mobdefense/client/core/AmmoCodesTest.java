package de.lardtom.mobdefense.client.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmmoCodesTest {
    @Test
    void designCodes() {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("minecraft:arrow", "ARW"),
                Map.entry("minecraft:spectral_arrow", "SPC"),
                Map.entry("minecraft:tipped_arrow", "TIP"),
                Map.entry("minecraft:snowball", "SNW"),
                Map.entry("minecraft:egg", "EGG"),
                Map.entry("minecraft:blue_egg", "EGG"),
                Map.entry("minecraft:brown_egg", "EGG"),
                Map.entry("minecraft:splash_potion", "SPL"),
                Map.entry("minecraft:lingering_potion", "LNG"),
                Map.entry("minecraft:experience_bottle", "XPB"),
                Map.entry("minecraft:wind_charge", "WND"),
                Map.entry("minecraft:fire_charge", "FIR"),
                Map.entry("minecraft:firework_rocket", "RKT"),
                Map.entry("minecraft:tnt", "TNT"),
                Map.entry("minecraft:bucket", "BKT"),
                Map.entry("minecraft:water_bucket", "BKT"),
                Map.entry("minecraft:axolotl_bucket", "BKT"),
                Map.entry("minecraft:bone_meal", "BON"),
                Map.entry("minecraft:shears", "ITM"),
                Map.entry("minecraft:zombie_spawn_egg", "ITM"),
                Map.entry("othermod:fire_arrow", "ARW"),
                Map.entry("othermod:honey_bucket", "BKT"));
        expected.forEach((item, code) -> assertEquals(code, AmmoCodes.code(item), item));
        assertEquals(AmmoCodes.EMPTY, AmmoCodes.code(""));
    }

    @Test
    void codesAreThreeAsciiLetters() {
        for (String code : AmmoCodes.ALL) {
            assertTrue(code.matches("[A-Z]{3}"), code);
        }
        assertEquals(15, AmmoCodes.ALL.size());
    }
}
