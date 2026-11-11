package de.lardtom.mobdefense.rules.core;

import java.util.List;
import java.util.Objects;

public record PresetValue(String preset, RuleSpec rule, int value) {
    public static final String REALISTIC = "realistic";
    public static final String TRAINING = "training";
    public static final String ARCADE = "arcade";
    public static final List<String> PRESETS = List.of(REALISTIC, TRAINING, ARCADE);

    public PresetValue {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(rule, "rule");
        if (!PRESETS.contains(preset)) {
            throw new IllegalArgumentException(rule.path() + ": unbekannte Voreinstellung " + preset + ", erlaubt sind " + PRESETS);
        }
        if (value < rule.min() || value > rule.max()) {
            throw new IllegalArgumentException(rule.path() + ": Wert " + value + " für " + preset + " außerhalb von " + rule.min() + " bis " + rule.max());
        }
    }

    public static PresetValue flag(String preset, RuleSpec rule, boolean value) {
        if (!rule.flag()) {
            throw new IllegalArgumentException(rule.path() + " ist kein Schalter");
        }
        return new PresetValue(preset, rule, value ? 1 : 0);
    }

    public boolean flagValue() {
        return this.value != 0;
    }
}
