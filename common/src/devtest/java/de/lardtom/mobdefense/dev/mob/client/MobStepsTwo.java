package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.client.HoverAssist;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.Failures;
import de.lardtom.mobdefense.mob.reaction.StareReaction;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.flight.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class MobStepsTwo {
    private static final String RULE_DAMAGE = MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path()).toString();
    private static final AtomicInteger POLLS = new AtomicInteger();
    private static final String CLEANUP_SINCE = "mob2.cleanup.since";
    private static final double SCENE_Z = 12.0;
    private static final double GAZE_MIN = 4.5;
    private static final double GAZE_MAX = 12.0;
    private static final double TAKEOFF_MIN = 1.0;
    private static final int LIFT_MAX = 8;
    private static final double PILOT_HEIGHT = 6.0;
    private static final double PILOT_TAKEOFF = 4.0;
    private static Lab lab = new Lab();

    private static final class Lab {
        @Nullable Entity drone;
        @Nullable Mob mob;
        @Nullable Mob second;
        double groundY;
        boolean assigned;
        boolean secondAssigned;
        final Set<UUID> shots = new HashSet<>();
        final Set<UUID> extra = new HashSet<>();
        @Nullable Vec3 mark;
        long since = -1;
        long counter;
        int checks;
    }

    private record Poll(boolean done, String note) {
        static Poll waiting(String note) {
            return new Poll(false, note);
        }

        static Poll done(String note) {
            return new Poll(true, note);
        }
    }

    @FunctionalInterface
    private interface Probe {
        Poll check(MinecraftServer server);
    }

    private MobStepsTwo() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("S10 stare – Kamerablick macht den Enderman wütend, er teleportiert sich zur tiefen Drohne und schlägt zu", 3600, Script.create()
                .server("Drohne schwebt auf 1 m", (server, state) -> setup(server, 1.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(0.8)))
                .server("Enderman im Kamerablick, zweiter Enderman hinter der Kamera", (server, state) -> {
                    lab.second = behind(server, EntityTypes.ENDERMAN, 6.0);
                    lab.mob = inGaze(server, EntityTypes.ENDERMAN);
                })
                .then("Kamerablick macht nur den angesehenen Enderman wütend", until(200, MobStepsTwo::provoked))
                .server("Enderman 9 m zur Seite", (server, state) -> aside(9.0))
                .then("Enderman teleportiert sich zur Drohne und schlägt zu", until(900, MobStepsTwo::teleported))
                .server("Blick auf die Szene", (server, state) -> view(server, 5.0, 0.5, 0.0, true))
                .append(Shots.photos("mob_stare")), MobStepsTwo::cleanup);
        HoverAssist.register();
        steps.add("S10 stare (Pilot) – hoch fliegende Drohne mit Pilot: der Kamerablick macht den Enderman wütend auf den Piloten", 3600,
                Script.create()
                        .server("5″ setzen, Pilot daneben", MobStepsTwo::piloted)
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(PILOT_TAKEOFF))
                        .run("Position halten und steigen", () -> Pilot.drone().ifPresent(d -> HoverAssist.hold(d.getX(), lab.groundY + PILOT_HEIGHT,
                                d.getZ())))
                        .then("schwebt ruhig", (ctx, ticks) -> pilotSettled(ticks))
                        .server("Pilot im Überlebensmodus, Enderman im Kamerablick", (server, state) -> {
                            lab.drone = Pilot.serverDrone(server, state);
                            Arena.player(server).setGameMode(GameType.SURVIVAL);
                            lab.counter = StareReaction.counts().pilotAttacks();
                            lab.mob = inGaze(server, EntityTypes.ENDERMAN);
                        })
                        .then("Enderman greift den Piloten an", until(300, MobStepsTwo::pilotTargeted))
                        .server("Pilot wieder im Kreativmodus", (server, state) -> Arena.player(server).setGameMode(GameType.CREATIVE)),
                PILOT_CLEANUP);
        steps.add("S10 stare (Knarz) – läuft ohne Kamerablick zur Drohne und erstarrt unter ihm", 3000, Script.create()
                .server("Drohne schwebt auf 1 m", (server, state) -> setup(server, 1.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(0.8)))
                .server("Knarz 6 m hinter der Kamera", (server, state) -> lab.mob = behind(server, EntityTypes.CREAKING, 6.0))
                .then("Knarz läuft zur Drohne", until(400, MobStepsTwo::creakingWalks))
                .server("Knarz in den Kamerablick", (server, state) -> moveIntoGaze(lab.mob))
                .then("Knarz erstarrt unter dem Kamerablick", until(300, MobStepsTwo::creakingFrozen))
                .server("Blick auf die Szene", (server, state) -> view(server, 5.0, 0.5, 0.0, true))
                .append(Shots.photos("mob_stare_creaking")), MobStepsTwo::cleanup);
        steps.add("S10 beam – Wächter im Wasser legt den Laser auf die Drohne über dem Wasser, Schaden nach der Ladezeit", 3600, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> setup(server, 3.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(2.5)))
                .server("Becken unter der Drohne, Wächter 6 m daneben", MobStepsTwo::pool)
                .then("Wächter lädt den Laser", until(400, MobStepsTwo::beamCharging))
                .server("Blick auf die Szene", (server, state) -> view(server, 7.0, 1.5, 3.0, false))
                .append(Shots.photos("mob_beam"))
                .then("Laser trifft nach der Ladezeit", until(600, MobStepsTwo::beamed)), MobStepsTwo::cleanup);
        steps.add("S10 caster – Magier: Fangzähne unter der tiefen Drohne, höher beschwört er Plagegeister", 4200, Script.create()
                .server("Drohne schwebt auf 1,5 m", (server, state) -> setup(server, 1.5, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(1.2)))
                .server("Magier 7 m daneben", (server, state) -> lab.mob = mob(server, EntityTypes.EVOKER, 7.0, 0.0, 0.0))
                .then("Fangzähne unter der Drohne", until(600, MobStepsTwo::fangsPlaced))
                .server("Blick auf die Szene", (server, state) -> view(server, 6.0, 0.5, 3.5, false))
                .append(Shots.photos("mob_caster"))
                .then("Fangzähne treffen die Drohne", until(400, MobStepsTwo::fangsBit))
                .server("Drohne steigt auf 5 m", (server, state) -> climb(server, 3.5))
                .then("Plagegeister jagen die Drohne", until(1000, MobStepsTwo::vexesHunt)), MobStepsTwo::cleanup);
        steps.add("S10 boss – Wither schießt Wither-Schädel auf die Drohne", 3600, Script.create()
                .server("Drohne schwebt auf 4 m", (server, state) -> {
                    Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false");
                    setup(server, 4.0, 0);
                })
                .then("Drohne in der Luft", until(600, server -> airborne(3.5)))
                .server("Wither 14 m daneben und 4 m höher", (server, state) -> lab.mob = mob(server, EntityTypes.WITHER, 14.0, 0.0, 4.0))
                .then("Wither-Schädel auf die Drohne", until(900, MobStepsTwo::skulled))
                .server("Blick auf die Szene", (server, state) -> view(server, 16.0, 3.0, 7.0, false))
                .append(Shots.photos("mob_boss")), MobStepsTwo::cleanup);
        steps.add("S10 ground – Silberfischchen greift die ganz tief schwebende Drohne an, höher nicht", 3000, Script.create()
                .server("Drohne schwebt auf 0,7 m", (server, state) -> setup(server, 0.7, 0))
                .then("Drohne tief in der Luft", until(600, MobStepsTwo::lowHover))
                .server("Silberfischchen 4 m daneben", (server, state) -> lab.mob = mob(server, EntityTypes.SILVERFISH, 4.0, 0.0, 0.0))
                .then("Silberfischchen läuft hin und beißt", until(600, server -> bitten("silverfish")))
                .server("Blick auf die Szene", (server, state) -> view(server, 3.0, 0.0, 0.0, true))
                .append(Shots.photos("mob_ground"))
                .server("Drohne steigt um 3 m", (server, state) -> climb(server, 3.0))
                .then("höher: keine Reaktion mehr", until(400, MobStepsTwo::groundReleased)), MobStepsTwo::cleanup);
        steps.add("S10 wind – Böe schießt Windkugeln, die Drohne wird über DRONE_KNOCKBACK weggestoßen", 3000, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> setup(server, 3.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(2.5)))
                .server("Böe 8 m daneben", (server, state) -> lab.mob = mob(server, EntityTypes.BREEZE, 8.0, 0.0, 0.0))
                .then("Windkugel stößt die Drohne", until(900, MobStepsTwo::winded))
                .server("Blick auf die Szene", (server, state) -> view(server, 8.0, 1.0, 4.0, false))
                .append(Shots.photos("mob_wind")), MobStepsTwo::cleanup);
        steps.add("S10 bullet – Shulker-Geschoss folgt der Drohne und trifft", 3000, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> setup(server, 3.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(2.5)))
                .server("Shulker 7 m daneben", (server, state) -> lab.mob = mob(server, EntityTypes.SHULKER, 7.0, 0.0, 0.0))
                .then("Geschoss folgt der Drohne", until(400, server -> launched(ShulkerBullet.class, "Geschosse")))
                .server("Blick auf die Szene", (server, state) -> view(server, 7.0, 1.0, 3.5, false))
                .append(Shots.photos("mob_bullet"))
                .then("Geschoss trifft", until(900, server -> struck("shulker", "shulker_bullet"))), MobStepsTwo::cleanup);
        steps.add("S10 potion – Hexe wirft Schadenstränke, der Treffer im Umkreis 2 schadet der Drohne", 3000, Script.create()
                .server("Drohne schwebt auf 2,5 m", (server, state) -> setup(server, 2.5, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(2.0)))
                .server("Hexe 6 m daneben", (server, state) -> lab.mob = mob(server, EntityTypes.WITCH, 6.0, 0.0, 0.0))
                .then("Schadenstrank trifft die Drohne", until(900, MobStepsTwo::splashed))
                .server("Blick auf die Szene", (server, state) -> view(server, 6.0, 1.0, 3.0, false))
                .append(Shots.photos("mob_potion")), MobStepsTwo::cleanup);
        steps.add("S10 jumper – Spinne und Schleim springen an die tiefe Drohne", 3600, Script.create()
                .server("Drohne schwebt auf 2 m", (server, state) -> setup(server, 2.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(1.6)))
                .server("Spinne 5 m daneben, Schleim 5 m auf der anderen Seite", (server, state) -> {
                    lab.mob = mob(server, EntityTypes.SPIDER, 5.0, 0.0, 0.0);
                    Slime slime = mob(server, EntityTypes.SLIME, -5.0, 0.0, 0.0);
                    slime.setSize(2, true);
                    lab.second = slime;
                })
                .then("Spinne und Schleim springen und treffen", until(900, MobStepsTwo::jumped))
                .server("Blick auf die Szene", (server, state) -> view(server, 6.0, 0.5, 0.0, false))
                .append(Shots.photos("mob_jumper")), MobStepsTwo::cleanup);
        steps.add("S10 trident – Ertrunkener mit Dreizack wirft ihn auf die Drohne", 3000, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> setup(server, 3.0, 0))
                .then("Drohne in der Luft", until(600, server -> airborne(2.5)))
                .server("Ertrunkener mit Dreizack 10 m daneben", (server, state) -> {
                    Mob drowned = mob(server, EntityTypes.DROWNED, 10.0, 0.0, 0.0);
                    drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                    drowned.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
                    lab.mob = drowned;
                })
                .then("Dreizack trifft die Drohne", until(900, MobStepsTwo::speared))
                .server("Blick auf die Szene", (server, state) -> view(server, 8.0, 1.0, 5.0, false))
                .append(Shots.photos("mob_trident")), MobStepsTwo::cleanup);
    }

    private static final SelfTest.ClientStep PILOT_CLEANUP = Script.create()
            .run("Tasten los, Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
            .server("Flug beenden, Kreativmodus", (server, state) -> {
                Pilot.release(server);
                Arena.player(server).setGameMode(GameType.CREATIVE);
            })
            .then("Aufräumen", (ctx, ticks) -> cleanup(ctx));

    private static StepResult pilotSettled(int ticks) {
        FlightState state = HoverAssist.last();
        double height = state == null ? 0.0 : state.y() - lab.groundY;
        String note = state == null ? "keine Flugdaten" : String.format(Locale.ROOT, "Höhe %.1f m, vy %.2f, vh %.2f", height, state.vy(),
                Math.hypot(state.vx(), state.vz()));
        if (state != null && ticks > 20 && Pilot.armed() && Math.abs(state.vy()) < 0.25 && Math.hypot(state.vx(), state.vz()) < 0.4
                && height > PILOT_HEIGHT * 0.75) {
            return StepResult.pass(note);
        }
        return ticks > 600 ? StepResult.fail("schwebt nicht ruhig: " + note) : StepResult.pending();
    }

    private static void piloted(MinecraftServer server, Map<String, Object> state) {
        Arena.rule(server, id(RULE_DAMAGE), "0");
        MobScene.listen();
        MobScene.clearEvents();
        lab = new Lab();
        BlockPos ground = Arena.ground(0, (int) SCENE_Z);
        lab.groundY = ground.getY() + 1.0;
        lab.drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "S10 Pilot", ground, 180.0F);
    }

    private static Poll pilotTargeted(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob enderman = mob(lab.mob, "Enderman");
        ServerPlayer player = Arena.player(server);
        long attacks = StareReaction.counts().pilotAttacks() - lab.counter;
        boolean pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot).filter(id -> id.equals(player.getUUID())).isPresent();
        String note = String.format(Locale.ROOT, "Drohne %.1f m hoch, Pilot %s, Enderman-Ziel %s, %d Angriffe auf den Piloten", drone.getY() - lab.groundY,
                pilot ? "ja" : "nein", enderman.getTarget() == player ? "Pilot" : String.valueOf(enderman.getTarget()), attacks);
        return pilot && attacks > 0 && enderman.getTarget() == player ? Poll.done(note) : Poll.waiting(note);
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }

    private static Script.Stage until(int maxTicks, Probe probe) {
        String key = "mob2.poll." + POLLS.incrementAndGet();
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

    private static void setup(MinecraftServer server, double metres, int damagePercent) {
        Arena.rule(server, id(RULE_DAMAGE), String.valueOf(damagePercent));
        MobScene.listen();
        MobScene.clearEvents();
        lab = new Lab();
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(0, (int) SCENE_Z);
        lab.groundY = ground.getY() + 1.0;
        lab.drone = MobScene.placeDrone(level, ground, "S10");
        AutonomyResult result = hover(lab.drone, Arena.player(server), metres);
        if (result != AutonomyResult.STARTED) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
    }

    private static AutonomyResult hover(Entity drone, ServerPlayer owner, double metres) {
        if (metres >= TAKEOFF_MIN) {
            return MobScene.hover(drone, owner, metres);
        }
        AutonomyOptions options = AutonomyOptions.of(MobScene.HOVER).label("MD TEST").maxDuration(Duration.ofMinutes(10)).takeoff(TAKEOFF_MIN);
        return PropwashApi.drones().autonomy().start(drone, owner, MobScene.hold(drone.getX(), drone.getY() + metres, drone.getZ()), options);
    }

    private static void climb(MinecraftServer server, double metres) {
        Entity drone = drone(lab.drone);
        PropwashApi.drones().autonomy().stop(drone);
        AutonomyResult result = MobScene.hover(drone, Arena.player(server), metres);
        if (result != AutonomyResult.STARTED) {
            throw new IllegalStateException("Steigflug startet nicht: " + result);
        }
        lab.since = -1;
        lab.checks = 0;
    }

    private static <T extends Mob> T mob(MinecraftServer server, EntityType<T> type, double dx, double dz, double dy) {
        Entity anchor = drone(lab.drone);
        Vec3 at = new Vec3(anchor.getX() + dx, lab.groundY + dy, anchor.getZ() + dz);
        return spawnAt(server, type, at, anchor.position());
    }

    private static <T extends Mob> T spawnAt(MinecraftServer server, EntityType<T> type, Vec3 at, Vec3 lookAt) {
        float yaw = (float) (-Mth.atan2(lookAt.x - at.x, lookAt.z - at.z) * Mth.RAD_TO_DEG);
        T mob = MobScene.spawn(Arena.level(server), type, at, yaw);
        Arena.track(mob);
        return mob;
    }

    private static CameraPose pose() {
        Entity drone = drone(lab.drone);
        return PropwashApi.drones().cameraPose(drone).orElseThrow(() -> new IllegalStateException("Drohne ohne Kamerablick (cameraPose leer)"));
    }

    private static Vec3 gazePoint(ServerLevel level, double eyeHeight) {
        CameraPose pose = pose();
        Vec3 eye = pose.eye();
        Vec3 look = pose.look();
        if (look.y < 0.05) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Kamera blickt nicht nach oben (look %.2f %.2f %.2f)", look.x, look.y, look.z));
        }
        for (int lift = 0; lift <= LIFT_MAX; lift++) {
            double feet = lab.groundY + lift;
            double distance = (feet + eyeHeight - eye.y) / look.y;
            if (distance >= GAZE_MIN && distance <= GAZE_MAX) {
                Vec3 at = eye.add(look.scale(distance));
                Vec3 spot = new Vec3(at.x, feet, at.z);
                if (lift > 0) {
                    BlockPos base = BlockPos.containing(spot.x, lab.groundY, spot.z);
                    Arena.fill(level, base, base.above(lift - 1), Blocks.STONE.defaultBlockState());
                }
                return spot;
            }
        }
        throw new IllegalStateException(String.format(Locale.ROOT, "kein Platz im Kamerablick (Auge %.2f, look.y %.2f)", eye.y - lab.groundY, look.y));
    }

    private static <T extends Mob> T inGaze(MinecraftServer server, EntityType<T> type) {
        ServerLevel level = Arena.level(server);
        Vec3 spot = gazePoint(level, type.getDimensions().eyeHeight());
        T mob = spawnAt(server, type, spot, drone(lab.drone).position());
        lab.mark = mob.position();
        return mob;
    }

    private static <T extends Mob> T behind(MinecraftServer server, EntityType<T> type, double metres) {
        Vec3 look = pose().look();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        Entity drone = drone(lab.drone);
        Vec3 at = new Vec3(drone.getX() - flat.x * metres, lab.groundY, drone.getZ() - flat.z * metres);
        T mob = spawnAt(server, type, at, drone.position());
        lab.mark = mob.position();
        return mob;
    }

    private static void moveIntoGaze(@Nullable Mob target) {
        Mob mob = mob(target, "Knarz");
        ServerLevel level = (ServerLevel) mob.level();
        Vec3 look = pose().look();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        flat = flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        Entity drone = drone(lab.drone);
        Vec3 at = new Vec3(drone.getX() + flat.x * GAZE_MIN, lab.groundY, drone.getZ() + flat.z * GAZE_MIN);
        mob.getNavigation().stop();
        float yaw = (float) (-Mth.atan2(drone.getX() - at.x, drone.getZ() - at.z) * Mth.RAD_TO_DEG);
        mob.teleportTo(level, at.x, at.y, at.z, Set.<Relative>of(), yaw, 0.0F, true);
        lab.mark = at;
        lab.since = -1;
        lab.checks = 0;
    }

    private static void aside(double metres) {
        Mob mob = mob(lab.mob, "Enderman");
        Vec3 look = pose().look();
        Vec3 side = new Vec3(-look.z, 0.0, look.x);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
        Entity drone = drone(lab.drone);
        Vec3 at = new Vec3(drone.getX() + side.x * metres, lab.groundY, drone.getZ() + side.z * metres);
        mob.getNavigation().stop();
        mob.teleportTo(at.x, at.y, at.z);
        lab.mark = at;
        lab.counter = StareReaction.counts().teleports();
    }

    private static Entity drone(@Nullable Entity drone) {
        if (drone == null || drone.isRemoved()) {
            throw new IllegalStateException("Drohne fehlt oder wurde zerstört");
        }
        return drone;
    }

    private static Mob mob(@Nullable Mob mob, String name) {
        if (mob == null || mob.isRemoved()) {
            throw new IllegalStateException(name + " fehlt");
        }
        return mob;
    }

    private static Poll airborne(double metres) {
        Entity drone = drone(lab.drone);
        DroneView view = PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Drohnen-Ansicht"));
        double height = drone.getY() - lab.groundY;
        String note = String.format(Locale.ROOT, "Höhe %.1f m, %s", height, view.flying() ? "fliegt (autonom)" : "fliegt nicht");
        return view.flying() && height >= metres ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll lowHover(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        DroneView view = PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Drohnen-Ansicht"));
        double height = drone.getY() - lab.groundY;
        String note = String.format(Locale.ROOT, "Höhe %.2f m, %s", height, view.flying() ? "fliegt (autonom)" : "fliegt nicht");
        if (!view.flying() || height < 0.3 || height > 0.95) {
            lab.checks = 0;
            return Poll.waiting(note);
        }
        return ++lab.checks >= 10 ? Poll.done(note) : Poll.waiting(note);
    }

    private static boolean assignedTo(Mob mob, Entity drone) {
        return DroneReactions.assigned(mob).filter(entity -> entity == drone).isPresent();
    }

    private static List<MobScene.Hit> hitsBy(Entity drone, String cause) {
        return MobScene.hits(drone).stream().filter(hit -> hit.cause().equals(cause)).toList();
    }

    private static String causes(Entity drone) {
        Map<String, Long> counts = new TreeMap<>();
        for (MobScene.Hit hit : MobScene.hits(drone)) {
            counts.merge(hit.cause() + "/" + hit.direct(), 1L, Long::sum);
        }
        return counts.toString();
    }

    private static <T extends Entity> int collect(ServerLevel level, Class<T> type, Entity owner, Set<UUID> into) {
        for (T entity : level.getEntitiesOfClass(type, Arena.box(level), candidate -> candidate instanceof Projectile projectile
                ? projectile.getOwner() == owner : candidate instanceof EvokerFangs fangs && fangs.getOwner() == owner)) {
            into.add(entity.getUUID());
        }
        return into.size();
    }

    private static Poll provoked(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob enderman = mob(lab.mob, "Enderman");
        lab.assigned |= assignedTo(enderman, drone);
        boolean provoked = StareReaction.provoked(enderman);
        Mob unseen = mob(lab.second, "zweiter Enderman");
        if (StareReaction.provoked(unseen) || DroneReactions.assigned(unseen).isPresent()) {
            throw new IllegalStateException("Enderman hinter der Kamera reagiert ohne Kamerablick");
        }
        Vec3 eye = pose().eye();
        String note = String.format(Locale.ROOT, "Enderman %s, %s, Abstand Kamera %.1f m, Provokationen %d", provoked ? "wütend" : "ruhig",
                lab.assigned ? "zugeordnet" : "frei", eye.distanceTo(enderman.getEyePosition()), StareReaction.counts().provocations());
        return provoked && lab.assigned ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll teleported(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob enderman = mob(lab.mob, "Enderman");
        double flat = MobScene.horizontal(enderman, drone.position());
        long teleports = StareReaction.counts().teleports() - lab.counter;
        int hits = hitsBy(drone, "enderman").size();
        String note = String.format(Locale.ROOT, "%d Teleports zur Drohne, Abstand %.1f m, %d Treffer, %s", teleports, flat, hits,
                assignedTo(enderman, drone) ? "zugeordnet" : "frei");
        return teleports > 0 && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll creakingWalks(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Creaking creaking = (Creaking) mob(lab.mob, "Knarz");
        lab.assigned |= assignedTo(creaking, drone);
        Vec3 start = lab.mark == null ? creaking.position() : lab.mark;
        double moved = creaking.position().distanceTo(start);
        String note = String.format(Locale.ROOT, "Knarz %s, %.1f m gelaufen, Abstand %.1f m, %s", lab.assigned ? "zugeordnet" : "frei", moved,
                MobScene.horizontal(creaking, drone.position()), creaking.canMove() ? "beweglich" : "erstarrt");
        return lab.assigned && moved >= 1.0 && creaking.canMove() ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll creakingFrozen(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Creaking creaking = (Creaking) mob(lab.mob, "Knarz");
        long now = creaking.level().getGameTime();
        String note = String.format(Locale.ROOT, "Knarz %s, Kamerablick %s, %d Ticks erstarrt, Erstarrungen %d", creaking.canMove() ? "beweglich" : "erstarrt",
                StareReaction.frozen(creaking) ? "ja" : "nein", lab.since < 0 ? 0 : now - lab.since, StareReaction.counts().freezes());
        if (creaking.canMove()) {
            lab.since = -1;
            return Poll.waiting(note);
        }
        if (lab.since < 0) {
            lab.since = now;
            lab.mark = creaking.position();
            return Poll.waiting(note);
        }
        double moved = creaking.position().distanceTo(lab.mark == null ? creaking.position() : lab.mark);
        if (moved > 0.2) {
            throw new IllegalStateException("Knarz bewegt sich trotz Kamerablick: " + note + String.format(Locale.ROOT, ", %.2f m", moved));
        }
        if (!hitsBy(drone, "creaking").isEmpty() && now - lab.since > 20) {
            throw new IllegalStateException("erstarrter Knarz schlägt zu: " + causes(drone));
        }
        return now - lab.since >= 40 ? Poll.done(note) : Poll.waiting(note);
    }

    private static void pool(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(0, (int) SCENE_Z);
        Arena.fill(level, ground.offset(-3, -2, -3), ground.offset(9, 0, 3), Blocks.WATER.defaultBlockState());
        Entity drone = drone(lab.drone);
        lab.mob = spawnAt(server, EntityTypes.GUARDIAN, new Vec3(drone.getX() + 6.0, lab.groundY - 1.8, drone.getZ()), drone.position());
    }

    private static Poll beamCharging(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob guardian = mob(lab.mob, "Wächter");
        boolean assigned = assignedTo(guardian, drone);
        if (assigned && lab.since < 0) {
            lab.since = guardian.level().getGameTime();
        }
        lab.assigned |= assigned;
        long charging = lab.since < 0 ? 0 : guardian.level().getGameTime() - lab.since;
        if (!hitsBy(drone, "guardian").isEmpty() && charging < 60) {
            throw new IllegalStateException("Laser trifft vor der Ladezeit nach " + charging + " Ticks");
        }
        String note = String.format(Locale.ROOT, "Wächter %s, im Wasser %s, lädt seit %d Ticks", lab.assigned ? "zugeordnet" : "frei",
                guardian.isInWater() ? "ja" : "nein", charging);
        return charging >= 30 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll beamed(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob guardian = mob(lab.mob, "Wächter");
        List<MobScene.Hit> hits = hitsBy(drone, "guardian");
        long charging = lab.since < 0 ? 0 : guardian.level().getGameTime() - lab.since;
        String note = String.format(Locale.ROOT, "%d Treffer nach %d Ticks, %s", hits.size(), charging, causes(drone));
        return hits.isEmpty() ? Poll.waiting(note) : Poll.done(note);
    }

    private static Poll fangsPlaced(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob evoker = mob(lab.mob, "Magier");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(evoker, drone);
        int fangs = collect(level, EvokerFangs.class, evoker, lab.shots);
        double nearest = level.getEntitiesOfClass(EvokerFangs.class, Arena.box(level), candidate -> candidate.getOwner() == evoker).stream()
                .mapToDouble(candidate -> MobScene.horizontal(candidate, drone.position())).min().orElse(Double.NaN);
        String note = String.format(Locale.ROOT, "Magier %s, %d Fangzähne, nächster %.1f m von der Drohne", lab.assigned ? "zugeordnet" : "frei", fangs,
                nearest);
        return lab.assigned && fangs > 0 && nearest <= 1.0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll fangsBit(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        List<MobScene.Hit> hits = MobScene.hits(drone).stream().filter(hit -> hit.cause().equals("evoker") && hit.direct().equals("evoker_fangs")).toList();
        String note = String.format(Locale.ROOT, "%d Bisse, %s", hits.size(), causes(drone));
        return hits.isEmpty() ? Poll.waiting(note) : Poll.done(note);
    }

    private static Poll vexesHunt(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob evoker = mob(lab.mob, "Magier");
        ServerLevel level = Arena.level(server);
        List<Vex> vexes = level.getEntitiesOfClass(Vex.class, Arena.box(level), vex -> vex.getOwner() == evoker);
        for (Vex vex : vexes) {
            Arena.track(vex);
        }
        long hunting = vexes.stream().filter(vex -> assignedTo(vex, drone)).count();
        String note = String.format(Locale.ROOT, "Drohne %.1f m hoch, %d Plagegeister, %d jagen die Drohne, Treffer %s", drone.getY() - lab.groundY,
                vexes.size(), hunting, causes(drone));
        return drone.getY() - lab.groundY > 2.5 && hunting > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll skulled(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob wither = mob(lab.mob, "Wither");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(wither, drone);
        int skulls = collect(level, WitherSkull.class, wither, lab.shots);
        int hits = hitsBy(drone, "wither").size();
        long blasts = MobScene.pushes(drone).stream().filter(push -> push.cause() == DroneKnockback.Cause.EXPLOSION).count();
        String note = String.format(Locale.ROOT, "Wither %s, %d Schädel, %d Treffer, %d Explosionsstöße, %s", lab.assigned ? "zugeordnet" : "frei", skulls,
                hits, blasts, causes(drone));
        return lab.assigned && skulls >= 2 && hits + blasts > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll bitten(String cause) {
        Entity drone = drone(lab.drone);
        Mob mob = mob(lab.mob, cause);
        lab.assigned |= assignedTo(mob, drone);
        int hits = hitsBy(drone, cause).size();
        String note = String.format(Locale.ROOT, "%s %s, Abstand %.1f m, Drohne %.1f m hoch, %d Treffer", cause, lab.assigned ? "zugeordnet" : "frei",
                MobScene.horizontal(mob, drone.position()), drone.getY() - lab.groundY, hits);
        return lab.assigned && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll groundReleased(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob mob = mob(lab.mob, "Silberfischchen");
        double height = drone.getY() - lab.groundY;
        boolean assigned = assignedTo(mob, drone);
        String note = String.format(Locale.ROOT, "Drohne %.1f m hoch, Silberfischchen %s", height, assigned ? "zugeordnet" : "frei");
        if (height < 2.0 || assigned) {
            lab.checks = 0;
            return Poll.waiting(note);
        }
        return ++lab.checks >= 10 ? Poll.done(note + ", 10 Prüfungen ohne Zuordnung") : Poll.waiting(note);
    }

    private static Poll winded(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob breeze = mob(lab.mob, "Böe");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(breeze, drone);
        int charges = collect(level, BreezeWindCharge.class, breeze, lab.shots);
        List<MobScene.Push> pushes = MobScene.pushes(drone).stream().filter(push -> push.cause() == DroneKnockback.Cause.WIND_CHARGE).toList();
        String note = String.format(Locale.ROOT, "Böe %s, %d Windkugeln, %d Windstöße%s", lab.assigned ? "zugeordnet" : "frei", charges, pushes.size(),
                pushes.isEmpty() ? "" : String.format(Locale.ROOT, " (Skala %.1f, Δv %.1f m/s)", pushes.getFirst().scale(), pushes.getFirst().delta().length()));
        return lab.assigned && charges > 0 && !pushes.isEmpty() && pushes.getFirst().scale() == 1.0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll launched(Class<? extends Entity> type, String label) {
        Entity drone = drone(lab.drone);
        Mob mob = mob(lab.mob, "Schütze");
        ServerLevel level = (ServerLevel) mob.level();
        lab.assigned |= assignedTo(mob, drone);
        int count = collect(level, type, mob, lab.shots);
        String note = String.format(Locale.ROOT, "%s %s, %d %s", mob.getType().getDescription().getString(), lab.assigned ? "zugeordnet" : "frei", count, label);
        return lab.assigned && count > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll struck(String cause, String direct) {
        Entity drone = drone(lab.drone);
        List<MobScene.Hit> hits = MobScene.hits(drone).stream().filter(hit -> hit.cause().equals(cause) && hit.direct().equals(direct)).toList();
        String note = String.format(Locale.ROOT, "%d Treffer %s/%s, %s", hits.size(), cause, direct, causes(drone));
        return hits.isEmpty() ? Poll.waiting(note) : Poll.done(note);
    }

    private static Poll splashed(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob witch = mob(lab.mob, "Hexe");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(witch, drone);
        int potions = collect(level, ThrownSplashPotion.class, witch, lab.shots);
        int hits = (int) hitsBy(drone, "witch").stream().filter(hit -> hit.direct().contains("potion")).count();
        String note = String.format(Locale.ROOT, "Hexe %s, %d Tränke, %d Treffer, %s", lab.assigned ? "zugeordnet" : "frei", potions, hits, causes(drone));
        return lab.assigned && potions > 0 && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll jumped(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob spider = mob(lab.mob, "Spinne");
        Mob slime = mob(lab.second, "Schleim");
        lab.assigned |= assignedTo(spider, drone);
        lab.secondAssigned |= assignedTo(slime, drone);
        int spiderHits = hitsBy(drone, "spider").size();
        int slimeHits = hitsBy(drone, "slime").size();
        String note = String.format(Locale.ROOT, "Spinne %s %.1f m, %d Treffer; Schleim %s %.1f m, %d Treffer", lab.assigned ? "zugeordnet" : "frei",
                MobScene.horizontal(spider, drone.position()), spiderHits, lab.secondAssigned ? "zugeordnet" : "frei",
                MobScene.horizontal(slime, drone.position()), slimeHits);
        return lab.assigned && lab.secondAssigned && spiderHits > 0 && slimeHits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll speared(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob drowned = mob(lab.mob, "Ertrunkener");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(drowned, drone);
        String kind = DroneReactions.kind(drowned).orElse("-");
        if (!"trident".equals(kind)) {
            throw new IllegalStateException("Ertrunkener mit Dreizack hat die Art " + kind + " statt trident");
        }
        int tridents = collect(level, ThrownTrident.class, drowned, lab.shots);
        int hits = (int) hitsBy(drone, "drowned").stream().filter(hit -> hit.direct().equals("trident")).count();
        String note = String.format(Locale.ROOT, "Ertrunkener %s (Art %s), %d Dreizacke, %d Treffer, %s", lab.assigned ? "zugeordnet" : "frei", kind,
                tridents, hits, causes(drone));
        return lab.assigned && tridents > 0 && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static void view(MinecraftServer server, double back, double up, double shift, boolean withMob) {
        Entity drone = drone(lab.drone);
        Vec3 target = drone.getBoundingBox().getCenter();
        Mob mob = lab.mob;
        if (withMob && mob != null && !mob.isRemoved()) {
            target = target.add(mob.getBoundingBox().getCenter()).scale(0.5);
        }
        target = target.add(shift, 0.0, 0.0);
        Vec3 eye = new Vec3(target.x, lab.groundY + up, target.z - back);
        double dx = target.x - eye.x;
        double dy = target.y - (eye.y + 1.62);
        double dz = target.z - eye.z;
        float yaw = (float) (-Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(dy, Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
        ServerPlayer player = Arena.player(server);
        player.teleportTo(Arena.level(server), eye.x, eye.y, eye.z, Set.<Relative>of(), yaw, pitch, true);
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        int since = (int) ctx.state().computeIfAbsent(CLEANUP_SINCE, key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            ServerLevel level = Arena.level(server);
            for (Entity drone : Arena.drones(level)) {
                PropwashApi.drones().autonomy().stop(drone);
            }
            for (Vex vex : level.getEntitiesOfClass(Vex.class, Arena.box(level), vex -> true)) {
                vex.discard();
            }
            int projectiles = MobScene.clearProjectiles(level, Arena.box(level));
            for (EvokerFangs fangs : level.getEntitiesOfClass(EvokerFangs.class, Arena.box(level), fangs -> true)) {
                fangs.discard();
            }
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
