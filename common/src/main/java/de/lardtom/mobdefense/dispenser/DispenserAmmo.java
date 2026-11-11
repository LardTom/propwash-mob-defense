package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.DispenserBlock;

import java.util.List;

public final class DispenserAmmo {
    public static final TagKey<Item> FLUID_BUCKETS = TagKey.create(Registries.ITEM, MobDefense.id("dispenser_fluid_buckets"));

    private DispenserAmmo() {
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static AmmoTable.Ammo ammo(ItemStack stack) {
        return AmmoTable.resolve(id(stack), fallback(stack));
    }

    public static AmmoTable.Kind kind(ItemStack stack) {
        return ammo(stack).kind();
    }

    public static String code(ItemStack stack) {
        return ammo(stack).code();
    }

    public static int limit(ItemStack stack) {
        return AmmoTable.limit(ammo(stack), stack.getMaxStackSize());
    }

    public static double grams(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        AmmoTable.Ammo ammo = ammo(stack);
        if (ammo.weighed()) {
            return AmmoTable.grams(ammo, stack.getCount(), 0.0);
        }
        return PropwashApi.accessories().itemGrams(stack);
    }

    public static double grams(List<ItemStack> contents) {
        double sum = 0.0;
        for (ItemStack stack : contents) {
            sum += grams(stack);
        }
        return sum;
    }

    static AmmoTable.Kind fallback(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(Items.TNT)) {
            return AmmoTable.Kind.TNT;
        }
        if (stack.is(Items.BUCKET)) {
            return AmmoTable.Kind.EMPTY_BUCKET;
        }
        if (stack.is(FLUID_BUCKETS) || item instanceof DispensibleContainerItem) {
            return AmmoTable.Kind.FLUID_BUCKET;
        }
        if (item instanceof ProjectileItem) {
            return AmmoTable.Kind.PROJECTILE;
        }
        boolean registered = DispenserBlock.DISPENSER_REGISTRY.containsKey(item);
        if (registered && !"minecraft".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
            return AmmoTable.Kind.BLOCK_USE;
        }
        if (registered || stack.has(DataComponents.EQUIPPABLE) || item instanceof SpawnEggItem) {
            return AmmoTable.Kind.ENTITY_USE;
        }
        return AmmoTable.Kind.ITEM;
    }
}
