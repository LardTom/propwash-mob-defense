package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.Aiming;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.KindWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.Vec3;

public final class FireballReaction implements Reaction {
    public static final String KIND = "fireball";
    public static final double FIREBALL_SPEED = 0.8;
    public static final double SPREAD = 0.04;
    public static final int BLAZE_CHARGE = 60;
    public static final int BLAZE_BURST = 6;
    public static final int BLAZE_REST = 100;
    public static final int BLAZE_SHOTS = 3;
    public static final int GHAST_WARN = 10;
    public static final int GHAST_FIRE = 20;
    public static final int GHAST_RELOAD = -40;
    private static final int LEVEL_EVENT_BLAZE_SHOOT = 1018;
    private static final int LEVEL_EVENT_GHAST_WARN = 1015;
    private static final int LEVEL_EVENT_GHAST_SHOOT = 1016;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public double maxRange(Mob mob) {
        return mob instanceof Ghast ? KindWindows.GHAST_MAX : KindWindows.BLAZE_MAX;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.halt(mob, memory);
        boolean sees = mob.getSensing().hasLineOfSight(drone.entity());
        if (mob instanceof Ghast ghast) {
            Moves.face(ghast, drone.center());
            ghast(ghast, drone, memory, sees);
        } else {
            Moves.look(mob, drone);
            blaze(mob, drone, memory, sees);
        }
    }

    private static void blaze(Mob mob, DroneState drone, ReactionMemory memory, boolean sees) {
        if (memory.cooldown > 0) {
            memory.cooldown--;
            return;
        }
        if (!sees) {
            memory.cooldown = 5;
            return;
        }
        memory.step++;
        if (memory.step == 1) {
            memory.cooldown = BLAZE_CHARGE;
        } else if (memory.step <= BLAZE_SHOTS + 1) {
            memory.cooldown = BLAZE_BURST;
            small(mob, drone);
            memory.shots++;
        } else {
            memory.cooldown = BLAZE_REST;
            memory.step = 0;
        }
    }

    private static void small(Mob mob, DroneState drone) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = new Vec3(mob.getX(), mob.getY(0.5) + 0.5, mob.getZ());
        Vec3 aim = Aiming.direction(from, drone, FIREBALL_SPEED, 0.0);
        Vec3 direction = new Vec3(aim.x + mob.getRandom().triangle(0.0, SPREAD), aim.y + mob.getRandom().triangle(0.0, SPREAD),
                aim.z + mob.getRandom().triangle(0.0, SPREAD)).normalize();
        SmallFireball fireball = new SmallFireball(level, mob, direction);
        fireball.setPos(fireball.getX(), from.y, fireball.getZ());
        level.addFreshEntity(fireball);
        if (!mob.isSilent()) {
            level.levelEvent(null, LEVEL_EVENT_BLAZE_SHOOT, mob.blockPosition(), 0);
        }
    }

    private static void ghast(Ghast ghast, DroneState drone, ReactionMemory memory, boolean sees) {
        if (sees) {
            memory.ticks++;
            if (memory.ticks == GHAST_WARN && !ghast.isSilent()) {
                ghast.level().levelEvent(null, LEVEL_EVENT_GHAST_WARN, ghast.blockPosition(), 0);
            }
            if (memory.ticks == GHAST_FIRE) {
                large(ghast, drone);
                memory.shots++;
                memory.ticks = GHAST_RELOAD;
            }
        } else if (memory.ticks > 0) {
            memory.ticks--;
        }
        ghast.setCharging(memory.ticks > GHAST_WARN);
    }

    private static void large(Ghast ghast, DroneState drone) {
        if (!(ghast.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 view = ghast.getViewVector(1.0F);
        Vec3 from = new Vec3(ghast.getX() + view.x * 4.0, ghast.getY(0.5) + 0.5, ghast.getZ() + view.z * 4.0);
        Vec3 direction = Aiming.direction(from, drone, FIREBALL_SPEED, 0.0);
        if (!ghast.isSilent()) {
            level.levelEvent(null, LEVEL_EVENT_GHAST_SHOOT, ghast.blockPosition(), 0);
        }
        LargeFireball fireball = new LargeFireball(level, ghast, direction, ghast.getExplosionPower());
        fireball.setPos(from.x, from.y, from.z);
        level.addFreshEntity(fireball);
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob instanceof Ghast ghast) {
            ghast.setCharging(false);
        }
        Moves.stop(mob, memory);
    }
}
