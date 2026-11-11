package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.client.OsdNotices;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.dev.mob.client.CovMdCLab.Poll;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.core.ThreatPulse;
import de.lardtom.mobdefense.mob.reaction.FlyerReaction;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.format;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.lab;

public final class UnarmedSteps {
    public static final int CALM_TICKS = 200;
    public static final int ATTACK_TICKS = 600;
    public static final int END_TICKS = 40;
    public static final int QUIET_TICKS = 100;
    public static final int NIGHT = 18000;
    public static final int THREAT_GRACE = 40;
    private static final String PHANTOM = "phantom";
    private static final String ZOMBIE = "zombie";
    private static long savedTime = -1L;

    private UnarmedSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("S10 nicht scharf – Drohne mit Pilot nachts im Freien am Boden: nicht scharf lassen Phantom und Zombie sie in Ruhe, kein THREAT; "
                + "scharf: Angriff und THREAT; entschärft: Angriff und THREAT enden", 4200, Script.create()
                .server("Nacht, 5″ setzen, Pilot daneben", UnarmedSteps::setup)
                .append(Pilot.fly())
                .server("Phantom 10 m daneben und 8 m höher, Zombie ohne Eigenbewegung 2,5 m daneben", UnarmedSteps::scene)
                .then("nicht scharf: keine Zuordnung, kein Treffer, kein THREAT", watch("THREAT bei nicht scharfer Drohne",
                        CovMdCLab.until(CALM_TICKS + 100, UnarmedSteps::untouched)))
                .then("scharf schalten", Pilot::arm)
                .then("scharf: Phantom und Zombie greifen an", CovMdCLab.until(ATTACK_TICKS, UnarmedSteps::engaged))
                .then("scharf: THREAT im OSD", (ctx, ticks) -> threatShown(ticks))
                .then("entschärfen", UnarmedSteps::disarm)
                .server("Entschärfen merken", (server, state) -> {
                    lab().since = CovMdCLab.now(server);
                    lab().values.put("hits", CovMdCLab.hits().size());
                })
                .then("entschärft: Angriff endet", CovMdCLab.until(END_TICKS + 100, UnarmedSteps::released))
                .then("entschärft: THREAT weg", (ctx, ticks) -> threatGone(ticks))
                .then("entschärft: danach Ruhe", watch("THREAT kommt nach dem Entschärfen wieder",
                        CovMdCLab.until(QUIET_TICKS + 100, UnarmedSteps::quiet))),
                CLEANUP);
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("Tasten los, Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
            .server("Flug beenden, Kreativmodus, Tageszeit zurück", (server, state) -> {
                Pilot.release(server);
                Arena.player(server).setGameMode(GameType.CREATIVE);
                if (savedTime >= 0L) {
                    Arena.command(server, "time set " + savedTime);
                    savedTime = -1L;
                }
            })
            .then("Aufräumen", (ctx, ticks) -> CovMdCLab.cleanup(ctx));

    private static void setup(MinecraftServer server, Map<String, Object> state) {
        CovMdCLab.reset(server, 0);
        ServerLevel level = Arena.level(server);
        savedTime = level.getDefaultClockTime();
        Arena.command(server, "time set " + NIGHT);
        BlockPos ground = Arena.ground(CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z);
        lab().ground = ground;
        lab().groundY = ground.getY() + 1.0;
        lab().drone = Pilot.spawn(server, state, MobScene.build(), "Nicht scharf", ground, 180.0F);
    }

    private static void scene(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        lab().drone = drone;
        DroneView view = CovMdCLab.view();
        DroneState now = CovMdCLab.state();
        if (view.pilot().isEmpty() || view.armed() || view.autopilot()) {
            throw new IllegalStateException("erwartet: Pilot verbunden, nicht scharf, kein Autopilot (Pilot " + view.pilot().isPresent() + ", scharf "
                    + view.armed() + ", Autopilot " + view.autopilot() + ")");
        }
        if (!now.landed()) {
            throw new IllegalStateException(format("Drohne liegt nicht am Boden (Höhe %.2f m)", now.height()));
        }
        if (!FlyerReaction.nightInOpen(Arena.level(server), now)) {
            throw new IllegalStateException("nicht nachts im Freien, das Phantom-Fenster wäre zu");
        }
        CovMdCLab.mob(server, PHANTOM, EntityTypes.PHANTOM, 10.0, 0.0, 8.0);
        Mob zombie = CovMdCLab.mob(server, ZOMBIE, EntityTypes.ZOMBIE, 2.5, 0.0, 0.0);
        zombie.setBaby(false);
        CovMdCLab.freeze(zombie);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll untouched(MinecraftServer server) {
        ServerLevel level = Arena.level(server);
        DroneView view = CovMdCLab.view();
        if (view.armed() || view.autopilot()) {
            throw new IllegalStateException("Drohne ist scharf oder im Autopilot");
        }
        Mob phantom = CovMdCLab.mob(PHANTOM);
        Mob zombie = CovMdCLab.mob(ZOMBIE);
        List<String> wrong = new ArrayList<>();
        if (DroneReactions.assigned(phantom).isPresent()) {
            wrong.add("Phantom zugeordnet");
        }
        if (DroneReactions.assigned(zombie).isPresent()) {
            wrong.add("Zombie zugeordnet");
        }
        if (DroneReactions.assignments(level) > 0) {
            wrong.add(DroneReactions.assignments(level) + " Zuordnungen im Level");
        }
        if (!CovMdCLab.hits().isEmpty()) {
            wrong.add("Treffer " + CovMdCLab.causes());
        }
        if (!wrong.isEmpty()) {
            throw new IllegalStateException("nicht scharfe Drohne mit Pilot angegriffen: " + String.join(", ", wrong));
        }
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("%d Ticks nicht scharf am Boden (Propwash meldet fliegt %s), nachts im Freien: Phantom %.1f m, Zombie %.1f m, "
                        + "keine Zuordnung, kein Treffer", waited, view.flying() ? "ja" : "nein", phantom.position().distanceTo(CovMdCLab.drone().position()),
                CovMdCLab.flat(zombie));
        return waited >= CALM_TICKS ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll engaged(MinecraftServer server) {
        DroneView view = CovMdCLab.view();
        Mob phantom = CovMdCLab.mob(PHANTOM);
        Mob zombie = CovMdCLab.mob(ZOMBIE);
        boolean phantomOn = CovMdCLab.everAssigned(phantom);
        boolean zombieOn = CovMdCLab.everAssigned(zombie);
        String note = format("scharf %s, Phantom %s, Zombie %s, Treffer %s", view.armed() ? "ja" : "nein", phantomOn ? "zugeordnet" : "frei",
                zombieOn ? "zugeordnet" : "frei", CovMdCLab.causes());
        return view.armed() && phantomOn && zombieOn ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll released(MinecraftServer server) {
        ServerLevel level = Arena.level(server);
        if (CovMdCLab.view().armed()) {
            throw new IllegalStateException("Drohne ist wieder scharf");
        }
        long since = CovMdCLab.now(server) - lab().since;
        int assignments = DroneReactions.assignments(level);
        boolean running = DroneReactions.assigned(CovMdCLab.mob(PHANTOM)).isPresent() || DroneReactions.assigned(CovMdCLab.mob(ZOMBIE)).isPresent()
                || assignments > 0;
        if (!running) {
            lab().mark = CovMdCLab.now(server);
            return Poll.done(format("Angriff endet spätestens %d Ticks nach dem Entschärfen, keine Zuordnung mehr", since));
        }
        if (since > END_TICKS) {
            throw new IllegalStateException(format("Angriff läuft %d Ticks nach dem Entschärfen weiter (%d Zuordnungen)", since, assignments));
        }
        return Poll.waiting(format("%d Ticks nach dem Entschärfen, %d Zuordnungen", since, assignments));
    }

    private static Poll quiet(MinecraftServer server) {
        ServerLevel level = Arena.level(server);
        if (CovMdCLab.view().armed()) {
            throw new IllegalStateException("Drohne ist wieder scharf");
        }
        int assignments = DroneReactions.assignments(level);
        if (DroneReactions.assigned(CovMdCLab.mob(PHANTOM)).isPresent() || DroneReactions.assigned(CovMdCLab.mob(ZOMBIE)).isPresent() || assignments > 0) {
            throw new IllegalStateException(format("nach dem Entschärfen wieder zugeordnet (%d Zuordnungen)", assignments));
        }
        int before = (int) lab().values.get("hits");
        long late = CovMdCLab.hits().stream().skip(before).filter(hit -> hit.tick() > lab().mark).count();
        if (late > 0) {
            throw new IllegalStateException("nach dem Ende des Angriffs noch " + late + " Treffer: " + CovMdCLab.causes());
        }
        long quiet = CovMdCLab.now(server) - lab().mark;
        String note = format("%d Ticks ohne Zuordnung und ohne Treffer, Phantom %.1f m entfernt", quiet,
                CovMdCLab.mob(PHANTOM).position().distanceTo(CovMdCLab.drone().position()));
        return quiet >= QUIET_TICKS ? Poll.done(note) : Poll.waiting(note);
    }

    private static Script.Stage watch(String failure, Script.Stage server) {
        String key = "unarmed.threat." + failure;
        return (ctx, ticks) -> {
            if (threat()) {
                ctx.state().putIfAbsent(key, ticks);
            }
            StepResult result = server.tick(ctx, ticks);
            if (!(ctx.state().get(key) instanceof Integer seen)) {
                return result;
            }
            if (result.failed()) {
                return StepResult.fail(failure + "; " + result.message());
            }
            return ticks - seen > THREAT_GRACE ? StepResult.fail(failure) : StepResult.pending();
        };
    }

    private static StepResult threatShown(int ticks) {
        if (threat()) {
            return StepResult.pass("THREAT beim Piloten");
        }
        return ticks > ATTACK_TICKS ? StepResult.fail("THREAT kommt nicht (Pakete " + OsdNotices.received() + ")") : StepResult.pending();
    }

    private static StepResult threatGone(int ticks) {
        if (!threat()) {
            return StepResult.pass("THREAT nach " + ticks + " Ticks weg");
        }
        return ticks > ThreatPulse.SHOW_TICKS + 20 ? StepResult.fail("THREAT bleibt nach dem Entschärfen") : StepResult.pending();
    }

    private static StepResult disarm(SelfTest.StepContext ctx, int ticks) {
        if (!Pilot.armed()) {
            return StepResult.pass();
        }
        if (ticks % 40 == 0) {
            Pilot.click(Pilot.ARM);
        }
        return ticks > 240 ? StepResult.fail("Drohne lässt sich nicht entschärfen") : StepResult.pending();
    }

    private static boolean threat() {
        Entity drone = Pilot.drone().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        return drone != null && mc.level != null && OsdNotices.active(Notice.THREAT, drone.getId(), mc.level.getGameTime());
    }
}
