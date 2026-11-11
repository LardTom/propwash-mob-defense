package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.accessory.MountPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class DispenserLog {
    public record Entry(long gameTime, FireGate.Outcome outcome, int slot, @Nullable DispenseResult result, FireGate.Notice notice, Vec3 muzzle,
                        Vec3 forward, Vec3 droneVelocity, Vec3 launch, MountPose mount) {
        public String status() {
            return this.result == null ? this.outcome.name() : this.result.status().name();
        }
    }

    public record Counts(int attempts, int dispensed) {
    }

    public static final int HISTORY = 32;
    private static final Map<Entity, Entry> LAST = new WeakHashMap<>();
    private static final Map<Entity, Deque<Entry>> HISTORIES = new WeakHashMap<>();
    private static final Map<Entity, Counts> COUNTS = new WeakHashMap<>();

    private DispenserLog() {
    }

    static synchronized void record(Entity drone, FireGate.Shot shot, @Nullable DispenseResult result, FireGate.Notice notice, MountPose mount,
                                    Vec3 droneVelocity) {
        Vec3 launch = result == null ? Vec3.ZERO : result.entity().map(Entity::getDeltaMovement).orElse(Vec3.ZERO);
        Entry entry = new Entry(drone.level().getGameTime(), shot.outcome(), shot.slot(), result, notice, mount.front(), mount.forward(), droneVelocity,
                launch, mount);
        LAST.put(drone, entry);
        Deque<Entry> history = HISTORIES.computeIfAbsent(drone, key -> new ArrayDeque<>());
        history.addLast(entry);
        while (history.size() > HISTORY) {
            history.removeFirst();
        }
        Counts before = COUNTS.getOrDefault(drone, new Counts(0, 0));
        COUNTS.put(drone, new Counts(before.attempts() + 1, before.dispensed() + (result != null && result.success() ? 1 : 0)));
    }

    public static synchronized List<Entry> history(Entity drone) {
        Deque<Entry> history = HISTORIES.get(drone);
        return history == null ? List.of() : List.copyOf(history);
    }

    public static synchronized Optional<Entry> last(Entity drone) {
        return Optional.ofNullable(LAST.get(drone));
    }

    public static synchronized Counts counts(Entity drone) {
        return COUNTS.getOrDefault(drone, new Counts(0, 0));
    }
}
