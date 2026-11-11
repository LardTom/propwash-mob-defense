package de.lardtom.mobdefense.mob;

import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class ReactionMemory {
    public int cooldown;
    public int special;
    public int step;
    public int ticks;
    public int shots;
    public int repath;
    public double angle;
    public @Nullable Vec3 point;
    public @Nullable Vec3 path;
    public @Nullable WalkTarget walk;

    public void reset() {
        this.cooldown = 0;
        this.special = 0;
        this.step = 0;
        this.ticks = 0;
        this.repath = 0;
        this.angle = 0.0;
        this.point = null;
        this.path = null;
    }
}
