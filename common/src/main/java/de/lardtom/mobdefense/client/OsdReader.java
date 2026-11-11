package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.core.AmmoCodes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OsdReader {
    public static final Identifier DISPENSER_DATA = MobDefense.id("dispenser");
    public static final Identifier BOOSTER_DATA = MobDefense.id("booster");
    private static int cachedDrone = Integer.MIN_VALUE;
    private static long cachedTime = Long.MIN_VALUE;
    private static OsdState cached = OsdState.NONE;

    private OsdReader() {
    }

    public static synchronized OsdState read(Entity drone, long gameTime) {
        if (drone.getId() == cachedDrone && gameTime == cachedTime) {
            return cached;
        }
        cached = compute(drone, gameTime);
        cachedDrone = drone.getId();
        cachedTime = gameTime;
        return cached;
    }

    public static synchronized void forget() {
        cachedDrone = Integer.MIN_VALUE;
        cachedTime = Long.MIN_VALUE;
        cached = OsdState.NONE;
    }

    static OsdState compute(Entity drone, long gameTime) {
        AccessoryRegistry accessories = PropwashApi.accessories();
        boolean dispenser = mounted(accessories, drone, AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
        boolean booster = mounted(accessories, drone, AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER);
        boolean spearMount = mounted(accessories, drone, AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT);
        if (!dispenser && !booster && !spearMount) {
            return OsdState.NONE;
        }
        List<String> codes = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        int selected = 0;
        boolean coolingDown = false;
        if (dispenser) {
            for (ItemStack stack : accessories.contents(drone, AccessorySlot.FRONT)) {
                codes.add(stack.isEmpty() ? AmmoCodes.EMPTY : AmmoCodes.code(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                counts.add(stack.isEmpty() ? 0 : stack.getCount());
            }
            Optional<CompoundTag> data = data(drone, DISPENSER_DATA);
            selected = data.map(tag -> tag.getIntOr("selected", 0)).orElse(0);
            coolingDown = data.map(tag -> tag.getLongOr("cooldown_until", 0L) > gameTime).orElse(false);
        }
        boolean rocket = false;
        long burnEndsAt = 0L;
        if (booster) {
            rocket = accessories.contents(drone, AccessorySlot.TOP).stream().anyMatch(stack -> !stack.isEmpty());
            burnEndsAt = data(drone, BOOSTER_DATA).map(tag -> tag.getLongOr("ends_at", 0L)).orElse(0L);
        }
        boolean spear = false;
        int spearMax = 0;
        int spearDamage = 0;
        if (spearMount) {
            for (ItemStack stack : accessories.contents(drone, AccessorySlot.BOTTOM)) {
                if (!stack.isEmpty()) {
                    spear = true;
                    spearMax = stack.isDamageableItem() ? stack.getMaxDamage() : 0;
                    spearDamage = stack.isDamageableItem() ? stack.getDamageValue() : 0;
                    break;
                }
            }
        }
        return new OsdState(dispenser, codes, counts, selected, coolingDown, booster, rocket, burnEndsAt, spearMount, spear, spearMax, spearDamage);
    }

    private static boolean mounted(AccessoryRegistry accessories, Entity drone, AccessorySlot slot, Identifier part) {
        return accessories.accessoryAt(drone, slot).filter(part::equals).isPresent();
    }

    public static Optional<CompoundTag> data(Entity drone, Identifier id) {
        return PropwashApi.droneData().find(id).flatMap(key -> encode(drone, key));
    }

    private static <T> Optional<CompoundTag> encode(Entity drone, DroneDataKey<T> key) {
        T value = PropwashApi.droneData().get(drone, key);
        if (value == null) {
            return Optional.empty();
        }
        return key.codec().encodeStart(NbtOps.INSTANCE, value).result().filter(CompoundTag.class::isInstance).map(CompoundTag.class::cast);
    }
}
