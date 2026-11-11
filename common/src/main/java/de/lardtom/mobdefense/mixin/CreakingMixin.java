package de.lardtom.mobdefense.mixin;

import de.lardtom.mobdefense.mob.reaction.FreezeHook;
import de.lardtom.mobdefense.mob.reaction.StareReaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.creaking.Creaking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Creaking.class)
public abstract class CreakingMixin implements FreezeHook {
    @Inject(method = "checkCanMove", at = @At("RETURN"), cancellable = true)
    private void mobdefense$droneStare(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && StareReaction.frozen((Mob) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
