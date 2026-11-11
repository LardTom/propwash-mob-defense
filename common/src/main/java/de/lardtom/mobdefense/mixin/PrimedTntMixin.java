package de.lardtom.mobdefense.mixin;

import de.lardtom.mobdefense.payload.TntGuard;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntMixin {
    @ModifyArg(method = "explode", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)V"),
            index = 8)
    private Level.ExplosionInteraction mobdefense$griefing(Level.ExplosionInteraction interaction) {
        return TntGuard.interaction((PrimedTnt) (Object) this, interaction);
    }
}
