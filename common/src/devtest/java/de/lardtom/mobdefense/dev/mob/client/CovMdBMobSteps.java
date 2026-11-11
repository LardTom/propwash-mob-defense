package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class CovMdBMobSteps {
    public static final int CALM_TICKS = 120;
    public static final double CALM_DRIFT = 3.0;
    public static final double APPROACH = 5.0;
    private static final Identifier RANGE = MobDefense.id(RuleDefaults.MOB_REACTION_RANGE.path());
    private static final Identifier DAMAGE = MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path());
    private static final String DRONE = "covmdb.mob.drone";
    private static final String MOB = "covmdb.mob.mob";
    private static final String START = "covmdb.mob.start";
    private static final String NOTE = "covmdb.mob.note";
    private static final String ASSIGNED = "covmdb.mob.assigned";
    private static final String CLOSEST = "covmdb.mob.closest";
    private static final String HITS = "covmdb.mob.hits";
    private static final String PENDING = "covmdb.mob.pending";

    private CovMdBMobSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Welt-Regeln: Reichweite – mob_reaction_range 8: Zombie in 15 Blöcken bleibt ruhig; 64: Zombie in 40 Blöcken mit Sichtlinie nähert sich",
                3000, Script.create()
                        .server("Drohne schwebt auf 3 m, Reichweite 8", (server, state) -> {
                            hover(server, state, Arena.ground(-20, 34), 3.0);
                            Arena.rule(server, RANGE, "8");
                        })
                        .then("Drohne in der Luft", poll(400, CovMdBMobSteps::airborne))
                        .server("Zombie in 15 Blöcken ohne Eigenbewegung", (server, state) -> {
                            zombie(server, state, 15.0);
                            ((Mob) state.get(MOB)).getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0);
                        })
                        .then("Zombie bleibt ruhig", poll(CALM_TICKS + 40, CovMdBMobSteps::calm))
                        .server("Reichweite 64, Zombie in 40 Blöcken", (server, state) -> {
                            Arena.rule(server, RANGE, "64");
                            ((Entity) state.get(MOB)).discard();
                            zombie(server, state, 40.0);
                        })
                        .then("Zombie nähert sich", poll(900, CovMdBMobSteps::approached)), CovMdBMobSteps::cleanup);
        steps.add("Welt-Regeln: Mob-Schaden an Drohnen – mob_damage_to_drones_percent 0, 100 und 400 bei Skelett-Treffern: Schaden 0, einfach, vierfach", 4800,
                Script.create()
                        .server("Drohne schwebt auf 4 m, Skelett in 10 m", (server, state) -> {
                            hover(server, state, Arena.ground(-20, 52), 4.0);
                            state.put(HITS, new ArrayList<String>());
                        })
                        .then("Drohne in der Luft", poll(400, CovMdBMobSteps::airborne))
                        .server("Skelett", (server, state) -> state.put(MOB, mob(server, state, EntityTypes.SKELETON, 10.0)))
                        .append(percent(0))
                        .append(percent(100))
                        .append(percent(400))
                        .then("Ergebnis", (ctx, ticks) -> {
                            @SuppressWarnings("unchecked")
                            List<String> hits = (List<String>) ctx.state().get(HITS);
                            return hits.size() == 3 ? StepResult.pass() : StepResult.fail("nur " + hits.size() + " Stufen: " + hits);
                        }), CovMdBMobSteps::cleanup);
    }

    private record Poll(boolean done, String note) {
    }

    private static Script.Stage poll(int maxTicks, Function<MinecraftServer, Poll> probe) {
        return (ctx, ticks) -> {
            Map<String, Object> state = ctx.state();
            Object pending = state.get(PENDING);
            if (pending instanceof CompletableFuture<?> future) {
                if (!future.isDone()) {
                    return ticks > maxTicks + 200 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
                }
                state.remove(PENDING);
                if (future.isCompletedExceptionally()) {
                    Throwable cause = future.exceptionNow();
                    return StepResult.fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
                Poll poll = (Poll) future.join();
                if (poll.done()) {
                    return StepResult.pass(poll.note());
                }
                if (ticks >= maxTicks) {
                    return StepResult.fail("nach " + maxTicks + " Ticks nicht erreicht: " + poll.note());
                }
            }
            if (ticks % 5 != 0) {
                return StepResult.pending();
            }
            CompletableFuture<Poll> next = new CompletableFuture<>();
            state.put(PENDING, next);
            ctx.onServer(server -> {
                try {
                    next.complete(probe.apply(server));
                } catch (RuntimeException e) {
                    next.completeExceptionally(e);
                }
            });
            return StepResult.pending();
        };
    }

    private static Map<String, Object> state;

    private static void hover(MinecraftServer server, Map<String, Object> values, BlockPos ground, double metres) {
        state = values;
        MobScene.listen();
        MobScene.clearEvents();
        Entity drone = Arena.track(MobScene.placeDrone(Arena.level(server), ground, "CovMdB Mob"));
        AutonomyResult result = MobScene.hover(drone, Arena.player(server), metres);
        if (!result.started()) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
        values.put(DRONE, drone);
        values.put("covmdb.mob.ground", ground.getY() + 1.0);
        values.put("covmdb.mob.metres", metres);
    }

    private static Entity drone() {
        Entity drone = (Entity) state.get(DRONE);
        if (drone == null || drone.isRemoved()) {
            throw new IllegalStateException("Drohne fehlt oder wurde zerstört");
        }
        return drone;
    }

    private static Poll airborne(MinecraftServer server) {
        Entity drone = drone();
        double height = drone.getY() - (double) state.get("covmdb.mob.ground");
        boolean flying = PropwashApi.drones().view(drone).map(view -> view.flying()).orElse(false);
        return new Poll(flying && height >= (double) state.get("covmdb.mob.metres") - 0.5, String.format(Locale.ROOT, "Höhe %.1f m", height));
    }

    private static <T extends Mob> T mob(MinecraftServer server, Map<String, Object> values, EntityType<T> type, double distance) {
        Entity drone = drone();
        Vec3 at = new Vec3(drone.getX() + distance, (double) values.get("covmdb.mob.ground"), drone.getZ());
        float yaw = (float) (-Mth.atan2(drone.getX() - at.x, drone.getZ() - at.z) * Mth.RAD_TO_DEG);
        T mob = MobScene.spawn(Arena.level(server), type, at, yaw);
        Arena.track(mob);
        return mob;
    }

    private static void zombie(MinecraftServer server, Map<String, Object> values, double distance) {
        Mob zombie = mob(server, values, EntityTypes.ZOMBIE, distance);
        values.put(MOB, zombie);
        values.put(START, MobScene.horizontal(zombie, drone().position()));
        values.put(CLOSEST, MobScene.horizontal(zombie, drone().position()));
        values.put(ASSIGNED, false);
        values.put("covmdb.mob.since", Arena.level(server).getGameTime());
    }

    private static Poll calm(MinecraftServer server) {
        Mob zombie = (Mob) state.get(MOB);
        Entity drone = drone();
        double start = (double) state.get(START);
        double distance = MobScene.horizontal(zombie, drone.position());
        boolean assigned = (boolean) state.get(ASSIGNED) || DroneReactions.assigned(zombie).isPresent();
        state.put(ASSIGNED, assigned);
        double closest = Math.min((double) state.get(CLOSEST), distance);
        state.put(CLOSEST, closest);
        long waited = Arena.level(server).getGameTime() - (long) state.get("covmdb.mob.since");
        String note = String.format(Locale.ROOT, "Reichweite 8: Zombie %.1f → %.1f Blöcke (nächster Punkt %.1f), zugeordnet %s nach %d Ticks", start, distance,
                closest, assigned ? "ja" : "nein", waited);
        if (assigned || start - closest > CALM_DRIFT) {
            throw new IllegalStateException(note);
        }
        return new Poll(waited >= CALM_TICKS, note);
    }

    private static Poll approached(MinecraftServer server) {
        Mob zombie = (Mob) state.get(MOB);
        Entity drone = drone();
        double start = (double) state.get(START);
        double distance = MobScene.horizontal(zombie, drone.position());
        boolean assigned = (boolean) state.get(ASSIGNED) || DroneReactions.assigned(zombie).filter(entity -> entity == drone).isPresent();
        state.put(ASSIGNED, assigned);
        String note = String.format(Locale.ROOT, "Reichweite 64: Zombie %.1f → %.1f Blöcke, zugeordnet %s, Sichtlinie %s", start, distance, assigned ? "ja" : "nein",
                zombie.hasLineOfSight(drone) ? "ja" : "nein");
        return new Poll(assigned && start - distance >= APPROACH, note);
    }

    private static Script percent(int percent) {
        return Script.create()
                .server("Mob-Schaden " + percent + " %", (server, values) -> {
                    Arena.rule(server, DAMAGE, Integer.toString(percent));
                    MobScene.clearEvents();
                })
                .then("Skelett trifft bei " + percent + " %", poll(1200, server -> hit(percent)));
    }

    @SuppressWarnings("unchecked")
    private static Poll hit(int percent) {
        Entity drone = (Entity) state.get(DRONE);
        List<MobScene.Hit> hits = MobScene.hits(drone).stream().filter(hit -> hit.cause().equals("skeleton")).toList();
        Mob skeleton = (Mob) state.get(MOB);
        String note = String.format(Locale.ROOT, "%d %%: %d Treffer, Skelett zugeordnet %s", percent, hits.size(),
                DroneReactions.assigned(skeleton).isPresent() ? "ja" : "nein");
        if (hits.isEmpty()) {
            return new Poll(false, note);
        }
        MobScene.Hit hit = hits.getFirst();
        float expected = hit.original() * percent / 100.0F;
        String result = String.format(Locale.ROOT, "%d %%: Skelett-Treffer (%s) %.2f → %.2f (erwartet %.2f)", percent, hit.direct(), hit.original(), hit.amount(),
                expected);
        if (!(hit.original() > 0.0F) || Math.abs(hit.amount() - expected) > 0.01F) {
            throw new IllegalStateException(result);
        }
        if (drone.isRemoved()) {
            result += ", Drohne danach zerstört";
        }
        ((List<String>) state.get(HITS)).add(result);
        return new Poll(true, result);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        int since = (int) ctx.state().computeIfAbsent("covmdb.mob.cleanup", key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, values) -> {
            Object drone = values.get(DRONE);
            if (drone instanceof Entity entity && !entity.isRemoved()) {
                PropwashApi.drones().autonomy().stop(entity);
            }
            ServerLevel level = Arena.level(server);
            MobScene.clearProjectiles(level, Arena.box(level));
        });
    }
}
