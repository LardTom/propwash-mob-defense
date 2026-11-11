package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.Boosters;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CovMdBSpeedLog {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile @Nullable CovMdBSpeedLog current;

    public record Point(long gameTime, double along, boolean burning) {
    }

    private final Entity drone;
    private final Vec3 nose;
    private final int ticks;
    private final List<Point> points = new ArrayList<>();
    private @Nullable Vec3 last;

    private CovMdBSpeedLog(Entity drone, Vec3 nose, int ticks) {
        this.drone = drone;
        this.nose = nose;
        this.ticks = ticks;
    }

    public static CovMdBSpeedLog start(Entity drone, Vec3 nose, int ticks) {
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(CovMdBSpeedLog::tick);
        }
        CovMdBSpeedLog log = new CovMdBSpeedLog(drone, nose, ticks);
        current = log;
        return log;
    }

    public synchronized List<Point> points() {
        return List.copyOf(this.points);
    }

    public synchronized boolean done() {
        return this.points.size() >= this.ticks || this.drone.isRemoved();
    }

    private static void tick(MinecraftServer server) {
        CovMdBSpeedLog log = current;
        if (log == null) {
            return;
        }
        synchronized (log) {
            if (log.points.size() >= log.ticks || log.drone.isRemoved()) {
                current = null;
                return;
            }
            Vec3 position = log.drone.position();
            if (log.last != null) {
                Vec3 moved = position.subtract(log.last);
                log.points.add(new Point(log.drone.level().getGameTime(), (moved.x * log.nose.x + moved.z * log.nose.z) * 20.0, Boosters.burning(log.drone)));
            }
            log.last = position;
        }
    }
}
