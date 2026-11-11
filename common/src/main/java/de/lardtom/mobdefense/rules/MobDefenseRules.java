package de.lardtom.mobdefense.rules;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.rules.core.PresetValue;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.worldrule.WorldRule;
import de.lardtom.propwash.api.worldrule.WorldRules;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MobDefenseRules {
    private static final Map<String, WorldRule<?>> RULES = new LinkedHashMap<>();

    private MobDefenseRules() {
    }

    public static synchronized void register() {
        if (!RULES.isEmpty()) {
            return;
        }
        WorldRules rules = PropwashApi.worldRules();
        for (RuleSpec spec : RuleDefaults.CORE) {
            register(rules, spec);
        }
        for (PresetValue preset : RuleDefaults.PRESET_VALUES) {
            preset(rules, preset);
        }
    }

    public static synchronized WorldRule<?> register(RuleSpec spec) {
        WorldRule<?> known = RULES.get(spec.path());
        return known != null ? known : register(PropwashApi.worldRules(), spec);
    }

    private static WorldRule<?> register(WorldRules rules, RuleSpec spec) {
        WorldRule<?> rule = spec.flag()
                ? rules.registerBoolean(MobDefense.id(spec.path()), spec.defaultFlag())
                : rules.registerInt(MobDefense.id(spec.path()), spec.defaultValue(), spec.min(), spec.max());
        RULES.put(spec.path(), rule);
        return rule;
    }

    private static void preset(WorldRules rules, PresetValue preset) {
        if (preset.rule().flag()) {
            rules.presetValue(flag(preset.rule()), preset.preset(), preset.flagValue());
        } else {
            rules.presetValue(number(preset.rule()), preset.preset(), preset.value());
        }
    }

    public static synchronized List<WorldRule<?>> all() {
        return List.copyOf(RULES.values());
    }

    public static synchronized Optional<WorldRule<?>> find(RuleSpec spec) {
        return Optional.ofNullable(RULES.get(spec.path()));
    }

    @SuppressWarnings("unchecked")
    public static WorldRule<Boolean> flag(RuleSpec spec) {
        WorldRule<?> rule = require(spec);
        if (rule.type() != WorldRule.Type.BOOLEAN) {
            throw new IllegalArgumentException(spec.path() + " ist kein Schalter");
        }
        return (WorldRule<Boolean>) rule;
    }

    @SuppressWarnings("unchecked")
    public static WorldRule<Integer> number(RuleSpec spec) {
        WorldRule<?> rule = require(spec);
        if (rule.type() != WorldRule.Type.INTEGER) {
            throw new IllegalArgumentException(spec.path() + " ist keine Zahl");
        }
        return (WorldRule<Integer>) rule;
    }

    public static boolean flag(@Nullable Level level, RuleSpec spec) {
        return find(spec).isEmpty() ? spec.defaultFlag() : flag(spec).get(level);
    }

    public static int number(@Nullable Level level, RuleSpec spec) {
        return find(spec).isEmpty() ? spec.defaultValue() : number(spec).get(level);
    }

    private static synchronized WorldRule<?> require(RuleSpec spec) {
        WorldRule<?> rule = RULES.get(spec.path());
        if (rule == null) {
            throw new IllegalStateException("Welt-Regel " + MobDefense.id(spec.path()) + " ist nicht angemeldet");
        }
        return rule;
    }
}
