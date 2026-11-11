package de.lardtom.mobdefense.mob;

import net.minecraft.world.entity.Mob;

public interface Reaction {
    String kind();

    default boolean accepts(Mob mob) {
        return true;
    }

    default double maxRange(Mob mob) {
        return Double.MAX_VALUE;
    }

    default boolean wants(Mob mob, DroneState drone, double distance) {
        return true;
    }

    default int priority() {
        return 1;
    }

    default void start(Mob mob, DroneState drone, ReactionMemory memory) {
    }

    void tick(Mob mob, DroneState drone, ReactionMemory memory);

    default void stop(Mob mob, ReactionMemory memory) {
    }
}
