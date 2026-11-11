package de.lardtom.mobdefense.mob;

import de.lardtom.propwash.api.event.DroneKnockback;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class Knockbacks {
    public static final double SCALE = 1.0;
    private static final ThreadLocal<@Nullable Entity> SCOPE = new ThreadLocal<>();
    private static int explosions;
    private static int windCharges;
    private static int strikes;

    private Knockbacks() {
    }

    public static void onKnockback(DroneKnockback event) {
        switch (event.cause()) {
            case EXPLOSION -> {
                event.setScale(SCALE);
                count(0);
            }
            case WIND_CHARGE -> {
                event.setScale(SCALE);
                count(1);
            }
            case OTHER -> {
                if (SCOPE.get() == event.drone()) {
                    event.setScale(SCALE);
                    count(2);
                }
            }
        }
    }

    public static <T> T scoped(Entity drone, Supplier<T> action) {
        Entity previous = SCOPE.get();
        SCOPE.set(drone);
        try {
            return action.get();
        } finally {
            SCOPE.set(previous);
        }
    }

    public static void push(Entity drone, Vec3 blocksPerTick) {
        if (!blocksPerTick.isFinite()) {
            return;
        }
        scoped(drone, () -> {
            drone.push(blocksPerTick.x, blocksPerTick.y, blocksPerTick.z);
            return Boolean.TRUE;
        });
    }

    private static synchronized void count(int cause) {
        switch (cause) {
            case 0 -> explosions++;
            case 1 -> windCharges++;
            default -> strikes++;
        }
    }

    public static synchronized int[] counts() {
        return new int[]{explosions, windCharges, strikes};
    }
}
