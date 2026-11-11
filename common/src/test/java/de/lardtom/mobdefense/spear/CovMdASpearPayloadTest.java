package de.lardtom.mobdefense.spear;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CovMdASpearPayloadTest {
    private static boolean ready;

    @BeforeAll
    static synchronized void boot() {
        if (ready) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(DataComponentInitializers.PendingComponents::apply);
        ready = true;
    }

    @Test
    void ironSpearWeighs300GramsOverPayloadGrams() {
        SpearBehavior behavior = new SpearBehavior();
        assertEquals(300.0, behavior.payloadGrams(List.of(new ItemStack(Items.IRON_SPEAR))).orElseThrow(), 1.0E-9, "Eisenspeer");
        assertEquals(180.0, behavior.payloadGrams(List.of(new ItemStack(Items.WOODEN_SPEAR))).orElseThrow(), 1.0E-9, "Holzspeer");
        assertEquals(360.0, behavior.payloadGrams(List.of(new ItemStack(Items.NETHERITE_SPEAR))).orElseThrow(), 1.0E-9, "Netheritspeer");
        assertEquals(0.0, behavior.payloadGrams(List.of(ItemStack.EMPTY)).orElseThrow(), 1.0E-9, "leer");
    }
}
