package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mixin.CubeMobMoveControlAccessor;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Knockbacks;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.Strikes;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.phys.Vec3;

public final class JumperReaction implements Reaction {
    public static final String KIND = "jumper";
    public static final double SPEED = 1.0;
    public static final double LEAP_RANGE = 4.0;
    public static final double CUBE_LEAP_RANGE = 2.5;
    public static final double LEAP_FORWARD = 0.4;
    public static final double CLIMB_CLOSE = 1.0;
    public static final int LEAP_COOLDOWN = 30;
    public static final int STRIKE_COOLDOWN = 20;
    public static final double TOUCH = 0.2;
    public static final double BUMP = 0.3;
    public static final float MAGMA_BONUS = 2.0F;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return !(mob instanceof AbstractCubeMob cube) || !cube.isTiny() || !(mob instanceof Enemy);
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        boolean climber = mob instanceof Spider;
        return StageTwoWindows.jumper(drone.height(), climber && Support.atWall(mob.level(), drone), climber);
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        mob.setAggressive(true);
        memory.special = LEAP_COOLDOWN / 2;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.look(mob, drone);
        double flat = Support.flat(mob, drone.center());
        if (mob instanceof AbstractCubeMob) {
            hop(mob, drone);
        } else if (flat > CLIMB_CLOSE) {
            Moves.walk(mob, drone.ground(), SPEED, memory);
        } else {
            mob.getMoveControl().setWantedPosition(drone.center().x, mob.getY(), drone.center().z, SPEED);
        }
        if (memory.special > 0) {
            memory.special--;
        } else if (mob.onGround() && flat <= (mob instanceof AbstractCubeMob ? CUBE_LEAP_RANGE : LEAP_RANGE)
                && drone.bottom() > mob.getY() + mob.getBbHeight() * 0.5 && Support.sees(mob, drone)) {
            leap(mob, drone);
            memory.special = LEAP_COOLDOWN;
        }
        if (memory.cooldown > 0) {
            memory.cooldown--;
        } else if (Strikes.touches(mob, drone, TOUCH)) {
            strike(mob, drone);
            memory.shots++;
            memory.cooldown = STRIKE_COOLDOWN;
        }
    }

    private static void hop(Mob mob, DroneState drone) {
        if (mob.getMoveControl() instanceof CubeMobMoveControlAccessor control) {
            control.mobdefense$setDirection(Support.yaw(mob, drone.center()), true);
            control.mobdefense$setWantedMovement(SPEED);
        }
    }

    private static void leap(Mob mob, DroneState drone) {
        double rise = drone.bottom() - mob.getY();
        Vec3 toward = Support.toward(mob, drone.center());
        double forward = Math.min(LEAP_FORWARD, Support.flat(mob, drone.center()) * 0.25);
        Vec3 motion = mob.getDeltaMovement();
        mob.setDeltaMovement(toward.x * forward + motion.x * 0.2, StageTwoWindows.leapSpeed(rise), toward.z * forward + motion.z * 0.2);
        mob.needsSync = true;
        if (mob instanceof AbstractCubeMob) {
            mob.playSound(mob instanceof MagmaCube ? SoundEvents.MAGMA_CUBE_JUMP : SoundEvents.SLIME_JUMP, 1.0F, 1.0F);
        }
    }

    private static void strike(Mob mob, DroneState drone) {
        if (!(mob instanceof AbstractCubeMob)) {
            Strikes.melee(mob, drone.entity());
            return;
        }
        ServerLevel level = Support.level(mob);
        if (level == null) {
            return;
        }
        if (!(mob instanceof Enemy) || !mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) {
            Vec3 away = Support.toward(mob, drone.center());
            Knockbacks.push(drone.entity(), new Vec3(away.x * BUMP, BUMP, away.z * BUMP));
            return;
        }
        float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) + (mob instanceof MagmaCube ? MAGMA_BONUS : 0.0F);
        if (Support.hurt(level, drone.entity(), mob.damageSources().mobAttack(mob), damage)) {
            mob.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (mob.getRandom().nextFloat() - mob.getRandom().nextFloat()) * 0.2F + 1.0F);
        }
    }

    public static boolean hops(Mob mob) {
        return !(mob instanceof AbstractCubeMob) || mob.getMoveControl() instanceof CubeMobMoveControlAccessor;
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        mob.setAggressive(false);
        Moves.stop(mob, memory);
    }
}
