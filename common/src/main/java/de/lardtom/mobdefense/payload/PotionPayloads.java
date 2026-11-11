package de.lardtom.mobdefense.payload;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PotionPayloads {
    public static final double SPLASH_REACH = 4.0;
    public static final double SPLASH_HEIGHT = 2.0;
    public static final double MARGIN = 1.0;

    private PotionPayloads() {
    }

    static boolean heals(PotionContents contents) {
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (instance.is(MobEffects.INSTANT_HEALTH)) {
                return true;
            }
        }
        return false;
    }

    public static void onHit(AbstractThrownPotion potion, ServerLevel level, ItemStack stack, HitResult hit, Runnable original) {
        if (!Payloads.is(potion)) {
            original.run();
            return;
        }
        PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        List<Holder<MobEffect>> harmful = EffectShield.harmful(contents);
        AABB area = potion.getBoundingBox().move(hit.getLocation().subtract(potion.position()))
                .inflate(SPLASH_REACH + MARGIN, SPLASH_HEIGHT + MARGIN, SPLASH_REACH + MARGIN);
        Set<AreaEffectCloud> before = new HashSet<>(level.getEntitiesOfClass(AreaEffectCloud.class, area));
        ServerPlayer owner = potion.getOwner() instanceof ServerPlayer player ? player : null;
        Map<Player, Float> health = new HashMap<>();
        if (owner != null && heals(contents)) {
            for (Player player : level.getEntitiesOfClass(Player.class, area, player -> player != owner && player.isAlive())) {
                health.put(player, player.getHealth());
            }
        }
        EffectShield.around(level, area, harmful, original);
        if (owner != null && health.entrySet().stream().anyMatch(entry -> entry.getKey().getHealth() > entry.getValue())) {
            PayloadAwards.award(owner, PayloadAwards.AIR_SUPPORT);
        }
        for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, area)) {
            if (!before.contains(cloud) && cloud.tickCount == 0) {
                Payloads.mark(cloud);
                EffectShield.watch(cloud, harmful);
            }
        }
    }
}
