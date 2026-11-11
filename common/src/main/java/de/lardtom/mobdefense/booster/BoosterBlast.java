package de.lardtom.mobdefense.booster;

import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public final class BoosterBlast {
    private BoosterBlast() {
    }

    public static Boosters.Blast explode(ServerLevel level, Entity drone, Vec3 at, ItemStack rocket, int stars, @Nullable ServerPlayer attacker) {
        float damage = BoostProfile.blastDamage(stars);
        if (damage <= 0.0F) {
            return Boosters.Blast.NONE;
        }
        FireworkRocketEntity firework = new FireworkRocketEntity(level, attacker, at.x, at.y, at.z, rocket);
        firework.setDeltaMovement(drone.getDeltaMovement());
        boolean shown = level.addFreshEntity(firework);
        if (shown) {
            level.broadcastEntityEvent(firework, EntityEvent.FIREWORKS_EXPLODE);
        }
        level.gameEvent(GameEvent.EXPLODE, at, GameEvent.Context.of(attacker));
        int hurt = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BoostProfile.BLAST_RADIUS))) {
            double distance = at.distanceTo(target.position());
            if (distance > BoostProfile.BLAST_RADIUS || skip(level, attacker, target) || !sees(level, at, target, firework)) {
                continue;
            }
            float amount = BoostProfile.blastDamageAt(stars, distance);
            if (amount > 0.0F && target.hurtServer(level, level.damageSources().fireworks(firework, attacker), amount)) {
                hurt++;
            }
        }
        boolean droneHurt = !drone.isRemoved() && drone.hurtServer(level, level.damageSources().fireworks(firework, null), damage);
        if (shown) {
            firework.discard();
        }
        return new Boosters.Blast(true, damage, droneHurt, hurt);
    }

    public static boolean skip(ServerLevel level, @Nullable ServerPlayer attacker, LivingEntity target) {
        if (!target.isAlive() || target.isSpectator() || target.isRemoved()) {
            return true;
        }
        if (target instanceof Player player) {
            if (player == attacker || attacker == null || !MobDefenseRules.flag(level, RuleDefaults.PAYLOADS_HIT_PLAYERS)) {
                return true;
            }
            return !attacker.canHarmPlayer(player);
        }
        return false;
    }

    private static boolean sees(ServerLevel level, Vec3 at, LivingEntity target, Entity context) {
        for (int step = 0; step < 2; step++) {
            Vec3 to = new Vec3(target.getX(), target.getY(0.5 * step), target.getZ());
            if (level.clip(new ClipContext(at, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context)).getType() == HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }
}
