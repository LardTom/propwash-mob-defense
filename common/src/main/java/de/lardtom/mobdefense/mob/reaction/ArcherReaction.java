package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.Aiming;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.KindWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class ArcherReaction extends ChaseReaction {
    public static final String KIND = "archer";
    public static final float ARROW_SPEED = 1.6F;
    public static final double DRAG_FACTOR = 0.96;
    public static final float SPREAD_FACTOR = 0.5F;
    public static final int DRAW_TICKS = 20;
    public static final int CHARGE_TICKS = 25;
    public static final int AIM_TICKS = 10;
    public static final int CROSSBOW_INTERVAL = 30;
    public static final int BOW_INTERVAL = 40;
    public static final int BOW_INTERVAL_HARD = 20;
    private static final int IDLE = 0;
    private static final int CHARGING = 1;
    private static final int AIMING = 2;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return !(mob instanceof AbstractPiglin) || mob.isHolding(Items.CROSSBOW);
    }

    @Override
    public double maxRange(Mob mob) {
        return ranged(mob) ? KindWindows.ARCHER_MAX : Double.MAX_VALUE;
    }

    @Override
    public boolean wants(Mob mob, DroneState drone, double distance) {
        return !ranged(mob) || KindWindows.archer(distance);
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        mob.setAggressive(true);
        memory.cooldown = DRAW_TICKS / 2;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        if (!ranged(mob)) {
            chase(mob, drone, memory);
            return;
        }
        Moves.halt(mob, memory);
        Moves.look(mob, drone);
        boolean sees = mob.getSensing().hasLineOfSight(drone.entity());
        if (mob.isHolding(Items.CROSSBOW)) {
            crossbow(mob, drone, memory, sees);
        } else {
            bow(mob, drone, memory, sees);
        }
    }

    private static void bow(Mob mob, DroneState drone, ReactionMemory memory, boolean sees) {
        if (!mob.isUsingItem()) {
            if (memory.cooldown > 0) {
                memory.cooldown--;
            } else if (sees) {
                mob.startUsingItem(ProjectileUtil.getWeaponHoldingHand(mob, Items.BOW));
            }
            return;
        }
        int used = mob.getTicksUsingItem();
        if (used >= DRAW_TICKS && sees) {
            mob.stopUsingItem();
            shoot(mob, drone, Items.BOW, BowItem.getPowerForTime(used));
            memory.shots++;
            memory.cooldown = mob.level().getDifficulty() == Difficulty.HARD ? BOW_INTERVAL_HARD : BOW_INTERVAL;
        }
    }

    private static void crossbow(Mob mob, DroneState drone, ReactionMemory memory, boolean sees) {
        switch (memory.step) {
            case IDLE -> {
                if (memory.cooldown > 0) {
                    memory.cooldown--;
                } else if (sees) {
                    charging(mob, true);
                    memory.step = CHARGING;
                    memory.ticks = 0;
                }
            }
            case CHARGING -> {
                if (++memory.ticks >= CHARGE_TICKS) {
                    charging(mob, false);
                    memory.step = AIMING;
                    memory.ticks = 0;
                }
            }
            default -> {
                if (++memory.ticks >= AIM_TICKS && sees) {
                    shoot(mob, drone, Items.CROSSBOW, 1.0F);
                    if (mob instanceof CrossbowAttackMob crossbowMob) {
                        crossbowMob.onCrossbowAttackPerformed();
                    }
                    memory.shots++;
                    memory.step = IDLE;
                    memory.cooldown = CROSSBOW_INTERVAL + mob.getRandom().nextInt(20);
                }
            }
        }
    }

    private static void shoot(Mob mob, DroneState drone, Item weaponItem, float power) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(mob, weaponItem);
        ItemStack weapon = mob.getItemInHand(hand);
        ItemStack ammo = mob.getProjectile(weapon);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, ammo, power, weapon);
        Vec3 direction = Aiming.direction(arrow.position(), drone, ARROW_SPEED * DRAG_FACTOR, arrow.getGravity());
        Projectile.spawnProjectileUsingShoot(arrow, level, ammo, direction.x, direction.y, direction.z, ARROW_SPEED,
                Aiming.uncertainty(mob) * SPREAD_FACTOR);
        float pitch = 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F);
        mob.playSound(weaponItem == Items.CROSSBOW ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.SKELETON_SHOOT, 1.0F, pitch);
    }

    private static void charging(Mob mob, boolean value) {
        if (mob instanceof CrossbowAttackMob crossbowMob) {
            crossbowMob.setChargingCrossbow(value);
        }
    }

    public static boolean ranged(Mob mob) {
        return mob.isHolding(Items.BOW) || mob.isHolding(Items.CROSSBOW);
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob.isUsingItem()) {
            mob.stopUsingItem();
        }
        charging(mob, false);
        super.stop(mob, memory);
    }
}
