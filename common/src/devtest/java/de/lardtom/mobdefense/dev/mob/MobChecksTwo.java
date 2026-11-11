package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionKinds;
import de.lardtom.mobdefense.mob.ReactionTags;
import de.lardtom.mobdefense.mob.reaction.BulletReaction;
import de.lardtom.mobdefense.mob.reaction.FreezeHook;
import de.lardtom.mobdefense.mob.reaction.JumperReaction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.creaking.Creaking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MobChecksTwo {
    public static final Map<String, String> SAMPLES = Map.of(
            "enderman", "stare", "creaking", "stare", "guardian", "beam", "evoker", "caster", "wither", "boss", "silverfish", "ground",
            "breeze", "wind", "shulker", "bullet", "witch", "potion", "spider", "jumper");

    private MobChecksTwo() {
    }

    public static String tags(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> sample : SAMPLES.entrySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(sample.getKey()));
            Reaction reaction = ReactionTags.reaction(type);
            if (reaction == null || !sample.getValue().equals(reaction.kind())) {
                problems.add(sample.getKey() + " → " + (reaction == null ? "keine" : reaction.kind()) + " statt " + sample.getValue());
            }
        }
        if (!BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityTypes.WARDEN).is(ReactionTags.IGNORES_DRONES)) {
            problems.add("warden fehlt in ignores_drones");
        }
        if (!ReactionKinds.variants().getOrDefault("chase", List.of()).contains("trident")) {
            problems.add("Variante trident fehlt: " + ReactionKinds.variants());
        }
        Reaction fallback = ReactionKinds.fallback();
        if (fallback == null || !"ground".equals(fallback.kind())) {
            problems.add("Auffang-Art ist " + (fallback == null ? "keine" : fallback.kind()) + " statt ground");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("übrige Arten am Server falsch: " + problems);
        }
        return SAMPLES.size() + " Stichproben, warden ausgenommen, trident als Variante von chase, Auffang-Art ground";
    }

    public static String hooks(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        if (!FreezeHook.class.isAssignableFrom(Creaking.class)) {
            problems.add("CreakingMixin nicht angewandt");
        }
        ServerLevel level = server.overworld();
        Mob shulker = EntityTypes.SHULKER.create(level, EntitySpawnReason.COMMAND);
        if (!(shulker instanceof Shulker) || !BulletReaction.peeks(shulker)) {
            problems.add("ShulkerAccessor nicht angewandt");
        }
        Mob slime = EntityTypes.SLIME.create(level, EntitySpawnReason.COMMAND);
        if (slime == null || !JumperReaction.hops(slime)) {
            problems.add("CubeMobMoveControlAccessor nicht angewandt");
        }
        if (shulker != null) {
            shulker.discard();
        }
        if (slime != null) {
            slime.discard();
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join(", ", problems));
        }
        return "CreakingMixin, ShulkerAccessor und CubeMobMoveControlAccessor angewandt";
    }
}
