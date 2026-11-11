package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
import org.jspecify.annotations.Nullable;

public final class HoverAssist implements FlightAssist {
    public static final String ID = MobDefense.id("selftest_hover").toString();
    public static final HoverAssist INSTANCE = new HoverAssist();

    public enum Mode {
        OFF,
        HOVER,
        HOLD,
        CRUISE
    }

    private static boolean registered;
    private static volatile Mode mode = Mode.OFF;
    private static volatile double targetY;
    private static volatile double targetX;
    private static volatile double targetZ;
    private static volatile double cruiseX;
    private static volatile double cruiseZ;
    private static volatile @Nullable FlightState last;
    private static volatile boolean tracking;
    private static volatile double trackX;
    private static volatile double trackZ;
    private static volatile double startAlong;
    private static volatile double minAlong;
    private static volatile double maxTilt;
    private static volatile double startTilt;
    private static volatile double maxRate;

    private HoverAssist() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PropwashApi.flight().registerAssist(ID, profile -> INSTANCE);
    }

    public static void hover(double y) {
        targetY = y;
        mode = Mode.HOVER;
    }

    public static void hold(double x, double y, double z) {
        targetX = x;
        targetY = y;
        targetZ = z;
        mode = Mode.HOLD;
    }

    public static void cruise(double y, double vx, double vz) {
        targetY = y;
        cruiseX = vx;
        cruiseZ = vz;
        mode = Mode.CRUISE;
    }

    public static void off() {
        mode = Mode.OFF;
        tracking = false;
    }

    public static double targetY() {
        return targetY;
    }

    public static Mode mode() {
        return mode;
    }

    public static @Nullable FlightState last() {
        return last;
    }

    public static void track(double forwardX, double forwardZ) {
        double length = Math.hypot(forwardX, forwardZ);
        trackX = length > 1.0E-6 ? forwardX / length : 0.0;
        trackZ = length > 1.0E-6 ? forwardZ / length : -1.0;
        FlightState state = last;
        startAlong = state == null ? 0.0 : along(state);
        minAlong = startAlong;
        startTilt = state == null ? 0.0 : tilt(state);
        maxTilt = startTilt;
        maxRate = 0.0;
        tracking = true;
    }

    public static double minAlong() {
        return minAlong;
    }

    public static double backChange() {
        return startAlong - minAlong;
    }

    public static double maxTiltChange() {
        return maxTilt - startTilt;
    }

    public static double maxTiltDegrees() {
        return maxTilt;
    }

    public static double maxRateDps() {
        return maxRate;
    }

    private static double rate(FlightState state) {
        return Math.sqrt(state.rollRateDps() * state.rollRateDps() + state.pitchRateDps() * state.pitchRateDps() + state.yawRateDps() * state.yawRateDps());
    }

    private static double along(FlightState state) {
        return state.vx() * trackX + state.vz() * trackZ;
    }

    private static double tilt(FlightState state) {
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, state.upY()))));
    }

    @Override
    public void update(FlightState state, AssistOutput out) {
        last = state;
        if (tracking) {
            minAlong = Math.min(minAlong, along(state));
            maxTilt = Math.max(maxTilt, tilt(state));
            maxRate = Math.max(maxRate, rate(state));
        }
        Mode now = mode;
        if (now == Mode.OFF) {
            return;
        }
        out.sticks(0.0, 0.0, 0.0, state.hoverThrottle());
        out.mode(StabilizationMode.ANGLE);
        out.yawRate(0.0);
        out.holdAltitude(targetY);
        if (now == Mode.HOLD) {
            out.holdPosition(targetX, targetZ);
        } else if (now == Mode.CRUISE) {
            out.velocity(cruiseX, cruiseZ);
            out.maxSpeed(Math.max(1.0, Math.hypot(cruiseX, cruiseZ) + 1.0));
        }
        out.label("TEST");
    }

    @Override
    public void reset() {
        last = null;
    }
}
