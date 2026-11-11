package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.mob.Access;
import de.lardtom.mobdefense.mob.Failures;
import de.lardtom.mobdefense.mob.Reaction;
import de.lardtom.mobdefense.mob.ReactionKinds;
import de.lardtom.mobdefense.mob.ReactionTags;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class MobChecks {
    public static final Map<String, String> SAMPLES = Map.of(
            "zombie", "chase", "hoglin", "brute", "skeleton", "archer", "ghast", "fireball", "creeper", "creeper", "phantom", "flyer");

    private MobChecks() {
    }

    public static String tags(MinecraftServer server) {
        Map<String, Integer> perKind = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        int ignored = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!"minecraft".equals(id.getNamespace()) || type.getCategory() != MobCategory.MONSTER) {
                continue;
            }
            if (BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(ReactionTags.IGNORES_DRONES)) {
                ignored++;
                continue;
            }
            Reaction reaction = ReactionTags.reaction(type);
            if (reaction != null) {
                perKind.merge(reaction.kind(), 1, Integer::sum);
            }
            String expected = SAMPLES.get(id.getPath());
            if (expected != null && (reaction == null || !expected.equals(reaction.kind()))) {
                problems.add(id.getPath() + " → " + (reaction == null ? "keine" : reaction.kind()) + " statt " + expected);
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Tags reacts/* am Server falsch: " + problems);
        }
        if (!perKind.keySet().containsAll(ReactionKinds.names())) {
            throw new IllegalStateException("Arten ohne Mob am Server: " + ReactionKinds.names() + " gegen " + perKind);
        }
        return "Arten " + perKind + ", " + ignored + " ausgenommen";
    }

    public static String wiring(MinecraftServer server) {
        if (!Access.MOB) {
            throw new IllegalStateException("MobAccessor nicht auf Mob angewandt");
        }
        if (!Access.PHANTOM) {
            throw new IllegalStateException("PhantomAccessor nicht auf Phantom angewandt");
        }
        if (!PropwashEvents.DRONE_KNOCKBACK.hasListeners() || !PropwashEvents.DRONE_DAMAGED.hasListeners()) {
            throw new IllegalStateException("Listener für DRONE_KNOCKBACK oder DRONE_DAMAGED fehlen");
        }
        if (!Failures.disabledTypes().isEmpty()) {
            throw new IllegalStateException("Reaktionen abgeschaltet: " + Failures.disabledTypes());
        }
        return "Accessoren auf Mob und Phantom angewandt, Listener DRONE_KNOCKBACK und DRONE_DAMAGED angemeldet";
    }
}
