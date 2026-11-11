package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.dev.mob.client.CovMdCLab.Poll;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.format;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.lab;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.until;

public final class CovMdCFairnessSteps {
    public static final double SKELETON_DISTANCE = 15.0;
    public static final int WINDOW_TICKS = 1200;
    public static final int TRIALS = 5;
    public static final int SURVIVAL_CAP_TICKS = 2400;
    public static final double SURVIVAL_MIN_SECONDS = 2.0;
    public static final int SKELETON_HITS_MIN = 10;
    public static final int SKELETON_HITS_MAX = 40;
    public static final double BLAZE_RATE_MIN = 0.5;
    public static final double BLAZE_RATE_MAX = 1.0;
    public static final int BLAZE_SHOTS_MIN = 12;
    public static final double INSIDE = 20.0;
    public static final double OUTSIDE_ZOMBIE = 28.0;
    public static final double OUTSIDE_SKELETON = 27.0;
    public static final int RANGE_TICKS = 200;
    private static final String SURVIVALS = "covmdc.survivals";

    private CovMdCFairnessSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Fairness: Reaktionsradius – Zombie in 20 m reagiert, Zombie in 28 m und Skelett in 27 m bleiben ruhig (mob_reaction_range 24)", 2400,
                Script.create()
                        .server("Drohne schwebt auf 4 m", (server, state) -> {
                            CovMdCLab.reset(server, 0);
                            CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 4.0, "CovMdC Radius");
                        })
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                        .server("Zombie in 20 m, Zombie in 28 m, Skelett in 27 m, alle ohne Eigenbewegung", CovMdCFairnessSteps::radius)
                        .then("nur der Zombie in 20 m reagiert", until(RANGE_TICKS + 200, CovMdCFairnessSteps::radiusChecked)), CovMdCLab::cleanup);
        Script skeletons = Script.create()
                .server("Drohne schwebt auf 4 m", (server, state) -> {
                    CovMdCLab.reset(server, 0);
                    CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 4.0, "CovMdC Fairness");
                })
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                .server("drei Skelette 15 m vor der Drohne", CovMdCFairnessSteps::skeletons)
                .then("Skelette schießen", until(600, CovMdCFairnessSteps::firstArrow))
                .server("eine Minute im Zeitraffer", (server, state) -> {
                    lab().mark = CovMdCLab.now(server);
                    Arena.command(server, "tick sprint " + WINDOW_TICKS);
                })
                .then("Treffer pro Minute im Bereich", until(2400, CovMdCFairnessSteps::perMinute))
                .server("Blick auf die Szene", (server, state) -> CovMdCLab.view(server, 10.0, 2.0, 7.0))
                .append(Shots.photos("covmdc_fairness_skeletons"));
        for (int i = 1; i <= TRIALS; i++) {
            skeletons.append(trial(i));
        }
        skeletons.check("Überlebenszeit", CovMdCFairnessSteps::survival);
        steps.add("Fairness: Skelette – drei Skelette 15 m vor der schwebenden 5″ am Tag: Treffer pro Minute im Bereich, mit vollem Schaden überlebt sie "
                + "mindestens die Mindestzeit", 9000, skeletons, CovMdCLab::cleanup);
        steps.add("Fairness: Lohe – Trefferquote der kleinen Feuerkugeln auf die schwebende Drohne in 10 m im Bereich", 4800, Script.create()
                .server("Drohne schwebt auf 4 m, Feuer zählt nicht", (server, state) -> {
                    CovMdCLab.reset(server, 0);
                    Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false");
                    MobScene.protectFromFire(true);
                    CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 4.0, "CovMdC Lohe");
                })
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                .server("Lohe 10 m vor der Drohne", (server, state) -> {
                    CovMdCLab.freeze(CovMdCLab.mob(server, "blaze", EntityTypes.BLAZE, 10.0, 0.0, 0.0));
                    CovMdCLab.watch(true);
                    lab().since = CovMdCLab.now(server);
                })
                .then("Lohe schießt", until(900, server -> CovMdCLab.shots(shot -> shot.owner().equals("blaze")).isEmpty()
                        ? Poll.waiting("noch keine Feuerkugel") : Poll.done("erste Feuerkugel")))
                .server("eine Minute im Zeitraffer", (server, state) -> {
                    lab().mark = CovMdCLab.now(server);
                    Arena.command(server, "tick sprint " + WINDOW_TICKS);
                })
                .then("Trefferquote im Bereich", until(2400, CovMdCFairnessSteps::blazeRate)), CovMdCLab::cleanup);
    }

    private static void radius(MinecraftServer server, Map<String, Object> state) {
        CovMdCLab.freeze(CovMdCLab.mob(server, "inside", EntityTypes.ZOMBIE, INSIDE, 0.0, 0.0));
        CovMdCLab.freeze(CovMdCLab.mob(server, "outside", EntityTypes.ZOMBIE, -OUTSIDE_ZOMBIE, 0.0, 0.0));
        CovMdCLab.freeze(CovMdCLab.mob(server, "skeleton", EntityTypes.SKELETON, 0.0, -OUTSIDE_SKELETON, 0.0));
        CovMdCLab.mob("inside").setBaby(false);
        CovMdCLab.mob("outside").setBaby(false);
        CovMdCLab.watch(true);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll radiusChecked(MinecraftServer server) {
        Mob inside = CovMdCLab.mob("inside");
        Mob outside = CovMdCLab.mob("outside");
        Mob skeleton = CovMdCLab.mob("skeleton");
        if (CovMdCLab.assignedTo(outside) || DroneReactions.goalInstalled(outside)) {
            throw new IllegalStateException(format("Zombie in %.1f m reagiert außerhalb des Reaktionsradius", CovMdCLab.flat(outside)));
        }
        if (CovMdCLab.assignedTo(skeleton) || !CovMdCLab.shots(shot -> shot.owner().equals("skeleton")).isEmpty()) {
            throw new IllegalStateException(format("Skelett in %.1f m reagiert außerhalb des Reaktionsradius", CovMdCLab.flat(skeleton)));
        }
        boolean reacts = CovMdCLab.everAssigned(inside);
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("Zombie in %.1f m %s; Zombie in %.1f m und Skelett in %.1f m nach %d Ticks ohne Reaktion", CovMdCLab.flat(inside),
                reacts ? "zugeordnet" : "frei", CovMdCLab.flat(outside), CovMdCLab.flat(skeleton), waited);
        return reacts && waited >= RANGE_TICKS ? Poll.done(note) : Poll.waiting(note);
    }

    private static void skeletons(MinecraftServer server, Map<String, Object> state) {
        for (int i = 0; i < 3; i++) {
            CovMdCLab.freeze(CovMdCLab.mob(server, "skeleton" + i, EntityTypes.SKELETON, SKELETON_DISTANCE, (i - 1) * 3.0, 0.0));
        }
        CovMdCLab.watch(true);
        lab().since = CovMdCLab.now(server);
        lab().values.put(SURVIVALS, new ArrayList<Long>());
    }

    private static Poll firstArrow(MinecraftServer server) {
        int arrows = CovMdCLab.shots(shot -> shot.owner().equals("skeleton")).size();
        long assigned = 0;
        for (int i = 0; i < 3; i++) {
            if (CovMdCLab.everAssigned(CovMdCLab.mob("skeleton" + i))) {
                assigned++;
            }
        }
        String note = format("%d Skelette zugeordnet, %d Pfeile", assigned, arrows);
        return assigned == 3 && arrows > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll perMinute(MinecraftServer server) {
        long start = lab().mark;
        long now = CovMdCLab.now(server);
        if (now - start < WINDOW_TICKS) {
            return Poll.waiting(format("%d von %d Ticks", now - start, WINDOW_TICKS));
        }
        long end = start + WINDOW_TICKS;
        Set<UUID> fired = CovMdCLab.shots(shot -> shot.owner().equals("skeleton") && shot.type().contains("arrow") && shot.tick() >= start && shot.tick() < end)
                .stream().map(CovMdCLab.Shot::id).collect(Collectors.toSet());
        long hits = CovMdCLab.hitsBy("skeleton").stream().filter(hit -> hit.directId() != null && fired.contains(hit.directId())).count();
        long arrows = fired.size();
        String note = format("in einer Minute %d Pfeile, %d Treffer (Quote %.0f %%), Bereich %d bis %d Treffer", arrows, hits, arrows == 0 ? 0.0 : 100.0 * hits / arrows,
                SKELETON_HITS_MIN, SKELETON_HITS_MAX);
        if (hits < SKELETON_HITS_MIN || hits > SKELETON_HITS_MAX) {
            throw new IllegalStateException("Treffer pro Minute außerhalb des Bereichs: " + note);
        }
        return Poll.done(note);
    }

    private static Script trial(int number) {
        return Script.create()
                .server("Versuch " + number + ": Mobs ohne Reaktion, alte Drohne weg", (server, state) -> {
                    Arena.rule(server, CovMdCLab.RULE_DAMAGE, "0");
                    Arena.rule(server, CovMdCLab.RULE_REACT, "false");
                    Entity old = lab().drone;
                    if (old != null && !old.isRemoved()) {
                        PropwashApi.drones().autonomy().stop(old);
                        old.discard();
                    }
                })
                .sleep(10)
                .server("Versuch " + number + ": neue Drohne schwebt auf 4 m", (server, state) -> {
                    CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, 4.0, "CovMdC Fairness " + number);
                    lab().checks = 0;
                })
                .then("Versuch " + number + ": Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                .server("Versuch " + number + ": Mobs reagieren, voller Mob-Schaden", (server, state) -> {
                    Arena.rule(server, CovMdCLab.RULE_DAMAGE, "100");
                    Arena.rule(server, CovMdCLab.RULE_REACT, "true");
                    lab().mark = CovMdCLab.now(server);
                })
                .then("Versuch " + number + ": Drohne fällt aus", until(SURVIVAL_CAP_TICKS + 200, server -> survived(server, number)));
    }

    @SuppressWarnings("unchecked")
    private static Poll survived(MinecraftServer server, int number) {
        Entity drone = lab().drone;
        long elapsed = CovMdCLab.now(server) - lab().mark;
        List<Long> survivals = (List<Long>) lab().values.get(SURVIVALS);
        if (drone == null || drone.isRemoved()) {
            survivals.add(elapsed);
            return Poll.done(format("Versuch %d: Drohne nach %.1f s zerstört", number, elapsed / 20.0));
        }
        if (elapsed >= SURVIVAL_CAP_TICKS) {
            survivals.add(elapsed);
            Arena.rule(server, CovMdCLab.RULE_DAMAGE, "0");
            return Poll.done(format("Versuch %d: Drohne nach %.1f s noch heil (Obergrenze)", number, elapsed / 20.0));
        }
        return Poll.waiting(format("Versuch %d: %.1f s, Treffer %s", number, elapsed / 20.0, CovMdCLab.causes()));
    }

    @SuppressWarnings("unchecked")
    private static SelfTest.StepResult survival() {
        List<Long> survivals = new ArrayList<>((List<Long>) lab().values.get(SURVIVALS));
        if (survivals.size() != TRIALS) {
            return SelfTest.StepResult.fail("nur " + survivals.size() + " Versuche");
        }
        survivals.sort(Long::compare);
        double min = survivals.getFirst() / 20.0;
        double median = survivals.get(survivals.size() / 2) / 20.0;
        String note = format("Überlebenszeit %s s, kürzeste %.1f s, Median %.1f s, Mindestzeit %.1f s", survivals.stream().map(t -> format("%.1f", t / 20.0)).toList(),
                min, median, SURVIVAL_MIN_SECONDS);
        return min >= SURVIVAL_MIN_SECONDS ? SelfTest.StepResult.pass(note) : SelfTest.StepResult.fail(note);
    }

    private static Poll blazeRate(MinecraftServer server) {
        long start = lab().mark;
        long now = CovMdCLab.now(server);
        if (now - start < WINDOW_TICKS) {
            return Poll.waiting(format("%d von %d Ticks", now - start, WINDOW_TICKS));
        }
        long end = start + WINDOW_TICKS;
        Set<UUID> balls = CovMdCLab.shots(shot -> shot.owner().equals("blaze") && shot.type().equals("small_fireball") && shot.tick() >= start && shot.tick() < end)
                .stream().map(CovMdCLab.Shot::id).collect(Collectors.toSet());
        long fired = balls.size();
        long hits = CovMdCLab.hitsBy("blaze").stream().filter(hit -> hit.directId() != null && balls.contains(hit.directId())).map(CovMdCLab.Hit::directId)
                .distinct().count();
        double rate = fired == 0 ? 0.0 : (double) hits / fired;
        String note = format("in einer Minute %d kleine Feuerkugeln, %d Treffer, Quote %.0f %% (Bereich %.0f bis %.0f %%)", fired, hits, rate * 100.0,
                BLAZE_RATE_MIN * 100.0, BLAZE_RATE_MAX * 100.0);
        if (fired < BLAZE_SHOTS_MIN || rate < BLAZE_RATE_MIN || rate > BLAZE_RATE_MAX) {
            throw new IllegalStateException("Trefferquote der Lohe außerhalb des Bereichs: " + note);
        }
        return Poll.done(note);
    }
}
