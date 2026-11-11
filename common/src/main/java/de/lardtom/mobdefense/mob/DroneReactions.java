package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.mixin.MobAccessor;
import de.lardtom.mobdefense.mob.core.Budget;
import de.lardtom.mobdefense.mob.core.ReactionLimits;
import de.lardtom.mobdefense.mob.core.Selection;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;

public final class DroneReactions {
    private static final Predicate<DroneView> ENGAGED = view -> view.armed() || view.autopilot();
    private static final Map<Mob, Assignment> ASSIGNED = new WeakHashMap<>();
    private static final Map<Mob, Boolean> GOALS = new WeakHashMap<>();
    private static final Map<ServerLevel, Selection.Pending<Entity, Mob>> PENDING = new WeakHashMap<>();
    private static volatile Predicate<DroneView> active = ENGAGED;
    private static boolean initialized;
    private static boolean tickFailureLogged;
    private static long ticks;
    private static long nanos;
    private static long searches;
    private static long evaluations;
    private static int maxEvaluations;
    private static long mobHits;

    public record Stats(long ticks, long nanos, long searches, long evaluations, int maxEvaluations, long mobHits) {
        public double microsPerTick() {
            return this.ticks == 0 ? 0.0 : this.nanos / 1000.0 / this.ticks;
        }
    }

    private DroneReactions() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MobDefense.onServerTick(DroneReactions::tick);
        PropwashEvents.DRONE_DAMAGED.register(DroneReactions::onDamaged);
        PropwashEvents.DRONE_KNOCKBACK.register(Knockbacks::onKnockback);
        PropwashEvents.DRONE_DAMAGED.register(Levitations::onDamaged);
        MobDefense.onServerTick(Levitations::tick);
        MobDefense.LOG.info("{}: Mob-Reaktionen bereit, Arten {}", MobDefense.MOD_NAME, ReactionKinds.names());
    }

    public static void setActiveFilter(@Nullable Predicate<DroneView> filter) {
        active = filter == null ? ENGAGED : filter;
    }

    public static boolean active(DroneState drone) {
        return active.test(drone.view());
    }

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            long start = System.nanoTime();
            int used = 0;
            try {
                used = tickLevel(level);
            } catch (RuntimeException e) {
                if (!tickFailureLogged) {
                    tickFailureLogged = true;
                    MobDefense.LOG.warn("{}: Reaktions-Takt in {} fehlgeschlagen", MobDefense.MOD_NAME, level.dimension().identifier(), e);
                }
                releaseLevel(level);
            }
            record(System.nanoTime() - start, used);
        }
    }

    private static int tickLevel(ServerLevel level) {
        long now = level.getGameTime();
        if (!MobDefenseRules.flag(level, RuleDefaults.MOBS_REACT_TO_DRONES)) {
            releaseLevel(level);
            return 0;
        }
        List<Entity> loaded = PropwashApi.drones().loaded(level);
        if (loaded.isEmpty()) {
            releaseLevel(level);
            return 0;
        }
        List<DroneState> drones = new ArrayList<>(loaded.size());
        for (Entity entity : loaded) {
            DroneState state = DroneState.of(entity, now);
            if (state != null && active(state)) {
                drones.add(state);
            }
        }
        if (drones.isEmpty()) {
            releaseLevel(level);
            return 0;
        }
        int range = MobDefenseRules.number(level, RuleDefaults.MOB_REACTION_RANGE);
        Budget budget = new Budget(ReactionLimits.EVALUATIONS_PER_TICK);
        Selection.Pending<Entity, Mob> pending = PENDING.computeIfAbsent(level, key -> new Selection.Pending<>());
        pending.resume(budget, now);
        for (DroneState drone : drones) {
            if (!ReactionLimits.due(now, drone.entity().getId(), ReactionLimits.SEARCH_PERIOD) || pending.waiting(drone.entity())) {
                continue;
            }
            Search search = new Search(level, drone.entity(), range);
            countSearch();
            pending.start(drone.entity(), Selection.nearest(candidates(level, drone, range), mob -> mob.distanceToSqr(drone.center())), search::acceptor,
                    budget, now);
        }
        driveBrains(level);
        expire(level, now);
        Threats.publish(level, now, threatened(level, now));
        return budget.used();
    }

    private static Collection<DroneState> threatened(ServerLevel level, long now) {
        Map<Entity, DroneState> out = new LinkedHashMap<>();
        for (Map.Entry<Mob, Assignment> entry : entries(level)) {
            Assignment assignment = entry.getValue();
            if (!assignment.started || assignment.until < now || !entry.getKey().isAlive() || out.containsKey(assignment.drone)) {
                continue;
            }
            DroneState drone = DroneState.of(assignment.drone, now);
            if (drone != null && active(drone)) {
                out.put(assignment.drone, drone);
            }
        }
        return out.values();
    }

    private static List<Mob> candidates(ServerLevel level, DroneState drone, int range) {
        AABB box = new AABB(drone.center(), drone.center()).inflate(range);
        return level.getEntitiesOfClass(Mob.class, box, mob -> mob.isAlive() && !mob.isNoAi() && !Failures.disabled(mob.getType())
                && ReactionTags.reaction(mob) != null);
    }

    private static boolean evaluate(ServerLevel level, Mob mob, DroneState drone, int range, long now) {
        if (!mob.isAlive() || mob.level() != level) {
            return false;
        }
        Reaction reaction = ReactionTags.reaction(mob);
        if (reaction == null || owned(mob)) {
            return false;
        }
        try {
            if (!reaction.accepts(mob)) {
                return false;
            }
            double distance = drone.distanceFrom(mob);
            if (distance > ReactionLimits.range(range, reaction.maxRange(mob))) {
                return false;
            }
            boolean brain = Moves.brain(mob);
            LivingEntity target = target(mob);
            if (target != null && target.isAlive() && (brain || !ReactionLimits.prefersDrone(distance, mob.distanceTo(target)))) {
                return false;
            }
            Assignment current = ASSIGNED.get(mob);
            boolean same = current != null && current.drone == drone.entity() && current.reaction == reaction;
            boolean sees = mob.getSensing().hasLineOfSight(drone.entity());
            if (!sees && !(same && ReactionLimits.remembers(now, current.seen))) {
                return false;
            }
            if (!reaction.wants(mob, drone, distance)) {
                return false;
            }
            if (current != null && !same && current.until >= now) {
                DroneState other = DroneState.of(current.drone, now);
                if (other != null && active(other) && other.distanceFrom(mob) <= distance) {
                    return false;
                }
            }
            if (!brain && !install(mob, reaction)) {
                return false;
            }
            if (!same) {
                if (current != null) {
                    release(mob, current);
                }
                current = new Assignment(drone.entity(), reaction, brain, now);
                ASSIGNED.put(mob, current);
            }
            current.until = now + ReactionLimits.HOLD_TICKS;
            if (sees) {
                current.seen = now;
            }
            return true;
        } catch (RuntimeException | LinkageError e) {
            Failures.disable(mob, reaction.kind(), e);
            ASSIGNED.remove(mob);
            return false;
        }
    }

    private static boolean install(Mob mob, Reaction reaction) {
        if (GOALS.containsKey(mob)) {
            return true;
        }
        if (!Access.MOB) {
            Failures.disableQuietly(mob);
            return false;
        }
        try {
            ((MobAccessor) mob).mobdefense$goalSelector().addGoal(reaction.priority(), new DroneGoal(mob));
            GOALS.put(mob, Boolean.TRUE);
            return true;
        } catch (RuntimeException | LinkageError e) {
            Failures.disable(mob, reaction.kind(), e);
            return false;
        }
    }

    static @Nullable Assignment usable(Mob mob) {
        Assignment assignment = ASSIGNED.get(mob);
        if (assignment == null || !mob.isAlive()) {
            return null;
        }
        long now = mob.level().getGameTime();
        if (assignment.until < now || Failures.disabled(mob.getType())) {
            return null;
        }
        DroneState drone = DroneState.of(assignment.drone, now);
        if (drone == null || drone.entity().level() != mob.level() || !active(drone)) {
            return null;
        }
        double distance = drone.distanceFrom(mob);
        LivingEntity target = target(mob);
        if (target != null && target.isAlive() && (assignment.brain || !ReactionLimits.prefersDrone(distance, mob.distanceTo(target)))) {
            return null;
        }
        try {
            return assignment.reaction.wants(mob, drone, distance) ? assignment : null;
        } catch (RuntimeException | LinkageError e) {
            fail(mob, assignment, e);
            return null;
        }
    }

    static void begin(Mob mob, Assignment assignment) {
        assignment.started = true;
        assignment.memory.reset();
        DroneState drone = DroneState.of(assignment.drone, mob.level().getGameTime());
        if (drone == null) {
            return;
        }
        try {
            assignment.reaction.start(mob, drone, assignment.memory);
        } catch (RuntimeException | LinkageError e) {
            fail(mob, assignment, e);
        }
    }

    static void drive(Mob mob, Assignment assignment) {
        DroneState drone = DroneState.of(assignment.drone, mob.level().getGameTime());
        if (drone == null) {
            return;
        }
        try {
            assignment.reaction.tick(mob, drone, assignment.memory);
        } catch (RuntimeException | LinkageError e) {
            fail(mob, assignment, e);
        }
    }

    static void end(Mob mob, Assignment assignment) {
        assignment.started = false;
        try {
            assignment.reaction.stop(mob, assignment.memory);
        } catch (RuntimeException | LinkageError e) {
            Failures.disable(mob, assignment.reaction.kind(), e);
        }
    }

    private static void fail(Mob mob, Assignment assignment, Throwable error) {
        Failures.disable(mob, assignment.reaction.kind(), error);
        assignment.until = Long.MIN_VALUE;
    }

    private static void release(Mob mob, Assignment assignment) {
        assignment.until = Long.MIN_VALUE;
        if (assignment.brain && assignment.started) {
            end(mob, assignment);
        }
    }

    private static List<Map.Entry<Mob, Assignment>> entries(ServerLevel level) {
        if (ASSIGNED.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<Mob, Assignment>> out = new ArrayList<>();
        for (Map.Entry<Mob, Assignment> entry : ASSIGNED.entrySet()) {
            Mob mob = entry.getKey();
            if (mob != null && mob.level() == level) {
                out.add(Map.entry(mob, entry.getValue()));
            }
        }
        return out;
    }

    private static void driveBrains(ServerLevel level) {
        for (Map.Entry<Mob, Assignment> entry : entries(level)) {
            Mob mob = entry.getKey();
            Assignment assignment = entry.getValue();
            if (!assignment.brain) {
                continue;
            }
            if (usable(mob) == assignment) {
                if (!assignment.started) {
                    begin(mob, assignment);
                }
                if (assignment.started && ASSIGNED.get(mob) == assignment) {
                    drive(mob, assignment);
                }
            } else if (assignment.started) {
                end(mob, assignment);
            }
        }
    }

    private static void expire(ServerLevel level, long now) {
        for (Map.Entry<Mob, Assignment> entry : entries(level)) {
            Mob mob = entry.getKey();
            Assignment assignment = entry.getValue();
            if (assignment.until < now || !mob.isAlive()) {
                if (assignment.brain && assignment.started) {
                    end(mob, assignment);
                }
                ASSIGNED.remove(mob, assignment);
            }
        }
    }

    private static void releaseLevel(ServerLevel level) {
        PENDING.remove(level);
        for (Map.Entry<Mob, Assignment> entry : entries(level)) {
            release(entry.getKey(), entry.getValue());
            ASSIGNED.remove(entry.getKey(), entry.getValue());
        }
    }

    public static @Nullable LivingEntity target(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            Brain<?> brain = mob.getBrain();
            if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
                target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
            }
        }
        return target;
    }

    private static boolean owned(Mob mob) {
        return mob instanceof TamableAnimal tame && tame.isTame();
    }

    private static void onDamaged(DroneDamage damage) {
        if (!(damage.source().getEntity() instanceof Mob)) {
            return;
        }
        countHit();
        int percent = MobDefenseRules.number(damage.level(), RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT);
        if (percent != 100) {
            damage.setAmount(damage.amount() * percent / 100.0F);
        }
    }

    public static Optional<Entity> assigned(Mob mob) {
        Assignment assignment = usable(mob);
        return assignment == null ? Optional.empty() : Optional.of(assignment.drone);
    }

    public static Optional<String> kind(Mob mob) {
        Reaction reaction = ReactionTags.reaction(mob);
        return reaction == null ? Optional.empty() : Optional.of(reaction.kind());
    }

    public static boolean goalInstalled(Mob mob) {
        return GOALS.containsKey(mob);
    }

    public static int assignments(ServerLevel level) {
        return entries(level).size();
    }

    private static synchronized void record(long spent, int used) {
        ticks++;
        nanos += spent;
        evaluations += used;
        maxEvaluations = Math.max(maxEvaluations, used);
    }

    private static synchronized void countSearch() {
        searches++;
    }

    private static synchronized void countHit() {
        mobHits++;
    }

    public static synchronized Stats stats() {
        return new Stats(ticks, nanos, searches, evaluations, maxEvaluations, mobHits);
    }

    public static synchronized void resetStats() {
        ticks = 0;
        nanos = 0;
        searches = 0;
        evaluations = 0;
        maxEvaluations = 0;
        mobHits = 0;
    }

    private record Search(ServerLevel level, Entity drone, int range) {
        private Optional<Predicate<Mob>> acceptor(long now) {
            DroneState state = DroneState.of(this.drone, now);
            if (state == null || !active(state)) {
                return Optional.empty();
            }
            return Optional.of(mob -> evaluate(this.level, mob, state, this.range, now));
        }
    }
}
