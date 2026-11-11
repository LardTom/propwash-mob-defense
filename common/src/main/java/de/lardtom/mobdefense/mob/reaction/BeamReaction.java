package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Knockbacks;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;

public final class BeamReaction implements Reaction {
    public static final String KIND = "beam";
    public static final int WARMUP = -10;
    public static final int PARTICLE_EVERY = 2;
    public static final double PARTICLE_STEP = 0.6;
    public static final float LOOK_SPEED = 90.0F;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return mob instanceof Guardian;
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.BEAM_MAX;
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        return StageTwoWindows.beam(distance, mob instanceof ElderGuardian);
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.ticks = WARMUP;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        if (!(mob instanceof Guardian guardian)) {
            return;
        }
        guardian.getNavigation().stop();
        Vec3 center = drone.center();
        guardian.getLookControl().setLookAt(center.x, center.y, center.z, LOOK_SPEED, LOOK_SPEED);
        if (!Support.sees(guardian, drone)) {
            memory.ticks = WARMUP;
            return;
        }
        memory.ticks++;
        ServerLevel level = Support.level(guardian);
        if (level == null) {
            return;
        }
        if (memory.ticks == 0 && !guardian.isSilent()) {
            level.playSound(null, guardian.getX(), guardian.getY(), guardian.getZ(), SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        int duration = guardian.getAttackDuration();
        if (memory.ticks >= 0 && memory.ticks % PARTICLE_EVERY == 0) {
            float progress = Math.min(1.0F, memory.ticks / (float) duration);
            Support.line(level, new DustParticleOptions(color(progress), 1.0F), guardian.getEyePosition(), center, PARTICLE_STEP);
        }
        if (memory.ticks >= duration) {
            fire(level, guardian, drone);
            memory.shots++;
            memory.ticks = WARMUP;
        }
    }

    private static void fire(ServerLevel level, Guardian guardian, DroneState drone) {
        float magic = 1.0F;
        if (level.getDifficulty() == Difficulty.HARD) {
            magic += 2.0F;
        }
        if (guardian instanceof ElderGuardian) {
            magic += 2.0F;
        }
        Support.hurt(level, drone.entity(), guardian.damageSources().indirectMagic(guardian, guardian), magic);
        Knockbacks.scoped(drone.entity(), () -> guardian.doHurtTarget(level, drone.entity()));
    }

    public static int color(float progress) {
        float scale = progress * progress;
        int red = 64 + (int) (scale * 191.0F);
        int green = 32 + (int) (scale * 191.0F);
        int blue = 128 - (int) (scale * 64.0F);
        return red << 16 | green << 8 | blue;
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        memory.ticks = WARMUP;
        Moves.stop(mob, memory);
    }
}
