package de.lardtom.mobdefense.rules.core;

import java.util.Objects;
import java.util.regex.Pattern;

public record RuleSpec(String path, boolean flag, int defaultValue, int min, int max) {
    private static final Pattern PATH = Pattern.compile("[a-z0-9_]{1,48}");

    public RuleSpec {
        Objects.requireNonNull(path, "path");
        if (!PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Ungültiger Regelname: " + path);
        }
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(path + ": Standardwert außerhalb von " + min + " bis " + max);
        }
        if (flag && (min != 0 || max != 1)) {
            throw new IllegalArgumentException(path + ": Schalter brauchen den Bereich 0 bis 1");
        }
    }

    public static RuleSpec flag(String path, boolean defaultValue) {
        return new RuleSpec(path, true, defaultValue ? 1 : 0, 0, 1);
    }

    public static RuleSpec number(String path, int defaultValue, int min, int max) {
        return new RuleSpec(path, false, defaultValue, min, max);
    }

    public boolean defaultFlag() {
        return this.defaultValue != 0;
    }
}
