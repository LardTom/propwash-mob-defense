package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CovMdBChargeWatch {
    public static final int MAX_TICKS = 60;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile @Nullable CovMdBChargeWatch current;

    private final Entity charge;
    private final Entity drone;
    private Vec3 lastCharge;
    private Vec3 lastDrone;
    private int ticks;
    private volatile @Nullable Vec3 burst;
    private volatile @Nullable Vec3 droneAtBurst;
    private volatile int burstTick = -1;

    private CovMdBChargeWatch(Entity charge, Entity drone) {
        this.charge = charge;
        this.drone = drone;
        this.lastCharge = charge.position();
        this.lastDrone = drone.position();
    }

    public static CovMdBChargeWatch start(Entity charge, Entity drone) {
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(CovMdBChargeWatch::tick);
        }
        CovMdBChargeWatch watch = new CovMdBChargeWatch(charge, drone);
        current = watch;
        return watch;
    }

    public @Nullable Vec3 burst() {
        return this.burst;
    }

    public @Nullable Vec3 droneAtBurst() {
        return this.droneAtBurst;
    }

    public int burstTick() {
        return this.burstTick;
    }

    public double distance() {
        Vec3 at = this.burst;
        Vec3 drone = this.droneAtBurst;
        return at == null || drone == null ? Double.NaN : at.distanceTo(drone);
    }

    private static void tick(MinecraftServer server) {
        CovMdBChargeWatch watch = current;
        if (watch == null) {
            return;
        }
        watch.ticks++;
        if (watch.charge.isRemoved()) {
            watch.burst = watch.lastCharge;
            watch.droneAtBurst = watch.lastDrone;
            watch.burstTick = watch.ticks;
            current = null;
            return;
        }
        watch.lastCharge = watch.charge.position();
        watch.lastDrone = watch.drone.position();
        if (watch.ticks > MAX_TICKS) {
            current = null;
        }
    }
}
