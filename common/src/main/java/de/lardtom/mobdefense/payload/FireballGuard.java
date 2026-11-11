package de.lardtom.mobdefense.payload;

import de.lardtom.mobdefense.dispenser.DispenserRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public final class FireballGuard {
    public static final double REACH_SCALE = 1.25;
    public static final double REACH_EXTRA = 0.5;

    private static final Set<SmallFireball> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static int quenched;

    private FireballGuard() {
    }

    static void track(SmallFireball fireball) {
        synchronized (TRACKED) {
            TRACKED.add(fireball);
        }
        check(fireball);
    }

    public static int quenched() {
        return quenched;
    }

    static void tick(MinecraftServer server) {
        List<SmallFireball> fireballs;
        synchronized (TRACKED) {
            if (TRACKED.isEmpty()) {
                return;
            }
            TRACKED.removeIf(Entity::isRemoved);
            fireballs = new ArrayList<>(TRACKED);
        }
        for (SmallFireball fireball : fireballs) {
            check(fireball);
        }
    }

    private static void check(SmallFireball fireball) {
        if (fireball.isRemoved() || !(fireball.level() instanceof ServerLevel level) || DispenserRules.droneGriefing(level)) {
            return;
        }
        Vec3 from = fireball.position();
        Vec3 delta = fireball.getDeltaMovement();
        double length = delta.length();
        if (length < 1.0E-6) {
            return;
        }
        Vec3 to = from.add(delta.scale((length * REACH_SCALE + REACH_EXTRA) / length));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, fireball));
        if (block.getType() == HitResult.Type.MISS) {
            return;
        }
        double blockDistance = block.getLocation().distanceToSqr(from);
        AABB swept = fireball.getBoundingBox().expandTowards(block.getLocation().subtract(from)).inflate(1.0);
        for (Entity entity : level.getEntities(fireball, swept, candidate -> candidate instanceof LivingEntity && candidate.isAlive()
                && !candidate.isSpectator() && candidate != fireball.getOwner())) {
            if (entity.getBoundingBox().inflate(0.3).clip(from, block.getLocation()).filter(at -> at.distanceToSqr(from) < blockDistance).isPresent()) {
                return;
            }
        }
        BlockPos at = block.getBlockPos().relative(block.getDirection());
        level.levelEvent(LevelEvent.SOUND_EXTINGUISH_FIRE, at, 0);
        fireball.discard();
        quenched++;
    }
}
