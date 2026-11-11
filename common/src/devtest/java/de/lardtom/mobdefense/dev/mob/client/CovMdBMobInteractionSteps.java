package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.OsdNotices;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.client.HoverAssist;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.Levitations;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class CovMdBMobInteractionSteps {
    public static final double HOVER = 3.0;
    public static final double SHORE = 7.0;
    public static final int POOL_NEAR = 3;
    public static final int POOL_FAR = 10;
    public static final int GUARDIAN_CHARGE = 80;
    public static final int ELDER_CHARGE = 60;
    public static final int CHARGE_SLACK = 300;
    public static final float BEAM_BASE = 1.0F;
    public static final float HARD_EXTRA = 2.0F;
    public static final float ELDER_EXTRA = 2.0F;
    public static final double LIFT_MIN = 2.0;
    public static final int LIFT_TICKS = 100;
    public static final int LEVITATION_TICKS = 200;
    public static final int PEAK_EARLIEST = 170;
    public static final int PEAK_LATEST = 245;
    public static final double BACK_MARGIN = 1.0;
    public static final int BACK_TICKS = 700;
    public static final int PARKED_TICKS = 80;
    public static final double PARKED_DRIFT = 0.1;
    public static final float PARKED_DAMAGE = 4.0F;
    private static final Identifier DAMAGE = MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path());
    private static final List<Hit> HITS = new ArrayList<>();
    private static final AtomicInteger POLLS = new AtomicInteger();
    private static boolean listening;
    private static Lab lab = new Lab();

    private record Hit(Entity drone, String cause, String direct, float original, long tick, double y) {
    }

    private record Poll(boolean done, String note) {
    }

    private static final class Lab {
        @Nullable Entity drone;
        @Nullable Mob mob;
        double groundY;
        double holdY;
        long assignedAt = -1;
        int sighted;
        long hitAt = -1;
        double hitY;
        double peakY = Double.NEGATIVE_INFINITY;
        long peakAt = -1;
        double restY;
        @Nullable Hit hit;
    }

    private CovMdBMobInteractionSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("Wächter-Laser: scharfe Drohne über Land – Wächter trifft nach 80, Großer Wächter nach 60 Ticks Ladezeit, Schaden wie bei Spielern", 4200,
                Script.create()
                        .server("Drohne schwebt auf 3 m über Gras, Becken 3 bis 11 m daneben", (server, state) -> {
                            hover(server, Arena.ground(-40, 30));
                            shore(server);
                        })
                        .then("Drohne in der Luft", poll(400, server -> airborne()))
                        .server("Wächter im Becken 7 m daneben", (server, state) -> guardian(server, EntityTypes.GUARDIAN))
                        .then("Wächter erfasst die Drohne und trifft nach der Ladezeit", poll(GUARDIAN_CHARGE + CHARGE_SLACK + 400,
                                server -> beamed(server, "guardian", GUARDIAN_CHARGE, 0.0F)))
                        .server("Großer Wächter statt des Wächters", (server, state) -> guardian(server, EntityTypes.ELDER_GUARDIAN))
                        .then("Großer Wächter erfasst die Drohne und trifft nach der Ladezeit", poll(ELDER_CHARGE + CHARGE_SLACK + 400,
                                server -> beamed(server, "elder_guardian", ELDER_CHARGE, ELDER_EXTRA))), CovMdBMobInteractionSteps::cleanup);
        steps.add("Shulker-Geschoss: Treffer hebt die autonom schwebende Drohne 10 s lang wie Schwebekraft an, danach sinkt sie auf ihre Höhe zurück", 3600,
                Script.create()
                        .server("Drohne schwebt auf 3 m", (server, state) -> hover(server, Arena.ground(-40, 60)))
                        .then("Drohne in der Luft", poll(400, server -> airborne()))
                        .server("Shulker 7 m daneben", (server, state) -> shulker(server))
                        .then("Geschoss trifft, Shulker und Geschosse weg", poll(900, CovMdBMobInteractionSteps::bulletHit))
                        .then("Drohne steigt trotz Höhenhalten um mindestens 2 m in 5 s", poll(LIFT_TICKS + 40, CovMdBMobInteractionSteps::lifted))
                        .then("Schwebekraft endet nach 10 s, die Drohne sinkt auf ihre Höhe zurück", poll(BACK_TICKS, CovMdBMobInteractionSteps::back)),
                CovMdBMobInteractionSteps::cleanup);
        steps.add("Shulker-Geschoss: abgestellte Drohne (nicht scharf, kein Autopilot) bleibt nach dem Treffer liegen", 2400,
                Script.create()
                        .server("Drohne abgestellt, Treffer eines Shulker-Geschosses wie im Flug (DamageSources#mobProjectile)", CovMdBMobInteractionSteps::parked)
                        .then("Treffer kommt als DRONE_DAMAGED an", poll(200, CovMdBMobInteractionSteps::parkedHit))
                        .then("Drohne bleibt liegen", poll(PARKED_TICKS + 40, CovMdBMobInteractionSteps::parkedStays)),
                CovMdBMobInteractionSteps::cleanup);
        steps.add("Mit Pilot: Wächter-Laser und Shulker-Geschoss an der geflogenen 5″ – BEDROHUNG am Client, Laser nach der Ladezeit, Auftrieb am Client", 6000,
                Script.create()
                        .server("5″ mit Pilot über Gras, Becken daneben, Mob-Schaden 0 %", CovMdBMobInteractionSteps::piloted)
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(HOVER))
                        .run("Position halten", () -> Pilot.drone().ifPresent(d -> HoverAssist.hold(d.getX(), HoverAssist.targetY(), d.getZ())))
                        .then("schwebt ruhig", (ctx, ticks) -> steady(ticks))
                        .server("Wächter im Becken 7 m daneben", (server, state) -> {
                            lab.drone = Pilot.serverDrone(server, state);
                            guardian(server, EntityTypes.GUARDIAN);
                        })
                        .then("BEDROHUNG am Client", (ctx, ticks) -> threat(ticks))
                        .then("Laser trifft nach der Ladezeit", poll(GUARDIAN_CHARGE + CHARGE_SLACK + 400,
                                server -> beamed(server, "guardian", GUARDIAN_CHARGE, 0.0F)))
                        .server("Wächter weg, Shulker 7 m daneben", (server, state) -> shulker(server))
                        .then("Geschoss trifft, Shulker und Geschosse weg", poll(900, CovMdBMobInteractionSteps::bulletHit))
                        .then("Drohne mit Pilot steigt trotz Höhenhalten um mindestens 2 m in 5 s", (ctx, ticks) -> clientLifted(ticks)),
                PILOT_CLEANUP);
    }

    private static final SelfTest.ClientStep PILOT_CLEANUP = Script.create()
            .run("Tasten los, Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
            .server("Flug beenden, Kreativmodus", (server, state) -> {
                Pilot.release(server);
                Arena.player(server).setGameMode(GameType.CREATIVE);
            })
            .then("Aufräumen", (ctx, ticks) -> cleanup(ctx));

    private static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.DRONE_DAMAGED.register(CovMdBMobInteractionSteps::damaged);
    }

    private static synchronized void damaged(DroneDamage damage) {
        Entity cause = damage.source().getEntity();
        Entity direct = damage.source().getDirectEntity();
        HITS.add(new Hit(damage.drone(), cause == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(cause.getType()).getPath(),
                direct == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getPath(), damage.originalAmount(),
                damage.level().getGameTime(), damage.drone().getY()));
    }

    private static synchronized List<Hit> hits(Entity drone, String cause, String direct) {
        return HITS.stream().filter(hit -> hit.drone() == drone && hit.cause().equals(cause) && hit.direct().equals(direct)).toList();
    }

    private static synchronized void reset() {
        HITS.clear();
        lab = new Lab();
    }

    private static Script.Stage poll(int maxTicks, Function<MinecraftServer, Poll> probe) {
        String key = "covmdb.interaction.poll." + POLLS.incrementAndGet();
        return (ctx, ticks) -> {
            Map<String, Object> state = ctx.state();
            Object pending = state.get(key);
            if (pending instanceof CompletableFuture<?> future) {
                if (!future.isDone()) {
                    return ticks > maxTicks + 200 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
                }
                state.remove(key);
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
            state.put(key, next);
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

    private static void hover(MinecraftServer server, BlockPos ground) {
        Arena.rule(server, DAMAGE, "0");
        listen();
        reset();
        lab.groundY = ground.getY() + 1.0;
        Entity drone = Arena.track(MobScene.placeDrone(Arena.level(server), ground, "CovMdB Mob"));
        lab.holdY = drone.getY() + HOVER;
        AutonomyResult result = MobScene.hover(drone, Arena.player(server), HOVER);
        if (!result.started()) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
        lab.drone = drone;
    }

    private static void piloted(MinecraftServer server, Map<String, Object> state) {
        Arena.rule(server, DAMAGE, "0");
        listen();
        reset();
        BlockPos ground = Arena.ground(-40, 90);
        lab.groundY = ground.getY() + 1.0;
        lab.drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "CovMdB Pilot", ground, 180.0F);
        shore(server);
    }

    private static void shore(MinecraftServer server) {
        Entity drone = drone();
        BlockPos below = BlockPos.containing(drone.getX(), lab.groundY - 1.0, drone.getZ());
        Arena.fill(Arena.level(server), below.offset(POOL_NEAR, -2, -2), below.offset(POOL_FAR, 0, 2), Blocks.WATER.defaultBlockState());
    }

    private static Entity drone() {
        Entity drone = lab.drone;
        if (drone == null || drone.isRemoved()) {
            throw new IllegalStateException("Drohne fehlt oder wurde zerstört");
        }
        return drone;
    }

    private static <T extends Mob> T spawn(MinecraftServer server, EntityType<T> type, Vec3 at) {
        Entity drone = drone();
        float yaw = (float) (-Mth.atan2(drone.getX() - at.x, drone.getZ() - at.z) * Mth.RAD_TO_DEG);
        T mob = MobScene.spawn(Arena.level(server), type, at, yaw);
        Arena.track(mob);
        return mob;
    }

    private static void guardian(MinecraftServer server, EntityType<? extends Mob> type) {
        Entity drone = drone();
        if (lab.mob != null) {
            lab.mob.discard();
        }
        BlockPos below = BlockPos.containing(drone.getX(), lab.groundY - 1.0, drone.getZ());
        lab.mob = spawn(server, type, new Vec3(below.getX() + SHORE + 0.5, lab.groundY - 1.8, below.getZ() + 0.5));
        lab.assignedAt = -1;
        lab.sighted = 0;
        synchronized (CovMdBMobInteractionSteps.class) {
            HITS.clear();
        }
    }

    private static void shulker(MinecraftServer server) {
        Entity drone = drone();
        if (lab.mob != null) {
            lab.mob.discard();
        }
        ServerLevel level = Arena.level(server);
        MobScene.clearProjectiles(level, Arena.box(level));
        lab.mob = spawn(server, EntityTypes.SHULKER, new Vec3(Math.floor(drone.getX()) - SHORE + 0.5, lab.groundY, Math.floor(drone.getZ()) + 0.5));
        lab.hitAt = -1;
        synchronized (CovMdBMobInteractionSteps.class) {
            HITS.clear();
        }
    }

    private static Poll airborne() {
        Entity drone = drone();
        double height = drone.getY() - lab.groundY;
        boolean flying = PropwashApi.drones().view(drone).map(DroneView::flying).orElse(false);
        return new Poll(flying && height >= HOVER - 0.5, String.format(Locale.ROOT, "Höhe %.1f m", height));
    }

    private static Poll beamed(MinecraftServer server, String cause, int charge, float elder) {
        Entity drone = drone();
        Mob guardian = lab.mob;
        if (guardian == null || guardian.isRemoved()) {
            throw new IllegalStateException(cause + " fehlt");
        }
        long now = Arena.level(server).getGameTime();
        boolean assigned = DroneReactions.assigned(guardian).filter(entity -> entity == drone).isPresent();
        if (assigned && lab.assignedAt < 0) {
            lab.assignedAt = now;
        }
        boolean sees = guardian.hasLineOfSight(drone);
        if (sees) {
            lab.sighted++;
        }
        List<Hit> hits = hits(drone, cause, cause);
        double height = drone.getY() - lab.groundY;
        String note = String.format(Locale.ROOT, "%s %s, im Wasser %s, Augen %.1f m unter dem Rand, Sichtlinie jetzt %s und in %d Prüfungen, Drohne %.1f m über Gras, "
                + "%d Treffer", cause, lab.assignedAt < 0 ? "frei" : "zugeordnet seit " + (now - lab.assignedAt) + " Ticks", guardian.isInWater() ? "ja" : "nein",
                lab.groundY - guardian.getEyeY(), sees ? "ja" : "nein", lab.sighted, height, hits.size());
        if (hits.isEmpty()) {
            return new Poll(false, note);
        }
        if (lab.assignedAt < 0) {
            throw new IllegalStateException("Treffer ohne beobachtete Zuordnung: " + note);
        }
        Hit first = hits.getFirst();
        long delay = first.tick() - lab.assignedAt;
        float magic = BEAM_BASE + (Arena.level(server).getDifficulty() == Difficulty.HARD ? HARD_EXTRA : 0.0F) + elder;
        String result = String.format(Locale.ROOT, "%s: erster Treffer %d Ticks nach der Zuordnung (Ladezeit %d), Schaden %.1f (erwartet %.1f), %d Treffer", cause,
                delay, charge, first.original(), magic, hits.size());
        if (delay < charge || delay > charge + CHARGE_SLACK) {
            throw new IllegalStateException("Ladezeit falsch: " + result);
        }
        if (Math.abs(first.original() - magic) > 0.01F) {
            throw new IllegalStateException("Schaden falsch: " + result);
        }
        return new Poll(true, result);
    }

    private static Poll bulletHit(MinecraftServer server) {
        Entity drone = drone();
        List<Hit> hits = hits(drone, "shulker", "shulker_bullet");
        String note = String.format(Locale.ROOT, "Shulker %s, Drohne %.1f m hoch, %d Treffer", lab.mob != null && DroneReactions.assigned(lab.mob).isPresent()
                ? "zugeordnet" : "frei", drone.getY() - lab.groundY, hits.size());
        if (hits.isEmpty()) {
            return new Poll(false, note);
        }
        lab.hit = hits.getFirst();
        lab.hitAt = lab.hit.tick();
        lab.hitY = lab.hit.y();
        if (lab.mob != null) {
            lab.mob.discard();
        }
        ServerLevel level = Arena.level(server);
        int removed = MobScene.clearProjectiles(level, Arena.box(level));
        return new Poll(true, note + String.format(Locale.ROOT, ", Treffer bei %.2f m, %d Geschosse entfernt", lab.hitY - lab.groundY, removed));
    }

    private static Poll lifted(MinecraftServer server) {
        Entity drone = drone();
        long now = Arena.level(server).getGameTime();
        track(drone, now);
        double rise = drone.getY() - lab.holdY;
        long since = now - lab.hitAt;
        String note = String.format(Locale.ROOT, "%d Ticks nach dem Treffer %.2f m über der Haltehöhe (beim Treffer %.2f m, höchster Punkt %.2f m), Schwebekraft %s",
                since, rise, lab.hitY - lab.holdY, lab.peakY - lab.holdY, Levitations.lifting(drone) ? "an" : "aus");
        if (since > LIFT_TICKS && rise < LIFT_MIN) {
            throw new IllegalStateException("Drohne steigt nicht: " + note);
        }
        return new Poll(rise >= LIFT_MIN, note);
    }

    private static Poll back(MinecraftServer server) {
        Entity drone = drone();
        long now = Arena.level(server).getGameTime();
        track(drone, now);
        long since = now - lab.hitAt;
        long peak = lab.peakAt - lab.hitAt;
        double over = drone.getY() - lab.holdY;
        String note = String.format(Locale.ROOT, "höchster Punkt %.1f m über der Haltehöhe %d Ticks nach dem Treffer, jetzt %.2f m nach %d Ticks", lab.peakY - lab.holdY,
                peak, over, since);
        if (since < LEVITATION_TICKS || over > BACK_MARGIN) {
            return new Poll(false, note);
        }
        if (Levitations.lifting(drone)) {
            throw new IllegalStateException("Schwebekraft noch an: " + note);
        }
        if (peak < PEAK_EARLIEST || peak > PEAK_LATEST) {
            throw new IllegalStateException("Schwebekraft nicht 10 s lang: " + note);
        }
        return new Poll(true, note);
    }

    private static void track(Entity drone, long now) {
        if (drone.getY() > lab.peakY) {
            lab.peakY = drone.getY();
            lab.peakAt = now;
        }
    }

    private static void parked(MinecraftServer server, Map<String, Object> state) {
        Arena.rule(server, DAMAGE, "0");
        listen();
        reset();
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(-40, 120);
        lab.groundY = ground.getY() + 1.0;
        Entity drone = Arena.track(MobScene.placeDrone(level, ground, "CovMdB abgestellt"));
        lab.drone = drone;
        lab.restY = drone.getY();
        Shulker shulker = spawn(server, EntityTypes.SHULKER, new Vec3(ground.getX() - 6.0 + 0.5, lab.groundY, ground.getZ() + 0.5));
        shulker.setNoAi(true);
        lab.mob = shulker;
        ShulkerBullet bullet = new ShulkerBullet(level, shulker, drone, Direction.Axis.Y);
        drone.hurtServer(level, level.damageSources().mobProjectile(bullet, shulker), PARKED_DAMAGE);
    }

    private static Poll parkedHit(MinecraftServer server) {
        Entity drone = drone();
        boolean active = PropwashApi.drones().view(drone).map(view -> view.armed() || view.autopilot()).orElse(true);
        if (active) {
            throw new IllegalStateException("abgestellte Drohne ist scharf oder im Autopilot");
        }
        List<Hit> hits = hits(drone, "shulker", "shulker_bullet");
        String note = String.format(Locale.ROOT, "%d Treffer", hits.size());
        if (hits.isEmpty()) {
            return new Poll(false, note);
        }
        lab.hitAt = hits.getFirst().tick();
        return new Poll(true, note);
    }

    private static Poll parkedStays(MinecraftServer server) {
        Entity drone = drone();
        long since = Arena.level(server).getGameTime() - lab.hitAt;
        double drift = drone.getY() - lab.restY;
        boolean lifting = Levitations.lifting(drone);
        String note = String.format(Locale.ROOT, "%d Ticks nach dem Treffer %.3f m über der Ruhelage, Schwebekraft %s", since, drift, lifting ? "an" : "aus");
        if (lifting) {
            throw new IllegalStateException("abgestellte Drohne hat Schwebekraft: " + note);
        }
        if (Math.abs(drift) > PARKED_DRIFT) {
            throw new IllegalStateException("abgestellte Drohne bewegt sich: " + note);
        }
        return new Poll(since >= PARKED_TICKS, note);
    }

    private static StepResult steady(int ticks) {
        FlightState state = HoverAssist.last();
        String note = state == null ? "keine Flugdaten" : String.format(Locale.ROOT, "Höhe %.1f m, vy %.2f, vh %.2f", state.y() - lab.groundY, state.vy(),
                Math.hypot(state.vx(), state.vz()));
        if (state != null && ticks > 20 && Pilot.armed() && Math.abs(state.vy()) < 0.25 && Math.hypot(state.vx(), state.vz()) < 0.4) {
            return StepResult.pass(note);
        }
        return ticks > 600 ? StepResult.fail("schwebt nicht ruhig: " + note) : StepResult.pending();
    }

    private static StepResult threat(int ticks) {
        Entity drone = Pilot.drone().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.level == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        if (OsdNotices.active(Notice.THREAT, drone.getId(), mc.level.getGameTime())) {
            return StepResult.pass("BEDROHUNG am Client nach " + ticks + " Ticks");
        }
        return ticks > 600 ? StepResult.fail("BEDROHUNG kommt nicht (Pakete " + OsdNotices.received() + ")") : StepResult.pending();
    }

    private static StepResult clientLifted(int ticks) {
        FlightState state = HoverAssist.last();
        if (state == null) {
            return StepResult.fail("keine Flugdaten am Client");
        }
        double rise = state.y() - HoverAssist.targetY();
        String note = String.format(Locale.ROOT, "%d Ticks nach dem Treffer %.2f m über der Haltehöhe, vy %.2f m/s", ticks, rise, state.vy());
        if (rise >= LIFT_MIN) {
            return StepResult.pass(note);
        }
        return ticks > LIFT_TICKS + 20 ? StepResult.fail("Drohne steigt nicht: " + note) : StepResult.pending();
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        int since = (int) ctx.state().computeIfAbsent("covmdb.interaction.cleanup", key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, values) -> {
            ServerLevel level = Arena.level(server);
            for (Entity drone : Arena.drones(level)) {
                PropwashApi.drones().autonomy().stop(drone);
            }
            MobScene.clearProjectiles(level, Arena.box(level));
        });
    }
}
