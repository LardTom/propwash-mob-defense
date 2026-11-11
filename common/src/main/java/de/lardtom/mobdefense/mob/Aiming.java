package de.lardtom.mobdefense.mob;

import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.Vec3;

public final class Aiming {
    private Aiming() {
    }

    public static Vec3 direction(Vec3 from, DroneState drone, double speedPerTick, double gravityPerTick) {
        return PropwashApi.drones().aim(from, drone.entity(), speedPerTick, gravityPerTick)
                .map(aim -> new Vec3(aim.dx(), aim.dy(), aim.dz()))
                .orElseGet(() -> straight(from, drone.center()));
    }

    public static Vec3 straight(Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        return length < 1.0E-6 ? new Vec3(0.0, 1.0, 0.0) : delta.scale(1.0 / length);
    }

    public static float uncertainty(Mob mob) {
        if (mob instanceof RangedAttackMob ranged) {
            return ranged.rangedAttackUncertainty(mob.level());
        }
        return 14 - mob.level().getDifficulty().getId() * 4;
    }
}
