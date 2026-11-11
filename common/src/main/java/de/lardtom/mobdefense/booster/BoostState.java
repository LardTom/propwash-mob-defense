package de.lardtom.mobdefense.booster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneDataKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public record BoostState(long endsAt, int flight, int stars, float thrustNewtons) {
    public static final String PATH = "booster";
    public static final BoostState IDLE = new BoostState(0L, 0, 0, 0.0F);
    public static final Codec<BoostState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("ends_at").forGetter(BoostState::endsAt),
            Codec.BYTE.xmap(Byte::intValue, Integer::byteValue).fieldOf("flight").forGetter(BoostState::flight),
            Codec.BYTE.xmap(Byte::intValue, Integer::byteValue).fieldOf("stars").forGetter(BoostState::stars),
            Codec.FLOAT.fieldOf("thrust_n").forGetter(BoostState::thrustNewtons)
    ).apply(instance, BoostState::new));

    private static @Nullable DroneDataKey<BoostState> key;

    public BoostState {
        endsAt = Math.max(0L, endsAt);
        flight = Math.max(0, Math.min(BoostProfile.MAX_FLIGHT, flight));
        stars = Math.max(0, Math.min(Byte.MAX_VALUE, stars));
        thrustNewtons = Float.isFinite(thrustNewtons) ? Math.max(0.0F, Math.min((float) BoostProfile.MAX_NEWTONS, thrustNewtons)) : 0.0F;
    }

    public boolean active() {
        return this.endsAt > 0L;
    }

    public boolean burning(long gameTime) {
        return BoostProfile.burning(this.endsAt, gameTime);
    }

    static synchronized void register() {
        if (key == null) {
            key = PropwashApi.droneData().register(MobDefense.id(PATH), CODEC, IDLE, DroneDataKey.Sync.TRACKING);
        }
    }

    public static boolean registered() {
        return key != null;
    }

    public static BoostState of(Entity drone) {
        DroneDataKey<BoostState> current = key;
        if (current == null) {
            return IDLE;
        }
        BoostState state = PropwashApi.droneData().get(drone, current);
        return state == null ? IDLE : state;
    }

    public static BoostState of(ItemStack droneItem) {
        DroneDataKey<BoostState> current = key;
        if (current == null) {
            return IDLE;
        }
        BoostState state = PropwashApi.droneData().get(droneItem, current);
        return state == null ? IDLE : state;
    }

    static void set(Entity drone, BoostState state) {
        DroneDataKey<BoostState> current = key;
        if (current == null) {
            return;
        }
        if (state.active()) {
            PropwashApi.droneData().set(drone, current, state);
        } else if (of(drone).active()) {
            PropwashApi.droneData().remove(drone, current);
        }
    }
}
