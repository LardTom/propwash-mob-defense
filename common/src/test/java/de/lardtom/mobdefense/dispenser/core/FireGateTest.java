package de.lardtom.mobdefense.dispenser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FireGateTest {
    private static final AmmoTable.Kind P = AmmoTable.Kind.PROJECTILE;
    private static final FireGate.Rules DEFAULTS = FireGate.Rules.DEFAULTS;

    private static AmmoTable.Kind[] slots(AmmoTable.Kind... kinds) {
        AmmoTable.Kind[] out = new AmmoTable.Kind[SlotCycle.SLOTS];
        System.arraycopy(kinds, 0, out, 0, kinds.length);
        return out;
    }

    private static FireGate.Rules rules(boolean realAmmo, boolean allowTnt, boolean allowFluids, boolean griefing, int cooldown) {
        return new FireGate.Rules(realAmmo, allowTnt, allowFluids, griefing, cooldown);
    }

    @Test
    void defaultsAreTheOwnerDecisions() {
        assertTrue(DEFAULTS.realAmmo());
        assertTrue(DEFAULTS.allowTnt());
        assertFalse(DEFAULTS.allowFluidBuckets());
        assertTrue(DEFAULTS.droneGriefing());
        assertEquals(4, DEFAULTS.cooldownTicks());
    }

    @Test
    void cooldownBlocksUntilReady() {
        assertEquals(FireGate.Outcome.COOLDOWN, FireGate.decide(99, 100, 0, slots(P), DEFAULTS).outcome());
        assertEquals(FireGate.Outcome.FIRE, FireGate.decide(100, 100, 0, slots(P), DEFAULTS).outcome());
        assertEquals(104, FireGate.nextReady(100, 4));
        assertEquals(101, FireGate.nextReady(100, 0), "auch ohne Abklingzeit höchstens ein Schuss je Tick");
        assertEquals(200, FireGate.nextReady(100, 100));
    }

    @Test
    void oneShotPerCooldownUnderAMacro() {
        long readyAt = 0;
        int shots = 0;
        int presses = 0;
        for (int tick = 0; tick < 200; tick++) {
            for (int press = 0; press < 3; press++) {
                presses++;
                FireGate.Shot shot = FireGate.decide(tick, readyAt, 0, slots(P), DEFAULTS);
                if (shot.outcome() != FireGate.Outcome.COOLDOWN) {
                    readyAt = FireGate.nextReady(tick, DEFAULTS.cooldownTicks());
                    shots++;
                }
            }
        }
        assertEquals(600, presses);
        assertEquals(50, shots, "200 Ticks bei 4 Ticks Abklingzeit");
    }

    @Test
    void emptyDispenserGivesNoAmmo() {
        FireGate.Shot shot = FireGate.decide(0, 0, 2, slots(), DEFAULTS);
        assertEquals(FireGate.Outcome.EMPTY, shot.outcome());
        assertEquals(-1, shot.slot());
        assertEquals(FireGate.Notice.NO_AMMO, shot.notice());
    }

    @Test
    void selectedSlotFiresOrTheNextFilledOne() {
        assertEquals(1, FireGate.decide(0, 0, 1, slots(P, P, P, P), DEFAULTS).slot());
        assertEquals(3, FireGate.decide(0, 0, 1, slots(null, null, null, P), DEFAULTS).slot());
    }

    @Test
    void realAmmoDecidesConsume() {
        assertTrue(FireGate.decide(0, 0, 0, slots(P), DEFAULTS).consume());
        FireGate.Shot free = FireGate.decide(0, 0, 0, slots(P), rules(false, true, false, true, 4));
        assertEquals(FireGate.Outcome.FIRE, free.outcome());
        assertFalse(free.consume(), "real_ammo aus: Item bleibt");
        assertEquals(FireGate.Outcome.EMPTY, FireGate.decide(0, 0, 0, slots(), rules(false, true, false, true, 4)).outcome(),
                "auch ohne echte Munition muss der Slot belegt sein");
    }

    @Test
    void tntNeedsTheRule() {
        FireGate.Shot locked = FireGate.decide(0, 0, 0, slots(AmmoTable.Kind.TNT), rules(true, false, false, true, 4));
        assertEquals(FireGate.Outcome.TNT_LOCKED, locked.outcome());
        assertEquals(FireGate.Notice.TNT_LOCKED, locked.notice());
        assertEquals(FireGate.Outcome.FIRE, FireGate.decide(0, 0, 0, slots(AmmoTable.Kind.TNT), DEFAULTS).outcome());
        assertEquals(FireGate.Outcome.FIRE, FireGate.decide(0, 0, 0, slots(AmmoTable.Kind.TNT), rules(true, true, false, false, 4)).outcome(),
                "ohne drone_griefing fällt TNT trotzdem, nur ohne Blockschaden");
    }

    @Test
    void fluidBucketsAreLockedOnlyWhenPlacingFluid() {
        FireGate.Shot locked = FireGate.decide(0, 0, 0, slots(AmmoTable.Kind.FLUID_BUCKET), DEFAULTS);
        assertEquals(FireGate.Outcome.BUCKET_LOCKED, locked.outcome());
        assertEquals(FireGate.Notice.BUCKET_LOCKED, locked.notice());
        FireGate.Shot allowed = FireGate.decide(0, 0, 0, slots(AmmoTable.Kind.FLUID_BUCKET), rules(true, true, true, true, 4));
        assertEquals(FireGate.Outcome.FIRE, allowed.outcome());
        assertTrue(allowed.allowFluids());
        FireGate.Shot scoop = FireGate.decide(0, 0, 0, slots(AmmoTable.Kind.EMPTY_BUCKET), DEFAULTS);
        assertEquals(FireGate.Outcome.FIRE, scoop.outcome(), "leerer Eimer schöpft");
        assertFalse(scoop.allowFluids());
        assertFalse(FireGate.decide(0, 0, 0, slots(P), rules(true, true, true, true, 4)).allowFluids(), "nur Eimer setzen Flüssigkeit");
    }

    @Test
    void griefingRuleStopsBlockChanges() {
        FireGate.Rules calm = rules(true, true, true, false, 4);
        for (AmmoTable.Kind kind : AmmoTable.Kind.values()) {
            FireGate.Outcome outcome = FireGate.decide(0, 0, 0, slots(kind), calm).outcome();
            if (kind.changesBlocks()) {
                assertEquals(FireGate.Outcome.GRIEFING_LOCKED, outcome, kind.name());
            } else {
                assertEquals(FireGate.Outcome.FIRE, outcome, kind.name());
            }
        }
        assertEquals(FireGate.Notice.NO_PERMISSION, FireGate.notice(FireGate.Outcome.GRIEFING_LOCKED));
    }

    @Test
    void dispenseStatusMapsToTheOsdWarning() {
        assertEquals(FireGate.Notice.NONE, FireGate.afterDispense("FIRED", P));
        assertEquals(FireGate.Notice.NONE, FireGate.afterDispense("PLACED", AmmoTable.Kind.TNT));
        assertEquals(FireGate.Notice.NONE, FireGate.afterDispense("USED", AmmoTable.Kind.BLOCK_USE));
        assertEquals(FireGate.Notice.NO_AMMO, FireGate.afterDispense("EMPTY", P));
        assertEquals(FireGate.Notice.NO_PERMISSION, FireGate.afterDispense("DENIED", AmmoTable.Kind.TNT));
        assertEquals(FireGate.Notice.BUCKET_LOCKED, FireGate.afterDispense("REFUSED", AmmoTable.Kind.FLUID_BUCKET));
        assertEquals(FireGate.Notice.TNT_LOCKED, FireGate.afterDispense("REFUSED", AmmoTable.Kind.TNT));
        assertEquals(FireGate.Notice.BARREL_BLOCKED, FireGate.afterDispense("REFUSED", AmmoTable.Kind.ITEM));
        assertEquals(FireGate.Notice.BARREL_BLOCKED, FireGate.afterDispense("BLOCKED", P));
    }

    @Test
    void failSoundOnEveryFailureButNotOnCooldown() {
        assertFalse(FireGate.failSound(FireGate.Outcome.COOLDOWN, ""));
        assertFalse(FireGate.failSound(FireGate.Outcome.FIRE, "FIRED"));
        assertTrue(FireGate.failSound(FireGate.Outcome.FIRE, "DENIED"));
        assertTrue(FireGate.failSound(FireGate.Outcome.FIRE, "BLOCKED"));
        assertTrue(FireGate.failSound(FireGate.Outcome.EMPTY, ""));
        assertTrue(FireGate.failSound(FireGate.Outcome.TNT_LOCKED, ""));
    }

    @Test
    void onlyFourSlots() {
        assertThrows(IllegalArgumentException.class, () -> FireGate.decide(0, 0, 0, new AmmoTable.Kind[9], DEFAULTS));
        assertEquals(0, new FireGate.Rules(true, true, false, true, -5).cooldownTicks());
    }
}
