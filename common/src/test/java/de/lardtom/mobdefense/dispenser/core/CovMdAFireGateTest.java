package de.lardtom.mobdefense.dispenser.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdAFireGateTest {
    private static final AmmoTable.Kind P = AmmoTable.Kind.PROJECTILE;

    private static AmmoTable.Kind[] slots(AmmoTable.Kind... kinds) {
        AmmoTable.Kind[] out = new AmmoTable.Kind[SlotCycle.SLOTS];
        System.arraycopy(kinds, 0, out, 0, kinds.length);
        return out;
    }

    @Test
    void barrelBlockedWarnsWithFailSoundForEveryAmmoKind() {
        for (AmmoTable.Kind kind : new AmmoTable.Kind[]{P, AmmoTable.Kind.ITEM, AmmoTable.Kind.ENTITY_USE, AmmoTable.Kind.BLOCK_USE,
                AmmoTable.Kind.EMPTY_BUCKET}) {
            FireGate.Shot shot = FireGate.decide(100, 0, 0, slots(kind), FireGate.Rules.DEFAULTS);
            assertEquals(FireGate.Outcome.FIRE, shot.outcome(), kind + ": vor dem Werfen ist der Lauf unbekannt");
            assertEquals(FireGate.Notice.BARREL_BLOCKED, FireGate.afterDispense("BLOCKED", kind), kind + ": Warnung");
            assertTrue(FireGate.failSound(shot.outcome(), "BLOCKED"), kind + ": Ton dispenser.fail");
        }
    }

    @Test
    void aBlockedBarrelStillStartsTheCooldownSoAMacroCannotSpamTheFailSound() {
        FireGate.Shot first = FireGate.decide(100, 0, 0, slots(P), FireGate.Rules.DEFAULTS);
        assertTrue(first.fires());
        long ready = FireGate.nextReady(100, FireGate.Rules.DEFAULTS.cooldownTicks());
        FireGate.Shot again = FireGate.decide(101, ready, 0, slots(P), FireGate.Rules.DEFAULTS);
        assertEquals(FireGate.Outcome.COOLDOWN, again.outcome());
        assertTrue(!FireGate.failSound(again.outcome(), ""), "in der Abklingzeit kein zweiter Fehlton");
    }

    @Test
    void realAmmoConsumesOnlyWhenTheShotLeaves() {
        FireGate.Shot shot = FireGate.decide(100, 0, 2, slots(P, null, P), FireGate.Rules.DEFAULTS);
        assertEquals(2, shot.slot());
        assertTrue(shot.consume(), "real_ammo: Verbrauch erst beim Werfen, BLOCKED wirft nichts");
    }
}
