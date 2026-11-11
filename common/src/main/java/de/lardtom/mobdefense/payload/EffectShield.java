package de.lardtom.mobdefense.payload;

import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class EffectShield {
    public static final int CLOUD_PERIOD = 5;
    public static final double CLOUD_MARGIN = 1.0;

    private static final Map<AreaEffectCloud, Watch> CLOUDS = new WeakHashMap<>();
    private static int restored;

    private EffectShield() {
    }

    private static final class Watch {
        private final List<Holder<MobEffect>> harmful;
        private Map<Player, Map<Holder<MobEffect>, MobEffectInstance>> snapshot = Map.of();

        private Watch(List<Holder<MobEffect>> harmful) {
            this.harmful = List.copyOf(harmful);
        }
    }

    public static List<Holder<MobEffect>> harmful(PotionContents contents) {
        Set<Holder<MobEffect>> out = new LinkedHashSet<>();
        for (MobEffectInstance instance : contents.getAllEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            if (effect.value().getCategory() == MobEffectCategory.HARMFUL && !effect.value().isInstantaneous()) {
                out.add(effect);
            }
        }
        return List.copyOf(out);
    }

    public static void around(ServerLevel level, AABB area, List<Holder<MobEffect>> harmful, Runnable action) {
        if (harmful.isEmpty() || !PayloadShield.shields(level)) {
            action.run();
            return;
        }
        Map<Player, Map<Holder<MobEffect>, MobEffectInstance>> before = snapshot(level, area, harmful);
        try {
            action.run();
        } finally {
            restore(before, harmful, 0);
        }
    }

    static void watch(AreaEffectCloud cloud, List<Holder<MobEffect>> harmful) {
        if (harmful.isEmpty()) {
            return;
        }
        synchronized (CLOUDS) {
            CLOUDS.put(cloud, new Watch(harmful));
        }
    }

    public static int watched() {
        synchronized (CLOUDS) {
            return CLOUDS.size();
        }
    }

    public static int restored() {
        return restored;
    }

    static void tick(MinecraftServer server) {
        List<Map.Entry<AreaEffectCloud, Watch>> entries;
        synchronized (CLOUDS) {
            if (CLOUDS.isEmpty()) {
                return;
            }
            Iterator<AreaEffectCloud> it = CLOUDS.keySet().iterator();
            while (it.hasNext()) {
                if (it.next().isRemoved()) {
                    it.remove();
                }
            }
            entries = new ArrayList<>(CLOUDS.entrySet());
        }
        for (Map.Entry<AreaEffectCloud, Watch> entry : entries) {
            AreaEffectCloud cloud = entry.getKey();
            Watch watch = entry.getValue();
            if (!(cloud.level() instanceof ServerLevel level)) {
                continue;
            }
            int phase = Math.floorMod(cloud.tickCount, CLOUD_PERIOD);
            if (phase == 0 && !watch.snapshot.isEmpty()) {
                restore(watch.snapshot, watch.harmful, 1);
                watch.snapshot = Map.of();
            }
            if (phase == CLOUD_PERIOD - 1) {
                watch.snapshot = PayloadShield.shields(level) ? snapshot(level, cloud.getBoundingBox().inflate(CLOUD_MARGIN), watch.harmful) : Map.of();
            }
        }
    }

    private static Map<Player, Map<Holder<MobEffect>, MobEffectInstance>> snapshot(ServerLevel level, AABB area, List<Holder<MobEffect>> harmful) {
        Map<Player, Map<Holder<MobEffect>, MobEffectInstance>> out = new LinkedHashMap<>();
        for (Player player : level.getEntitiesOfClass(Player.class, area, player -> player.isAlive() && !player.isSpectator())) {
            Map<Holder<MobEffect>, MobEffectInstance> effects = new HashMap<>();
            for (Holder<MobEffect> effect : harmful) {
                MobEffectInstance active = player.getEffect(effect);
                effects.put(effect, active == null ? null : new MobEffectInstance(active));
            }
            out.put(player, effects);
        }
        return out;
    }

    private static void restore(Map<Player, Map<Holder<MobEffect>, MobEffectInstance>> before, List<Holder<MobEffect>> harmful, int elapsedTicks) {
        for (Map.Entry<Player, Map<Holder<MobEffect>, MobEffectInstance>> entry : before.entrySet()) {
            Player player = entry.getKey();
            if (player.isRemoved()) {
                continue;
            }
            for (Holder<MobEffect> effect : harmful) {
                MobEffectInstance was = entry.getValue().get(effect);
                MobEffectInstance now = player.getEffect(effect);
                if (same(was, now, elapsedTicks)) {
                    continue;
                }
                if (was == null) {
                    player.removeEffect(effect);
                } else {
                    MobEffectInstance back = new MobEffectInstance(was.getEffect(), was.isInfiniteDuration() ? MobEffectInstance.INFINITE_DURATION
                            : Math.max(1, was.getDuration() - elapsedTicks), was.getAmplifier(), was.isAmbient(), was.isVisible(), was.showIcon());
                    player.forceAddEffect(back, null);
                }
                restored++;
            }
        }
    }

    static boolean same(@Nullable MobEffectInstance was, @Nullable MobEffectInstance now, int elapsedTicks) {
        if (was == null || now == null) {
            return was == now;
        }
        if (was.getAmplifier() != now.getAmplifier() || was.isAmbient() != now.isAmbient() || was.isVisible() != now.isVisible()) {
            return false;
        }
        if (was.isInfiniteDuration() || now.isInfiniteDuration()) {
            return was.isInfiniteDuration() == now.isInfiniteDuration();
        }
        int drop = was.getDuration() - now.getDuration();
        return drop >= 0 && drop <= elapsedTicks;
    }
}
