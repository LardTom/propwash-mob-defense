package de.lardtom.mobdefense.mob.reaction;

import de.lardtom.mobdefense.mixin.ShulkerAccessor;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionMemory;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;

public final class BulletReaction implements Reaction {
    public static final String KIND = "bullet";
    public static final int FIRST_SHOT = 20;
    public static final int PEEK_OPEN = 100;
    public static final float LOOK_SPEED = 180.0F;

    @Override
    public String kind() {
        return KIND;
    }

    @Override
    public boolean accepts(Mob mob) {
        return mob instanceof Shulker;
    }

    @Override
    public double maxRange(Mob mob) {
        return StageTwoWindows.BULLET_MAX;
    }

    @Override
    public void start(Mob mob, DroneState drone, ReactionMemory memory) {
        memory.cooldown = FIRST_SHOT;
        peek(mob, PEEK_OPEN);
    }

    @Override
    public void tick(Mob mob, DroneState drone, ReactionMemory memory) {
        if (!(mob instanceof Shulker shulker)) {
            return;
        }
        Vec3 center = drone.center();
        shulker.getLookControl().setLookAt(center.x, center.y, center.z, LOOK_SPEED, LOOK_SPEED);
        if (--memory.cooldown > 0) {
            return;
        }
        if (!Support.sees(shulker, drone)) {
            memory.cooldown = 5;
            return;
        }
        memory.cooldown = 20 + shulker.getRandom().nextInt(10) * 20 / 2;
        ServerLevel level = Support.level(shulker);
        if (level == null) {
            return;
        }
        level.addFreshEntity(new ShulkerBullet(level, shulker, drone.entity(), shulker.getAttachFace().getAxis()));
        shulker.playSound(SoundEvents.SHULKER_SHOOT, 2.0F, (shulker.getRandom().nextFloat() - shulker.getRandom().nextFloat()) * 0.2F + 1.0F);
        memory.shots++;
    }

    public static boolean peeks(Mob mob) {
        return mob instanceof ShulkerAccessor;
    }

    private static void peek(Mob mob, int amount) {
        if (mob instanceof ShulkerAccessor accessor) {
            accessor.mobdefense$setRawPeekAmount(amount);
        }
    }

    @Override
    public void stop(Mob mob, ReactionMemory memory) {
        peek(mob, 0);
    }
}
