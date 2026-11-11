package de.lardtom.mobdefense.mob;

import net.minecraft.world.entity.Entity;

public final class Assignment {
    final Entity drone;
    final Reaction reaction;
    final boolean brain;
    final ReactionMemory memory = new ReactionMemory();
    long until;
    long seen;
    boolean started;

    Assignment(Entity drone, Reaction reaction, boolean brain, long now) {
        this.drone = drone;
        this.reaction = reaction;
        this.brain = brain;
        this.until = now;
        this.seen = now;
    }

    public Entity drone() {
        return this.drone;
    }

    public Reaction reaction() {
        return this.reaction;
    }

    public boolean brain() {
        return this.brain;
    }

    public ReactionMemory memory() {
        return this.memory;
    }

    public boolean started() {
        return this.started;
    }
}
