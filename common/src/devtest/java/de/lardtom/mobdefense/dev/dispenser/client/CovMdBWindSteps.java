package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.CovMdBChargeWatch;
import de.lardtom.mobdefense.dev.dispenser.CovMdBDrift;
import de.lardtom.mobdefense.dev.dispenser.CovMdBLanes;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.event.DroneKnockback;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CovMdBWindSteps {
    public static final double HEIGHT = 5.0;
    public static final double WALL_DISTANCE = 2.5;
    public static final double CLOSE_DISTANCE = 1.5;
    public static final double RECOVER_SECONDS = 2.0;
    public static final double LEVEL_DEGREES = 15.0;
    public static final double MIN_HEIGHT = 0.5;
    public static final double STRONG_SHARE = 0.5;
    public static final int WINDOW_TICKS = 50;
    public static final int DRIFT_TICKS = 50;
    private static final String DRONE = "covmdb.wind.drone";
    private static final String DRIFT = "covmdb.wind.drift";
    private static final String SHOT = "covmdb.wind.shot";
    private static final String FREE = "covmdb.wind.free";
    private static final String NOTE = "covmdb.wind.note";
    private static final String WALL = "covmdb.wind.distance";

    private CovMdBWindSteps() {
    }

    private record Shot(Vec3 start, Vec3 forward, double massKg, double recoilNs, int pushesBefore, CovMdBChargeWatch watch) {
    }

    private record Drift(double back, double peakBack, int windPushes, double burstDistance, int burstTick, double maxTilt, double lastSteep, double endTilt,
                         double minHeight, boolean grounded) {
        boolean recovered() {
            return this.lastSteep <= RECOVER_SECONDS && this.endTilt < LEVEL_DEGREES && !this.grounded && this.minHeight >= MIN_HEIGHT;
        }
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Windkugel fängt sich – 5″ nach dem Stoß im Angle-Modus ohne Eingriff in 2 s unter 15°, kein Bodenkontakt aus 5 m, Stoß kräftig",
                2400, Script.create()
                        .server("5″ mit Windkugeln", (server, state) -> takeOff(server, state, Arena.ground(10, -18), 0.0))
                        .then("schwebt autonom auf 5 m", CovMdBEach.launch(state -> drone(Arena.level(Minecraft.getInstance().getSingleplayerServer()), state), HEIGHT, DRIFT))
                        .server("Platz halten", (server, state) -> hold(state))
                        .then("schwebt ruhig", CovMdBWindSteps::settled)
                        .server("Windkugel ins Freie, Knüppel in der Mitte, Gas auf Schwebe", CovMdBWindSteps::fire)
                        .sleep(WINDOW_TICKS)
                        .server("Lage, Höhe und Stoß auswerten", CovMdBWindSteps::recovery)
                        .then("Ergebnis", CovMdBWindSteps::note), CovMdBWindSteps::cleanup);
        steps.add(wallName(CLOSE_DISTANCE), 3600, wallStep(CLOSE_DISTANCE, 10, -42), CovMdBWindSteps::cleanup);
    }

    public static String wallName(double distance) {
        return String.format(Locale.GERMANY, "Windkugel an der Wand – %.1f Blöcke vor der Wand schieben Rückstoß und Windstoß die 5″ weiter zurück als ins Freie",
                distance);
    }

    public static Script wallStep(double distance, int dx, int dz) {
        return Script.create()
                .server("5″ mit Windkugeln schwebt vor dem späteren Wandplatz", (server, state) -> {
                    state.put(WALL, distance);
                    takeOff(server, state, Arena.ground(dx, dz), 3.0);
                })
                .then("schwebt autonom", CovMdBEach.launch(state -> drone(Arena.level(Minecraft.getInstance().getSingleplayerServer()), state), HEIGHT, DRIFT))
                .server("Platz halten", (server, state) -> hold(state))
                .then("schwebt ruhig", CovMdBWindSteps::settled)
                .server("Windkugel ins Freie", CovMdBWindSteps::fire)
                .sleep(DRIFT_TICKS)
                .server("Versatz ins Freie", (server, state) -> state.put(FREE, drift(server, state)))
                .server("zurück an den Platz, Wand bauen, nachladen", CovMdBWindSteps::wall)
                .then("schwebt wieder ruhig", CovMdBWindSteps::settled)
                .server("Windkugel gegen die Wand", CovMdBWindSteps::fire)
                .sleep(DRIFT_TICKS)
                .server("Versatz an der Wand", CovMdBWindSteps::compare)
                .then("Ergebnis", CovMdBWindSteps::note);
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static Entity drone(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Arena.level(server).getEntity((UUID) state.get(DRONE));
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt");
        }
        return drone;
    }

    private static void takeOff(MinecraftServer server, Map<String, Object> state, BlockPos ground, double shift) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        MobScene.listen();
        float yaw = CovMdBLanes.FACING.toYRot();
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        Entity drone = Arena.track(DispenserRig.place(level, player, DispenserRig.fiveInch(), "CovMdB Windkugel", ground));
        DispenserRig.load(drone, new ItemStack(Items.WIND_CHARGE, 2));
        state.put(DRONE, drone.getUUID());
        state.put("covmdb.wind.shift", shift);
    }

    private static void hold(Map<String, Object> state) {
        CovMdBDrift drift = (CovMdBDrift) state.get(DRIFT);
        drift.hold(drift.targetX() + (double) state.get("covmdb.wind.shift"), drift.targetY(), drift.targetZ());
    }

    private static Entity drone(ServerLevel level, Map<String, Object> state) {
        Entity drone = level.getEntity((UUID) state.get(DRONE));
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt");
        }
        return drone;
    }

    private static StepResult settled(SelfTest.StepContext ctx, int ticks) {
        CovMdBDrift drift = (CovMdBDrift) ctx.state().get(DRIFT);
        if (ticks > 30 && drift.settled()) {
            CovMdBDrift.Sample last = drift.last();
            return StepResult.pass(String.format(Locale.ROOT, "schwebt bei %.2f/%.2f/%.2f, %.1f m über Grund", last.x(), last.y(), last.z(), last.height()));
        }
        CovMdBDrift.Sample last = drift.last();
        return ticks > 600 ? StepResult.fail("schwebt nicht ruhig: " + last) : StepResult.pending();
    }

    private static void fire(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        CovMdBDrift drift = (CovMdBDrift) state.get(DRIFT);
        DroneView view = DispenserRig.view(drone);
        Vec3 forward = new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
        int pushes = MobScene.pushes(drone).size();
        drift.level();
        drift.record();
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        if (entry.result() == null || !entry.result().success() || !(entry.result().entity().orElse(null) instanceof WindCharge)) {
            throw new IllegalStateException("keine Windkugel: " + entry.status());
        }
        state.put(SHOT, new Shot(drone.position(), forward, view.massKg(), entry.result().recoilNs(), pushes,
                CovMdBChargeWatch.start(entry.result().entity().get(), drone)));
    }

    private static List<CovMdBDrift.Sample> samples(Map<String, Object> state) {
        List<CovMdBDrift.Sample> samples = ((CovMdBDrift) state.get(DRIFT)).stop();
        if (samples.size() < 10) {
            throw new IllegalStateException("zu wenige Messpunkte: " + samples.size());
        }
        return samples;
    }

    private static void recovery(MinecraftServer server, Map<String, Object> state) {
        Shot shot = (Shot) state.get(SHOT);
        List<CovMdBDrift.Sample> samples = samples(state);
        double t0 = samples.getFirst().time();
        double peakBack = 0.0;
        double maxTilt = 0.0;
        double lastSteep = 0.0;
        double minHeight = Double.POSITIVE_INFINITY;
        boolean grounded = false;
        boolean armed = true;
        for (CovMdBDrift.Sample sample : samples) {
            peakBack = Math.max(peakBack, -sample.along(shot.forward().x, shot.forward().z));
            maxTilt = Math.max(maxTilt, sample.tiltDeg());
            if (sample.tiltDeg() >= LEVEL_DEGREES) {
                lastSteep = sample.time() - t0;
            }
            minHeight = Math.min(minHeight, sample.height());
            grounded |= sample.grounded();
            armed &= sample.armed();
        }
        CovMdBDrift.Sample end = samples.getLast();
        double ideal = shot.recoilNs() / shot.massKg();
        String report = String.format(Locale.ROOT, "Rückstoß %.2f N·s an %.3f kg (ideal %.2f m/s), Rückwärtstempo höchstens %.2f m/s (%.0f %%), Lage höchstens "
                        + "%.1f°, zuletzt über %.0f° nach %.2f s, am Ende %.1f°, Höhe über Grund mindestens %.2f m (Start %.1f m), Bodenkontakt %s, scharf %s, "
                        + "%d Messpunkte über %.2f s",
                shot.recoilNs(), shot.massKg(), ideal, peakBack, 100.0 * peakBack / ideal, maxTilt, LEVEL_DEGREES, lastSteep, end.tiltDeg(), minHeight,
                samples.getFirst().height(), grounded ? "ja" : "nein", armed ? "durchgehend" : "nein", samples.size(), end.time() - t0);
        boolean recovered = lastSteep <= RECOVER_SECONDS && end.tiltDeg() < LEVEL_DEGREES;
        boolean strong = peakBack >= STRONG_SHARE * ideal;
        if (!recovered || grounded || !armed || minHeight < MIN_HEIGHT || !strong) {
            throw new IllegalStateException(report);
        }
        state.put(NOTE, report + "; Schwelle „kräftig“: mindestens " + (int) (STRONG_SHARE * 100) + " % von Impuls/Masse");
    }

    private static Drift drift(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        Shot shot = (Shot) state.get(SHOT);
        List<CovMdBDrift.Sample> samples = samples(state);
        double peakBack = 0.0;
        double maxTilt = 0.0;
        double lastSteep = 0.0;
        double minHeight = Double.POSITIVE_INFINITY;
        boolean grounded = false;
        double t0 = samples.getFirst().time();
        for (CovMdBDrift.Sample sample : samples) {
            peakBack = Math.max(peakBack, -sample.along(shot.forward().x, shot.forward().z));
            maxTilt = Math.max(maxTilt, sample.tiltDeg());
            if (sample.tiltDeg() >= LEVEL_DEGREES) {
                lastSteep = sample.time() - t0;
            }
            minHeight = Math.min(minHeight, sample.height());
            grounded |= sample.grounded();
        }
        CovMdBDrift.Sample end = samples.getLast();
        Vec3 moved = new Vec3(end.x(), end.y(), end.z()).subtract(shot.start());
        double back = -(moved.x * shot.forward().x + moved.z * shot.forward().z);
        int wind = (int) MobScene.pushes(drone).stream().skip(shot.pushesBefore()).filter(push -> push.cause() == DroneKnockback.Cause.WIND_CHARGE).count();
        return new Drift(back, peakBack, wind, shot.watch().distance(), shot.watch().burstTick(), maxTilt, lastSteep, end.tiltDeg(), minHeight, grounded);
    }

    private static void wall(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = drone(server, state);
        CovMdBDrift drift = (CovMdBDrift) state.get(DRIFT);
        Shot shot = (Shot) state.get(SHOT);
        if (Math.abs(shot.forward().x - 1.0) > 0.05) {
            throw new IllegalStateException("Nase zeigt nicht nach Osten: " + shot.forward());
        }
        double distance = (double) state.get(WALL);
        double faceX = drift.targetX() + distance;
        int wallX = (int) Math.round(faceX);
        drift.hold(wallX - distance, drift.targetY(), drift.targetZ());
        BlockPos ground = Arena.ground();
        int z = (int) Math.floor(drift.targetZ());
        Arena.fill(level, new BlockPos(wallX, ground.getY() + 1, z - 3), new BlockPos(wallX, ground.getY() + 10, z + 3), Blocks.STONE.defaultBlockState());
        DispenserRig.load(drone, new ItemStack(Items.WIND_CHARGE, 2));
        state.put("covmdb.wind.wall", wallX);
    }

    private static void compare(MinecraftServer server, Map<String, Object> state) {
        Drift free = (Drift) state.get(FREE);
        Drift wall = drift(server, state);
        CovMdBDrift drift = (CovMdBDrift) state.get(DRIFT);
        Shot shot = (Shot) state.get(SHOT);
        double distance = (int) state.get("covmdb.wind.wall") - shot.start().x;
        String report = String.format(Locale.ROOT, "ins Freie %.2f m zurück in %.1f s (höchstens %.2f m/s, %d Windstöße), an der Wand (Wand %.2f m vor der Drohne) "
                        + "%.2f m zurück (höchstens %.2f m/s, %d Windstöße über DRONE_KNOCKBACK), Windkugel platzt nach %d Ticks %.2f m von der Drohne "
                        + "(Vanilla-Wirkradius 2,4 Blöcke)", free.back(), DRIFT_TICKS / 20.0, free.peakBack(), free.windPushes(), distance, wall.back(),
                wall.peakBack(), wall.windPushes(), wall.burstTick(), wall.burstDistance())
                + String.format(Locale.ROOT, "; danach an der Wand Lage höchstens %.0f°, zuletzt über %.0f° nach %.2f s, am Ende %.1f°, Höhe mindestens %.2f m, "
                        + "Bodenkontakt %s – %s", wall.maxTilt(), LEVEL_DEGREES, wall.lastSteep(), wall.endTilt(), wall.minHeight(), wall.grounded() ? "ja" : "nein",
                wall.recovered() ? "fängt sich" : "fängt sich nicht");
        drift.hold(drift.targetX(), drift.targetY(), drift.targetZ());
        if (wall.windPushes() < 1 || !(wall.back() > free.back() + 0.05) || !wall.recovered()) {
            throw new IllegalStateException(report);
        }
        state.put(NOTE, report + String.format(Locale.ROOT, ", +%.0f %% Versatz", 100.0 * (wall.back() - free.back()) / Math.max(1.0E-6, free.back())));
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        int since = (int) ctx.state().computeIfAbsent("covmdb.wind.cleanup", key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            Object id = state.get(DRONE);
            Entity drone = id == null ? null : Arena.level(server).getEntity((UUID) id);
            if (drone != null) {
                PropwashApi.drones().autonomy().stop(drone);
            }
        });
    }
}
