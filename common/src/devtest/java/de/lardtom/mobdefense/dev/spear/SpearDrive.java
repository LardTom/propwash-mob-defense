package de.lardtom.mobdefense.dev.spear;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SpearDrive {
    public static final int TICKS_AFTER_HIT = 3;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile @Nullable Run current;

    private SpearDrive() {
    }

    public static final class Run {
        private final Entity drone;
        private final LivingEntity target;
        private final Vec3 step;
        private final Vec3 start;
        private final double maxDistance;
        private final ItemStack spearBefore;
        private volatile int ticks;
        private volatile int hitTick = -1;
        private volatile boolean done;
        private volatile double travelled;

        private Run(Entity drone, LivingEntity target, Vec3 direction, double metresPerSecond, double maxDistance) {
            this.drone = drone;
            this.target = target;
            this.step = direction.normalize().scale(metresPerSecond / 20.0);
            this.start = drone.position();
            this.maxDistance = maxDistance;
            this.spearBefore = spear(drone);
        }

        public boolean done() {
            return this.done;
        }

        public boolean hit() {
            return this.hitTick >= 0;
        }

        public int ticks() {
            return this.ticks;
        }

        public double travelled() {
            return this.travelled;
        }
    }

    public static Run start(Entity drone, LivingEntity target, Vec3 direction, double metresPerSecond, double maxDistance) {
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(SpearDrive::tick);
        }
        Run run = new Run(drone, target, direction, metresPerSecond, maxDistance);
        drone.setNoGravity(true);
        drone.setDeltaMovement(run.step);
        current = run;
        return run;
    }

    public static void stop() {
        Run run = current;
        current = null;
        if (run != null) {
            run.done = true;
            run.drone.setDeltaMovement(Vec3.ZERO);
        }
    }

    public static ItemStack spear(Entity drone) {
        List<ItemStack> contents = PropwashApi.accessories().contents(drone, AccessorySlot.BOTTOM);
        return contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst();
    }

    private static void tick(MinecraftServer server) {
        Run run = current;
        if (run == null || run.done) {
            return;
        }
        run.ticks++;
        run.travelled = run.drone.position().subtract(run.start).length();
        if (run.drone.isRemoved()) {
            stop();
            return;
        }
        if (run.hitTick < 0 && (run.target.hurtTime > 0 || !ItemStack.matches(spear(run.drone), run.spearBefore))) {
            run.hitTick = run.ticks;
        }
        boolean settled = run.hitTick >= 0 && run.ticks - run.hitTick >= TICKS_AFTER_HIT;
        if (settled || run.travelled >= run.maxDistance || run.ticks > 200) {
            stop();
            return;
        }
        run.drone.setDeltaMovement(run.step);
    }
}
