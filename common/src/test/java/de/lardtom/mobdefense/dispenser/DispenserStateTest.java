package de.lardtom.mobdefense.dispenser;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispenserStateTest {
    static final int MAX_BYTES = 40;

    private static <T> T roundTrip(Codec<T> codec, T value) {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        return codec.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static <T> int bytes(Codec<T> codec, T value) throws IOException {
        Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeAnyTag(tag, new DataOutputStream(out));
        return out.size();
    }

    @Test
    void selectionSurvivesSavingWithTheFieldsOfTheDesign() throws IOException {
        DispenserState.Selection selection = new DispenserState.Selection(3, 123_456_789_012L);
        assertEquals(selection, roundTrip(DispenserState.Selection.CODEC, selection));
        CompoundTag tag = (CompoundTag) DispenserState.Selection.CODEC.encodeStart(NbtOps.INSTANCE, selection).getOrThrow();
        assertEquals(Set.of("selected", "cooldown_until"), tag.keySet(), "Felder wie design.md §4.2 und der OSD-Teil sie lesen");
        assertEquals(3, tag.getIntOr("selected", -1));
        assertEquals(123_456_789_012L, tag.getLongOr("cooldown_until", -1L));
        assertTrue(bytes(DispenserState.Selection.CODEC, selection) <= MAX_BYTES, "Drohnen-Daten klein halten");
        assertEquals(1, new DispenserState.Selection(5, 0L).selected());
        assertEquals(3, new DispenserState.Selection(-1, 0L).selected());
    }
}
