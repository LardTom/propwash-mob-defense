package de.lardtom.mobdefense.dispenser.core;

import java.util.Objects;

public final class FireGate {
    public enum Outcome {
        FIRE,
        COOLDOWN,
        EMPTY,
        TNT_LOCKED,
        BUCKET_LOCKED,
        GRIEFING_LOCKED
    }

    public enum Notice {
        NONE,
        NO_AMMO,
        BUCKET_LOCKED,
        TNT_LOCKED,
        BARREL_BLOCKED,
        NO_PERMISSION
    }

    public record Rules(boolean realAmmo, boolean allowTnt, boolean allowFluidBuckets, boolean droneGriefing, int cooldownTicks) {
        public static final Rules DEFAULTS = new Rules(true, true, false, true, 4);

        public Rules {
            cooldownTicks = Math.max(0, cooldownTicks);
        }
    }

    public record Shot(Outcome outcome, int slot, boolean consume, boolean allowFluids) {
        public Shot {
            Objects.requireNonNull(outcome, "outcome");
        }

        public boolean fires() {
            return this.outcome == Outcome.FIRE;
        }

        public Notice notice() {
            return FireGate.notice(this.outcome);
        }
    }

    private FireGate() {
    }

    public static boolean ready(long now, long readyAt) {
        return now >= readyAt;
    }

    public static long nextReady(long now, int cooldownTicks) {
        return now + Math.max(1, cooldownTicks);
    }

    public static Shot decide(long now, long readyAt, int selected, AmmoTable.Kind[] slots, Rules rules) {
        Objects.requireNonNull(rules, "rules");
        boolean[] filled = new boolean[SlotCycle.SLOTS];
        if (slots == null || slots.length != SlotCycle.SLOTS) {
            throw new IllegalArgumentException("Der Dispenser hat genau " + SlotCycle.SLOTS + " Slots");
        }
        for (int i = 0; i < SlotCycle.SLOTS; i++) {
            filled[i] = slots[i] != null;
        }
        if (!ready(now, readyAt)) {
            return new Shot(Outcome.COOLDOWN, -1, false, false);
        }
        int slot = SlotCycle.firing(selected, filled);
        if (slot < 0) {
            return new Shot(Outcome.EMPTY, -1, false, false);
        }
        AmmoTable.Kind kind = slots[slot];
        if (kind == AmmoTable.Kind.TNT && !rules.allowTnt()) {
            return new Shot(Outcome.TNT_LOCKED, slot, false, false);
        }
        if (kind == AmmoTable.Kind.FLUID_BUCKET && !rules.allowFluidBuckets()) {
            return new Shot(Outcome.BUCKET_LOCKED, slot, false, false);
        }
        if (kind.changesBlocks() && !rules.droneGriefing()) {
            return new Shot(Outcome.GRIEFING_LOCKED, slot, false, false);
        }
        boolean fluids = kind == AmmoTable.Kind.FLUID_BUCKET && rules.allowFluidBuckets() && rules.droneGriefing();
        return new Shot(Outcome.FIRE, slot, rules.realAmmo(), fluids);
    }

    public static Notice notice(Outcome outcome) {
        return switch (outcome) {
            case FIRE, COOLDOWN -> Notice.NONE;
            case EMPTY -> Notice.NO_AMMO;
            case TNT_LOCKED -> Notice.TNT_LOCKED;
            case BUCKET_LOCKED -> Notice.BUCKET_LOCKED;
            case GRIEFING_LOCKED -> Notice.NO_PERMISSION;
        };
    }

    public static Notice afterDispense(String status, AmmoTable.Kind kind) {
        Objects.requireNonNull(status, "status");
        return switch (status) {
            case "FIRED", "PLACED", "USED" -> Notice.NONE;
            case "EMPTY" -> Notice.NO_AMMO;
            case "DENIED" -> Notice.NO_PERMISSION;
            case "REFUSED" -> kind == AmmoTable.Kind.FLUID_BUCKET ? Notice.BUCKET_LOCKED : kind == AmmoTable.Kind.TNT ? Notice.TNT_LOCKED
                    : Notice.BARREL_BLOCKED;
            default -> Notice.BARREL_BLOCKED;
        };
    }

    public static boolean failSound(Outcome outcome, String status) {
        if (outcome == Outcome.COOLDOWN) {
            return false;
        }
        return outcome != Outcome.FIRE || !("FIRED".equals(status) || "PLACED".equals(status) || "USED".equals(status));
    }
}
