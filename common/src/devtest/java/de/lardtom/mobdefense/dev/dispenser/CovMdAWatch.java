package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CovMdAWatch {
    private static final List<Watch> WATCHES = new ArrayList<>();
    private static final List<Mover> MOVERS = new ArrayList<>();
    private static final List<Damage> DAMAGES = new ArrayList<>();
    private static final List<Consumer<MinecraftServer>> TICKS = new ArrayList<>();
    private static final List<Later> LATER = new ArrayList<>();
    private static boolean installed;

    private static final class Later {
        private int ticks;
        private final Runnable action;

        private Later(int ticks, Runnable action) {
            this.ticks = ticks;
            this.action = action;
        }
    }

    public record Damage(Entity drone, String source, @Nullable Entity direct, @Nullable Entity cause, float amount, long time, boolean cancelled) {
    }

    public static final class Watch {
        private final Entity entity;
        private final Vec3 along;
        private final Vec3 start;
        private final int limit;
        private int ticks;
        private int removedAt = -1;
        private int hurts;
        private float health = -1.0F;
        private double maxAlong;
        private double maxSpeedAlong;
        private Vec3 last;
        private boolean done;
        private final List<double[]> path = new ArrayList<>();

        private Watch(Entity entity, Vec3 along, int limit) {
            this.entity = entity;
            this.along = along.lengthSqr() > 1.0E-9 ? along.normalize() : Vec3.ZERO;
            this.start = entity.position();
            this.last = this.start;
            this.limit = limit;
            this.health = entity instanceof LivingEntity living ? living.getHealth() : -1.0F;
        }

        private void tick() {
            if (this.done) {
                return;
            }
            this.ticks++;
            if (this.entity.isRemoved()) {
                if (this.removedAt < 0) {
                    this.removedAt = this.ticks;
                }
                this.done = true;
                return;
            }
            this.last = this.entity.position();
            this.path.add(new double[]{this.last.subtract(this.start).dot(this.along), this.last.y});
            this.maxAlong = Math.max(this.maxAlong, this.last.subtract(this.start).dot(this.along));
            this.maxSpeedAlong = Math.max(this.maxSpeedAlong, this.entity.getDeltaMovement().dot(this.along));
            if (this.entity instanceof LivingEntity living) {
                if (this.health >= 0.0F && living.getHealth() < this.health) {
                    this.hurts++;
                }
                this.health = living.getHealth();
            }
            if (this.ticks >= this.limit) {
                this.done = true;
            }
        }

        public synchronized boolean done() {
            return this.done;
        }

        public synchronized int removedAt() {
            return this.removedAt;
        }

        public synchronized double maxAlong() {
            return this.maxAlong;
        }

        public synchronized double maxSpeedAlong() {
            return this.maxSpeedAlong;
        }

        public synchronized double heightAt(double distance) {
            double[] before = null;
            for (double[] point : this.path) {
                if (point[0] >= distance) {
                    if (before == null) {
                        return point[1];
                    }
                    double t = (distance - before[0]) / Math.max(1.0E-6, point[0] - before[0]);
                    return before[1] + t * (point[1] - before[1]);
                }
                before = point;
            }
            return Double.NaN;
        }

        public synchronized int hurts() {
            return this.hurts;
        }

        public synchronized Vec3 last() {
            return this.last;
        }

        public synchronized Vec3 start() {
            return this.start;
        }

        public Entity entity() {
            return this.entity;
        }
    }

    public static final class Mover {
        private final Entity drone;
        private final Vec3 step;
        private final int limit;
        private int ticks;
        private boolean done;

        private Mover(Entity drone, Vec3 metresPerSecond, int limit) {
            this.drone = drone;
            this.step = metresPerSecond.scale(1.0 / 20.0);
            this.limit = limit;
        }

        private void tick() {
            if (this.done) {
                return;
            }
            this.ticks++;
            if (this.drone.isRemoved() || this.ticks > this.limit) {
                this.done = true;
                this.drone.setDeltaMovement(Vec3.ZERO);
                return;
            }
            this.drone.setNoGravity(true);
            this.drone.setDeltaMovement(this.step);
        }

        public synchronized boolean done() {
            return this.done;
        }

        public synchronized void stop() {
            this.done = true;
            this.drone.setDeltaMovement(Vec3.ZERO);
        }

        public synchronized int ticks() {
            return this.ticks;
        }
    }

    private CovMdAWatch() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        MobDefense.onServerTick(CovMdAWatch::tick);
        PropwashEvents.DRONE_DAMAGED.register(CovMdAWatch::damaged);
    }

    public static synchronized Watch watch(Entity entity, Vec3 along, int ticks) {
        install();
        Watch watch = new Watch(entity, along, ticks);
        WATCHES.add(watch);
        return watch;
    }

    public static synchronized Mover move(Entity drone, Vec3 metresPerSecond, int ticks) {
        install();
        Mover mover = new Mover(drone, metresPerSecond, ticks);
        MOVERS.add(mover);
        return mover;
    }

    public static synchronized void later(int ticks, Runnable action) {
        install();
        LATER.add(new Later(ticks, action));
    }

    public static synchronized void every(Consumer<MinecraftServer> action) {
        install();
        TICKS.add(action);
    }

    public static synchronized void clear() {
        for (Mover mover : MOVERS) {
            mover.stop();
        }
        MOVERS.clear();
        WATCHES.clear();
        TICKS.clear();
        LATER.clear();
        DAMAGES.clear();
    }

    public static synchronized List<Damage> damages(Entity drone) {
        List<Damage> out = new ArrayList<>();
        for (Damage damage : DAMAGES) {
            if (damage.drone() == drone) {
                out.add(damage);
            }
        }
        return out;
    }

    private static synchronized void damaged(DroneDamage damage) {
        Entity direct = damage.source().getDirectEntity();
        DAMAGES.add(new Damage(damage.drone(), damage.source().typeHolder().getRegisteredName(), direct, damage.source().getEntity(), damage.amount(),
                damage.drone().level().getGameTime(), damage.cancelled()));
    }

    public static String type(@Nullable Entity entity) {
        return entity == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    private static synchronized void tick(MinecraftServer server) {
        for (Watch watch : WATCHES) {
            synchronized (watch) {
                watch.tick();
            }
        }
        WATCHES.removeIf(Watch::done);
        for (Mover mover : MOVERS) {
            synchronized (mover) {
                mover.tick();
            }
        }
        MOVERS.removeIf(Mover::done);
        for (Consumer<MinecraftServer> action : List.copyOf(TICKS)) {
            action.accept(server);
        }
        List<Later> due = new ArrayList<>();
        for (Later later : LATER) {
            if (--later.ticks <= 0) {
                due.add(later);
            }
        }
        LATER.removeAll(due);
        for (Later later : due) {
            later.action.run();
        }
    }
}
