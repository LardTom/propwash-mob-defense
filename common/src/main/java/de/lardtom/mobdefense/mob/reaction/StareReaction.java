package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.Gaze;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.CameraPose;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public final class StareReaction extends ChaseReaction {
    public static final String KIND = "stare";
    public static final int TELEPORT_INTERVAL = 30;
    public static final int TELEPORT_TRIES = 8;
    public static final int FREEZE_TICKS = 2;
    public static final double TELEPORT_RADIUS = 1.5;
    private static final Map<Mob, Provoked> PROVOKED = new WeakHashMap<>();
    private static final Map<Mob, Long> FROZEN = new WeakHashMap<>();
    private static long provocations;
    private static long freezes;
    private static long teleports;
    private static long pilotAttacks;

    private record Provoked(Entity drone, long until) {
    }

    public record Counts(long provocations, long freezes, long teleports, long pilotAttacks) {
    }

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        if (!(mob instanceof Enderman enderman)) {
            return true;
        }
        long now = mob.level().getGameTime();
        if (enderStared(enderman, drone)) {
            provoke(enderman, drone, now);
        }
        Provoked provoked = PROVOKED.get(enderman);
        return provoked != null && provoked.drone() == drone.entity() && provoked.until() >= now;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        super.start(mob, drone, memory);
        if (mob instanceof Enderman enderman) {
            enderman.setBeingStaredAt();
            enderman.playStareSound();
        }
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        if (mob instanceof Enderman enderman) {
            enderman(enderman, drone, memory);
        } else {
            creaking(mob, drone, memory);
        }
    }

    private void enderman(Enderman enderman, DroneState drone, ReactionMemory memory) {
        if (!StageTwoWindows.stareLow(drone.height())) {
            ServerPlayer pilot = pilot(enderman, drone);
            if (pilot != null) {
                enderman.setTarget(pilot);
                enderman.setPersistentAngerTarget(EntityReference.of(pilot));
                enderman.startPersistentAngerTimer();
                PROVOKED.remove(enderman);
                countPilot();
                return;
            }
        }
        if (memory.special > 0) {
            memory.special--;
        } else if (StageTwoWindows.stareTeleport(Support.flat(enderman, drone.center()))) {
            memory.special = TELEPORT_INTERVAL;
            if (teleportNear(enderman, drone)) {
                memory.shots++;
                countTeleport();
            }
        }
        chase(enderman, drone, memory);
    }

    private void creaking(Mob mob, DroneState drone, ReactionMemory memory) {
        if (creakingStared(mob, drone)) {
            if (!frozen(mob)) {
                countFreeze();
            }
            FROZEN.put(mob, mob.level().getGameTime() + FREEZE_TICKS);
            Moves.halt(mob, memory);
            return;
        }
        chase(mob, drone, memory);
    }

    @Override
    protected void strike(Mob mob, DroneState drone, ReactionMemory memory) {
        if (!(mob instanceof Creaking creaking)) {
            super.strike(mob, drone, memory);
            return;
        }
        ServerLevel level = Support.level(mob);
        if (level == null || !creaking.canMove()) {
            return;
        }
        level.broadcastEntityEvent(creaking, (byte) 4);
        creaking.playAttackSound();
        float damage = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : 1.0F;
        Support.hurt(level, drone.entity(), mob.damageSources().mobAttack(mob), damage);
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        FROZEN.remove(mob);
        super.stop(mob, memory);
    }

    public static boolean frozen(Mob mob) {
        Long until = FROZEN.get(mob);
        return until != null && until >= mob.level().getGameTime();
    }

    public static boolean provoked(Mob mob) {
        Provoked provoked = PROVOKED.get(mob);
        return provoked != null && provoked.until() >= mob.level().getGameTime();
    }

    public static boolean enderStared(Enderman enderman, DroneState drone) {
        Optional<CameraPose> pose = PropwashApi.drones().cameraPose(drone.entity());
        if (pose.isEmpty()) {
            return false;
        }
        Vec3 eye = new Vec3(enderman.getX(), enderman.getEyeY(), enderman.getZ());
        return looks(pose.get(), eye, Gaze.ENDERMAN_CONE, true) && Support.clear(enderman.level(), pose.get().eye(), eye, drone.entity(),
                ClipContext.Block.COLLIDER);
    }

    public static boolean creakingStared(Mob mob, DroneState drone) {
        Optional<CameraPose> pose = PropwashApi.drones().cameraPose(drone.entity());
        if (pose.isEmpty()) {
            return false;
        }
        double[] heights = {mob.getEyeY(), mob.getY() + 0.5 * mob.getScale(), (mob.getEyeY() + mob.getY()) / 2.0};
        for (double height : heights) {
            Vec3 point = new Vec3(mob.getX(), height, mob.getZ());
            if (looks(pose.get(), point, Gaze.CREAKING_CONE, false) && Support.clear(mob.level(), pose.get().eye(), point, drone.entity(),
                    ClipContext.Block.VISUAL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looks(CameraPose pose, Vec3 point, double cone, boolean scale) {
        return Gaze.inCone(pose.eye().x, pose.eye().y, pose.eye().z, pose.look().x, pose.look().y, pose.look().z, point.x, point.y, point.z, cone, scale,
                pose.verticalFovDegrees());
    }

    private static void provoke(Enderman enderman, DroneState drone, long now) {
        Provoked current = PROVOKED.get(enderman);
        if (current == null || current.drone() != drone.entity() || current.until() < now) {
            countProvocation();
        }
        PROVOKED.put(enderman, new Provoked(drone.entity(), now + StageTwoWindows.angerTicks(enderman.getRandom().nextDouble())));
    }

    private static @Nullable ServerPlayer pilot(Enderman enderman, DroneState drone) {
        Optional<UUID> id = drone.view().pilot();
        if (id.isEmpty() || !(enderman.level() instanceof ServerLevel level)) {
            return null;
        }
        LivingEntity player = level.getPlayerByUUID(id.get());
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level() == level && enderman.canAttack(serverPlayer)
                && !serverPlayer.isSpectator() && !serverPlayer.isCreative()) {
            return serverPlayer;
        }
        return null;
    }

    private static boolean teleportNear(Enderman enderman, DroneState drone) {
        Vec3 ground = drone.ground();
        for (int i = 0; i < TELEPORT_TRIES; i++) {
            double angle = enderman.getRandom().nextDouble() * Math.PI * 2.0;
            double x = ground.x + Math.cos(angle) * TELEPORT_RADIUS;
            double z = ground.z + Math.sin(angle) * TELEPORT_RADIUS;
            if (enderman.teleport(x, ground.y + 1.0, z)) {
                return true;
            }
        }
        return false;
    }

    private static synchronized void countProvocation() {
        provocations++;
    }

    private static synchronized void countFreeze() {
        freezes++;
    }

    private static synchronized void countTeleport() {
        teleports++;
    }

    private static synchronized void countPilot() {
        pilotAttacks++;
    }

    public static synchronized Counts counts() {
        return new Counts(provocations, freezes, teleports, pilotAttacks);
    }
}
