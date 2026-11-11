package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.mob.reaction.ArcherReaction;
import de.lardtom.mobdefense.mob.reaction.BeamReaction;
import de.lardtom.mobdefense.mob.reaction.BossReaction;
import de.lardtom.mobdefense.mob.reaction.BruteReaction;
import de.lardtom.mobdefense.mob.reaction.BulletReaction;
import de.lardtom.mobdefense.mob.reaction.CasterReaction;
import de.lardtom.mobdefense.mob.reaction.ChaseReaction;
import de.lardtom.mobdefense.mob.reaction.CreeperReaction;
import de.lardtom.mobdefense.mob.reaction.FireballReaction;
import de.lardtom.mobdefense.mob.reaction.FlyerReaction;
import de.lardtom.mobdefense.mob.reaction.GroundReaction;
import de.lardtom.mobdefense.mob.reaction.JumperReaction;
import de.lardtom.mobdefense.mob.reaction.PotionReaction;
import de.lardtom.mobdefense.mob.reaction.StareReaction;
import de.lardtom.mobdefense.mob.reaction.TridentReaction;
import de.lardtom.mobdefense.mob.reaction.WindReaction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ReactionKinds {
    private static final Pattern KIND = Pattern.compile("[a-z][a-z_]{0,31}");
    private static final Map<String, Entry> KINDS = new LinkedHashMap<>();
    private static volatile List<Entry> snapshot = List.of();
    private static final Map<String, List<Reaction>> VARIANTS = new LinkedHashMap<>();
    private static volatile Map<String, List<Reaction>> variantSnapshot = Map.of();
    private static volatile @Nullable Reaction fallback;

    public record Entry(Reaction reaction, TagKey<EntityType<?>> tag) {
    }

    static {
        register(new ChaseReaction());
        register(new BruteReaction());
        register(new ArcherReaction());
        register(new FireballReaction());
        register(new CreeperReaction());
        register(new FlyerReaction());
        register(new StareReaction());
        register(new BeamReaction());
        register(new CasterReaction());
        register(new BossReaction());
        register(new GroundReaction());
        register(new WindReaction());
        register(new BulletReaction());
        register(new PotionReaction());
        register(new JumperReaction());
        registerVariant(TridentReaction.BASE, new TridentReaction());
        registerFallback(GroundReaction.KIND);
    }

    private ReactionKinds() {
    }

    public static synchronized void register(Reaction reaction) {
        String kind = reaction.kind();
        if (!KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("Ungültige Reaktionsart: " + kind);
        }
        if (KINDS.containsKey(kind)) {
            throw new IllegalArgumentException("Reaktionsart doppelt angemeldet: " + kind);
        }
        KINDS.put(kind, new Entry(reaction, ReactionTags.reacts(kind)));
        snapshot = List.copyOf(new ArrayList<>(KINDS.values()));
    }

    public static synchronized void registerVariant(String base, Reaction variant) {
        String kind = variant.kind();
        if (!KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("Ungültige Reaktionsart: " + kind);
        }
        if (!KINDS.containsKey(base)) {
            throw new IllegalArgumentException("Grundart fehlt für Variante " + kind + ": " + base);
        }
        if (KINDS.containsKey(kind) || VARIANTS.values().stream().flatMap(List::stream).anyMatch(other -> other.kind().equals(kind))) {
            throw new IllegalArgumentException("Reaktionsart doppelt angemeldet: " + kind);
        }
        VARIANTS.computeIfAbsent(base, key -> new ArrayList<>()).add(variant);
        Map<String, List<Reaction>> copy = new LinkedHashMap<>();
        VARIANTS.forEach((key, list) -> copy.put(key, List.copyOf(list)));
        variantSnapshot = Map.copyOf(copy);
    }

    public static synchronized void registerFallback(String kind) {
        Entry entry = KINDS.get(kind);
        if (entry == null) {
            throw new IllegalArgumentException("Auffang-Art fehlt: " + kind);
        }
        fallback = entry.reaction();
    }

    public static @Nullable Reaction fallback() {
        return fallback;
    }

    public static Reaction refine(Reaction base, Mob mob) {
        List<Reaction> variants = variantSnapshot.get(base.kind());
        if (variants != null) {
            for (Reaction variant : variants) {
                if (variant.accepts(mob)) {
                    return variant;
                }
            }
        }
        return base;
    }

    public static Map<String, List<String>> variants() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        variantSnapshot.forEach((base, list) -> out.put(base, list.stream().map(Reaction::kind).toList()));
        return out;
    }

    public static List<Entry> entries() {
        return snapshot;
    }

    public static List<String> names() {
        return snapshot.stream().map(entry -> entry.reaction().kind()).toList();
    }

    public static Optional<Reaction> find(String kind) {
        for (Entry entry : snapshot) {
            if (entry.reaction().kind().equals(kind)) {
                return Optional.of(entry.reaction());
            }
        }
        return Optional.empty();
    }
}
