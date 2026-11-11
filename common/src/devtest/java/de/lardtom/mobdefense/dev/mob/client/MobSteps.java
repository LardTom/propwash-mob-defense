package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.Failures;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneKnockback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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

public final class MobSteps {
    private static final String RULE_REACT = MobDefense.id(RuleDefaults.MOBS_REACT_TO_DRONES.path()).toString();
    private static final String RULE_DAMAGE = MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path()).toString();
    private static final AtomicInteger POLLS = new AtomicInteger();
    private static final String CLEANUP_SINCE = "mob.cleanup.since";
    private static final double SCENE_Z = 12.0;
    private static Lab lab = new Lab();

    private static final class Lab {
        @Nullable Entity drone;
        @Nullable Entity parked;
        @Nullable Mob mob;
        @Nullable Mob second;
        double groundY;
        double startDistance;
        boolean assigned;
        boolean secondAssigned;
        final Set<UUID> small = new HashSet<>();
        final Set<UUID> large = new HashSet<>();
        final Set<UUID> arrows = new HashSet<>();
        long since = -1;
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

    private MobSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("S10 chase – Zombie wird von der schwebenden Drohne angelockt, läuft darunter und schlägt zu; Schaden mit mob_damage_to_drones_percent",
                3000, Script.create()
                        .server("Drohne schwebt auf 3 m", (server, state) -> setup(server, 3.0))
                        .then("Drohne in der Luft", until(600, server -> airborne(2.5)))
                        .server("Zombie 6 m daneben, Mob-Schaden 50 %", (server, state) -> {
                            Arena.rule(server, id(RULE_DAMAGE), "50");
                            lab.mob = mob(server, EntityTypes.ZOMBIE, 6.0, 0.0);
                        })
                        .then("Zombie läuft unter die Drohne und schlägt zu", until(600, MobSteps::chased))
                        .server("Mob-Schaden 0 % für das Foto", (server, state) -> Arena.rule(server, id(RULE_DAMAGE), "0"))
                        .server("Blick auf die Szene", (server, state) -> view(server, 5.0, 0.5, 0.0, true))
                        .append(Shots.photos("mob_chase")), MobSteps::cleanup);
        steps.add("S10 brute – Vindicator (Goal) und Hoglin (Brain) laufen zur Drohne, der Hoglin schleudert sie nach oben", 3000, Script.create()
                .server("Drohne schwebt auf 3 m", (server, state) -> {
                    Arena.rule(server, id(RULE_DAMAGE), "0");
                    setup(server, 3.0);
                })
                .then("Drohne in der Luft", until(600, server -> airborne(2.5)))
                .server("Vindicator und Hoglin je 6 m daneben", (server, state) -> {
                    lab.mob = mob(server, EntityTypes.VINDICATOR, 6.0, 0.0);
                    lab.second = mob(server, EntityTypes.HOGLIN, -6.0, 0.0);
                    ((Hoglin) lab.second).setImmuneToZombification(true);
                    ((Hoglin) lab.second).setBaby(false);
                })
                .then("beide an der Drohne, Hoglin-Stoß über DRONE_KNOCKBACK", until(800, MobSteps::bruted))
                .server("Blick auf die Szene", (server, state) -> view(server, 6.0, 0.5, 0.0, false))
                .append(Shots.photos("mob_brute")), MobSteps::cleanup);
        steps.add("S10 archer – Skelett trifft eine schwebende Drohne in 5 Schüssen mindestens einmal", 3000, Script.create()
                .server("Drohne schwebt auf 4 m", (server, state) -> {
                    Arena.rule(server, id(RULE_DAMAGE), "0");
                    setup(server, 4.0);
                })
                .then("Drohne in der Luft", until(600, server -> airborne(3.5)))
                .server("Skelett 10 m daneben", (server, state) -> lab.mob = mob(server, EntityTypes.SKELETON, 10.0, 0.0))
                .then("Treffer in höchstens 5 Schüssen", until(900, MobSteps::archered))
                .server("Blick auf die Szene", (server, state) -> view(server, 8.0, 1.0, 0.0, true))
                .append(Shots.photos("mob_archer")), MobSteps::cleanup);
        steps.add("S10 fireball – Blaze schießt drei kleine Feuerkugeln, Ghast eine große; die Explosion stößt die Drohne über DRONE_KNOCKBACK",
                3600, Script.create()
                        .server("Drohne schwebt auf 4 m", (server, state) -> {
                            Arena.rule(server, id(RULE_DAMAGE), "0");
                            Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false");
                            MobScene.protectFromFire(true);
                            setup(server, 4.0);
                        })
                        .then("Drohne in der Luft", until(600, server -> airborne(3.5)))
                        .server("Blaze 10 m daneben, Ghast 20 m entfernt und 8 m höher", (server, state) -> {
                            lab.mob = mob(server, EntityTypes.BLAZE, 10.0, 0.0);
                            lab.second = mob(server, EntityTypes.GHAST, -20.0, 0.0, 8.0);
                        })
                        .then("Feuerkugeln und Explosionsstoß", until(1200, MobSteps::fireballed))
                        .server("Blick auf die Szene", (server, state) -> view(server, 22.0, 2.0, -5.0, false))
                        .append(Shots.photos("mob_fireball")), MobSteps::cleanup);
        steps.add("S10 creeper – Creeper läuft unter die tief fliegende Drohne und zündet; die Explosion stößt die Drohne über DRONE_KNOCKBACK",
                3000, Script.create()
                        .server("Drohne schwebt auf 2 m", (server, state) -> {
                            Arena.rule(server, id(RULE_DAMAGE), "0");
                            Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false");
                            setup(server, 2.0);
                        })
                        .then("Drohne in der Luft", until(600, server -> airborne(1.6)))
                        .server("Creeper 7 m daneben mit langer Zündschnur", (server, state) -> {
                            lab.mob = mob(server, EntityTypes.CREEPER, 7.0, 0.0);
                            Arena.command(server, "data merge entity " + lab.mob.getUUID() + " {Fuse:200s}");
                        })
                        .then("Creeper unter der Drohne zündet", until(600, MobSteps::creeperLit))
                        .server("Blick auf die Szene", (server, state) -> view(server, 4.5, 0.0, 0.0, true))
                        .append(Shots.photos("mob_creeper"))
                        .then("Explosion stößt die Drohne", until(400, MobSteps::creeperBlew)), MobSteps::cleanup);
        steps.add("S10 flyer – Phantom jagt die Drohne über 10 Blöcken und stößt herab", 3000, Script.create()
                .server("Drohne schwebt auf 12 m", (server, state) -> {
                    Arena.rule(server, id(RULE_DAMAGE), "0");
                    setup(server, 12.0);
                })
                .then("Drohne über 10 Blöcken", until(800, server -> airborne(11.0)))
                .server("Phantom 10 m daneben und 8 m höher", (server, state) -> lab.mob = mob(server, EntityTypes.PHANTOM, 10.0, 0.0, 20.0))
                .then("Phantom jagt und trifft die Drohne", until(900, MobSteps::flown))
                .server("Blick auf die Szene", (server, state) -> view(server, 10.0, 0.0, 0.0, false))
                .append(Shots.photos("mob_flyer")), MobSteps::cleanup);
        steps.add("S10 Regeln – Regel aus: keine Reaktion, wieder an: Reaktion; geparkte Drohne bleibt unbehelligt; Spieler bleibt erstes Ziel", 3000,
                Script.create()
                        .server("geparkte Drohne und Zombie 3 m daneben", (server, state) -> {
                            Arena.rule(server, id(RULE_DAMAGE), "0");
                            parked(server);
                        })
                        .then("geparkte Drohne bleibt unbehelligt", until(120, MobSteps::parkedIgnored))
                        .server("Drohne startet, Regel mobs_react_to_drones aus", (server, state) -> {
                            Arena.rule(server, id(RULE_REACT), "false");
                            hoverParked(server, 3.0);
                        })
                        .then("Regel aus: keine Reaktion", until(160, MobSteps::ruleOffIgnored))
                        .server("Regel wieder an", (server, state) -> Arena.rule(server, id(RULE_REACT), "true"))
                        .then("Regel an: Zombie reagiert", until(200, MobSteps::ruleOnReacts))
                        .server("Spieler im Überlebensmodus 4 m vom Zombie, Drohne weiter weg", MobSteps::playerFirst)
                        .then("Zombie bleibt beim Spieler", until(200, MobSteps::playerStays)), MobSteps::cleanup);
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }

    private static Script.Stage until(int maxTicks, Probe probe) {
        String key = "mob.poll." + POLLS.incrementAndGet();
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

    private static void setup(MinecraftServer server, double metres) {
        MobScene.listen();
        MobScene.clearEvents();
        lab = new Lab();
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(0, (int) SCENE_Z);
        lab.groundY = ground.getY() + 1.0;
        lab.drone = MobScene.placeDrone(level, ground, "S10");
        AutonomyResult result = MobScene.hover(lab.drone, Arena.player(server), metres);
        if (result != AutonomyResult.STARTED) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
    }

    private static void parked(MinecraftServer server) {
        MobScene.listen();
        MobScene.clearEvents();
        lab = new Lab();
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(0, (int) SCENE_Z);
        lab.groundY = ground.getY() + 1.0;
        lab.parked = MobScene.placeDrone(level, ground, "S10 geparkt");
        lab.mob = mob(server, EntityTypes.ZOMBIE, 3.0, 0.0);
    }

    private static void hoverParked(MinecraftServer server, double metres) {
        Entity drone = drone(lab.parked);
        AutonomyResult result = MobScene.hover(drone, Arena.player(server), metres);
        if (result != AutonomyResult.STARTED) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
        lab.drone = drone;
        lab.parked = null;
    }

    private static <T extends Mob> T mob(MinecraftServer server, EntityType<T> type, double dx, double dz) {
        return mob(server, type, dx, dz, 0.0);
    }

    private static <T extends Mob> T mob(MinecraftServer server, EntityType<T> type, double dx, double dz, double dy) {
        Entity anchor = lab.drone != null ? lab.drone : drone(lab.parked);
        Vec3 at = new Vec3(anchor.getX() + dx, lab.groundY + dy, anchor.getZ() + dz);
        float yaw = (float) (-Mth.atan2(anchor.getX() - at.x, anchor.getZ() - at.z) * Mth.RAD_TO_DEG);
        T mob = MobScene.spawn(Arena.level(server), type, at, yaw);
        Arena.track(mob);
        if (lab.drone != null) {
            lab.startDistance = MobScene.horizontal(mob, lab.drone.position());
        }
        return mob;
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

    private static boolean assignedTo(Mob mob, Entity drone) {
        return DroneReactions.assigned(mob).filter(entity -> entity == drone).isPresent();
    }

    private static List<MobScene.Hit> hitsBy(Entity drone, String cause) {
        return MobScene.hits(drone).stream().filter(hit -> hit.cause().equals(cause)).toList();
    }

    private static Poll chased(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob zombie = mob(lab.mob, "Zombie");
        lab.assigned |= assignedTo(zombie, drone);
        double distance = MobScene.horizontal(zombie, drone.position());
        List<MobScene.Hit> hits = hitsBy(drone, "zombie");
        String note = String.format(Locale.ROOT, "Zombie %s, Abstand %.1f → %.1f m, %d Treffer, Goal %s", lab.assigned ? "zugeordnet" : "frei",
                lab.startDistance, distance, hits.size(), DroneReactions.goalInstalled(zombie) ? "ja" : "nein");
        if (!lab.assigned || hits.isEmpty()) {
            return Poll.waiting(note);
        }
        MobScene.Hit hit = hits.getFirst();
        if (Math.abs(hit.amount() - hit.original() * 0.5F) > 0.01F) {
            throw new IllegalStateException("Mob-Schaden " + hit.original() + " → " + hit.amount() + " statt 50 %");
        }
        return Poll.done(note + String.format(Locale.ROOT, ", Schaden %.1f → %.2f (50 %%)", hit.original(), hit.amount()));
    }

    private static Poll bruted(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob vindicator = mob(lab.mob, "Vindicator");
        Mob hoglin = mob(lab.second, "Hoglin");
        lab.assigned |= assignedTo(vindicator, drone);
        lab.secondAssigned |= assignedTo(hoglin, drone);
        double toVindicator = MobScene.horizontal(vindicator, drone.position());
        double toHoglin = MobScene.horizontal(hoglin, drone.position());
        List<MobScene.Push> pushes = MobScene.pushes(drone).stream()
                .filter(push -> push.cause() == DroneKnockback.Cause.OTHER && push.scale() > 0.0 && push.delta().y > 0.0).toList();
        int vindicatorHits = hitsBy(drone, "vindicator").size();
        int hoglinHits = hitsBy(drone, "hoglin").size();
        String note = String.format(Locale.ROOT, "Vindicator %s %.1f m, %d Treffer; Hoglin %s %.1f m, %d Treffer, %d Stöße nach oben (Skala %s, Δv %s m/s), "
                        + "%d Stöße insgesamt",
                lab.assigned ? "zugeordnet" : "frei", toVindicator, vindicatorHits, lab.secondAssigned ? "zugeordnet (Brain)" : "frei", toHoglin,
                hoglinHits, pushes.size(), pushes.isEmpty() ? "-" : String.format(Locale.ROOT, "%.1f", pushes.getFirst().scale()),
                pushes.isEmpty() ? "-" : String.format(Locale.ROOT, "%.1f", pushes.getFirst().delta().length()), MobScene.pushes(drone).size());
        boolean done = lab.assigned && lab.secondAssigned && vindicatorHits > 0 && hoglinHits > 0 && !pushes.isEmpty();
        return done ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll archered(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob skeleton = mob(lab.mob, "Skelett");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(skeleton, drone);
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, Arena.box(level), arrow -> arrow.getOwner() == skeleton)) {
            lab.arrows.add(arrow.getUUID());
        }
        int hits = (int) hitsBy(drone, "skeleton").stream().filter(hit -> hit.direct().contains("arrow")).count();
        String note = String.format(Locale.ROOT, "Skelett %s, %d Pfeile, %d Treffer", lab.assigned ? "zugeordnet" : "frei", lab.arrows.size(), hits);
        if (hits > 0 && lab.arrows.size() <= 5) {
            return Poll.done(note);
        }
        if (lab.arrows.size() >= 5) {
            if (lab.since < 0) {
                lab.since = level.getGameTime();
            } else if (level.getGameTime() - lab.since > 40) {
                throw new IllegalStateException("kein Treffer in 5 Schüssen: " + note);
            }
        }
        return Poll.waiting(note);
    }

    private static Poll fireballed(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob blaze = mob(lab.mob, "Blaze");
        Mob ghast = mob(lab.second, "Ghast");
        ServerLevel level = Arena.level(server);
        lab.assigned |= assignedTo(blaze, drone);
        lab.secondAssigned |= assignedTo(ghast, drone);
        AABB box = Arena.box(level);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box, p -> p instanceof SmallFireball || p instanceof LargeFireball)) {
            if (projectile instanceof SmallFireball && projectile.getOwner() == blaze) {
                lab.small.add(projectile.getUUID());
            } else if (projectile instanceof LargeFireball && projectile.getOwner() == ghast) {
                lab.large.add(projectile.getUUID());
            }
        }
        List<MobScene.Push> blasts = MobScene.pushes(drone).stream().filter(push -> push.cause() == DroneKnockback.Cause.EXPLOSION).toList();
        String note = String.format(Locale.ROOT, "Blaze %s, %d kleine; Ghast %s, %d große; %d Explosionsstöße (Skala %s), Treffer %s",
                lab.assigned ? "zugeordnet" : "frei", lab.small.size(), lab.secondAssigned ? "zugeordnet" : "frei", lab.large.size(), blasts.size(),
                blasts.isEmpty() ? "-" : String.format(Locale.ROOT, "%.1f", blasts.getFirst().scale()), causes(drone));
        boolean done = lab.small.size() >= 3 && !lab.large.isEmpty() && !blasts.isEmpty() && blasts.getFirst().scale() == 1.0;
        return done ? Poll.done(note) : Poll.waiting(note);
    }

    private static String causes(Entity drone) {
        Map<String, Long> counts = new TreeMap<>();
        for (MobScene.Hit hit : MobScene.hits(drone)) {
            counts.merge(hit.cause() + "/" + hit.direct(), 1L, Long::sum);
        }
        return counts.toString();
    }

    private static Poll creeperLit(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob mob = mob(lab.mob, "Creeper");
        Creeper creeper = (Creeper) mob;
        lab.assigned |= assignedTo(creeper, drone);
        double distance = creeper.getBoundingBox().getCenter().distanceTo(drone.getBoundingBox().getCenter());
        String note = String.format(Locale.ROOT, "Creeper %s, Abstand %.1f m, Zündung %d", lab.assigned ? "zugeordnet" : "frei", distance,
                creeper.getSwellDir());
        return lab.assigned && creeper.getSwellDir() > 0 && distance <= 3.0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll creeperBlew(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        boolean gone = lab.mob == null || lab.mob.isRemoved();
        List<MobScene.Push> blasts = MobScene.pushes(drone).stream().filter(push -> push.cause() == DroneKnockback.Cause.EXPLOSION).toList();
        String note = String.format(Locale.ROOT, "Creeper %s, %d Explosionsstöße%s", gone ? "explodiert" : "noch da", blasts.size(),
                blasts.isEmpty() ? "" : String.format(Locale.ROOT, " (Skala %.1f, Δv %.1f m/s)", blasts.getFirst().scale(),
                        blasts.getFirst().delta().length()));
        return gone && !blasts.isEmpty() && blasts.getFirst().scale() == 1.0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll flown(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob phantom = mob(lab.mob, "Phantom");
        lab.assigned |= assignedTo(phantom, drone);
        double distance = phantom.position().distanceTo(drone.position());
        int hits = hitsBy(drone, "phantom").size();
        String note = String.format(Locale.ROOT, "Phantom %s, Abstand %.1f m, %d Treffer, Drohne %.1f m über Boden", lab.assigned ? "zugeordnet" : "frei",
                distance, hits, drone.getY() - lab.groundY);
        return lab.assigned && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll parkedIgnored(MinecraftServer server) {
        Entity parked = drone(lab.parked);
        Mob zombie = mob(lab.mob, "Zombie");
        if (DroneReactions.assigned(zombie).isPresent() || !hitsBy(parked, "zombie").isEmpty()) {
            throw new IllegalStateException("Zombie reagiert auf die geparkte Drohne");
        }
        return zombie.tickCount >= 100 ? Poll.done("Zombie 100 Ticks neben der geparkten Drohne ohne Reaktion, Goal " + (DroneReactions.goalInstalled(zombie)
                ? "ja" : "nein")) : Poll.waiting("Zombie " + zombie.tickCount + " Ticks alt");
    }

    private static Poll ruleOffIgnored(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob zombie = mob(lab.mob, "Zombie");
        if (DroneReactions.assigned(zombie).isPresent() || DroneReactions.assignments(Arena.level(server)) > 0) {
            throw new IllegalStateException("Reaktion trotz mobs_react_to_drones=false");
        }
        double height = drone.getY() - lab.groundY;
        if (lab.since < 0) {
            lab.since = Arena.level(server).getGameTime();
        }
        long waited = Arena.level(server).getGameTime() - lab.since;
        String note = String.format(Locale.ROOT, "Regel aus, Drohne %.1f m hoch, %d Ticks ohne Reaktion", height, waited);
        return waited >= 100 && height >= 2.0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll ruleOnReacts(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob zombie = mob(lab.mob, "Zombie");
        boolean assigned = assignedTo(zombie, drone);
        return assigned ? Poll.done("Regel an: Zombie der Drohne zugeordnet") : Poll.waiting("Zombie noch frei");
    }

    private static void playerFirst(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(lab.drone);
        Mob zombie = mob(lab.mob, "Zombie");
        ServerPlayer player = Arena.player(server);
        zombie.teleportTo(drone.getX() + 9.0, lab.groundY, drone.getZ());
        zombie.getNavigation().stop();
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(Arena.level(server), drone.getX() + 13.0, lab.groundY, drone.getZ(), Set.<Relative>of(), 90.0F, 0.0F, true);
        lab.assigned = false;
    }

    private static Poll playerStays(MinecraftServer server) {
        Entity drone = drone(lab.drone);
        Mob zombie = mob(lab.mob, "Zombie");
        ServerPlayer player = Arena.player(server);
        boolean targetsPlayer = zombie.getTarget() == player;
        if (targetsPlayer && assignedTo(zombie, drone)) {
            throw new IllegalStateException("Zombie mit Spielerziel geht zur Drohne, obwohl sie nicht halb so nah ist");
        }
        double toDrone = drone.getBoundingBox().getCenter().distanceTo(zombie.getEyePosition());
        double toPlayer = zombie.distanceTo(player);
        String note = String.format(Locale.ROOT, "Zombie-Ziel %s, Drohne %.1f m, Spieler %.1f m", targetsPlayer ? "Spieler" : String.valueOf(zombie.getTarget()),
                toDrone, toPlayer);
        if (targetsPlayer && ++lab.checks >= 20) {
            player.setGameMode(GameType.CREATIVE);
            return Poll.done(note + ", 20 Prüfungen ohne Wechsel zur Drohne");
        }
        return Poll.waiting(note);
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
            MobScene.protectFromFire(false);
            ServerLevel level = Arena.level(server);
            for (Entity drone : Arena.drones(level)) {
                PropwashApi.drones().autonomy().stop(drone);
            }
            int projectiles = MobScene.clearProjectiles(level, Arena.box(level));
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
