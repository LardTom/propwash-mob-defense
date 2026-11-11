package de.lardtom.mobdefense.payload;

import de.lardtom.mobdefense.MobDefense;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import org.jspecify.annotations.Nullable;

public final class Payloads {
    public static final String TAG = "mobdefense.payload";
    private static boolean initialized;

    private Payloads() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MobDefense.onServerTick(Payloads::tick);
        OwnShotGuard.register();
    }

    public static void launched(Entity entity, Entity drone) {
        if (!(entity instanceof Projectile) && !(entity instanceof PrimedTnt)) {
            return;
        }
        mark(entity);
        if (entity instanceof Projectile projectile) {
            OwnShotGuard.fired(projectile, drone);
        }
        if (entity instanceof SmallFireball fireball) {
            FireballGuard.track(fireball);
        }
        if (entity instanceof WindCharge wind && wind.getOwner() instanceof ServerPlayer owner) {
            PayloadAwards.award(owner, PayloadAwards.RECOIL);
        }
    }

    static void mark(Entity entity) {
        entity.addTag(TAG);
    }

    public static boolean is(@Nullable Entity entity) {
        return entity != null && (entity instanceof Projectile || entity instanceof PrimedTnt || entity instanceof AreaEffectCloud)
                && entity.entityTags().contains(TAG);
    }

    private static void tick(MinecraftServer server) {
        FireballGuard.tick(server);
        EffectShield.tick(server);
    }
}
