package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.MobDefense;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class Failures {
    private static final Set<EntityType<?>> DISABLED = ConcurrentHashMap.newKeySet();

    private Failures() {
    }

    public static boolean disabled(EntityType<?> type) {
        return !DISABLED.isEmpty() && DISABLED.contains(type);
    }

    public static void disable(Mob mob, String kind, Throwable error) {
        EntityType<?> type = mob.getType();
        if (DISABLED.add(type)) {
            MobDefense.LOG.warn("{}: Reaktion {} für {} abgeschaltet, Fehler im Zugriff auf Vanilla: {}", MobDefense.MOD_NAME, kind,
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), error.toString(), error);
        }
    }

    public static void disableQuietly(Mob mob) {
        DISABLED.add(mob.getType());
    }

    public static List<String> disabledTypes() {
        return DISABLED.stream().map(type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).sorted().toList();
    }

    public static void clear() {
        DISABLED.clear();
    }
}
