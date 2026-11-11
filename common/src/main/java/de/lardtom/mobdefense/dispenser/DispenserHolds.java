package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.FireCadence;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class DispenserHolds {
    private static final Map<Entity, FireCadence.Hold> HOLDS = new WeakHashMap<>();

    private DispenserHolds() {
    }

    static synchronized Optional<FireCadence.Hold> get(Entity drone) {
        return Optional.ofNullable(HOLDS.get(drone));
    }

    static synchronized void put(Entity drone, FireCadence.Hold hold) {
        HOLDS.put(drone, hold);
    }

    static synchronized void release(Entity drone) {
        HOLDS.remove(drone);
    }

    public static synchronized boolean holding(Entity drone) {
        return HOLDS.containsKey(drone);
    }
}
