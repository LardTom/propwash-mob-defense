package de.lardtom.mobdefense.spear;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdBSpearCooldownTest {
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
    void everyVanillaSpearWaitsTenTicksPerTarget() {
        List<String> spears = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            KineticWeapon kinetic = new ItemStack(item).get(DataComponents.KINETIC_WEAPON);
            if (kinetic == null) {
                continue;
            }
            spears.add(BuiltInRegistries.ITEM.getKey(item).getPath());
            assertEquals(10, kinetic.contactCooldownTicks(), BuiltInRegistries.ITEM.getKey(item) + ": Treffer-Sperre je Ziel");
        }
        assertTrue(spears.size() >= 7, "nur " + spears + " mit minecraft:kinetic_weapon");
    }
}
