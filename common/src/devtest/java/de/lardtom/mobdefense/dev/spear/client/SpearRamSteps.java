package de.lardtom.mobdefense.dev.spear.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.client.HoverAssist;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.spear.SpearChecks;
import de.lardtom.mobdefense.spear.SpearFeedback;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SpearRamSteps {
    public static final int PASSES = 5;
    public static final int MIN_CONTACTS = 4;
    public static final double RUN = 14.0;
    public static final double SPEED = 12.0;
    public static final double HOVER_METRES = 1.2;
    public static final double FOLLOW_UNTIL = 4.0;
    public static final int SETTLE_TICKS = 15;
    public static final int PASS_TICKS = 160;
    public static final int BACK_TICKS = 300;
    public static final int REARM_TICKS = 600;
    public static final String FLIP = "key.propwash.drone_flip_upright";
    private static final List<String> LOG = new CopyOnWriteArrayList<>();
    private static boolean listening;
    private static volatile Mob villager;
    private static volatile int droneId = -1;
    private static volatile int frameContacts;
    private static volatile String frameNote = "";
    private static volatile Vec3 nose = Vec3.ZERO;
    private static volatile Vec3 start = Vec3.ZERO;
    private static volatile double villagerY;
    private static float before;
    private static int contactTick;
    private static double peak;
    private static int contacts;
    private static int stabbed;
    private static int fallen;

    private SpearRamSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        Script script = Script.create()
                .server("7″ mit Netheritspeer", SpearRamSteps::rig)
                .append(Pilot.fly())
                .append(Pilot.takeOff(HOVER_METRES))
                .sleep(40)
                .run("ausrichten", SpearRamSteps::align)
                .sleep(60)
                .server("Dorfbewohner vor der Nase", SpearRamSteps::reposition)
                .sleep(10);
        for (int i = 1; i <= PASSES; i++) {
            int n = i;
            script.run("Anflug " + n, SpearRamSteps::go)
                    .then("Kontakt " + n, (ctx, t) -> contact(ctx, t, n))
                    .then("wieder scharf " + n, SpearRamSteps::rearm)
                    .run("zurück " + n, () -> HoverAssist.hold(start.x, HoverAssist.targetY(), start.z))
                    .then("zurück erreicht " + n, SpearRamSteps::back)
                    .run("neu ausrichten " + n, SpearRamSteps::align)
                    .sleep(40)
                    .server("Dorfbewohner zurück " + n, SpearRamSteps::reposition)
                    .sleep(10);
        }
        script.then("Ergebnis", SpearRamSteps::result);
        steps.add("S8 Rammen im echten Flug – 7″ mit Netheritspeer fliegt fünfmal mit 12 m/s in Dorfbewohner (ein gefallener wird ersetzt): jeder "
                + "Kontakt ist ein Speer-Stich, mindestens vier Kontakte, Dorfbewohner fallen", 9000, script, Script.create()
                .run("Flughilfe aus, landen", () -> Pilot.land(Minecraft.getInstance()))
                .server("Flug beenden", (server, state) -> Pilot.release(server)));
    }

    private static void rig(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.spawn(server, state, SpearFeedbackSteps.sevenInch(), "S8 Rammen 7", Arena.ground(0, -8), 180.0F);
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"))
                .set(0, new ItemStack(Items.NETHERITE_SPEAR));
        droneId = drone.getId();
        villager = null;
        LOG.clear();
        contacts = 0;
        stabbed = 0;
        fallen = 0;
        if (!listening) {
            listening = true;
            PropwashEvents.DRONE_HIT_ENTITY.register(hit -> {
                Mob target = villager;
                if (target != null && hit.target() == target && hit.drone().getId() == droneId) {
                    frameContacts++;
                    double along = PropwashApi.drones().view(hit.drone()).map(view -> view.forward().normalize().dot(hit.relativeVelocity())).orElse(0.0);
                    frameNote = String.format(Locale.ROOT, "Körpertreffer %.1f bei %.1f m/s, %.1f m/s entlang der Nase", hit.damage(), hit.closingSpeed(),
                            along);
                }
            });
        }
    }

    private static void align() {
        Pilot.drone().ifPresent(d -> {
            start = d.position();
            HoverAssist.hold(start.x, HoverAssist.targetY(), start.z);
        });
    }

    private static void go() {
        Mob v = villager;
        before = v == null ? 0.0F : v.getHealth();
        contactTick = -1;
        peak = 0.0;
        frameContacts = 0;
        frameNote = "";
        SpearFeedback.reset();
        HoverAssist.cruise(HoverAssist.targetY(), nose.x * SPEED, nose.z * SPEED);
    }

    private static void follow(MinecraftServer server) {
        Mob v = villager;
        Entity d = Arena.level(server).getEntity(droneId);
        if (v == null || d == null || !v.isAlive()) {
            return;
        }
        Vec3 flat = new Vec3(v.getX() - d.getX(), 0.0, v.getZ() - d.getZ());
        double along = flat.dot(nose);
        if (along < FOLLOW_UNTIL) {
            return;
        }
        Vec3 side = flat.subtract(nose.scale(along));
        v.snapTo(v.getX() - side.x, villagerY, v.getZ() - side.z, v.getYRot(), 0.0F);
        v.setDeltaMovement(Vec3.ZERO);
    }

    private static StepResult contact(SelfTest.StepContext ctx, int t, int n) {
        Mob v = villager;
        if (v == null) {
            return StepResult.fail("kein Dorfbewohner");
        }
        if (before <= 0.0F || (t == 0 && !v.isAlive())) {
            return StepResult.pass("kein Dorfbewohner vor dem Anflug");
        }
        Entity drone = Pilot.drone().orElse(null);
        double along = drone == null ? 0.0 : drone.position().subtract(start).dot(nose);
        FlightState fs = HoverAssist.last();
        if (contactTick < 0) {
            peak = Math.max(peak, fs == null ? 0.0 : Math.hypot(fs.vx(), fs.vz()));
            ctx.onServer(SpearRamSteps::follow);
        }
        boolean spear = SpearFeedback.last().filter(shown -> shown.target() == v.getId()).isPresent();
        if (contactTick < 0 && (v.getHealth() < before || !v.isAlive() || frameContacts > 0 || spear)) {
            contactTick = t;
        }
        boolean finished = !v.isAlive() || (contactTick >= 0 && t - contactTick > SETTLE_TICKS) || along > RUN + 3.0 || t > PASS_TICKS;
        if (!finished) {
            return StepResult.pending();
        }
        if (drone != null) {
            HoverAssist.hold(drone.getX(), HoverAssist.targetY(), drone.getZ());
        }
        boolean touched = contactTick >= 0;
        if (touched) {
            contacts++;
            if (spear) {
                stabbed++;
            }
            if (!v.isAlive()) {
                fallen++;
            }
        }
        DamageSource last = v.getLastDamageSource();
        String note = String.format(Locale.ROOT, "Anflug %d: %s, Tempo %.1f m/s, Leben %.1f -> %.1f, Speer-Stich %s, %s, letzte Schadensart %s", n,
                touched ? "Kontakt" : "kein Kontakt", peak, before, Math.max(0.0F, v.getHealth()), spear ? "ja" : "nein",
                frameContacts > 0 ? frameNote : "kein Körpertreffer", last == null ? "-" : last.typeHolder().getRegisteredName());
        LOG.add(note);
        ctx.log(note);
        return StepResult.pass();
    }

    private static StepResult rearm(SelfTest.StepContext ctx, int t) {
        if (Pilot.armed()) {
            return StepResult.pass();
        }
        int phase = t % 60;
        if (phase == 0) {
            Pilot.click(FLIP);
        } else if (phase == 30) {
            Pilot.click(Pilot.ARM);
        }
        return t > REARM_TICKS ? StepResult.fail("Drohne lässt sich nach dem Kontakt nicht wieder scharf schalten") : StepResult.pending();
    }

    private static StepResult back(SelfTest.StepContext ctx, int t) {
        Entity drone = Pilot.drone().orElse(null);
        FlightState fs = HoverAssist.last();
        double speed = fs == null ? 0.0 : Math.hypot(fs.vx(), fs.vz());
        if (drone != null && drone.position().subtract(start).horizontalDistance() < 0.3 && speed < 0.3 && t > 20) {
            return StepResult.pass();
        }
        return t > BACK_TICKS ? StepResult.pass("zurück nicht ganz erreicht") : StepResult.pending();
    }

    private static void reposition(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        nose = SpearChecks.nose(drone);
        villagerY = Math.max(Arena.ground().getY() + 1.0, HoverAssist.targetY() - 1.0);
        Mob v = villager;
        if (v == null || !v.isAlive()) {
            ServerLevel level = Arena.level(server);
            v = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
            if (v == null) {
                throw new IllegalStateException("Dorfbewohner nicht erzeugt");
            }
            v.setNoAi(true);
            v.setNoGravity(true);
            v.setPersistenceRequired();
            Arena.spawn(level, v);
            villager = v;
        }
        Vec3 spot = drone.position().add(nose.scale(RUN));
        float toward = (float) Math.toDegrees(Math.atan2(-nose.x, nose.z));
        v.snapTo(spot.x, villagerY, spot.z, toward + 180.0F, 0.0F);
        v.setDeltaMovement(Vec3.ZERO);
        SpearFeedback.reset();
    }

    private static StepResult result(SelfTest.StepContext ctx, int ticks) {
        List<String> problems = new ArrayList<>();
        if (contacts < MIN_CONTACTS) {
            problems.add("nur " + contacts + " Kontakte, nötig sind " + MIN_CONTACTS);
        }
        if (stabbed < contacts) {
            problems.add((contacts - stabbed) + " von " + contacts + " Kontakten ohne Speer-Stich");
        }
        if (fallen == 0) {
            problems.add("kein Dorfbewohner gefallen");
        }
        String summary = String.format(Locale.ROOT, "%d Kontakte, davon %d mit Speer-Stich, %d Dorfbewohner gefallen", contacts, stabbed, fallen)
                + " | " + String.join(" | ", LOG);
        return problems.isEmpty() ? StepResult.pass(summary) : StepResult.fail(String.join("; ", problems) + " | " + summary);
    }
}
