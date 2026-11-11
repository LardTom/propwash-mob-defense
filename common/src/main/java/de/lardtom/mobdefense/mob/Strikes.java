package de.lardtom.mobdefense.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Strikes {
    public static final double REACH = 0.83;
    public static final double ROAR_PUSH = 0.5;
    public static final double ROAR_LIFT = 0.15;
    public static final double THROW_MIN_LIFT = 0.25;

    private Strikes() {
    }

    public static boolean inReach(Mob mob, DroneState drone, double above) {
        AABB attack = mob.getBoundingBox().inflate(REACH, 0.0, REACH).expandTowards(0.0, above, 0.0);
        return attack.intersects(drone.box());
    }

    public static boolean touches(Mob mob, DroneState drone, double grow) {
        return mob.getBoundingBox().inflate(grow).intersects(drone.box());
    }

    public static boolean melee(Mob mob, Entity drone) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        mob.swingForAttack(InteractionHand.MAIN_HAND);
        return Knockbacks.scoped(drone, () -> mob.doHurtTarget(level, drone));
    }

    public static boolean bite(Mob mob, Entity drone, SoundEvent sound) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        level.broadcastEntityEvent(mob, (byte) 4);
        mob.playSound(sound, 1.0F, 1.0F);
        float attack = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1.0F;
        float damage = !mob.isBaby() && (int) attack > 0 ? attack / 2.0F + mob.getRandom().nextInt((int) attack) : attack;
        boolean hurt = Knockbacks.scoped(drone, () -> drone.hurtServer(level, mob.damageSources().mobAttack(mob), damage));
        if (hurt && !mob.isBaby()) {
            throwUp(mob, drone);
        }
        return hurt;
    }

    public static void throwUp(Mob mob, Entity drone) {
        double power = mob.getAttributes().hasAttribute(Attributes.ATTACK_KNOCKBACK) ? mob.getAttributeValue(Attributes.ATTACK_KNOCKBACK) : 1.0;
        power = Math.max(0.6, power);
        Vec3 away = horizontal(drone.position().subtract(mob.position()));
        double side = power * (mob.getRandom().nextFloat() * 0.5F + 0.2F);
        double lift = Math.max(THROW_MIN_LIFT, power * (0.5 + mob.getRandom().nextFloat() * 0.5) * 0.5);
        Knockbacks.push(drone, new Vec3(away.x * side, lift, away.z * side));
    }

    public static void roar(Mob mob, Entity drone) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 away = horizontal(drone.position().subtract(mob.position()));
        mob.playSound(SoundEvents.RAVAGER_ROAR, 1.0F, 1.0F);
        Vec3 center = mob.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.POOF, center.x, center.y, center.z, 40, 0.6, 0.6, 0.6, 0.2);
        Knockbacks.push(drone, new Vec3(away.x * ROAR_PUSH, ROAR_LIFT, away.z * ROAR_PUSH));
    }

    private static Vec3 horizontal(Vec3 delta) {
        Vec3 flat = new Vec3(delta.x, 0.0, delta.z);
        double length = flat.length();
        return length < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.scale(1.0 / length);
    }
}
