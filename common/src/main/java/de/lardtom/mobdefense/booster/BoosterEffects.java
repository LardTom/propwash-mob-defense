package de.lardtom.mobdefense.booster;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

final class BoosterEffects {
    static final float LAUNCH_VOLUME = 3.0F;
    static final double SPARK_SPEED = 0.15;
    static final double SMOKE_SPEED = 0.05;

    private BoosterEffects() {
    }

    static void launch(ServerLevel level, Entity drone) {
        level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, LAUNCH_VOLUME, 1.0F);
    }

    static void sparks(ServerLevel level, Vec3 tail, Vec3 forward) {
        Vec3 back = forward.lengthSqr() > 1.0E-12 ? forward.normalize().scale(-1.0) : Vec3.ZERO;
        level.sendParticles(ParticleTypes.FIREWORK, tail.x, tail.y, tail.z, 0, back.x, back.y, back.z, SPARK_SPEED);
        level.sendParticles(ParticleTypes.SMOKE, tail.x, tail.y, tail.z, 0, back.x, back.y, back.z, SMOKE_SPEED);
    }

    static void burnOut(ServerLevel level, Vec3 tail) {
        level.sendParticles(ParticleTypes.POOF, tail.x, tail.y, tail.z, 3, 0.03, 0.03, 0.03, 0.01);
    }

    static void extinguish(ServerLevel level, Entity drone) {
        level.sendParticles(ParticleTypes.SMOKE, drone.getX(), drone.getY(), drone.getZ(), 4, 0.05, 0.05, 0.05, 0.01);
        level.playSound(null, drone.getX(), drone.getY(), drone.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.AMBIENT, 0.6F, 1.4F);
    }
}
