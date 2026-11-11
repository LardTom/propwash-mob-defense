package de.lardtom.mobdefense.dev.spear;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.spear.SpearStrikes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CovMdBSpearRun {
    public static final int MAX_TICKS = 200;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile @Nullable CovMdBSpearRun current;

    private final Entity drone;
    private final Vec3 step;
    private final Vec3 start;
    private final double distance;
    private final List<Integer> stabs = new ArrayList<>();
    private int wear;
    private boolean wasEmpty;
    private volatile int ticks;
    private volatile boolean done;
    private volatile boolean broke;

    private CovMdBSpearRun(Entity drone, Vec3 direction, double metresPerSecond, double distance) {
        this.drone = drone;
        this.step = direction.normalize().scale(metresPerSecond / 20.0);
        this.start = drone.position();
        this.distance = distance;
        ItemStack spear = SpearDrive.spear(drone);
        this.wear = spear.getDamageValue();
        this.wasEmpty = spear.isEmpty();
    }

    public static CovMdBSpearRun start(Entity drone, Vec3 direction, double metresPerSecond, double distance) {
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(CovMdBSpearRun::tick);
        }
        SpearDrive.stop();
        SpearStrikes.forget(drone);
        CovMdBSpearRun run = new CovMdBSpearRun(drone, direction, metresPerSecond, distance);
        drone.setNoGravity(true);
        drone.setDeltaMovement(run.step);
        current = run;
        return run;
    }

    public static void stop() {
        CovMdBSpearRun run = current;
        current = null;
        if (run != null) {
            run.done = true;
            run.drone.setDeltaMovement(Vec3.ZERO);
        }
    }

    public boolean done() {
        return this.done;
    }

    public synchronized List<Integer> stabs() {
        return List.copyOf(this.stabs);
    }

    public boolean broke() {
        return this.broke;
    }

    public int ticks() {
        return this.ticks;
    }

    public double travelled() {
        return this.drone.position().subtract(this.start).length();
    }

    private static void tick(MinecraftServer server) {
        CovMdBSpearRun run = current;
        if (run == null || run.done) {
            return;
        }
        run.ticks++;
        ItemStack spear = SpearDrive.spear(run.drone);
        synchronized (run) {
            if (!run.wasEmpty && spear.isEmpty()) {
                run.broke = true;
                run.stabs.add(run.ticks);
            } else if (!spear.isEmpty() && spear.getDamageValue() > run.wear) {
                for (int i = run.wear; i < spear.getDamageValue(); i++) {
                    run.stabs.add(run.ticks);
                }
            }
        }
        run.wear = spear.isEmpty() ? run.wear : spear.getDamageValue();
        run.wasEmpty = spear.isEmpty();
        if (run.drone.isRemoved() || run.travelled() >= run.distance || run.ticks > MAX_TICKS) {
            stop();
            return;
        }
        run.drone.setDeltaMovement(run.step);
    }
}
