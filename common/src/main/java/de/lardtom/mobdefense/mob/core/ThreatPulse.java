package de.lardtom.mobdefense.mob.core;

public final class ThreatPulse {
    public static final int REFRESH_TICKS = 20;
    public static final int SHOW_TICKS = 40;
    public static final int RETRY_TICKS = 5;
    public static final long NEVER = Long.MIN_VALUE;

    private ThreatPulse() {
    }

    public static boolean due(long now, long last) {
        return last == NEVER || now < last || now - last >= REFRESH_TICKS;
    }

    public static long mark(long now, boolean sent) {
        return sent ? now : now - REFRESH_TICKS + RETRY_TICKS;
    }
}
