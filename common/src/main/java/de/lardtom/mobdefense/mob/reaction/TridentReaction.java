package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.Aiming;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class TridentReaction implements Reaction {
    public static final String KIND = "trident";
    public static final String BASE = ChaseReaction.KIND;
    public static final int INTERVAL = 40;
    public static final int DRAW_TICKS = 20;
    public static final float SPEED = 1.6F;
    public static final double DRAG = 0.99;
    public static final float SPREAD_FACTOR = 0.5F;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return mob.getMainHandItem().is(Items.TRIDENT);
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.TRIDENT_MAX;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        mob.setAggressive(true);
        memory.cooldown = DRAW_TICKS;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        Moves.halt(mob, memory);
        Moves.look(mob, drone);
        if (!Support.sees(mob, drone)) {
            if (mob.isUsingItem()) {
                mob.stopUsingItem();
            }
            memory.cooldown = Math.max(memory.cooldown, DRAW_TICKS);
            return;
        }
        if (!mob.isUsingItem() && memory.cooldown <= DRAW_TICKS) {
            mob.startUsingItem(InteractionHand.MAIN_HAND);
        }
        if (memory.cooldown > 0) {
            memory.cooldown--;
            return;
        }
        throwAt(mob, drone);
        mob.stopUsingItem();
        memory.shots++;
        memory.cooldown = INTERVAL;
    }

    private static void throwAt(Mob mob, DroneState drone) {
        ServerLevel level = Support.level(mob);
        if (level == null) {
            return;
        }
        ItemStack held = mob.getMainHandItem();
        ItemStack trident = held.is(Items.TRIDENT) ? held : new ItemStack(Items.TRIDENT);
        ThrownTrident thrown = new ThrownTrident(level, mob, trident);
        Vec3 direction = Aiming.direction(thrown.position(), drone, SPEED * DRAG, thrown.getGravity());
        Projectile.spawnProjectileUsingShoot(thrown, level, trident, direction.x, direction.y, direction.z, SPEED,
                Aiming.uncertainty(mob) * SPREAD_FACTOR);
        mob.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
        }
        mob.setAggressive(false);
        Moves.stop(mob, memory);
    }
}
