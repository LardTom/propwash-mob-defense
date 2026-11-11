package de.lardtom.mobdefense.dev.booster;

import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class BoostFlight implements FlightAssist {
    public enum Mode {
        HOLD,
        LEVEL
    }

    public record Sample(double time, double along, double vy, double y, double massKg, double pitchDeg) {
    }

    public record Snapshot(double time, double x, double y, double z, double vx, double vy, double vz, boolean armed) {
        public double horizontalSpeed() {
            return Math.hypot(this.vx, this.vz);
        }
    }

    private volatile double x;
    private volatile double z;
    private final double y;
    private volatile Mode mode = Mode.HOLD;
    private volatile double trackX;
    private volatile double trackZ = -1.0;
    private volatile boolean recording;
    private volatile @Nullable Snapshot last;
    private final List<Sample> samples = new ArrayList<>();

    public BoostFlight(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void level() {
        this.mode = Mode.LEVEL;
    }

    public void hold(double atX, double atZ) {
        this.x = atX;
        this.z = atZ;
        this.mode = Mode.HOLD;
    }

    public double targetY() {
        return this.y;
    }

    public @Nullable Snapshot last() {
        return this.last;
    }

    public synchronized void record(double forwardX, double forwardZ) {
        double length = Math.hypot(forwardX, forwardZ);
        this.trackX = length > 1.0E-6 ? forwardX / length : 0.0;
        this.trackZ = length > 1.0E-6 ? forwardZ / length : -1.0;
        this.samples.clear();
        this.recording = true;
    }

    public synchronized void stopRecording() {
        this.recording = false;
    }

    public synchronized List<Sample> samples() {
        return List.copyOf(this.samples);
    }

    @Override
    public void update(FlightState state, AssistOutput out) {
        this.last = new Snapshot(state.time(), state.x(), state.y(), state.z(), state.vx(), state.vy(), state.vz(), state.armed());
        if (this.recording) {
            synchronized (this) {
                this.samples.add(new Sample(state.time(), state.vx() * this.trackX + state.vz() * this.trackZ, state.vy(), state.y(), state.massKg(),
                        state.pitchDeg()));
            }
        }
        out.sticks(0.0, 0.0, 0.0, state.hoverThrottle());
        out.mode(StabilizationMode.ANGLE);
        out.yawRate(0.0);
        out.holdAltitude(this.y);
        if (this.mode == Mode.HOLD) {
            out.holdPosition(this.x, this.z);
        }
        out.label("MD BOOST");
    }

    public static double speedAt(List<Sample> samples, double time) {
        Sample best = null;
        for (Sample sample : samples) {
            if (best == null || Math.abs(sample.time() - time) < Math.abs(best.time() - time)) {
                best = sample;
            }
        }
        return best == null ? Double.NaN : best.along();
    }

    public static double maxAlong(List<Sample> samples, double from, double to) {
        double max = Double.NEGATIVE_INFINITY;
        for (Sample sample : samples) {
            if (sample.time() >= from && sample.time() <= to) {
                max = Math.max(max, sample.along());
            }
        }
        return max;
    }
}
