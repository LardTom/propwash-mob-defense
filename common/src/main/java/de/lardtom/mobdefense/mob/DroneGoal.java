package de.lardtom.mobdefense.mob;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

final class DroneGoal extends Goal {
    private final Mob mob;
    private @Nullable Assignment running;

    DroneGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return DroneReactions.usable(this.mob) != null;
    }

    @Override
    public boolean canContinueToUse() {
        Assignment current = DroneReactions.usable(this.mob);
        return current != null && current == this.running;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.running = DroneReactions.usable(this.mob);
        if (this.running != null) {
            DroneReactions.begin(this.mob, this.running);
        }
    }

    @Override
    public void tick() {
        Assignment current = this.running;
        if (current != null) {
            DroneReactions.drive(this.mob, current);
        }
    }

    @Override
    public void stop() {
        Assignment current = this.running;
        this.running = null;
        if (current != null) {
            DroneReactions.end(this.mob, current);
        }
    }
}
