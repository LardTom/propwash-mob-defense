package de.lardtom.mobdefense.spear;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.spear.core.SpearDamage;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;

import java.util.Optional;

public final class Spears {
    public static final TagKey<Item> ACCEPTS = TagKey.create(Registries.ITEM, MobDefense.id("spear_mount_accepts"));
    public static final int SLOT = 0;
    public static final SpearBehavior BEHAVIOR = new SpearBehavior();

    private static boolean registered;

    private Spears() {
    }

    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;
        PropwashApi.accessories().register(MobDefense.SPEAR_MOUNT, BEHAVIOR);
        PropwashEvents.DRONE_HIT_ENTITY.register(SpearStrikes::onHit);
    }

    public static boolean accepts(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ACCEPTS) && stack.has(DataComponents.KINETIC_WEAPON);
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static SpearDamage.Stats stats(KineticWeapon kinetic) {
        return new SpearDamage.Stats(kinetic.damageMultiplier(), threshold(kinetic.damageConditions()), threshold(kinetic.knockbackConditions()),
                threshold(kinetic.dismountConditions()));
    }

    private static Optional<SpearDamage.Threshold> threshold(Optional<KineticWeapon.Condition> condition) {
        return condition.map(value -> new SpearDamage.Threshold(value.minSpeed(), value.minRelativeSpeed()));
    }
}
