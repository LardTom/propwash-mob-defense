package de.lardtom.mobdefense.booster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoostStateTest {
    static final int VALUE_BYTES = Long.BYTES + 1 + 1 + Float.BYTES;
    static final int MAX_NBT_BYTES = 64;

    private static int nbtBytes(BoostState state) throws IOException {
        Tag tag = BoostState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeAnyTag(tag, new DataOutputStream(out));
        return out.size();
    }

    @Test
    void fieldsOfTheDesignAndTheOsdContract() throws IOException {
        BoostState state = new BoostState(123_456_789L, 2, 3, 12.74F);
        CompoundTag tag = (CompoundTag) BoostState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow();
        assertEquals(Set.of("ends_at", "flight", "stars", "thrust_n"), tag.keySet());
        assertEquals(123_456_789L, tag.getLongOr("ends_at", -1L), "OSD liest ends_at als Spielzeit");
        assertEquals(state, BoostState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
        assertTrue(VALUE_BYTES < 32, "Werte unter 32 Byte");
        assertTrue(nbtBytes(state) <= MAX_NBT_BYTES, "mit Feldnamen " + nbtBytes(state) + " Byte");
    }

    @Test
    void idleIsNotBurning() {
        assertFalse(BoostState.IDLE.active());
        assertFalse(BoostState.IDLE.burning(0L));
        BoostState state = new BoostState(200L, 1, 0, 12.0F);
        assertTrue(state.active());
        assertTrue(state.burning(199L));
        assertFalse(state.burning(200L));
    }

    @Test
    void valuesStayInRange() {
        BoostState state = new BoostState(-5L, 9, -1, 500.0F);
        assertEquals(0L, state.endsAt());
        assertEquals(3, state.flight());
        assertEquals(0, state.stars());
        assertEquals(60.0F, state.thrustNewtons());
        assertEquals(0.0F, new BoostState(1L, 1, 1, Float.NaN).thrustNewtons());
        assertEquals(127, new BoostState(1L, 1, 300, 1.0F).stars());
    }

    @Test
    void behaviorNamesActionThreeAndShowsTheRocket() {
        BoosterBehavior behavior = BoosterBehavior.INSTANCE;
        assertEquals(1, behavior.slots());
        assertEquals(Optional.of("mobdefense.action.booster.ignite"), behavior.actionNameKey(3));
        assertEquals(Optional.empty(), behavior.actionNameKey(1));
        assertEquals(Optional.empty(), behavior.actionNameKey(2));
        assertEquals(Optional.empty(), behavior.actionNameKey(4));
        assertEquals(Optional.of(BoosterBehavior.DISPLAY), behavior.display(0));
        assertEquals(Optional.empty(), behavior.display(1));
        assertEquals(90.0F, BoosterBehavior.DISPLAY.yawDegrees());
        assertEquals(-90.0F, BoosterBehavior.DISPLAY.rollDegrees());
    }
}
