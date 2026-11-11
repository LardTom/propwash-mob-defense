package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.flight.AssistOutput;
import de.lardtom.propwash.api.flight.FlightAssist;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.flight.StabilizationMode;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class CovMdBDrift implements FlightAssist {
    public static final Identifier ID = MobDefense.id("selftest_cov_md_b");

    public enum Mode {
        HOLD,
        LEVEL,
        GLIDE,
        CRUISE
    }

    public record Sample(double time, double x, double y, double z, double vx, double vy, double vz, double tiltDeg, double height, boolean grounded,
                         boolean armed) {
        public double along(double fx, double fz) {
            return this.vx * fx + this.vz * fz;
        }
    }

    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile Mode mode = Mode.HOLD;
    private volatile double cruiseX;
    private volatile double cruiseZ;
    private volatile boolean recording;
    private volatile @Nullable Sample last;
    private final List<Sample> samples = new ArrayList<>();

    public CovMdBDrift(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static CovMdBDrift start(Entity drone, ServerPlayer owner, double metres) {
        CovMdBDrift drift = new CovMdBDrift(drone.getX(), drone.getY() + metres, drone.getZ());
        AutonomyResult result = drift.begin(drone, owner, metres);
        if (!result.started()) {
            throw new IllegalStateException("autonomer Flug startet nicht: " + result);
        }
        return drift;
    }

    public AutonomyResult begin(Entity drone, ServerPlayer owner, double metres) {
        AutonomyOptions options = AutonomyOptions.of(ID).label("MD COV").maxDuration(Duration.ofMinutes(5)).takeoff(Math.min(30.0, metres));
        return PropwashApi.drones().autonomy().start(drone, owner, this, options);
    }

    public void hold(double atX, double atY, double atZ) {
        this.x = atX;
        this.y = atY;
        this.z = atZ;
        this.mode = Mode.HOLD;
    }

    public void level() {
        this.mode = Mode.LEVEL;
    }

    public void glide() {
        this.mode = Mode.GLIDE;
    }

    public void cruise(double vx, double vz) {
        this.cruiseX = vx;
        this.cruiseZ = vz;
        this.mode = Mode.CRUISE;
    }

    public Mode mode() {
        return this.mode;
    }

    public double targetX() {
        return this.x;
    }

    public double targetY() {
        return this.y;
    }

    public double targetZ() {
        return this.z;
    }

    public @Nullable Sample last() {
        return this.last;
    }

    public synchronized void record() {
        this.samples.clear();
        this.recording = true;
    }

    public synchronized List<Sample> stop() {
        this.recording = false;
        return List.copyOf(this.samples);
    }

    public synchronized List<Sample> samples() {
        return List.copyOf(this.samples);
    }

    public boolean settled() {
        Sample sample = this.last;
        return sample != null && sample.armed() && Math.abs(sample.vy()) < 0.3 && Math.hypot(sample.vx(), sample.vz()) < 0.3
                && Math.abs(sample.y() - this.y) < 0.6 && Math.hypot(sample.x() - this.x, sample.z() - this.z) < 0.4 && sample.tiltDeg() < 5.0;
    }

    @Override
    public void update(FlightState state, AssistOutput out) {
        double tilt = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, state.upY()))));
        Sample sample = new Sample(state.time(), state.x(), state.y(), state.z(), state.vx(), state.vy(), state.vz(), tilt, state.heightAboveGround(),
                state.grounded(), state.armed());
        this.last = sample;
        if (this.recording) {
            synchronized (this) {
                this.samples.add(sample);
            }
        }
        out.sticks(0.0, 0.0, 0.0, state.hoverThrottle());
        out.mode(StabilizationMode.ANGLE);
        out.yawRate(0.0);
        Mode now = this.mode;
        if (now != Mode.LEVEL) {
            out.holdAltitude(this.y);
        }
        if (now == Mode.HOLD) {
            out.holdPosition(this.x, this.z);
        } else if (now == Mode.CRUISE) {
            out.velocity(this.cruiseX, this.cruiseZ);
            out.maxSpeed(Math.min(AssistOutput.MAX_SPEED, Math.hypot(this.cruiseX, this.cruiseZ) + 1.0));
        }
        out.label("MD COV");
    }
}
