package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.MobDefense;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jspecify.annotations.Nullable;

public final class ReactionTags {
    public static final String REACTS = "reacts/";
    public static final TagKey<EntityType<?>> IGNORES_DRONES = TagKey.create(Registries.ENTITY_TYPE, MobDefense.id("ignores_drones"));

    private ReactionTags() {
    }

    public static TagKey<EntityType<?>> reacts(String kind) {
        return TagKey.create(Registries.ENTITY_TYPE, MobDefense.id(REACTS + kind));
    }

    public static @Nullable Reaction reaction(Mob mob) {
        Holder<EntityType<?>> holder = mob.typeHolder();
        if (holder.is(IGNORES_DRONES)) {
            return null;
        }
        Reaction base = reaction(holder);
        if (base == null) {
            return mob instanceof Enemy ? ReactionKinds.fallback() : null;
        }
        return ReactionKinds.refine(base, mob);
    }

    public static @Nullable Reaction reaction(EntityType<?> type) {
        return reaction(BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type));
    }

    public static @Nullable Reaction reaction(Holder<EntityType<?>> holder) {
        if (holder.is(IGNORES_DRONES)) {
            return null;
        }
        for (ReactionKinds.Entry entry : ReactionKinds.entries()) {
            if (holder.is(entry.tag())) {
                return entry.reaction();
            }
        }
        return null;
    }
}
