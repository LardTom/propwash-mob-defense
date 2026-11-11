package de.lardtom.mobdefense.booster;

import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.booster.core.BoosterPose;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.ItemDisplay;
import de.lardtom.propwash.api.part.Vec3Mm;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public final class BoosterBehavior implements AccessoryBehavior {
    public static final BoosterBehavior INSTANCE = new BoosterBehavior();
    public static final int SLOT = 0;
    public static final int IGNITE = 3;
    public static final String IGNITE_KEY = "mobdefense.action.booster.ignite";
    public static final ItemDisplay DISPLAY = new ItemDisplay(new Vec3Mm(BoosterPose.CENTER_X_MM, BoosterPose.CENTER_UP_MM, BoosterPose.CENTER_Z_MM),
            BoosterPose.YAW, BoosterPose.PITCH, BoosterPose.ROLL, BoosterPose.SCALE);
    private static final Optional<ItemDisplay> SHOWN = Optional.of(DISPLAY);
    private static final Optional<String> IGNITE_NAME = Optional.of(IGNITE_KEY);

    private BoosterBehavior() {
    }

    @Override
    public int slots() {
        return 1;
    }

    @Override
    public boolean accepts(int slot, ItemStack stack) {
        return slot == SLOT && Boosters.accepts(stack);
    }

    @Override
    public int maxStackSize(int slot, ItemStack stack) {
        return 1;
    }

    @Override
    public OptionalDouble payloadGrams(List<ItemStack> contents) {
        int rockets = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!Boosters.accepts(stack)) {
                return OptionalDouble.empty();
            }
            rockets += stack.getCount();
        }
        return OptionalDouble.of(BoostProfile.payloadGrams(rockets));
    }

    @Override
    public Optional<String> actionNameKey(int number) {
        return number == IGNITE ? IGNITE_NAME : Optional.empty();
    }

    @Override
    public Optional<ItemDisplay> display(int slot) {
        return slot == SLOT ? SHOWN : Optional.empty();
    }

    @Override
    public void onAction(AccessoryAction action) {
        if (action.number() == IGNITE && action.pressed()) {
            Boosters.ignite(action);
        }
    }

    @Override
    public void tick(AccessoryContext context) {
        Boosters.tick(context);
    }
}
