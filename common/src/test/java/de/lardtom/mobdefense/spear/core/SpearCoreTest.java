package de.lardtom.mobdefense.spear.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpearCoreTest {
    @Test
    void massOfEveryVanillaSpearFromTheDesignTable() {
        assertEquals(List.of("minecraft:wooden_spear", "minecraft:stone_spear", "minecraft:copper_spear", "minecraft:iron_spear",
                "minecraft:golden_spear", "minecraft:diamond_spear", "minecraft:netherite_spear").stream().sorted().toList(),
                SpearMass.VANILLA.keySet().stream().sorted().toList());
        assertEquals(OptionalDouble.of(180.0), SpearMass.grams("minecraft:wooden_spear"));
        assertEquals(OptionalDouble.of(260.0), SpearMass.grams("minecraft:stone_spear"));
        assertEquals(OptionalDouble.of(280.0), SpearMass.grams("minecraft:copper_spear"));
        assertEquals(OptionalDouble.of(300.0), SpearMass.grams("minecraft:iron_spear"));
        assertEquals(OptionalDouble.of(340.0), SpearMass.grams("minecraft:golden_spear"));
        assertEquals(OptionalDouble.of(280.0), SpearMass.grams("minecraft:diamond_spear"));
        assertEquals(OptionalDouble.of(360.0), SpearMass.grams("minecraft:netherite_spear"));
    }

    @Test
    void unknownItemsFallBackToPropwash() {
        assertTrue(SpearMass.grams("minecraft:trident").isEmpty());
        assertTrue(SpearMass.grams("othermod:bronze_spear").isEmpty());
        assertTrue(SpearMass.grams(null).isEmpty());
    }

    @Test
    void contactCooldownLikeVanilla() {
        ContactMemory memory = new ContactMemory();
        assertFalse(memory.recent(7, 100, 10));
        memory.remember(7, 100);
        assertTrue(memory.recent(7, 100, 10));
        assertTrue(memory.recent(7, 109, 10));
        assertFalse(memory.recent(7, 110, 10));
        assertFalse(memory.recent(8, 105, 10));
        assertFalse(memory.recent(7, 100, 0));
    }

    @Test
    void contactMemoryStaysSmall() {
        ContactMemory memory = new ContactMemory();
        for (int i = 0; i < 1000; i++) {
            memory.remember(i, i * 10L);
        }
        assertTrue(memory.size() <= ContactMemory.KEEP_TICKS / 10 + ContactMemory.PRUNE_SIZE, "Einträge: " + memory.size());
        assertTrue(memory.recent(999, 9990, 10));
        memory.clear();
        assertEquals(0, memory.size());
    }

    @Test
    void spearSitsInTheClampsAndTheTipPointsForward() {
        assertEquals(0.6F, SpearPose.SCALE);
        assertEquals(2.0 * 7.5 * Math.sqrt(2.0) * 10.0 * 0.6, SpearPose.lengthMm(), 1.0e-4);
        assertTrue(SpearPose.TIP_FORWARD_MM > 28.8 + 50.0, "Spitze " + SpearPose.TIP_FORWARD_MM + " mm");
        assertTrue(SpearPose.TAIL_FORWARD_MM < -23.4, "Ende " + SpearPose.TAIL_FORWARD_MM + " mm");
        assertEquals(SpearPose.AXIS_Y_MM, SpearPose.TIP_UP_MM);
        assertEquals(SpearPose.TIP_FORWARD_MM / 1000.0, SpearPose.tipForwardMetres(), 1.0e-12);
        assertEquals(-90.0F, SpearPose.YAW);
        assertEquals(45.0F, SpearPose.ROLL);
    }
}
