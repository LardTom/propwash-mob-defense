package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

public final class MotionSampler implements Runs.Run {
    private final Entity drone;
    private final int ticks;
    private int seen;
    private int stillPropwash;
    private int stillEntity;
    private double sumPropwash;
    private double sumEntity;
    private @Nullable Vec3 last;
    private boolean done;

    private MotionSampler(Entity drone, int ticks) {
        this.drone = drone;
        this.ticks = ticks;
    }

    public static MotionSampler start(Entity drone, int ticks) {
        return Runs.start(new MotionSampler(drone, ticks));
    }

    @Override
    public void tick(MinecraftServer server) {
        if (this.done) {
            return;
        }
        Vec3 position = this.drone.position();
        double propwash = PropwashApi.drones().view(this.drone).map(DroneView::speed).orElse(0.0);
        double entity = this.last == null ? 0.0 : position.subtract(this.last).length() * 20.0;
        if (this.last != null) {
            this.seen++;
            this.sumPropwash += propwash;
            this.sumEntity += entity;
            this.stillPropwash += propwash < 0.01 ? 1 : 0;
            this.stillEntity += entity < 0.01 ? 1 : 0;
        }
        this.last = position;
        if (this.seen >= this.ticks) {
            this.done = true;
        }
    }

    public double meanPropwash() {
        return this.seen == 0 ? 0.0 : this.sumPropwash / this.seen;
    }

    public double meanEntity() {
        return this.seen == 0 ? 0.0 : this.sumEntity / this.seen;
    }

    @Override
    public boolean done() {
        return this.done;
    }

    @Override
    public @Nullable String failure() {
        return null;
    }

    @Override
    public String summary() {
        return String.format(Locale.ROOT, "Server über %d Ticks: Propwash-Tempo im Mittel %.2f m/s, in %d Ticks 0; Entity-Position %.2f m/s, in %d Ticks unbewegt",
                this.seen, this.meanPropwash(), this.stillPropwash, this.meanEntity(), this.stillEntity);
    }

    @Override
    public void abort(MinecraftServer server, String reason) {
        this.done = true;
    }
}
