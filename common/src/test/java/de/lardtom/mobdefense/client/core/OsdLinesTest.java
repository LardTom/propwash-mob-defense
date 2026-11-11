package de.lardtom.mobdefense.client.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdLinesTest {
    @Test
    void dispenserShowsSlotCodeCountAndCooldown() {
        assertEquals("DSP 2 ARW 12", OsdLines.dispenser(1, "ARW", 12, false));
        assertEquals("DSP 1 TNT 4*", OsdLines.dispenser(0, "TNT", 4, true));
        assertEquals(OsdLines.DISPENSER_EMPTY, OsdLines.dispenser(-1, "ARW", 12, false));
        assertEquals(OsdLines.DISPENSER_EMPTY, OsdLines.dispenser(0, AmmoCodes.EMPTY, 0, false));
        assertEquals("DSP --", OsdLines.DISPENSER_EMPTY);
    }

    @Test
    void slotsBracketTheSelectedSlot() {
        assertEquals("[ARW]SPL WND ---", OsdLines.slots(List.of("ARW", "SPL", "WND", "---"), 0));
        assertEquals("ARW[SPL]WND ---", OsdLines.slots(List.of("ARW", "SPL", "WND", "---"), 1));
        assertEquals("ARW SPL WND[---]", OsdLines.slots(List.of("ARW", "SPL", "WND", "---"), 3));
        assertEquals("--- --- --- ---", OsdLines.slots(List.of("---", "---", "---", "---"), -1));
        assertEquals(OsdLines.SLOTS_PREVIEW, OsdLines.slots(List.of("ARW", "SPL", "WND", "---"), 0));
    }

    @Test
    void boosterReadyBurningEmpty() {
        assertEquals("RKT RDY", OsdLines.booster(true));
        assertEquals("RKT ---", OsdLines.booster(false));
        assertEquals("RKT 1.2", OsdLines.boosterBurning(1.2));
        assertEquals("RKT 1.3", OsdLines.boosterBurning(1.21));
        assertEquals("RKT 2.3", OsdLines.boosterBurning(2.25));
        assertEquals("RKT 0.0", OsdLines.boosterBurning(-1.0));
        assertEquals("RKT 9.9", OsdLines.boosterBurning(30.0));
    }

    @Test
    void spearShowsDurabilityInPercent() {
        assertEquals("SPR 87%", OsdLines.spear(250, 33));
        assertEquals("SPR 100%", OsdLines.spear(250, 0));
        assertEquals("SPR 1%", OsdLines.spear(1000, 999));
        assertEquals("SPR 0%", OsdLines.spear(250, 250));
        assertEquals("SPR 100%", OsdLines.spear(0, 0));
    }

    @Test
    void everyLineFitsAndIsAscii() {
        List<String> lines = List.of(OsdLines.dispenser(3, "XPB", 64, true), OsdLines.slots(List.of("ARW", "SPL", "WND", "TNT"), 2),
                OsdLines.boosterBurning(2.25), OsdLines.booster(true), OsdLines.booster(false), OsdLines.spear(250, 0), OsdLines.SPEAR_EMPTY,
                OsdLines.DISPENSER_EMPTY, OsdLines.DISPENSER_PREVIEW, OsdLines.SLOTS_PREVIEW, OsdLines.BOOSTER_PREVIEW, OsdLines.SPEAR_PREVIEW);
        for (String line : lines) {
            assertTrue(OsdLines.printableAscii(line), line);
        }
        assertTrue(OsdLines.dispenser(3, "XPB", 999, true).length() <= OsdLines.DISPENSER_CHARS);
        assertTrue(OsdLines.slots(List.of("ARW", "SPL", "WND", "TNT"), 3).length() <= OsdLines.SLOTS_CHARS);
        assertTrue(OsdLines.boosterBurning(9.9).length() <= OsdLines.BOOSTER_CHARS);
        assertTrue(OsdLines.spear(1, 0).length() <= OsdLines.SPEAR_CHARS);
        assertTrue(!OsdLines.printableAscii("Ü") && !OsdLines.printableAscii("\t"));
    }
}
