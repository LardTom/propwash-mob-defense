package de.lardtom.mobdefense.client;

import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OsdStateTest {
    private static OsdState dispenser(List<String> codes, List<Integer> counts, int selected, boolean cooling) {
        return new OsdState(true, codes, counts, selected, cooling, false, false, 0L, false, false, 0, 0);
    }

    @Test
    void firesTheSelectedSlotOrTheNextFilled() {
        OsdState state = dispenser(List.of("ARW", "SPL", "WND", "---"), List.of(12, 1, 3, 0), 1, false);
        assertEquals("DSP 2 SPL 1", state.dispenserText());
        assertEquals("ARW[SPL]WND ---", state.slotsText());
        OsdState next = dispenser(List.of("ARW", "---", "WND", "---"), List.of(12, 0, 3, 0), 1, true);
        assertEquals("DSP 3 WND 3*", next.dispenserText());
        assertEquals("ARW[---]WND ---", next.slotsText());
        OsdState empty = dispenser(List.of(), List.of(), 0, false);
        assertEquals("DSP --", empty.dispenserText());
        assertEquals("[---]--- --- ---", empty.slotsText());
    }

    @Test
    void elementsOnlyWithTheirAccessory() {
        assertNull(MobDefenseOsd.dispenser(OsdState.NONE));
        assertNull(MobDefenseOsd.slots(OsdState.NONE));
        assertNull(MobDefenseOsd.booster(OsdState.NONE, 0.0));
        assertNull(MobDefenseOsd.spear(OsdState.NONE));
        OsdText empty = MobDefenseOsd.dispenser(dispenser(List.of(), List.of(), 0, false));
        assertEquals(OsdTone.CAUTION, empty.tone());
    }

    @Test
    void boosterReadyBurningEmpty() {
        OsdState ready = new OsdState(false, List.of(), List.of(), 0, false, true, true, 0L, false, false, 0, 0);
        assertEquals("RKT RDY", MobDefenseOsd.booster(ready, 1000.0).text());
        OsdState burning = new OsdState(false, List.of(), List.of(), 0, false, true, false, 1035L, false, false, 0, 0);
        OsdText text = MobDefenseOsd.booster(burning, 1010.0);
        assertEquals("RKT 1.3", text.text());
        assertEquals(OsdTone.ACCENT, text.tone());
        assertEquals("RKT ---", MobDefenseOsd.booster(burning, 1040.0).text());
        assertEquals(OsdTone.CAUTION, MobDefenseOsd.booster(burning, 1040.0).tone());
    }

    @Test
    void spearDurability() {
        OsdState spear = new OsdState(false, List.of(), List.of(), 0, false, false, false, 0L, true, true, 250, 33);
        assertEquals("SPR 87%", MobDefenseOsd.spear(spear).text());
        assertEquals(OsdTone.NORMAL, MobDefenseOsd.spear(spear).tone());
        OsdState worn = new OsdState(false, List.of(), List.of(), 0, false, false, false, 0L, true, true, 250, 230);
        assertEquals(OsdTone.CAUTION, MobDefenseOsd.spear(worn).tone());
        OsdState none = new OsdState(false, List.of(), List.of(), 0, false, false, false, 0L, true, false, 0, 0);
        assertEquals("SPR ---", MobDefenseOsd.spear(none).text());
    }
}
