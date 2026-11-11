package de.lardtom.mobdefense.mixin;

import net.minecraft.world.entity.monster.Shulker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Shulker.class)
public interface ShulkerAccessor {
    @Invoker("setRawPeekAmount")
    void mobdefense$setRawPeekAmount(int amount);
}
