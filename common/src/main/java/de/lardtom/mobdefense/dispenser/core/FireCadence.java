package de.lardtom.mobdefense.dispenser.core;

public final class FireCadence {
    public static final int MIN_TICKS = 4;
    public static final int MAX_TICKS = 40;
    public static final int DEFAULT_DELAY_TICKS = 10;
    public static final int DEFAULT_INTERVAL_TICKS = 4;
    public static final int MILLIS_PER_TICK = 50;

    public record Setting(int delayTicks, int intervalTicks) {
        public static final Setting DEFAULT = new Setting(DEFAULT_DELAY_TICKS, DEFAULT_INTERVAL_TICKS);

        public Setting {
            delayTicks = clamp(delayTicks);
            intervalTicks = clamp(intervalTicks);
        }

        public int effectiveDelay(int ruleCooldownTicks) {
            return Math.max(this.delayTicks, this.effectiveInterval(ruleCooldownTicks));
        }

        public int effectiveInterval(int ruleCooldownTicks) {
            return Math.max(MIN_TICKS, Math.max(this.intervalTicks, floor(ruleCooldownTicks)));
        }
    }

    public record Hold(long nextAt, boolean fired) {
    }

    private FireCadence() {
    }

    public static int clamp(int ticks) {
        return Math.max(MIN_TICKS, Math.min(MAX_TICKS, ticks));
    }

    private static int floor(int ruleCooldownTicks) {
        return Math.max(1, ruleCooldownTicks);
    }

    public static Hold press(long now, boolean attempted, long readyAt, Setting setting, int ruleCooldownTicks) {
        return attempted ? new Hold(now + setting.effectiveDelay(ruleCooldownTicks), true) : new Hold(Math.max(now, readyAt), false);
    }

    public static long nextReady(long now, Setting setting, int ruleCooldownTicks) {
        return FireGate.nextReady(now, setting.effectiveInterval(ruleCooldownTicks));
    }

    public static Hold held(long now) {
        return new Hold(now, false);
    }

    public static boolean due(long now, Hold hold) {
        return now >= hold.nextAt();
    }

    public static Hold after(long now, Hold hold, Setting setting, int ruleCooldownTicks) {
        int wait = hold.fired() ? setting.effectiveInterval(ruleCooldownTicks) : setting.effectiveDelay(ruleCooldownTicks);
        return new Hold(now + wait, true);
    }

    public static int shots(int heldTicks, Setting setting, int ruleCooldownTicks) {
        if (heldTicks <= 0) {
            return 0;
        }
        int delay = setting.effectiveDelay(ruleCooldownTicks);
        if (heldTicks <= delay) {
            return 1;
        }
        return 2 + (heldTicks - 1 - delay) / setting.effectiveInterval(ruleCooldownTicks);
    }

    public static int millis(int ticks) {
        return ticks * MILLIS_PER_TICK;
    }
}
