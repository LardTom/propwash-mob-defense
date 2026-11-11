package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.Aiming;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.phys.Vec3;

public final class BossReaction implements Reaction {
    public static final String KIND = "boss";
    public static final int INTERVAL = 20;
    public static final int HEADS = 3;
    public static final double SKULL_SPEED = 1.0;
    public static final double CENTER_HEAD = 3.0;
    public static final double SIDE_HEAD = 2.2;
    public static final double SIDE_OFFSET = 1.3;
    private static final int LEVEL_EVENT_WITHER_SHOOT = 1024;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.BOSS_MAX;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.cooldown = INTERVAL / 2;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.halt(mob, memory);
        Moves.look(mob, drone);
        if (memory.cooldown > 0) {
            memory.cooldown--;
            return;
        }
        if (!Support.sees(mob, drone)) {
            memory.cooldown = 5;
            return;
        }
        ServerLevel level = Support.level(mob);
        if (level == null) {
            return;
        }
        shoot(level, mob, drone, memory.step % HEADS);
        memory.step++;
        memory.shots++;
        memory.cooldown = INTERVAL;
    }

    public static Vec3 head(Mob mob, int head) {
        double scale = mob.getScale();
        if (head <= 0) {
            return new Vec3(mob.getX(), mob.getY() + CENTER_HEAD * scale, mob.getZ());
        }
        double angle = Math.toRadians(mob.yBodyRot + 180.0 * (head - 1));
        return new Vec3(mob.getX() + Math.cos(angle) * SIDE_OFFSET * scale, mob.getY() + SIDE_HEAD * scale,
                mob.getZ() + Math.sin(angle) * SIDE_OFFSET * scale);
    }

    private static void shoot(ServerLevel level, Mob mob, DroneState drone, int head) {
        Vec3 from = head(mob, head);
        Vec3 direction = Aiming.direction(from, drone, SKULL_SPEED, 0.0);
        if (!mob.isSilent()) {
            level.levelEvent(null, LEVEL_EVENT_WITHER_SHOOT, mob.blockPosition(), 0);
        }
        WitherSkull skull = new WitherSkull(level, mob, direction.normalize());
        skull.setOwner(mob);
        skull.setPos(from.x, from.y, from.z);
        level.addFreshEntity(skull);
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        Moves.stop(mob, memory);
    }
}
