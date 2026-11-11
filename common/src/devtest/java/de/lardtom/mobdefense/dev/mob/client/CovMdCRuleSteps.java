package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.Guests;
import de.lardtom.mobdefense.dev.dispenser.client.HoverAssist;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.mob.CovMdCPack;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.dev.mob.client.CovMdCLab.Poll;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.core.ReactionLimits;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.FlightState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.format;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.lab;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.until;

public final class CovMdCRuleSteps {
    public static final int PARKED_TICKS = 1200;
    public static final int PARKED_ZOMBIES = 5;
    public static final int LANDED_TICKS = 200;
    public static final int SIGHT_TICKS = 100;
    public static final int CROWD = 30;
    public static final double PLAYER_DISTANCE = 20.0;
    public static final double DRONE_FAR = 15.0;
    public static final double DRONE_NEAR = 7.0;
    public static final double RESCUE_DISTANCE = 30.0;
    public static final double RESCUE_HOLD = 6.0;
    public static final String RESCUE_KEY = "key.propwash.rescue";
    public static final double AIM_DEGREES = 20.0;
    public static final int ARRIVE_TICKS = 40;
    private static final String HOME = "covmdc.home";
    private static final String RESCUE_START = "covmdc.rescue.start";

    private CovMdCRuleSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("Mob-Reaktionen: Nur fliegende Drohnen – abgestellte Drohne und 5 Zombies, 1200 Ticks im Zeitraffer: kein Schaden, keine Zuordnung, "
                + "kein Ziel-Goal", 2400, Script.create()
                .server("abgestellte Drohne, 5 Zombies daneben", CovMdCRuleSteps::parked)
                .server("Zeitraffer 1200 Ticks", (server, state) -> Arena.command(server, "tick sprint " + PARKED_TICKS))
                .then("Zombies lassen die geparkte Drohne in Ruhe", until(2200, CovMdCRuleSteps::parkedIgnored)), CovMdCLab::cleanup);
        steps.add("Mob-Reaktionen: Gelandet während des Flugs – gelandete, scharfe Drohne mit Pilotensitzung, Zombie schlägt binnen 200 Ticks zu, "
                + "Silberfischchen und Endermite greifen die gelandete Drohne an", 3000, Script.create()
                .server("5″ setzen, Pilot daneben", (server, state) -> {
                    CovMdCLab.reset(server, 0);
                    BlockPos ground = Arena.ground(CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z);
                    lab().ground = ground;
                    lab().groundY = ground.getY() + 1.0;
                    lab().drone = Pilot.spawn(server, state, MobScene.build(), "CovMdC gelandet", ground, 180.0F);
                })
                .append(Pilot.fly())
                .then("am Boden scharf schalten", Pilot::arm)
                .server("Zombie, Silberfischchen und Endermite neben der gelandeten Drohne", CovMdCRuleSteps::landedScene)
                .then("Zombie schlägt binnen 200 Ticks zu", until(LANDED_TICKS + 100, CovMdCRuleSteps::landedZombie))
                .then("Silberfischchen und Endermite beißen die gelandete Drohne", until(600, CovMdCRuleSteps::landedGround)), PILOT_CLEANUP);
        steps.add("Mob-Reaktionen: Sichtlinie – Steinwand zwischen Skelett und schwebender Drohne: 100 Ticks kein Pfeil, Wand weg: Schuss", 3000,
                Script.create()
                        .server("Drohne schwebt auf 4 m", (server, state) -> {
                            CovMdCLab.reset(server, 0);
                            CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 4.0, "CovMdC Sichtlinie");
                        })
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                        .server("Steinwand 5 m vor der Drohne, Skelett 10 m dahinter", CovMdCRuleSteps::wall)
                        .then("hinter der Wand: kein Pfeil, keine Zuordnung", until(SIGHT_TICKS + 100, CovMdCRuleSteps::blind))
                        .server("Blick auf die Szene", (server, state) -> CovMdCLab.view(server, 9.0, 2.0, 5.0))
                        .append(Shots.photos("covmdc_sightline"))
                        .server("Wand weg", CovMdCRuleSteps::unwall)
                        .then("Skelett schießt auf die Drohne", until(500, CovMdCRuleSteps::sighted)), CovMdCLab::cleanup);
        steps.add("Mob-Reaktionen: Höchstens etwa ein Dutzend – 30 Zombies um die schwebende Drohne: höchstens 12 zugeordnet, genau die 12 nächsten, "
                + "höchstens 64 Auswertungen je Tick", 3000, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> {
                    CovMdCLab.reset(server, 0);
                    CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 3.0, "CovMdC Dutzend");
                })
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(2.5)))
                .server("30 Zombies ohne Eigenbewegung in 3 bis 17,5 m", CovMdCRuleSteps::crowd)
                .then("die 12 nächsten reagieren, der Rest bleibt ruhig", until(600, CovMdCRuleSteps::dozen))
                .server("Blick auf die Szene", (server, state) -> CovMdCLab.view(server, 22.0, 6.0, 0.0))
                .append(Shots.photos("covmdc_dozen")), CovMdCLab::cleanup);
        steps.add("Mob-Reaktionen: Heimflug – GPS Rescue über Skeletten: sie schießen weiter auf die heimfliegende Drohne", 4800, Script.create()
                .server("5″ setzen, Pilot daneben", CovMdCRuleSteps::rescueSetup)
                .append(Pilot.fly())
                .append(Pilot.takeOff(3.0))
                .run("30 m wegfliegen und auf 6 m halten", () -> {
                    Vec3 home = home();
                    HoverAssist.hold(home.x, lab().groundY + RESCUE_HOLD, home.z - RESCUE_DISTANCE);
                })
                .then("Drohne 30 m vom Startpunkt", (ctx, ticks) -> away(ticks))
                .server("drei Skelette entlang des Heimwegs", CovMdCRuleSteps::rescueSkeletons)
                .then("Skelette schießen auf die schwebende Drohne", until(600, CovMdCRuleSteps::rescueShooting))
                .run("GPS Rescue auslösen", () -> {
                    HoverAssist.off();
                    Pilot.click(RESCUE_KEY);
                })
                .server("Beginn des Heimflugs merken", (server, state) -> {
                    lab().values.put(RESCUE_START, CovMdCLab.now(server));
                    lab().values.put("covmdc.rescue.from", CovMdCLab.drone().position());
                    lab().count = 0;
                    lab().mark = -1;
                })
                .then("Skelette schießen auf die heimfliegende Drohne", until(900, CovMdCRuleSteps::rescueReturning)), PILOT_CLEANUP);
        steps.add("Mob-Reaktionen: Spieler bleibt erstes Ziel – Testspieler im Überleben 20 m vom Zombie: Drohne in 15 m lässt ihn beim Spieler, "
                + "in 7 m wechselt er zur Drohne", 3600, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> {
                    CovMdCLab.reset(server, 0);
                    CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 3.0, "CovMdC Spieler zuerst");
                })
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(2.5)))
                .server("Zombie 15 m von der Drohne, Testspieler 20 m dahinter als Ziel", (server, state) -> pair(server, DRONE_FAR, true))
                .then("Zombie bleibt beim Spieler", until(300, CovMdCRuleSteps::staysAtPlayer))
                .server("Zombie 7 m von der Drohne, Testspieler 20 m dahinter", (server, state) -> pair(server, DRONE_NEAR, false))
                .then("Zombie wechselt zur Drohne", until(300, CovMdCRuleSteps::switched))
                .server("Zombie darf laufen", (server, state) -> CovMdCLab.unfreeze(CovMdCLab.mob("zombie")))
                .then("Zombie läuft zur Drohne", until(400, CovMdCRuleSteps::walksToDrone)), CovMdCLab::cleanup);
        steps.add("Mob-Reaktionen: Datenpaket – minecraft:zombie im Tag mobdefense:ignores_drones, /reload: Zombies reagieren nicht mehr, das Skelett schon",
                4800, Script.create()
                        .server("Test-Datenpaket in die Welt, /reload", (server, state) -> {
                            CovMdCLab.reset(server, 0);
                            CovMdCPack.install(server);
                            Arena.command(server, "reload");
                        })
                        .then("Tag nach /reload geladen", until(600, server -> CovMdCPack.zombieIgnored() && CovMdCPack.skeletonArcher()
                                ? Poll.done("Zombie " + CovMdCPack.kind(EntityTypes.ZOMBIE) + ", Skelett " + CovMdCPack.kind(EntityTypes.SKELETON)
                                + ", Paket " + (CovMdCPack.enabled(server) ? "aktiv" : "nicht aktiv"))
                                : Poll.waiting("Zombie " + CovMdCPack.kind(EntityTypes.ZOMBIE))))
                        .server("Drohne schwebt auf 4 m", (server, state) -> CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 4.0,
                                "CovMdC Datenpaket"))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                        .server("zwei Zombies unter der Drohne, Skelett 10 m daneben", CovMdCRuleSteps::packScene)
                        .then("Zombies ignorieren die Drohne, das Skelett schießt", until(800, CovMdCRuleSteps::packReaction))
                        .server("Test-Datenpaket aus", (server, state) -> Arena.command(server, "datapack disable \"" + CovMdCPack.PACK_ID + "\""))
                        .then("Zombie reagiert wieder als chase", until(600, server -> "chase".equals(CovMdCPack.kind(EntityTypes.ZOMBIE))
                                ? Poll.done("Zombie wieder chase") : Poll.waiting("Zombie " + CovMdCPack.kind(EntityTypes.ZOMBIE))))
                        .server("Test-Datenpaket löschen", (server, state) -> CovMdCPack.remove(server)), PACK_CLEANUP);
    }

    private static final SelfTest.ClientStep PILOT_CLEANUP = Script.create()
            .run("Tasten los, Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
            .server("Flug beenden, Kreativmodus", (server, state) -> {
                Pilot.release(server);
                Arena.player(server).setGameMode(GameType.CREATIVE);
            })
            .then("Aufräumen", (ctx, ticks) -> CovMdCLab.cleanup(ctx));

    private static final SelfTest.ClientStep PACK_CLEANUP = Script.create()
            .server("Test-Datenpaket sicher aus", (server, state) -> {
                if (CovMdCPack.enabled(server)) {
                    Arena.command(server, "datapack disable \"" + CovMdCPack.PACK_ID + "\"");
                }
            })
            .await("Zombie wieder chase", 600, () -> "chase".equals(CovMdCPack.kind(EntityTypes.ZOMBIE)))
            .server("Test-Datenpaket löschen", (server, state) -> CovMdCPack.remove(server))
            .then("Aufräumen", (ctx, ticks) -> CovMdCLab.cleanup(ctx));

    private static void parked(MinecraftServer server, Map<String, Object> state) {
        CovMdCLab.reset(server, 100);
        Entity drone = CovMdCLab.place(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, "CovMdC geparkt");
        DroneView view = CovMdCLab.view();
        if (view.flying() || view.pilot().isPresent()) {
            throw new IllegalStateException("abgestellte Drohne fliegt oder hat einen Piloten");
        }
        for (int i = 0; i < PARKED_ZOMBIES; i++) {
            double angle = Math.toRadians(i * 72.0);
            double radius = 2.0 + i * 0.5;
            CovMdCLab.mob(server, "zombie" + i, EntityTypes.ZOMBIE, Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0);
        }
        lab().since = CovMdCLab.now(server);
        lab().values.put("closest", Double.MAX_VALUE);
        lab().values.put("drone", drone.position());
    }

    private static Poll parkedIgnored(MinecraftServer server) {
        Entity drone = CovMdCLab.drone();
        if (PropwashApi.drones().view(drone).map(DroneView::flying).orElse(false)) {
            throw new IllegalStateException("abgestellte Drohne gilt als fliegend");
        }
        double closest = (double) lab().values.get("closest");
        int goals = 0;
        for (int i = 0; i < PARKED_ZOMBIES; i++) {
            Mob zombie = CovMdCLab.mob("zombie" + i);
            if (DroneReactions.assigned(zombie).isPresent()) {
                throw new IllegalStateException("Zombie " + i + " ist der geparkten Drohne zugeordnet");
            }
            if (DroneReactions.goalInstalled(zombie)) {
                goals++;
            }
            closest = Math.min(closest, CovMdCLab.flat(zombie));
        }
        lab().values.put("closest", closest);
        if (goals > 0) {
            throw new IllegalStateException(goals + " Zombies haben ein Ziel-Goal zur Drohne bekommen");
        }
        if (!CovMdCLab.hits().isEmpty()) {
            throw new IllegalStateException("geparkte Drohne getroffen: " + CovMdCLab.causes());
        }
        if (DroneReactions.assignments(Arena.level(server)) > 0) {
            throw new IllegalStateException("Zuordnungen im Level ohne fliegende Drohne: " + DroneReactions.assignments(Arena.level(server)));
        }
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("%d Ticks, 5 Zombies ohne Zuordnung und ohne Ziel-Goal, kein Treffer, nächster Zombie %.1f m", waited, closest);
        return waited >= PARKED_TICKS ? Poll.done(note) : Poll.waiting(note);
    }

    private static void landedScene(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        lab().drone = drone;
        DroneView view = CovMdCLab.view();
        DroneState drone0 = CovMdCLab.state();
        if (view.pilot().isEmpty() || !view.flying() || !view.armed()) {
            throw new IllegalStateException("keine scharfe Pilotensitzung (Pilot " + view.pilot().isPresent() + ", fliegt " + view.flying() + ", scharf "
                    + view.armed() + ")");
        }
        if (!drone0.landed()) {
            throw new IllegalStateException(format("Drohne liegt nicht am Boden (Höhe %.2f m)", drone0.height()));
        }
        Mob zombie = CovMdCLab.mob(server, "zombie", EntityTypes.ZOMBIE, 2.5, 0.0, 0.0);
        zombie.setBaby(false);
        CovMdCLab.mob(server, "silverfish", EntityTypes.SILVERFISH, -2.0, 0.0, 0.0);
        CovMdCLab.mob(server, "endermite", EntityTypes.ENDERMITE, 0.0, -2.0, 0.0);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll landedZombie(MinecraftServer server) {
        long waited = CovMdCLab.now(server) - lab().since;
        DroneState state = CovMdCLab.state();
        List<CovMdCLab.Hit> hits = CovMdCLab.hitsBy("zombie");
        Mob zombie = CovMdCLab.mob("zombie");
        String note = format("nach %d Ticks %d Treffer (Zombie %s, Abstand %.1f m, Drohne %s, Höhe %.2f m)", waited, hits.size(),
                CovMdCLab.assignedTo(zombie) ? "zugeordnet" : "frei", CovMdCLab.flat(zombie), state.landed() ? "gelandet" : "in der Luft", state.height());
        if (!hits.isEmpty()) {
            long at = hits.getFirst().tick() - lab().since;
            if (at > LANDED_TICKS) {
                throw new IllegalStateException("erster Zombie-Treffer erst nach " + at + " Ticks");
            }
            return Poll.done(note + ", erster Treffer nach " + at + " Ticks, Verursacher " + hits.getFirst().cause());
        }
        if (waited > LANDED_TICKS) {
            throw new IllegalStateException("kein Zombie-Treffer binnen 200 Ticks: " + note);
        }
        return Poll.waiting(note);
    }

    private static Poll landedGround(MinecraftServer server) {
        int silverfish = CovMdCLab.hitsBy("silverfish").size();
        int endermite = CovMdCLab.hitsBy("endermite").size();
        DroneState state = CovMdCLab.state();
        String note = format("Silberfischchen %d Treffer (%s), Endermite %d Treffer (%s), Drohne %s", silverfish,
                CovMdCLab.everAssigned(CovMdCLab.mob("silverfish")) ? "zugeordnet" : "frei", endermite,
                CovMdCLab.everAssigned(CovMdCLab.mob("endermite")) ? "zugeordnet" : "frei", state.landed() ? "gelandet" : "in der Luft");
        return silverfish > 0 && endermite > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static void wall(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = CovMdCLab.drone();
        BlockPos base = BlockPos.containing(drone.getX() + 5.0, lab().groundY, drone.getZ());
        Arena.fill(level, base.offset(0, 0, -4), base.offset(0, 8, 4), Blocks.STONE.defaultBlockState());
        lab().values.put("wall", base);
        CovMdCLab.freeze(CovMdCLab.mob(server, "skeleton", EntityTypes.SKELETON, 10.0, 0.0, 0.0));
        lab().since = CovMdCLab.now(server);
    }

    private static Poll blind(MinecraftServer server) {
        Mob skeleton = CovMdCLab.mob("skeleton");
        ServerLevel level = Arena.level(server);
        int arrows = CovMdCLab.owned(level, AbstractArrow.class, skeleton).size();
        boolean sees = skeleton.getSensing().hasLineOfSight(CovMdCLab.drone());
        if (arrows > 0 || CovMdCLab.assignedTo(skeleton)) {
            throw new IllegalStateException(format("Skelett reagiert hinter der Wand (%d Pfeile, Sichtlinie %s)", arrows, sees));
        }
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("%d Ticks hinter der Wand ohne Pfeil und ohne Zuordnung, Sichtlinie %s", waited, sees ? "ja" : "nein");
        return waited >= SIGHT_TICKS ? Poll.done(note) : Poll.waiting(note);
    }

    private static void unwall(MinecraftServer server, Map<String, Object> state) {
        BlockPos base = (BlockPos) lab().values.get("wall");
        Arena.fill(Arena.level(server), base.offset(0, 0, -4), base.offset(0, 8, 4), Blocks.AIR.defaultBlockState());
        lab().since = CovMdCLab.now(server);
    }

    private static Poll sighted(MinecraftServer server) {
        Mob skeleton = CovMdCLab.mob("skeleton");
        ServerLevel level = Arena.level(server);
        List<AbstractArrow> arrows = CovMdCLab.owned(level, AbstractArrow.class, skeleton);
        boolean assigned = CovMdCLab.everAssigned(skeleton);
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("nach %d Ticks: Skelett %s, %d Pfeile, Treffer %s", waited, assigned ? "zugeordnet" : "frei", arrows.size(), CovMdCLab.causes());
        return assigned && !arrows.isEmpty() ? Poll.done(note) : Poll.waiting(note);
    }

    private static void crowd(MinecraftServer server, Map<String, Object> state) {
        DroneReactions.resetStats();
        for (int i = 0; i < CROWD; i++) {
            double radius = 3.0 + i * 0.5;
            double angle = Math.toRadians(i * 137.5);
            Mob zombie = CovMdCLab.mob(server, "zombie" + i, EntityTypes.ZOMBIE, Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0);
            zombie.setBaby(false);
            CovMdCLab.freeze(zombie);
        }
        lab().since = CovMdCLab.now(server);
        lab().count = 0;
    }

    private static Poll dozen(MinecraftServer server) {
        DroneState drone = CovMdCLab.state();
        List<Mob> zombies = new ArrayList<>();
        for (int i = 0; i < CROWD; i++) {
            zombies.add(CovMdCLab.mob("zombie" + i));
        }
        Set<Mob> assigned = zombies.stream().filter(CovMdCLab::assignedTo).collect(Collectors.toSet());
        lab().count = Math.max(lab().count, assigned.size());
        if (assigned.size() > ReactionLimits.MOBS_PER_DRONE) {
            throw new IllegalStateException(assigned.size() + " Zombies der Drohne zugeordnet, höchstens 12 erlaubt");
        }
        int maxEvaluations = DroneReactions.stats().maxEvaluations();
        if (maxEvaluations > ReactionLimits.EVALUATIONS_PER_TICK) {
            throw new IllegalStateException(maxEvaluations + " Auswertungen in einem Tick, höchstens 64 erlaubt");
        }
        List<Mob> nearest = zombies.stream().sorted(Comparator.comparingDouble(drone::distanceFrom)).limit(ReactionLimits.MOBS_PER_DRONE).toList();
        Set<Mob> far = new HashSet<>(zombies);
        nearest.forEach(far::remove);
        long goals = far.stream().filter(DroneReactions::goalInstalled).count();
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("nach %d Ticks %d zugeordnet (höchstens %d), davon unter den 12 nächsten %d, Ziel-Goal bei den 18 entfernten %d, "
                        + "größte Zahl Auswertungen je Tick %d", waited, assigned.size(), lab().count, assigned.stream().filter(nearest::contains).count(), goals,
                maxEvaluations);
        if (goals > 0) {
            throw new IllegalStateException("entfernte Zombies haben ein Ziel-Goal bekommen: " + note);
        }
        if (waited < 100 || assigned.size() != ReactionLimits.MOBS_PER_DRONE || !assigned.containsAll(nearest)) {
            lab().checks = 0;
            return Poll.waiting(note);
        }
        return ++lab().checks >= 20 ? Poll.done(note + ", 20 Prüfungen in Folge genau die 12 nächsten") : Poll.waiting(note);
    }

    private static void rescueSetup(MinecraftServer server, Map<String, Object> state) {
        CovMdCLab.reset(server, 0);
        BlockPos ground = Arena.ground(-CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z + 30);
        lab().ground = ground;
        lab().groundY = ground.getY() + 1.0;
        lab().drone = Pilot.spawn(server, state, MobScene.build(), "CovMdC Heimflug", ground, 180.0F);
        lab().values.put(HOME, new Vec3(lab().drone.getX(), lab().drone.getY(), lab().drone.getZ()));
        lab().checks = 0;
    }

    private static Vec3 home() {
        return (Vec3) lab().values.get(HOME);
    }

    private static SelfTest.StepResult away(int ticks) {
        FlightState state = HoverAssist.last();
        Vec3 home = home();
        if (state == null) {
            return ticks > 600 ? SelfTest.StepResult.fail("keine Flugdaten") : SelfTest.StepResult.pending();
        }
        double distance = Math.hypot(state.x() - home.x, state.z() - home.z);
        double speed = Math.hypot(state.vx(), state.vz());
        if (distance > RESCUE_DISTANCE - 3.0) {
            lab().checks++;
        }
        if (lab().checks >= ARRIVE_TICKS) {
            return SelfTest.StepResult.pass(format("%.1f m vom Startpunkt, %.1f m hoch, %.1f m/s", distance, state.y() - lab().groundY, speed));
        }
        return ticks > 900 ? SelfTest.StepResult.fail(format("nicht angekommen: %.1f m, %.1f m/s", distance, speed)) : SelfTest.StepResult.pending();
    }

    private static void rescueSkeletons(MinecraftServer server, Map<String, Object> state) {
        Vec3 home = home();
        lab().drone = Pilot.serverDrone(server, state);
        double[][] spots = {{8.0, -25.0}, {-8.0, -15.0}, {8.0, -5.0}};
        for (int i = 0; i < spots.length; i++) {
            Vec3 at = new Vec3(home.x + spots[i][0], lab().groundY, home.z + spots[i][1]);
            Mob skeleton = CovMdCLab.mobAt(server, "skeleton" + i, EntityTypes.SKELETON, at, CovMdCLab.drone().position());
            CovMdCLab.freeze(skeleton);
        }
        CovMdCLab.watch(true);
        lab().since = CovMdCLab.now(server);
    }

    private static List<Mob> skeletons() {
        List<Mob> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            out.add(CovMdCLab.mob("skeleton" + i));
        }
        return out;
    }

    private static List<CovMdCLab.Shot> skeletonArrows(long from) {
        return CovMdCLab.shots(shot -> shot.owner().equals("skeleton") && shot.type().contains("arrow") && shot.tick() >= from);
    }

    private static Poll rescueShooting(MinecraftServer server) {
        long assigned = skeletons().stream().filter(CovMdCLab::assignedTo).count();
        int arrows = skeletonArrows(lab().since).size();
        String note = format("%d Skelette zugeordnet, %d Pfeile auf die schwebende Drohne, Treffer %s", assigned, arrows, CovMdCLab.causes());
        return assigned > 0 && arrows > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll rescueReturning(MinecraftServer server) {
        Entity drone = CovMdCLab.drone();
        Vec3 home = home();
        Vec3 from = (Vec3) lab().values.get("covmdc.rescue.from");
        long start = (long) lab().values.get(RESCUE_START);
        double startDistance = Math.hypot(from.x - home.x, from.z - home.z);
        double distance = Math.hypot(drone.getX() - home.x, drone.getZ() - home.z);
        double climbed = drone.getY() - from.y;
        boolean returning = startDistance - distance >= 4.0 || climbed >= 3.0;
        DroneView view = CovMdCLab.view();
        long assigned = skeletons().stream().filter(CovMdCLab::assignedTo).count();
        if (returning && assigned > 0) {
            lab().count++;
        }
        if (returning && lab().mark < 0) {
            lab().mark = CovMdCLab.now(server);
        }
        List<CovMdCLab.Shot> arrows = skeletonArrows(lab().mark < 0 ? Long.MAX_VALUE : lab().mark);
        long aimed = arrows.stream().filter(shot -> shot.flatAim() < AIM_DEGREES).count();
        String note = format("Heimflug: %.1f → %.1f m vom Startpunkt, %.1f m gestiegen, fliegt %s, %d Skelette zugeordnet, %d Pfeile im Heimflug "
                        + "(%d auf die Drohne gerichtet), Treffer %s", startDistance, distance, climbed, view.flying() ? "ja" : "nein", assigned, arrows.size(), aimed,
                CovMdCLab.causes());
        if (!returning && CovMdCLab.now(server) - start > 200) {
            throw new IllegalStateException("GPS Rescue fliegt nicht heim: " + note);
        }
        return returning && lab().count > 0 && aimed > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static void pair(MinecraftServer server, double droneDistance, boolean create) {
        Entity drone = CovMdCLab.drone();
        Mob zombie;
        ServerPlayer guest;
        if (create) {
            zombie = CovMdCLab.mob(server, "zombie", EntityTypes.ZOMBIE, droneDistance, 0.0, 0.0);
            zombie.setBaby(false);
            CovMdCLab.freeze(zombie);
            Vec3 at = new Vec3(zombie.getX() + PLAYER_DISTANCE, lab().groundY, zombie.getZ());
            guest = Guests.place(server, "CovMdC_Pilotin", Arena.level(server), at, 90.0F);
            Guests.ready(guest, 20.0F);
            lab().values.put("guest", guest);
        } else {
            zombie = CovMdCLab.mob("zombie");
            guest = (ServerPlayer) lab().values.get("guest");
            CovMdCLab.teleport(zombie, droneDistance, 0.0, 0.0);
            guest.snapTo(zombie.getX() + PLAYER_DISTANCE, lab().groundY, zombie.getZ(), 90.0F, 0.0F);
            guest.setDeltaMovement(Vec3.ZERO);
        }
        zombie.setTarget(guest);
        lab().since = CovMdCLab.now(server);
        lab().checks = 0;
        lab().values.put("start", CovMdCLab.flat(zombie));
        lab().values.put("drone.pos", drone.position());
    }

    private static Poll staysAtPlayer(MinecraftServer server) {
        Mob zombie = CovMdCLab.mob("zombie");
        ServerPlayer guest = (ServerPlayer) lab().values.get("guest");
        DroneState drone = CovMdCLab.state();
        double toDrone = drone.distanceFrom(zombie);
        double toPlayer = zombie.distanceTo(guest);
        boolean targetsPlayer = zombie.getTarget() == guest;
        if (CovMdCLab.assignedTo(zombie)) {
            throw new IllegalStateException(format("Zombie wechselt zur Drohne, obwohl sie %.1f m und der Spieler %.1f m entfernt ist", toDrone, toPlayer));
        }
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("Zombie-Ziel %s, Drohne %.1f m, Spieler %.1f m, %d Ticks ohne Wechsel", targetsPlayer ? "Testspieler" : String.valueOf(zombie.getTarget()),
                toDrone, toPlayer, waited);
        if (!targetsPlayer) {
            throw new IllegalStateException("Zombie hat den Testspieler nicht als Ziel: " + note);
        }
        return waited >= 100 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll switched(MinecraftServer server) {
        Mob zombie = CovMdCLab.mob("zombie");
        ServerPlayer guest = (ServerPlayer) lab().values.get("guest");
        DroneState drone = CovMdCLab.state();
        double toDrone = drone.distanceFrom(zombie);
        double toPlayer = zombie.distanceTo(guest);
        boolean assigned = CovMdCLab.assignedTo(zombie);
        String note = format("Drohne %.1f m, Spieler %.1f m (halber Abstand %.1f m), Zombie %s, Vanilla-Ziel %s", toDrone, toPlayer, toPlayer / 2.0,
                assigned ? "der Drohne zugeordnet" : "frei", zombie.getTarget() == guest ? "Testspieler" : String.valueOf(zombie.getTarget()));
        return assigned ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll walksToDrone(MinecraftServer server) {
        Mob zombie = CovMdCLab.mob("zombie");
        double start = (double) lab().values.get("start");
        double distance = CovMdCLab.flat(zombie);
        boolean assigned = CovMdCLab.everAssigned(zombie);
        String note = format("Zombie %.1f → %.1f m zur Drohne, %s, Treffer %s", start, distance, assigned ? "zugeordnet" : "frei", CovMdCLab.causes());
        return assigned && start - distance >= 2.0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static void packScene(MinecraftServer server, Map<String, Object> state) {
        Mob first = CovMdCLab.mob(server, "zombie0", EntityTypes.ZOMBIE, 3.0, 0.0, 0.0);
        Mob second = CovMdCLab.mob(server, "zombie1", EntityTypes.ZOMBIE, -3.0, 1.0, 0.0);
        first.setBaby(false);
        second.setBaby(false);
        CovMdCLab.mob(server, "skeleton", EntityTypes.SKELETON, 0.0, -10.0, 0.0);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll packReaction(MinecraftServer server) {
        ServerLevel level = Arena.level(server);
        for (int i = 0; i < 2; i++) {
            Mob zombie = CovMdCLab.mob("zombie" + i);
            if (DroneReactions.assigned(zombie).isPresent() || DroneReactions.goalInstalled(zombie)) {
                throw new IllegalStateException("Zombie " + i + " reagiert trotz ignores_drones");
            }
        }
        if (!CovMdCLab.hitsBy("zombie").isEmpty()) {
            throw new IllegalStateException("Zombie trifft trotz ignores_drones: " + CovMdCLab.causes());
        }
        Mob skeleton = CovMdCLab.mob("skeleton");
        boolean assigned = CovMdCLab.everAssigned(skeleton);
        int arrows = CovMdCLab.owned(level, AbstractArrow.class, skeleton).size();
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("nach %d Ticks: Zombies ohne Zuordnung und ohne Ziel-Goal; Skelett %s, %d Pfeile, Treffer %s", waited,
                assigned ? "zugeordnet" : "frei", arrows, CovMdCLab.causes());
        return waited >= 100 && assigned && arrows > 0 ? Poll.done(note) : Poll.waiting(note);
    }
}
