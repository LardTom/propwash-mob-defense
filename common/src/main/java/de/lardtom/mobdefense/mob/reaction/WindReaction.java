package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mob.Aiming;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Moves;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class WindReaction implements Reaction {
    public static final String KIND = "wind";
    public static final int CHARGE_TICKS = 15;
    public static final int RECOVER_TICKS = 4;
    public static final int COOLDOWN_TICKS = 10;
    public static final float SPEED = 0.7F;
    private static final int IDLE = 0;
    private static final int CHARGING = 1;
    private static final int RECOVERING = 2;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return mob instanceof Breeze;
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.WIND_MAX;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.step = IDLE;
        memory.cooldown = COOLDOWN_TICKS;
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        if (!(mob instanceof Breeze breeze)) {
            return;
        }
        Moves.halt(breeze, memory);
        Moves.look(breeze, drone);
        boolean sees = Support.sees(breeze, drone);
        switch (memory.step) {
            case CHARGING -> {
                if (++memory.ticks >= CHARGE_TICKS) {
                    if (sees) {
                        shoot(breeze, drone);
                        memory.shots++;
                    }
                    memory.step = RECOVERING;
                    memory.ticks = 0;
                }
            }
            case RECOVERING -> {
                if (++memory.ticks >= RECOVER_TICKS) {
                    standUp(breeze);
                    memory.step = IDLE;
                    memory.cooldown = COOLDOWN_TICKS;
                }
            }
            default -> {
                if (memory.cooldown > 0) {
                    memory.cooldown--;
                } else if (sees) {
                    breeze.setPose(Pose.SHOOTING);
                    breeze.playSound(SoundEvents.BREEZE_INHALE, 1.0F, 1.0F);
                    memory.step = CHARGING;
                    memory.ticks = 0;
                }
            }
        }
    }

    private static void shoot(Breeze breeze, DroneState drone) {
        ServerLevel level = Support.level(breeze);
        if (level == null) {
            return;
        }
        BreezeWindCharge charge = new BreezeWindCharge(breeze, level);
        Vec3 direction = Aiming.direction(charge.position(), drone, SPEED, 0.0);
        Projectile.spawnProjectileUsingShoot(charge, level, ItemStack.EMPTY, direction.x, direction.y, direction.z, SPEED,
                5 - level.getDifficulty().getId() * 4);
        breeze.playSound(SoundEvents.BREEZE_SHOOT, 1.5F, 1.0F);
    }

    private static void standUp(Breeze breeze) {
        if (breeze.getPose() == Pose.SHOOTING) {
            breeze.setPose(Pose.STANDING);
        }
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        if (mob instanceof Breeze breeze) {
            standUp(breeze);
        }
        memory.step = IDLE;
        Moves.stop(mob, memory);
    }
}
