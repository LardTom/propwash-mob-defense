package de.lardtom.mobdefense.dispenser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlotCycleTest {
    @Test
    void firesSelectedSlotWhenFilled() {
        assertEquals(2, SlotCycle.firing(2, new boolean[]{true, true, true, true}));
    }

    @Test
    void firesNextFilledSlotWhenSelectedIsEmpty() {
        assertEquals(3, SlotCycle.firing(1, new boolean[]{false, false, false, true}));
        assertEquals(0, SlotCycle.firing(3, new boolean[]{true, false, false, false}));
    }

    @Test
    void emptyDispenserDoesNotFire() {
        assertEquals(-1, SlotCycle.firing(0, new boolean[4]));
    }

    @Test
    void nextSkipsEmptySlotsAndWraps() {
        assertEquals(3, SlotCycle.next(0, new boolean[]{true, false, false, true}));
        assertEquals(0, SlotCycle.next(3, new boolean[]{true, false, false, true}));
        assertEquals(1, SlotCycle.next(1, new boolean[]{false, true, false, false}));
        assertEquals(2, SlotCycle.next(2, new boolean[4]));
    }

    @Test
    void onlyFourSlots() {
        assertThrows(IllegalArgumentException.class, () -> SlotCycle.firing(0, new boolean[9]));
    }

    @Test
    void nextStartsAtTheSlotThatWouldFire() {
        boolean[] filled = {false, true, false, true};
        int firing = SlotCycle.firing(0, filled);
        assertEquals(1, firing);
        assertEquals(3, SlotCycle.next(firing, filled));
        assertEquals(1, SlotCycle.next(3, filled));
    }

    @Test
    void negativeAndLargeSelectionsWrap() {
        boolean[] full = {true, true, true, true};
        assertEquals(3, SlotCycle.firing(-1, full));
        assertEquals(1, SlotCycle.firing(5, full));
        assertEquals(0, SlotCycle.next(-1, full));
    }
}
