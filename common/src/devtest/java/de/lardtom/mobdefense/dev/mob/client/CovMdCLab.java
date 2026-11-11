package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.Guests;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.Failures;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class CovMdCLab {
    public static final Identifier RULE_REACT = MobDefense.id(RuleDefaults.MOBS_REACT_TO_DRONES.path());
    public static final Identifier RULE_DAMAGE = MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path());
    public static final Identifier RULE_RANGE = MobDefense.id(RuleDefaults.MOB_REACTION_RANGE.path());
    public static final int SCENE_X = 24;
    public static final int SCENE_Z = 24;
    public static final double TAKEOFF_MIN = 1.0;
    public static final double WATCH_RADIUS = 48.0;
    public static final int TRAVEL_TICKS = 3;
    private static final AtomicInteger POLLS = new AtomicInteger();
    private static final List<Hit> HITS = new ArrayList<>();
    private static final List<Push> PUSHES = new ArrayList<>();
    private static final Map<UUID, Shot> SHOTS = new LinkedHashMap<>();
    private static boolean listening;
    private static volatile boolean watching;
    private static Lab lab = new Lab();

    public static final class Lab {
        public @Nullable Entity drone;
        public double groundY;
        public BlockPos ground = BlockPos.ZERO;
        public double holdY;
        public final Map<String, Mob> mobs = new LinkedHashMap<>();
        public final Set<Mob> assigned = new LinkedHashSet<>();
        public final Map<String, Object> values = new HashMap<>();
        public final Map<String, Double> attributes = new HashMap<>();
        public long since = -1;
        public long mark = -1;
        public int checks;
        public int count;
        public @Nullable Vec3 spot;
    }

    public record Hit(Entity drone, String cause, String direct, @Nullable UUID directId, float original, float amount, long tick, boolean fire) {
    }

    public record Push(Entity drone, DroneKnockback.Cause cause, Vec3 delta, double scale, long tick, String nearest, double nearestDistance) {
        public double horizontal() {
            return Math.hypot(this.delta.x, this.delta.z);
        }
    }

    public record Shot(UUID id, String type, String owner, long tick, Vec3 from, Vec3 motion, Vec3 target, Vec3 last) {
        public Vec3 travel() {
            return this.last.subtract(this.from);
        }

        public Vec3 toTarget() {
            return this.target.subtract(this.from);
        }

        public double aim() {
            Vec3 travel = this.travel();
            return CovMdCLab.angle(travel.lengthSqr() > 1.0E-4 ? travel : this.motion, this.toTarget());
        }

        public double flatAim() {
            Vec3 travel = this.travel();
            return CovMdCLab.flatAngle(travel.lengthSqr() > 1.0E-4 ? travel : this.motion, this.toTarget());
        }
    }

    public record Poll(boolean done, String note) {
        public static Poll waiting(String note) {
            return new Poll(false, note);
        }

        public static Poll done(String note) {
            return new Poll(true, note);
        }
    }

    @FunctionalInterface
    public interface Probe {
        Poll check(MinecraftServer server);
    }

    private CovMdCLab() {
    }

    public static Lab lab() {
        return lab;
    }

    public static synchronized void listen() {
        MobScene.listen();
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.DRONE_DAMAGED.register(CovMdCLab::damaged);
        PropwashEvents.DRONE_KNOCKBACK.register(CovMdCLab::pushed);
        MobDefense.onServerTick(CovMdCLab::watchTick);
    }

    private static String path(@Nullable Entity entity) {
        return entity == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    private static synchronized void damaged(DroneDamage damage) {
        Entity cause = damage.source().getEntity();
        Entity direct = damage.source().getDirectEntity();
        HITS.add(new Hit(damage.drone(), path(cause), path(direct), direct == null ? null : direct.getUUID(), damage.originalAmount(), damage.amount(),
                damage.level().getGameTime(), damage.source().is(DamageTypeTags.IS_FIRE)));
    }

    private static synchronized void pushed(DroneKnockback knockback) {
        Entity drone = knockback.drone();
        String nearest = "-";
        double best = Double.MAX_VALUE;
        for (Map.Entry<String, Mob> entry : lab.mobs.entrySet()) {
            Mob mob = entry.getValue();
            if (mob.isRemoved() || mob.level() != drone.level()) {
                continue;
            }
            double distance = Math.hypot(mob.getX() - drone.getX(), mob.getZ() - drone.getZ());
            if (distance < best) {
                best = distance;
                nearest = entry.getKey();
            }
        }
        PUSHES.add(new Push(drone, knockback.cause(), knockback.deltaVelocity(), knockback.scale(), drone.level().getGameTime(), nearest,
                best == Double.MAX_VALUE ? Double.NaN : best));
    }

    public static synchronized void clearEvents() {
        HITS.clear();
        PUSHES.clear();
        SHOTS.clear();
        MobScene.clearEvents();
    }

    public static synchronized List<Hit> hits() {
        Entity drone = lab.drone;
        return HITS.stream().filter(hit -> hit.drone() == drone).toList();
    }

    public static synchronized List<Hit> hitsBy(String cause) {
        return hits().stream().filter(hit -> hit.cause().equals(cause)).toList();
    }

    public static synchronized List<Push> pushes() {
        Entity drone = lab.drone;
        return PUSHES.stream().filter(push -> push.drone() == drone).toList();
    }

    public static synchronized List<Shot> shots(Predicate<Shot> filter) {
        return SHOTS.values().stream().filter(filter).toList();
    }

    public static void watch(boolean value) {
        watching = value;
    }

    private static void watchTick(MinecraftServer server) {
        if (!watching) {
            return;
        }
        Entity drone = lab.drone;
        if (drone == null || drone.isRemoved() || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        AABB box = drone.getBoundingBox().inflate(WATCH_RADIUS);
        Vec3 center = drone.getBoundingBox().getCenter();
        for (Entity entity : level.getEntities((Entity) null, box, entity -> entity instanceof Projectile || entity instanceof EvokerFangs)) {
            record(entity, level.getGameTime(), center);
        }
    }

    private static synchronized void record(Entity entity, long tick, Vec3 droneCenter) {
        Shot known = SHOTS.get(entity.getUUID());
        if (known != null) {
            if (tick - known.tick() <= TRAVEL_TICKS) {
                SHOTS.put(entity.getUUID(), new Shot(known.id(), known.type(), known.owner(), known.tick(), known.from(), known.motion(), known.target(),
                        entity.position()));
            }
            return;
        }
        Entity owner = entity instanceof Projectile projectile ? projectile.getOwner() : entity instanceof EvokerFangs fangs ? fangs.getOwner() : null;
        SHOTS.put(entity.getUUID(), new Shot(entity.getUUID(), path(entity), path(owner), tick, entity.position(), entity.getDeltaMovement(), droneCenter,
                entity.position()));
    }

    public static Script.Stage until(int maxTicks, Probe probe) {
        String key = "covmdc.poll." + POLLS.incrementAndGet();
        return (ctx, ticks) -> {
            Map<String, Object> state = ctx.state();
            Object pending = state.get(key);
            if (pending instanceof CompletableFuture<?> future) {
                if (!future.isDone()) {
                    return ticks > maxTicks + 200 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
                }
                state.remove(key);
                Poll poll;
                try {
                    poll = (Poll) future.join();
                } catch (CompletionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    return StepResult.fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
                if (poll.done()) {
                    return StepResult.pass(poll.note());
                }
                if (ticks >= maxTicks) {
                    return StepResult.fail("nach " + maxTicks + " Ticks nicht erreicht: " + poll.note());
                }
            }
            CompletableFuture<Poll> next = new CompletableFuture<>();
            state.put(key, next);
            ctx.onServer(server -> {
                try {
                    next.complete(probe.check(server));
                } catch (RuntimeException e) {
                    next.completeExceptionally(e);
                }
            });
            return StepResult.pending();
        };
    }

    public static void reset(MinecraftServer server, int damagePercent) {
        listen();
        clearEvents();
        watch(false);
        lab = new Lab();
        Arena.rule(server, RULE_DAMAGE, String.valueOf(damagePercent));
    }

    public static Entity place(MinecraftServer server, int dx, int dz, String name) {
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(dx, dz);
        lab.ground = ground;
        lab.groundY = ground.getY() + 1.0;
        Entity drone = Arena.track(MobScene.placeDrone(level, ground, name));
        lab.drone = drone;
        return drone;
    }

    public static Entity hover(MinecraftServer server, int dx, int dz, double metres, String name) {
        Entity drone = place(server, dx, dz, name);
        start(server, drone, drone.getX(), lab.groundY + metres, drone.getZ(), true);
        return drone;
    }

    public static void start(MinecraftServer server, Entity drone, double x, double y, double z, boolean takeoff) {
        PropwashApi.drones().autonomy().stop(drone);
        AutonomyOptions options = AutonomyOptions.of(MobScene.HOVER).label("MD TEST").maxDuration(Duration.ofMinutes(20));
        if (takeoff) {
            options = options.takeoff(Mth.clamp(y - drone.getY(), TAKEOFF_MIN, AutonomyOptions.MAX_TAKEOFF_METRES));
        }
        AutonomyResult result = PropwashApi.drones().autonomy().start(drone, Arena.player(server), MobScene.hold(x, y, z), options);
        if (!result.started()) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
        lab.holdY = y;
    }

    public static void climb(MinecraftServer server, double metres) {
        Entity drone = drone();
        start(server, drone, drone.getX(), lab.holdY + metres, drone.getZ(), false);
        lab.since = -1;
        lab.checks = 0;
    }

    public static Entity drone() {
        Entity drone = lab.drone;
        if (drone == null || drone.isRemoved()) {
            throw new IllegalStateException("Drohne fehlt oder wurde zerstört");
        }
        return drone;
    }

    public static DroneView view() {
        return PropwashApi.drones().view(drone()).orElseThrow(() -> new IllegalStateException("keine Drohnen-Ansicht"));
    }

    public static double height() {
        return drone().getY() - lab.groundY;
    }

    public static DroneState state() {
        Entity drone = drone();
        DroneState state = DroneState.of(drone, drone.level().getGameTime());
        if (state == null) {
            throw new IllegalStateException("kein Drohnen-Zustand");
        }
        return state;
    }

    public static Probe airborne(double metres) {
        return server -> {
            DroneView view = view();
            double height = height();
            String note = String.format(Locale.ROOT, "Höhe %.2f m, %s", height, view.flying() ? "fliegt" : "fliegt nicht");
            if (!view.flying() || height < metres) {
                lab.checks = 0;
                return Poll.waiting(note);
            }
            return ++lab.checks >= 5 ? Poll.done(note) : Poll.waiting(note);
        };
    }

    public static Probe band(double low, double high) {
        return server -> {
            DroneView view = view();
            double height = height();
            String note = String.format(Locale.ROOT, "Höhe %.2f m, %s", height, view.flying() ? "fliegt" : "fliegt nicht");
            if (!view.flying() || height < low || height > high) {
                lab.checks = 0;
                return Poll.waiting(note);
            }
            return ++lab.checks >= 10 ? Poll.done(note) : Poll.waiting(note);
        };
    }

    public static <T extends Mob> T mob(MinecraftServer server, String key, EntityType<T> type, double dx, double dz, double dy) {
        Entity anchor = drone();
        return mobAt(server, key, type, new Vec3(anchor.getX() + dx, lab.groundY + dy, anchor.getZ() + dz), anchor.position());
    }

    public static <T extends Mob> T mobAt(MinecraftServer server, String key, EntityType<T> type, Vec3 at, Vec3 lookAt) {
        float yaw = (float) (-Mth.atan2(lookAt.x - at.x, lookAt.z - at.z) * Mth.RAD_TO_DEG);
        T mob = MobScene.spawn(Arena.level(server), type, at, yaw);
        Arena.track(mob);
        lab.mobs.put(key, mob);
        return mob;
    }

    public static Mob mob(String key) {
        Mob mob = lab.mobs.get(key);
        if (mob == null || mob.isRemoved()) {
            throw new IllegalStateException(key + " fehlt");
        }
        return mob;
    }

    public static void discard(String key) {
        Mob mob = lab.mobs.remove(key);
        if (mob != null) {
            mob.discard();
        }
    }

    public static void freeze(Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            lab.attributes.putIfAbsent(mob.getStringUUID(), speed.getBaseValue());
            speed.setBaseValue(0.0);
        }
        AttributeInstance flying = mob.getAttribute(Attributes.FLYING_SPEED);
        if (flying != null) {
            flying.setBaseValue(0.0);
        }
        mob.getNavigation().stop();
    }

    public static void tough(Mob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 600, 4, false, false), null);
    }

    public static void unfreeze(Mob mob) {
        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        Double original = lab.attributes.get(mob.getStringUUID());
        if (speed != null && original != null) {
            speed.setBaseValue(original);
        }
    }

    public static void teleport(Mob mob, double dx, double dz, double dy) {
        Entity drone = drone();
        Vec3 at = new Vec3(drone.getX() + dx, lab.groundY + dy, drone.getZ() + dz);
        float yaw = (float) (-Mth.atan2(drone.getX() - at.x, drone.getZ() - at.z) * Mth.RAD_TO_DEG);
        mob.getNavigation().stop();
        mob.teleportTo((ServerLevel) mob.level(), at.x, at.y, at.z, Set.<Relative>of(), yaw, 0.0F, true);
        mob.setYHeadRot(yaw);
    }

    public static boolean assignedTo(Mob mob) {
        Entity drone = lab.drone;
        boolean assigned = drone != null && DroneReactions.assigned(mob).filter(entity -> entity == drone).isPresent();
        if (assigned) {
            lab.assigned.add(mob);
        }
        return assigned;
    }

    public static boolean everAssigned(Mob mob) {
        return lab.assigned.contains(mob) || assignedTo(mob);
    }

    public static double flat(Entity entity) {
        Entity drone = drone();
        return Math.hypot(entity.getX() - drone.getX(), entity.getZ() - drone.getZ());
    }

    public static long now(MinecraftServer server) {
        return Arena.level(server).getGameTime();
    }

    public static String causes() {
        Map<String, Long> counts = new TreeMap<>();
        for (Hit hit : hits()) {
            counts.merge(hit.cause() + "/" + hit.direct(), 1L, Long::sum);
        }
        return counts.toString();
    }

    public static <T extends Entity> List<T> owned(ServerLevel level, Class<T> type, Entity owner) {
        return level.getEntitiesOfClass(type, Arena.box(level), candidate -> candidate instanceof Projectile projectile
                ? projectile.getOwner() == owner : candidate instanceof EvokerFangs fangs && fangs.getOwner() == owner);
    }

    public static double angle(Vec3 a, Vec3 b) {
        double la = a.length();
        double lb = b.length();
        if (la < 1.0E-6 || lb < 1.0E-6) {
            return 180.0;
        }
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b) / (la * lb), -1.0, 1.0)));
    }

    public static double flatAngle(Vec3 a, Vec3 b) {
        return angle(new Vec3(a.x, 0.0, a.z), new Vec3(b.x, 0.0, b.z));
    }

    public static int solidBlocks(ServerLevel level, BlockPos from, BlockPos to) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) {
                count++;
            }
        }
        return count;
    }

    public static void view(MinecraftServer server, double back, double up, double shift) {
        Entity drone = drone();
        Vec3 target = drone.getBoundingBox().getCenter().add(shift, 0.0, 0.0);
        Vec3 eye = new Vec3(target.x, lab.groundY + up, target.z - back);
        double dx = target.x - eye.x;
        double dy = target.y - (eye.y + 1.62);
        double dz = target.z - eye.z;
        float yaw = (float) (-Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(dy, Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
        ServerPlayer player = Arena.player(server);
        player.teleportTo(Arena.level(server), eye.x, eye.y, eye.z, Set.<Relative>of(), yaw, pitch, true);
    }

    public static String format(String pattern, Object... args) {
        return String.format(Locale.ROOT, pattern, args);
    }

    public static StepResult cleanup(SelfTest.StepContext ctx) {
        int since = (int) ctx.state().computeIfAbsent("covmdc.cleanup.since", key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            watch(false);
            MobScene.protectFromFire(false);
            Arena.command(server, "tick sprint stop");
            ServerLevel level = Arena.level(server);
            for (Entity drone : Arena.drones(level)) {
                PropwashApi.drones().autonomy().stop(drone);
            }
            for (Vex vex : level.getEntitiesOfClass(Vex.class, Arena.box(level), vex -> true)) {
                vex.discard();
            }
            for (EvokerFangs fangs : level.getEntitiesOfClass(EvokerFangs.class, Arena.box(level), fangs -> true)) {
                fangs.discard();
            }
            int projectiles = MobScene.clearProjectiles(level, Arena.box(level));
            Guests.removeAll();
            List<String> disabled = Failures.disabledTypes();
            if (!disabled.isEmpty()) {
                throw new IllegalStateException("Reaktionen abgeschaltet: " + disabled);
            }
            if (projectiles > 0) {
                MobDefense.LOG.info("[Mob-Defense-Selbsttest] {} Geschosse entfernt", projectiles);
            }
        });
    }
}
