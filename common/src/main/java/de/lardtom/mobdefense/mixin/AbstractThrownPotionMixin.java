package de.lardtom.mobdefense.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.lardtom.mobdefense.payload.PotionPayloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractThrownPotion.class)
public abstract class AbstractThrownPotionMixin {
    @WrapOperation(method = "onHit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/projectile/throwableitemprojectile/AbstractThrownPotion;onHitAsPotion(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/HitResult;)V"))
    private void mobdefense$shieldPlayers(AbstractThrownPotion potion, ServerLevel level, ItemStack stack, HitResult hit, Operation<Void> original) {
        PotionPayloads.onHit(potion, level, stack, hit, () -> original.call(potion, level, stack, hit));
    }
}
