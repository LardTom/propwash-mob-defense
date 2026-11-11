package de.lardtom.mobdefense.spear.core;

import java.util.Objects;
import java.util.Optional;

public final class SpearDamage {
    public static final float BASE_DAMAGE = 1.0F;
    public static final float MAX_DAMAGE = 15.0F;
    public static final float ENTITY_FACTOR = 1.0F;
    public static final int DEFAULT_PERCENT = 100;
    public static final int MAX_PERCENT = 100;

    private SpearDamage() {
    }

    public record Threshold(float minSpeed, float minRelativeSpeed) {
        public boolean met(double attackerSpeed, double relativeSpeed) {
            return attackerSpeed >= this.minSpeed * ENTITY_FACTOR && relativeSpeed >= this.minRelativeSpeed * ENTITY_FACTOR;
        }
    }

    public record Stats(float damageMultiplier, Optional<Threshold> damage, Optional<Threshold> knockback, Optional<Threshold> dismount) {
        public Stats {
            Objects.requireNonNull(damage, "damage");
            Objects.requireNonNull(knockback, "knockback");
            Objects.requireNonNull(dismount, "dismount");
        }
    }

    public record Outcome(boolean damage, boolean knockback, boolean dismount, float amount) {
        public static final Outcome NONE = new Outcome(false, false, false, 0.0F);

        public boolean any() {
            return this.damage || this.knockback || this.dismount;
        }
    }

    public static int vanilla(double relativeSpeed, float damageMultiplier) {
        if (!Double.isFinite(relativeSpeed) || !Float.isFinite(damageMultiplier)) {
            return (int) BASE_DAMAGE;
        }
        return (int) BASE_DAMAGE + (int) Math.floor(Math.max(0.0, relativeSpeed) * damageMultiplier);
    }

    public static float scaled(double relativeSpeed, float damageMultiplier, int percent) {
        int share = Math.max(0, Math.min(MAX_PERCENT, percent));
        if (share == 0) {
            return 0.0F;
        }
        double value = Math.floor(vanilla(relativeSpeed, damageMultiplier) * share / 100.0);
        return (float) Math.min(MAX_DAMAGE, Math.max(0.0, value));
    }

    public static Outcome evaluate(Stats stats, double attackerSpeed, double relativeSpeed, int percent) {
        Objects.requireNonNull(stats, "stats");
        if (!Double.isFinite(attackerSpeed) || !Double.isFinite(relativeSpeed)) {
            return Outcome.NONE;
        }
        double relative = Math.max(0.0, relativeSpeed);
        boolean damage = stats.damage().map(threshold -> threshold.met(attackerSpeed, relative)).orElse(false);
        boolean knockback = stats.knockback().map(threshold -> threshold.met(attackerSpeed, relative)).orElse(false);
        boolean dismount = stats.dismount().map(threshold -> threshold.met(attackerSpeed, relative)).orElse(false);
        if (!damage && !knockback && !dismount) {
            return Outcome.NONE;
        }
        float amount = damage ? scaled(relative, stats.damageMultiplier(), percent) : 0.0F;
        return new Outcome(damage, knockback, dismount, amount);
    }
}
