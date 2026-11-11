package de.lardtom.mobdefense.dispenser;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public final class DispenserState {
    public static final String PATH = "dispenser";

    public record Selection(int selected, long cooldownUntil) {
        public static final Selection DEFAULT = new Selection(0, 0L);
        public static final Codec<Selection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BYTE.xmap(Byte::intValue, Integer::byteValue).fieldOf("selected").forGetter(Selection::selected),
                Codec.LONG.fieldOf("cooldown_until").forGetter(Selection::cooldownUntil)
        ).apply(instance, Selection::new));

        public Selection {
            selected = Math.floorMod(selected, SlotCycle.SLOTS);
        }

        public Selection withSelected(int value) {
            return new Selection(value, this.cooldownUntil);
        }

        public Selection withCooldownUntil(long value) {
            return new Selection(this.selected, value);
        }
    }

    private static @Nullable DroneDataKey<Selection> key;

    private DispenserState() {
    }

    static synchronized void register() {
        if (key == null) {
            key = PropwashApi.droneData().register(MobDefense.id(PATH), Selection.CODEC, Selection.DEFAULT, DroneDataKey.Sync.TRACKING);
        }
    }

    public static boolean registered() {
        return key != null;
    }

    public static Selection selection(Entity drone) {
        DroneDataKey<Selection> current = key;
        return current == null ? Selection.DEFAULT : PropwashApi.droneData().get(drone, current);
    }

    public static Selection selection(ItemStack droneItem) {
        DroneDataKey<Selection> current = key;
        return current == null ? Selection.DEFAULT : PropwashApi.droneData().get(droneItem, current);
    }

    static void select(Entity drone, int slot) {
        DroneDataKey<Selection> current = key;
        Selection now = selection(drone);
        if (current != null && now.selected() != Math.floorMod(slot, SlotCycle.SLOTS)) {
            PropwashApi.droneData().set(drone, current, now.withSelected(slot));
        }
    }

    static void cooldownUntil(Entity drone, long gameTime) {
        DroneDataKey<Selection> current = key;
        if (current != null) {
            PropwashApi.droneData().set(drone, current, selection(drone).withCooldownUntil(gameTime));
        }
    }
}
