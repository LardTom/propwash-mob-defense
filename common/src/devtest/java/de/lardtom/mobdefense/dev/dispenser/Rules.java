package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Rules {
    public static final Identifier PVP = Identifier.withDefaultNamespace("pvp");
    public static final Identifier TNT_EXPLODES = Identifier.withDefaultNamespace("tnt_explodes");

    private final MinecraftServer server;
    private final Map<Identifier, String> before = new LinkedHashMap<>();

    public Rules(MinecraftServer server) {
        this.server = server;
    }

    public static Identifier id(RuleSpec spec) {
        return MobDefense.id(spec.path());
    }

    public Rules set(RuleSpec spec, Object value) {
        return this.set(id(spec), value);
    }

    public Rules set(Identifier id, Object value) {
        GameRule<?> rule = rule(id);
        this.before.putIfAbsent(id, this.server.getGameRules().getAsString(rule));
        apply(this.server, rule, String.valueOf(value));
        return this;
    }

    public String get(Identifier id) {
        return this.server.getGameRules().getAsString(rule(id));
    }

    public void restore() {
        for (Map.Entry<Identifier, String> entry : this.before.entrySet()) {
            apply(this.server, rule(entry.getKey()), entry.getValue());
        }
        this.before.clear();
    }

    private static GameRule<?> rule(Identifier id) {
        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
        if (rule == null) {
            throw new IllegalStateException("Gamerule " + id + " fehlt");
        }
        return rule;
    }

    private static <T> void apply(MinecraftServer server, GameRule<T> rule, String value) {
        T parsed = rule.deserialize(value).result().orElseThrow(() -> new IllegalStateException("Wert " + value + " passt nicht zu " + rule.id()));
        server.getGameRules().set(rule, parsed, server);
        String now = server.getGameRules().getAsString(rule);
        if (!value.equals(now)) {
            throw new IllegalStateException("Gamerule " + rule.id() + " ist " + now + " statt " + value);
        }
    }
}
