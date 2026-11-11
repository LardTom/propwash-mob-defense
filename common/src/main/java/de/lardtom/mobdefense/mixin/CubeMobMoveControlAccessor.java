package de.lardtom.mobdefense.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob$CubeMobMoveControl")
public interface CubeMobMoveControlAccessor {
    @Invoker("setDirection")
    void mobdefense$setDirection(float yRot, boolean aggressive);

    @Invoker("setWantedMovement")
    void mobdefense$setWantedMovement(double speedModifier);
}
