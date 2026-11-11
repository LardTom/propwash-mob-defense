package de.lardtom.mobdefense.spear;

import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.net.Notices;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class SpearFeedback {
    public static final int NOTICE_TICKS = 30;
    public static final int CRIT_PARTICLES = 12;
    public static final double CRIT_SPREAD = 0.2;
    public static final double CRIT_SPEED = 0.4;
    public static final float INDICATOR_MIN_DAMAGE = 2.0F;
    public static final float VOLUME = 1.0F;
    public static final float PITCH = 1.0F;
    private static final AtomicReference<Shown> LAST = new AtomicReference<>();

    private SpearFeedback() {
    }

    public record Shown(int target, boolean sound, boolean pilotSound, int viewers, boolean pilotParticles, int indicators, boolean notice) {
    }

    public static Shown hit(ServerLevel level, Entity drone, @Nullable ServerPlayer pilot, LivingEntity target, KineticWeapon kinetic, float healthLost) {
        Vec3 at = new Vec3(target.getX(), target.getY(0.5), target.getZ());
        SoundSource source = drone.getSoundSource();
        boolean sound = false;
        boolean pilotSound = false;
        Optional<Holder<SoundEvent>> hitSound = kinetic.hitSound();
        if (hitSound.isPresent()) {
            long seed = level.getRandom().nextLong();
            level.playSeededSound(pilot, at.x, at.y, at.z, hitSound.get(), source, VOLUME, PITCH, seed);
            sound = true;
            if (pilot != null && pilot.connection != null) {
                pilot.connection.send(new ClientboundSoundPacket(hitSound.get(), source, at.x, at.y, at.z, VOLUME, PITCH, seed));
                pilotSound = true;
            }
        }
        int indicators = healthLost > INDICATOR_MIN_DAMAGE ? (int) (healthLost * 0.5F) : 0;
        int viewers = 0;
        boolean pilotParticles = false;
        for (ServerPlayer player : level.players()) {
            boolean own = player == pilot;
            boolean sent = level.sendParticles(player, ParticleTypes.CRIT, own, false, at.x, at.y, at.z, CRIT_PARTICLES, CRIT_SPREAD, CRIT_SPREAD, CRIT_SPREAD,
                    CRIT_SPEED);
            if (indicators > 0) {
                level.sendParticles(player, ParticleTypes.DAMAGE_INDICATOR, own, false, at.x, at.y, at.z, indicators, 0.1, 0.0, 0.1, 0.2);
            }
            if (sent) {
                viewers++;
                pilotParticles |= own;
            }
        }
        boolean notice = pilot != null && Notices.send(pilot, drone, Notice.HIT, NOTICE_TICKS);
        Shown shown = new Shown(target.getId(), sound, pilotSound, viewers, pilotParticles, indicators, notice);
        LAST.set(shown);
        return shown;
    }

    public static Optional<Shown> last() {
        return Optional.ofNullable(LAST.get());
    }

    public static void reset() {
        LAST.set(null);
    }
}
