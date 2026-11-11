package de.lardtom.mobdefense.dev.booster.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.BoostState;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.booster.Boosters;
import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.booster.BoostFlight;
import de.lardtom.mobdefense.dev.booster.BoosterRig;
import de.lardtom.mobdefense.dev.booster.CovMdBElytraReference;
import de.lardtom.mobdefense.dev.booster.CovMdBSpeedLog;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.CovMdBDrift;
import de.lardtom.mobdefense.dev.dispenser.CovMdBLanes;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdBEach;
import de.lardtom.mobdefense.dev.dispenser.client.HoverAssist;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.spear.CovMdBSpearRun;
import de.lardtom.mobdefense.dev.spear.SpearDrive;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CovMdBBoosterSteps {
    public static final String EXIT_KEY = "key.propwash.drone_exit";
    public static final double TOLERANCE = 0.30;
    public static final double CRUISE = 10.0;
    public static final int FRAMES = 8;
    public static final int FRAME_TICKS = 2;
    public static final int LAG_TICKS = 2;
    private static final String DRONE = "covmdb.booster.drone";
    private static final String FLIGHT = "covmdb.booster.flight";
    private static final String LOG = "covmdb.booster.log";
    private static final String NOTE = "covmdb.booster.note";
    private static final String COUNTS = "covmdb.booster.counts";
    private static final String HOVER = "covmdb.booster.hover";
    private static final String V0 = "covmdb.booster.v0";
    private static boolean hudHidden;

    private CovMdBBoosterSteps() {
    }

    private record Counts(int attempts, int arrows, int spearWear, boolean burning, long ending) {
    }

    private record Boost(double v0, double gain, double seconds, int pushes) {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("Booster: Flug beenden beim Brennen – nach 5 Ticks FPV beenden (PILOT_CHANGED), danach kein Schub mehr, Tempo nimmt ab", 2400,
                Script.create()
                        .server("Drohne mit Raketen-Booster und Rakete", CovMdBBoosterSteps::pilotScene)
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(4.0))
                        .server("Zündung über Zubehör-Aktion 3", CovMdBBoosterSteps::pilotIgnite)
                        .sleep(5)
                        .run("Taste FPV beenden", () -> {
                            Pilot.land(Minecraft.getInstance());
                            Pilot.click(EXIT_KEY);
                        })
                        .await("FPV endet", 80, () -> !PropwashClientApi.input().flying())
                        .server("Booster endet mit PILOT_CHANGED", CovMdBBoosterSteps::exited)
                        .then("Ende", CovMdBBoosterSteps::note)
                        .sleep(30)
                        .server("kein Schub, Tempo nimmt ab", CovMdBBoosterSteps::coasting)
                        .then("Tempo", CovMdBBoosterSteps::note), CovMdBBoosterSteps::cleanup);
        steps.add("Booster: alle drei Teile an der 7″ – Aktion 1 nur Pfeile, Aktion 3 nur Booster, Speer-Treffer am Zombie, keine Doppelauslösung", 3000,
                Script.create()
                        .server("7″ mit Dispenser, Speer und Booster schwebt", CovMdBBoosterSteps::sevenScene)
                        .then("schwebt ruhig", CovMdBBoosterSteps::boostSettled)
                        .server("Aktion 1", (server, state) -> state.put(NOTE, action(server, state, 1)))
                        .then("Aktion 1", CovMdBBoosterSteps::note)
                        .sleep(6)
                        .server("Aktion 3", (server, state) -> state.put(NOTE, action(server, state, 3)))
                        .then("Aktion 3", CovMdBBoosterSteps::note)
                        .then("Brennschluss", CovMdBBoosterSteps::burnedOut)
                        .server("Landen, Speer-Anlauf mit 15 m/s gegen einen Zombie", CovMdBBoosterSteps::spearRun)
                        .then("Anlauf", CovMdBBoosterSteps::spearDone)
                        .server("Speer-Treffer", CovMdBBoosterSteps::spearHit)
                        .then("Speer", CovMdBBoosterSteps::note), CovMdBBoosterSteps::cleanup);
        steps.add("Booster gegen Elytra-Rakete – Tempozuwachs im Geradeausflug und Schubdauer mit Flugdauer 1, Toleranz ±30 %, Zuwachs aus dem Schwebeflug zur Info", 4000,
                Script.create()
                        .server("5″ mit Raketen-Booster", (server, state) -> driftScene(server, state, Arena.ground(-45, -40)))
                        .then("schwebt autonom auf 6 m", CovMdBEach.launch(CovMdBBoosterSteps::serverDrone, 6.0, FLIGHT))
                        .then("schwebt ruhig", CovMdBBoosterSteps::driftSettled)
                        .server("Zündung aus dem Schwebeflug", CovMdBBoosterSteps::driftIgnite)
                        .then("Brennschluss", CovMdBBoosterSteps::burnedOut)
                        .sleep(10)
                        .server("Messung Schwebeflug", (server, state) -> state.put(HOVER, measure(server, state)))
                        .server("Geradeausflug mit 10 m/s", (server, state) -> {
                            CovMdBDrift drift = (CovMdBDrift) state.get(FLIGHT);
                            Vec3 nose = nose(drone(server, state));
                            drift.cruise(nose.x * CRUISE, nose.z * CRUISE);
                        })
                        .then("Tempo 10 m/s erreicht", CovMdBBoosterSteps::cruising)
                        .server("Zündung im Geradeausflug", CovMdBBoosterSteps::driftIgnite)
                        .then("Brennschluss", CovMdBBoosterSteps::burnedOut)
                        .sleep(10)
                        .server("Vergleich mit der Vanilla-Elytra-Rakete", CovMdBBoosterSteps::compare)
                        .then("Ergebnis", CovMdBBoosterSteps::note), CovMdBBoosterSteps::cleanup);
        steps.add("bildschirmfotos: Booster-Funken am Heck – Bildfolge in festem Takt, Third Person", 2400, Script.create()
                .server("Drohne mit Raketen-Booster", (server, state) -> driftScene(server, state, Arena.ground(-8, 12)))
                .then("schwebt tief", CovMdBEach.launch(CovMdBBoosterSteps::serverDrone, 2.5, FLIGHT))
                .then("schwebt ruhig", CovMdBBoosterSteps::driftSettled)
                .server("Kamera seitlich, Blick auf die Bahn der ersten Meter", CovMdBBoosterSteps::sideCamera)
                .sleep(15)
                .run("HUD aus", CovMdBBoosterSteps::hideHud)
                .server("Zündung", (server, state) -> {
                    Entity drone = drone(server, state);
                    BoosterRig.load(drone, BoosterRig.rocket(3, 0));
                    if (!BoosterRig.ignite(drone, Arena.player(server))) {
                        throw new IllegalStateException("Booster zündet nicht");
                    }
                })
                .then("Bildfolge", CovMdBBoosterSteps::sequence)
                .run("HUD zurück", CovMdBBoosterSteps::restoreHud)
                .then("Brennschluss", CovMdBBoosterSteps::burnedOut), CovMdBBoosterSteps::cleanup);
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static Entity drone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(DRONE);
        Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt am Server");
        }
        return drone;
    }

    private static Vec3 nose(Entity drone) {
        DroneView view = BoosterRig.view(drone);
        return new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
    }

    private static Entity place(MinecraftServer server, Map<String, Object> state, BuildSpec spec, BlockPos ground, String name) {
        BoosterRig.listen();
        ServerPlayer player = Arena.player(server);
        float yaw = CovMdBLanes.FACING.toYRot();
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        Entity drone = Arena.track(BoosterRig.place(Arena.level(server), player, spec, name, ground));
        Boosters.forget(drone.getUUID());
        state.put(DRONE, drone.getUUID());
        state.put(Pilot.DRONE, drone.getUUID());
        return drone;
    }

    private static StepResult burnedOut(SelfTest.StepContext ctx, int ticks) {
        Object id = ctx.state().get(DRONE);
        if (id instanceof UUID uuid && Boosters.lastEnding(uuid).isPresent()) {
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("Booster brennt nicht aus") : StepResult.pending();
    }

    private static void pilotScene(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = place(server, state, BoosterRig.build(), Arena.ground(20, -50), "CovMdB Booster Pilot");
        BoosterRig.load(drone, BoosterRig.rocket(3, 0));
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(Pilot.GOGGLES));
        Vec3 stand = drone.position().add(0.0, 0.0, 3.0);
        player.teleportTo(Arena.level(server), stand.x, stand.y, stand.z, Set.<Relative>of(), CovMdBLanes.FACING.toYRot(), 10.0F, true);
    }

    private static void pilotIgnite(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        if (!BoosterRig.ignite(drone, Arena.player(server))) {
            throw new IllegalStateException("Booster zündet nicht");
        }
        state.put(LOG, CovMdBSpeedLog.start(drone, nose(drone), 80));
    }

    private static void exited(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        Boosters.Ending ending = Boosters.lastEnding(drone.getUUID()).orElseThrow(() -> new IllegalStateException("kein Ende des Boosters"));
        if (ending.reason() != Boosters.Reason.PILOT_CHANGED || Boosters.burning(drone) || BoostState.of(drone).active()
                || ending.pushes() >= BoostProfile.burnTicks(3)) {
            throw new IllegalStateException("Ende " + ending + ", brennt " + Boosters.burning(drone));
        }
        state.put("covmdb.booster.stop", ending.gameTime());
        state.put(NOTE, "FPV beendet nach " + ending.pushes() + " von " + BoostProfile.burnTicks(3) + " Schüben: Ende PILOT_CHANGED, Booster aus, "
                + "Drohnen-Daten zurückgesetzt, Pilot " + BoosterRig.view(drone).pilot().map(UUID::toString).orElse("keiner"));
    }

    private static void coasting(MinecraftServer server, Map<String, Object> state) {
        CovMdBSpeedLog log = (CovMdBSpeedLog) state.get(LOG);
        long stop = (long) state.get("covmdb.booster.stop");
        List<CovMdBSpeedLog.Point> points = log.points();
        double atStop = Double.NaN;
        double peakAfter = Double.NEGATIVE_INFINITY;
        double end = Double.NaN;
        int burningAfter = 0;
        for (CovMdBSpeedLog.Point point : points) {
            if (point.gameTime() <= stop + LAG_TICKS) {
                atStop = Double.isNaN(atStop) ? point.along() : Math.max(atStop, point.along());
            } else {
                peakAfter = Math.max(peakAfter, point.along());
            }
            if (point.gameTime() > stop) {
                end = point.along();
                burningAfter += point.burning() ? 1 : 0;
            }
        }
        String report = String.format(Locale.ROOT, "Tempo entlang der Nase beim Aussteigen (bis 2 Ticks danach, Positionsmessung) %.2f m/s, danach höchstens "
                + "%.2f m/s, nach %d Ticks %.2f m/s, "
                + "%d Ticks mit Schub nach dem Aussteigen", atStop, peakAfter, points.size(), end, burningAfter);
        if (Double.isNaN(atStop) || Double.isNaN(end) || burningAfter > 0 || peakAfter > atStop + 0.3 || !(end < atStop)) {
            throw new IllegalStateException(report);
        }
        state.put(NOTE, report);
    }

    private static BuildSpec sevenInch() {
        return BuildSpec.of(BoosterRig.propwash("long_range_7"), BoosterRig.propwash("stack_30"), BoosterRig.propwash("m2807_1300"),
                        BoosterRig.propwash("p7_2b"), BoosterRig.propwash("digital_hd"), BoosterRig.propwash("b6s_4000_lion"))
                .withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT).withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT)
                .withAccessory(AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER);
    }

    private static void sevenScene(MinecraftServer server, Map<String, Object> state) {
        Entity drone = place(server, state, sevenInch(), Arena.ground(20, -64), "CovMdB 7 Zoll");
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 16));
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
        BoosterRig.load(drone, BoosterRig.rocket(1, 0));
        state.put(FLIGHT, BoosterRig.hover(drone, Arena.player(server), 4.0));
        state.put(COUNTS, counts(drone));
    }

    private static StepResult boostSettled(SelfTest.StepContext ctx, int ticks) {
        BoostFlight flight = (BoostFlight) ctx.state().get(FLIGHT);
        BoostFlight.Snapshot last = flight.last();
        if (last != null && ticks > 40 && last.armed() && Math.abs(last.vy()) < 0.3 && last.horizontalSpeed() < 0.4
                && Math.abs(last.y() - flight.targetY()) < 0.6) {
            return StepResult.pass(String.format(Locale.ROOT, "schwebt bei y %.2f", last.y()));
        }
        return ticks > 600 ? StepResult.fail("schwebt nicht ruhig: " + last) : StepResult.pending();
    }

    private static Counts counts(Entity drone) {
        ItemStack arrows = DispenserRig.inventory(drone).get(0);
        ItemStack spear = SpearDrive.spear(drone);
        long ending = Boosters.lastEnding(drone.getUUID()).map(Boosters.Ending::gameTime).orElse(-1L);
        return new Counts(DispenserLog.counts(drone).attempts(), arrows.is(Items.ARROW) ? arrows.getCount() : 0, spear.getDamageValue(),
                Boosters.burning(drone), ending);
    }

    private static String action(MinecraftServer server, Map<String, Object> state, int number) {
        Entity drone = drone(server, state);
        Counts before = (Counts) state.get(COUNTS);
        boolean pressed = PropwashApi.accessories().triggerAction(drone, number, true, Arena.player(server));
        PropwashApi.accessories().triggerAction(drone, number, false, Arena.player(server));
        Counts after = counts(drone);
        boolean rocket = BoosterRig.booster(drone).get(BoosterBehavior.SLOT).is(Items.FIREWORK_ROCKET);
        state.put(COUNTS, after);
        String report = String.format(Locale.ROOT, "Aktion %d (angenommen %s): Dispenser %d → %d Versuche, Pfeile %d → %d, Booster %s → %s, Rakete im Slot %s, "
                        + "Speer-Abnutzung %d → %d", number, pressed, before.attempts(), after.attempts(), before.arrows(), after.arrows(),
                before.burning() ? "brennt" : "aus", after.burning() ? "brennt" : "aus", rocket, before.spearWear(), after.spearWear());
        boolean ok;
        if (number == 1) {
            DispenserLog.Entry entry = DispenserLog.last(drone).orElse(null);
            boolean arrow = entry != null && entry.result() != null && entry.result().entity().orElse(null) instanceof AbstractArrow;
            ok = arrow && after.attempts() == before.attempts() + 1 && after.arrows() == before.arrows() - 1 && !after.burning() && rocket
                    && after.spearWear() == before.spearWear();
        } else {
            ok = after.burning() && after.attempts() == before.attempts() && after.arrows() == before.arrows() && after.spearWear() == before.spearWear();
        }
        if (!ok) {
            throw new IllegalStateException(report);
        }
        return report;
    }

    private static void spearRun(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = drone(server, state);
        Boosters.Ending ending = Boosters.lastEnding(drone.getUUID()).orElseThrow();
        if (ending.reason() != Boosters.Reason.BURNED_OUT) {
            throw new IllegalStateException("Booster endet mit " + ending.reason());
        }
        PropwashApi.drones().autonomy().stop(drone);
        state.put(COUNTS, counts(drone));
        Vec3 nose = nose(drone);
        BlockPos ground = Arena.ground(30, -64);
        Vec3 start = new Vec3(ground.getX() + 0.5, ground.getY() + 1.9, ground.getZ() + 0.5);
        drone.setNoGravity(true);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.setPos(start.x, start.y, start.z);
        SpearStrikes.forget(drone);
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0, false, false));
        Vec3 at = new Vec3(start.x, ground.getY() + 1.0, start.z).add(nose.scale(3.0));
        zombie.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        Arena.spawn(level, zombie);
        state.put("covmdb.booster.zombie", zombie);
        state.put("covmdb.booster.run", CovMdBSpearRun.start(drone, nose, 15.0, 5.5));
    }

    private static StepResult spearDone(SelfTest.StepContext ctx, int ticks) {
        CovMdBSpearRun run = (CovMdBSpearRun) ctx.state().get("covmdb.booster.run");
        if (run != null && run.done()) {
            return StepResult.pass();
        }
        return ticks > 300 ? StepResult.fail("Anlauf endet nicht") : StepResult.pending();
    }

    private static void spearHit(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        Counts before = (Counts) state.get(COUNTS);
        Counts after = counts(drone);
        Zombie zombie = (Zombie) state.get("covmdb.booster.zombie");
        float lost = zombie.getMaxHealth() - zombie.getHealth();
        String report = String.format(Locale.ROOT, "Speer-Anlauf: Zombie −%.1f, Speer-Abnutzung %d → %d, Dispenser %d → %d Versuche, Pfeile %d → %d, "
                        + "Booster %s, letztes Booster-Ende unverändert %s", lost, before.spearWear(), after.spearWear(), before.attempts(), after.attempts(),
                before.arrows(), after.arrows(), after.burning() ? "brennt" : "aus", after.ending() == before.ending());
        if (!(lost > 0.0F) || after.spearWear() != before.spearWear() + 1 || after.attempts() != before.attempts() || after.arrows() != before.arrows()
                || after.burning() || after.ending() != before.ending()) {
            throw new IllegalStateException(report);
        }
        state.put(NOTE, report + "; Zähler je Funktion: Dispenser 1 Schuss, Booster 1 Zündung, Speer 1 Treffer");
    }

    private static void driftScene(MinecraftServer server, Map<String, Object> state, BlockPos ground) {
        place(server, state, BoosterRig.build(), ground, "CovMdB Booster");
    }

    private static Entity serverDrone(Map<String, Object> state) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("kein integrierter Server");
        }
        return drone(server, state);
    }

    private static StepResult driftSettled(SelfTest.StepContext ctx, int ticks) {
        CovMdBDrift drift = (CovMdBDrift) ctx.state().get(FLIGHT);
        if (ticks > 30 && drift.settled()) {
            return StepResult.pass();
        }
        return ticks > 600 ? StepResult.fail("schwebt nicht ruhig: " + drift.last()) : StepResult.pending();
    }

    private static StepResult cruising(SelfTest.StepContext ctx, int ticks) {
        CovMdBDrift drift = (CovMdBDrift) ctx.state().get(FLIGHT);
        CovMdBDrift.Sample last = drift.last();
        double speed = last == null ? 0.0 : Math.hypot(last.vx(), last.vz());
        int steady = (int) ctx.state().getOrDefault("covmdb.booster.steady", 0);
        steady = Math.abs(speed - CRUISE) < 0.6 ? steady + 1 : 0;
        ctx.state().put("covmdb.booster.steady", steady);
        if (steady >= 15) {
            return StepResult.pass(String.format(Locale.ROOT, "Geradeausflug mit %.2f m/s", speed));
        }
        return ticks > 600 ? StepResult.fail(String.format(Locale.ROOT, "Tempo %.2f statt %.0f m/s", speed, CRUISE)) : StepResult.pending();
    }

    private static void driftIgnite(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        CovMdBDrift drift = (CovMdBDrift) state.get(FLIGHT);
        Vec3 nose = nose(drone);
        CovMdBDrift.Sample last = drift.last();
        double v0 = last == null ? 0.0 : last.along(nose.x, nose.z);
        Boosters.forget(drone.getUUID());
        BoosterRig.load(drone, BoosterRig.rocket(1, 0));
        drift.glide();
        drift.record();
        state.put(V0, v0);
        if (!BoosterRig.ignite(drone, Arena.player(server))) {
            throw new IllegalStateException("Booster zündet nicht");
        }
    }

    private static Boost measure(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        CovMdBDrift drift = (CovMdBDrift) state.get(FLIGHT);
        Vec3 nose = nose(drone);
        List<CovMdBDrift.Sample> samples = drift.stop();
        Boosters.Ending ending = Boosters.lastEnding(drone.getUUID()).orElseThrow(() -> new IllegalStateException("kein Ende"));
        if (ending.reason() != Boosters.Reason.BURNED_OUT || samples.size() < 20) {
            throw new IllegalStateException("Ende " + ending.reason() + ", " + samples.size() + " Messpunkte");
        }
        double v0 = (double) state.get(V0);
        double peak = v0;
        for (CovMdBDrift.Sample sample : samples) {
            peak = Math.max(peak, sample.along(nose.x, nose.z));
        }
        drift.hold(drone.getX(), drift.targetY(), drone.getZ());
        return new Boost(v0, peak - v0, ending.pushes() * BoostProfile.TICK_SECONDS, ending.pushes());
    }

    private static void compare(MinecraftServer server, Map<String, Object> state) {
        Boost hover = (Boost) state.get(HOVER);
        Boost cruise = measure(server, state);
        CovMdBElytraReference.Result elytraHover = CovMdBElytraReference.rocket(1, hover.v0());
        CovMdBElytraReference.Result elytraCruise = CovMdBElytraReference.rocket(1, cruise.v0());
        double hoverGain = hover.gain() / elytraHover.gainMetresPerSecond();
        double cruiseGain = cruise.gain() / elytraCruise.gainMetresPerSecond();
        double burn = cruise.seconds() / elytraCruise.boostSeconds();
        String report = String.format(Locale.ROOT, "Schwebeflug: Booster +%.1f m/s (von %.1f), Elytra-Rakete +%.1f m/s (%.1f…%.1f), Verhältnis %.2f; "
                        + "Geradeausflug: Booster +%.1f m/s (von %.1f), Elytra-Rakete +%.1f m/s (%.1f…%.1f), Verhältnis %.2f; Schubdauer Booster %.2f s "
                        + "(%d Schübe), Elytra-Rakete im Mittel %.2f s, Verhältnis %.2f; Toleranz ±%.0f %%",
                hover.gain(), hover.v0(), elytraHover.gainMetresPerSecond(), elytraHover.minGain(), elytraHover.maxGain(), hoverGain, cruise.gain(),
                cruise.v0(), elytraCruise.gainMetresPerSecond(), elytraCruise.minGain(), elytraCruise.maxGain(), cruiseGain, cruise.seconds(),
                cruise.pushes(), elytraCruise.boostSeconds(), burn, TOLERANCE * 100.0);
        boolean ok = Math.abs(cruiseGain - 1.0) <= TOLERANCE && Math.abs(burn - 1.0) <= TOLERANCE;
        if (!ok) {
            throw new IllegalStateException(report);
        }
        state.put(NOTE, report);
    }

    private static void sideCamera(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(server, state);
        DroneView view = BoosterRig.view(drone);
        Vec3 nose = nose(drone);
        Vec3 right = new Vec3(-nose.z, 0.0, nose.x);
        Vec3 tail = view.center().add(nose.scale(1.2));
        Vec3 eye = tail.add(right.scale(2.6)).subtract(nose.scale(0.6)).add(0.0, 0.4, 0.0);
        Vec3 feet = eye.subtract(0.0, player.getEyeHeight(), 0.0);
        Vec3 look = tail.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(Arena.level(server), feet.x, feet.y, feet.z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    @SuppressWarnings("unchecked")
    private static StepResult sequence(SelfTest.StepContext ctx, int ticks) {
        if (!Shots.enabled()) {
            return StepResult.pass("ohne Bildschirmfotos (Schalter aus)");
        }
        List<CompletableFuture<String>> frames = (List<CompletableFuture<String>>) ctx.state().computeIfAbsent("covmdb.booster.frames",
                key -> new ArrayList<CompletableFuture<String>>());
        if (frames.size() < FRAMES) {
            if (ticks % FRAME_TICKS == 0) {
                frames.add(Shots.capture(String.format(Locale.ROOT, "cov_booster_sparks_%02d", frames.size() + 1)));
            }
            return StepResult.pending();
        }
        for (CompletableFuture<String> frame : frames) {
            if (!frame.isDone()) {
                return ticks > 400 ? StepResult.fail("Bildfolge hängt") : StepResult.pending();
            }
            if (frame.isCompletedExceptionally()) {
                return StepResult.fail("Bild der Folge: " + frame.exceptionNow());
            }
        }
        return StepResult.pass(FRAMES + " Bilder cov_booster_sparks_01…" + String.format(Locale.ROOT, "%02d", FRAMES) + " im Abstand von " + FRAME_TICKS
                + " Ticks ab der Zündung, Kamera seitlich hinter der Bahn der ersten Meter (" + frames.getFirst().join() + ")");
    }

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hudHidden = true;
        }
    }

    private static void restoreHud() {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hudHidden = false;
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        restoreHud();
        int since = (int) ctx.state().computeIfAbsent("covmdb.booster.cleanup", key -> ctx.tick());
        if (ctx.tick() == since) {
            Pilot.land(Minecraft.getInstance());
        }
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            CovMdBSpearRun.stop();
            Pilot.release(server);
            Object id = state.get(DRONE);
            Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
            if (drone != null) {
                PropwashApi.drones().autonomy().stop(drone);
            }
            ServerPlayer player = Arena.player(server);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }
}
