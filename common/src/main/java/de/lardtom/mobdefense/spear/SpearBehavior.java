package de.lardtom.mobdefense.spear;

import de.lardtom.mobdefense.spear.core.SpearMass;
import de.lardtom.mobdefense.spear.core.SpearPose;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.part.Vec3Mm;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class SpearBehavior implements AccessoryBehavior {
    public static final ItemDisplay DISPLAY = new ItemDisplay(new Vec3Mm(SpearPose.AXIS_X_MM, SpearPose.AXIS_Y_MM, SpearPose.CENTER_Z_MM),
            SpearPose.YAW, SpearPose.PITCH, SpearPose.ROLL, SpearPose.SCALE);
    private static final Optional<ItemDisplay> SHOWN = Optional.of(DISPLAY);

    SpearBehavior() {
    }

    @Override
    public int slots() {
        return 1;
    }

    @Override
    public boolean accepts(int slot, ItemStack stack) {
        return slot == Spears.SLOT && Spears.accepts(stack);
    }

    @Override
    public int maxStackSize(int slot, ItemStack stack) {
        return 1;
    }

    @Override
    public OptionalDouble payloadGrams(List<ItemStack> contents) {
        double grams = 0.0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            OptionalDouble known = SpearMass.grams(Spears.id(stack));
            if (known.isEmpty()) {
                return OptionalDouble.empty();
            }
            grams += known.getAsDouble() * stack.getCount();
        }
        return OptionalDouble.of(grams);
    }

    @Override
    public Optional<ItemDisplay> display(int slot) {
        return slot == Spears.SLOT ? SHOWN : Optional.empty();
    }

    @Override
    public void tick(AccessoryContext context) {
        SpearStrikes.tick(context);
    }
}
