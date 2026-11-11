package de.lardtom.mobdefense.dev.mob.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.Guests;
import de.lardtom.mobdefense.dev.mob.client.CovMdCLab.Poll;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.mob.DroneState;
import de.lardtom.mobdefense.mob.core.StageTwoWindows;
import de.lardtom.mobdefense.mob.reaction.PotionReaction;
import de.lardtom.mobdefense.mob.reaction.StareReaction;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.event.DroneKnockback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.format;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.lab;
import static de.lardtom.mobdefense.dev.mob.client.CovMdCLab.until;

public final class CovMdCKindSteps {
    public static final double TRIDENT_FAR = 20.0;
    public static final double TRIDENT_NEAR = 14.0;
    public static final double WITCH_FAR = 12.0;
    public static final double WITCH_NEAR = 8.0;
    public static final double EVOKER_FAR = 14.0;
    public static final double EVOKER_NEAR = 8.0;
    public static final double WITHER_FAR = 36.0;
    public static final double WITHER_NEAR = 28.0;
    public static final int WITHER_RANGE_RULE = 48;
    public static final int BEAM_DELAY = 80;
    public static final double AIM_DEGREES = 12.0;
    public static final double GAZE_MIN = 4.5;
    public static final int QUIET_TICKS = 120;
    public static final int FANGS_GRACE = 60;
    public static final double WALL_GAP = 0.15;
    public static final double CLIMB_HEIGHT = 6.0;
    private static final String SINCE_HIGH = "covmdc.high";

    private CovMdCKindSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Mob je Art: Ertrunkene und Zombie-Nautilus – Wasserbecken, Ertrunkener ohne Dreizack und Zombie-Nautilus schwimmen unter die tiefe "
                + "Drohne und greifen an", 3600, Script.create()
                .server("Drohne schwebt auf 1,2 m", hover(1.2, 0))
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(0.9)))
                .server("Becken unter der Drohne, Ertrunkener und Zombie-Nautilus 7 m daneben im Wasser", CovMdCKindSteps::pool)
                .then("beide schwimmen unter die Drohne und greifen an", until(1500, CovMdCKindSteps::pooled))
                .server("Blick auf die Szene", (server, state) -> CovMdCLab.view(server, 8.0, 1.5, 3.0))
                .append(Shots.photos("covmdc_pool")), CovMdCLab::cleanup);
        steps.add("Mob je Art: Ertrunkener mit Dreizack – in 20 m kein Wurf, in 14 m wirft er den Dreizack auf die Drohne und trifft", 3600, Script.create()
                .server("Drohne schwebt auf 3 m", hover(3.0, 0))
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(2.5)))
                .server("Ertrunkener mit Dreizack 20 m daneben", CovMdCKindSteps::tridentFar)
                .then("in 20 m kein Wurf", until(QUIET_TICKS + 100, server -> quiet("drowned", "trident", QUIET_TICKS)))
                .server("Ertrunkener auf 14 m", (server, state) -> closer("drowned", TRIDENT_NEAR))
                .then("Dreizack fliegt auf die Drohne und trifft", until(1200, CovMdCKindSteps::tridentNear)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Diener, Piglin-Barbar, Witherskelett – nähern sich der tiefen Drohne und schlagen zu", 3000, Script.create()
                .server("Drohne schwebt auf 2 m", hover(2.0, 0))
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(1.6)))
                .server("Diener und Witherskelett je 6 m daneben", (server, state) -> {
                    CovMdCLab.mob(server, "vindicator", EntityTypes.VINDICATOR, 6.0, 0.0, 0.0);
                    CovMdCLab.mob(server, "wither_skeleton", EntityTypes.WITHER_SKELETON, 0.0, -6.0, 0.0);
                    lab().since = CovMdCLab.now(server);
                })
                .then("Diener und Witherskelett an der Drohne und mit Treffer", until(1500, server -> allHit("vindicator", "wither_skeleton")))
                .server("Piglin-Barbar allein (er greift Witherskelette an)", (server, state) -> {
                    CovMdCLab.discard("vindicator");
                    CovMdCLab.discard("wither_skeleton");
                    AbstractPiglin brute = CovMdCLab.mob(server, "piglin_brute", EntityTypes.PIGLIN_BRUTE, -6.0, 0.0, 0.0);
                    brute.setImmuneToZombification(true);
                    brute.setBaby(false);
                    lab().since = CovMdCLab.now(server);
                })
                .then("Piglin-Barbar an der Drohne und mit Treffer", until(1500, server -> allHit("piglin_brute"))), CovMdCLab::cleanup);
        steps.add("Mob je Art: Zombifizierter Piglin – ruhig neben der Drohne, nach einem Schlag des Testspielers wütend und greift die Drohne an", 3600,
                Script.create()
                        .server("Drohne schwebt auf 2 m", hover(2.0, 0))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(1.6)))
                        .server("Zombifizierter Piglin 1,8 m neben der Drohne", (server, state) -> {
                            Mob piglin = CovMdCLab.mob(server, "zombified_piglin", EntityTypes.ZOMBIFIED_PIGLIN, 1.8, 0.0, 0.0);
                            piglin.setBaby(false);
                            CovMdCLab.freeze(piglin);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("ruhig: keine Zuordnung, kein Treffer", until(QUIET_TICKS + 100, CovMdCKindSteps::piglinCalm))
                        .server("Testspieler schlägt den Piglin, wütend geht er 14 m weg", CovMdCKindSteps::provoke)
                        .then("wütender Piglin greift die Drohne an", until(900, CovMdCKindSteps::piglinAngry)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Verwüster, Hoglin, Zoglin – das Brüllen des Verwüsters stößt die Drohne waagerecht weg, Hoglin und Zoglin werfen sie nach oben",
                4800, Script.create()
                        .server("Drohne schwebt auf 2 m", hover(2.0, 0))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(1.6)))
                        .server("Verwüster 6 m daneben", (server, state) -> {
                            CovMdCLab.mob(server, "ravager", EntityTypes.RAVAGER, 6.0, 0.0, 0.0);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("Brüllen stößt die Drohne waagerecht", until(1200, CovMdCKindSteps::roared))
                        .server("Hoglin statt Verwüster", (server, state) -> {
                            CovMdCLab.discard("ravager");
                            Hoglin hoglin = CovMdCLab.mob(server, "hoglin", EntityTypes.HOGLIN, -6.0, 0.0, 0.0);
                            hoglin.setImmuneToZombification(true);
                            hoglin.setBaby(false);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("Hoglin wirft die Drohne nach oben", until(1200, server -> thrown("hoglin")))
                        .server("Zoglin statt Hoglin", (server, state) -> {
                            CovMdCLab.discard("hoglin");
                            Mob zoglin = CovMdCLab.mob(server, "zoglin", EntityTypes.ZOGLIN, 0.0, -6.0, 0.0);
                            zoglin.setBaby(false);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("Zoglin wirft die Drohne nach oben", until(1200, server -> thrown("zoglin"))), CovMdCLab::cleanup);
        steps.add("Mob je Art: Plünderer, Illusionist, Piglin mit Armbrust – schießen ihre eigenen Geschosse mit der Drohne als Ziel", 3600, Script.create()
                .server("Drohne schwebt auf 4 m", hover(4.0, 0))
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                .server("Plünderer, Illusionist und Piglin mit Armbrust je 10 m daneben", (server, state) -> {
                    CovMdCLab.mob(server, "pillager", EntityTypes.PILLAGER, 10.0, 0.0, 0.0);
                    CovMdCLab.mob(server, "illusioner", EntityTypes.ILLUSIONER, -10.0, 0.0, 0.0);
                    AbstractPiglin piglin = CovMdCLab.mob(server, "piglin", EntityTypes.PIGLIN, 0.0, -10.0, 0.0);
                    piglin.setImmuneToZombification(true);
                    piglin.setBaby(false);
                    piglin.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                    piglin.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
                    CovMdCLab.watch(true);
                    lab().since = CovMdCLab.now(server);
                })
                .then("jeder schießt auf die Drohne", until(1800, CovMdCKindSteps::archers)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Hexe – in 12 m kein Wurf, in 8 m wirft sie Schadenstränke; ein Platzen im Umkreis 2 schadet der Drohne nach Vanilla-Formel",
                3600, Script.create()
                        .server("Drohne schwebt auf 2,5 m", hover(2.5, 0))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(2.0)))
                        .server("Hexe 12 m daneben", (server, state) -> {
                            CovMdCLab.freeze(CovMdCLab.mob(server, "witch", EntityTypes.WITCH, WITCH_FAR, 0.0, 0.0));
                            CovMdCLab.watch(true);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("in 12 m kein Wurf", until(QUIET_TICKS + 100, server -> quiet("witch", "potion", QUIET_TICKS)))
                        .server("Hexe auf 8 m", (server, state) -> closer("witch", WITCH_NEAR))
                        .then("Trank platzt höchstens 2 Blöcke von der Drohne und schadet", until(1500, CovMdCKindSteps::splashed)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Magier – in 14 m keine Reaktion; Drohne auf 1,5 m: Fangzähne unter ihr; höher: keine Fangzähne mehr, Plagegeister jagen sie",
                4800, Script.create()
                        .server("Drohne schwebt auf 1,5 m", hover(1.5, 0))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(1.2)))
                        .server("Magier 14 m daneben", (server, state) -> {
                            CovMdCLab.freeze(CovMdCLab.mob(server, "evoker", EntityTypes.EVOKER, EVOKER_FAR, 0.0, 0.0));
                            CovMdCLab.watch(true);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("in 14 m keine Reaktion", until(QUIET_TICKS + 100, CovMdCKindSteps::evokerQuiet))
                        .server("Magier auf 8 m", (server, state) -> closer("evoker", EVOKER_NEAR))
                        .then("Fangzähne unter der tiefen Drohne beißen", until(900, CovMdCKindSteps::fanged))
                        .server("Drohne steigt um 2,5 m", (server, state) -> {
                            CovMdCLab.climb(server, 2.5);
                            lab().values.remove(SINCE_HIGH);
                        })
                        .then("höher: Plagegeister jagen die Drohne, keine Fangzähne mehr", until(1500, CovMdCKindSteps::vexed)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Creeper-Krater folgt mob_griefing – mob_griefing aus, Creeper zündet an der Drohne: Blockzahl gleich, Drohne trotzdem gestoßen",
                3000, Script.create()
                        .server("Drohne schwebt auf 2 m, mob_griefing aus", hover(2.0, 0))
                        .server("mob_griefing aus", (server, state) -> Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false"))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(1.6)))
                        .server("Blöcke zählen, Creeper 7 m daneben", CovMdCKindSteps::creeper)
                        .then("Explosion stößt die Drohne, kein Krater", until(1200, CovMdCKindSteps::noCrater)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Schleim, Magmawürfel, Schwefelwürfel – springen die Drohne auf 2,5 m an, auf 5,5 m nicht", 4800, Script.create()
                .server("Drohne schwebt auf 2,5 m", hover(2.5, 0))
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(2.1)))
                .server("Schwefelwürfel 4 m daneben", (server, state) -> {
                    cube(server, "sulfur_cube", EntityTypes.SULFUR_CUBE, 4.0, 0.0);
                    lab().since = CovMdCLab.now(server);
                })
                .then("Schwefelwürfel springt an und stößt die Drohne", until(1200, CovMdCKindSteps::sulfur))
                .server("Schleim und Magmawürfel statt Schwefelwürfel", (server, state) -> {
                    CovMdCLab.discard("sulfur_cube");
                    cube(server, "slime", EntityTypes.SLIME, 4.0, 0.0);
                    cube(server, "magma_cube", EntityTypes.MAGMA_CUBE, -4.0, 0.0);
                    lab().since = CovMdCLab.now(server);
                })
                .then("Schleim und Magmawürfel springen an und treffen", until(1200, server -> allHit("slime", "magma_cube")))
                .server("Drohne steigt um 3 m", (server, state) -> CovMdCLab.climb(server, 3.0))
                .then("auf 5,5 m keine Reaktion", until(900, server -> released(4.8, "slime", "magma_cube"))), CovMdCLab::cleanup);
        steps.add("Mob je Art: Spinnen – Spinne und Höhlenspinne springen die Drohne auf 2 m an; auf 9 m nur an der Wand: sie klettern hoch und treffen",
                6000, Script.create()
                        .server("Drohne schwebt auf 2 m", hover(2.0, 0))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(1.6)))
                        .server("Spinne und Höhlenspinne je 5 m daneben", (server, state) -> {
                            CovMdCLab.tough(CovMdCLab.mob(server, "spider", EntityTypes.SPIDER, -5.0, 2.0, 0.0));
                            CovMdCLab.tough(CovMdCLab.mob(server, "cave_spider", EntityTypes.CAVE_SPIDER, -5.0, -2.0, 0.0));
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("beide springen an und treffen", until(1200, server -> allHit("spider", "cave_spider")))
                        .server("Drohne steigt um 7 m", (server, state) -> CovMdCLab.climb(server, 7.0))
                        .then("auf 9 m frei in der Luft: keine Reaktion", until(900, server -> released(8.3, "spider", "cave_spider")))
                        .server("Wand direkt neben der Drohne, Spinnen 6 m auf der anderen Seite", CovMdCKindSteps::wall)
                        .then("Spinnen klettern die Wand hoch und treffen", until(2400, CovMdCKindSteps::climbed))
                        .server("Blick auf die Szene", (server, state) -> CovMdCLab.view(server, 9.0, 3.0, 0.0))
                        .append(Shots.photos("covmdc_wall_climb")), CovMdCLab::cleanup);
        steps.add("Mob je Art: Wächter – Laser auf die Drohne über dem Wasser, Schaden frühestens 80 Ticks nach Beginn", 3600, Script.create()
                .server("Drohne schwebt auf 3 m", hover(3.0, 0))
                .then("Drohne in der Luft", until(600, CovMdCLab.airborne(2.5)))
                .server("Becken unter der Drohne, Wächter 6 m daneben", CovMdCKindSteps::guardian)
                .then("erster Schaden frühestens nach 80 Ticks", until(1200, CovMdCKindSteps::beamed)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Knarz – ohne Kamerablick sinkt der Abstand zur Drohne, unter dem Blick bleibt er stehen, danach läuft er wieder", 4800,
                Script.create()
                        .server("Drohne schwebt auf 1 m", hover(1.0, 0))
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(0.8)))
                        .server("Knarz 8 m hinter der Kamera", (server, state) -> {
                            behind(server, "creaking", 8.0, true);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("Abstand sinkt ohne Kamerablick", until(600, server -> creakingCloser(2.0)))
                        .server("Knarz in den Kamerablick", (server, state) -> intoGaze())
                        .then("unter dem Kamerablick steht er", until(600, CovMdCKindSteps::creakingStill))
                        .server("Knarz wieder hinter die Kamera", (server, state) -> behind(server, "creaking", 7.0, false))
                        .then("Abstand sinkt wieder", until(600, server -> creakingCloser(1.5))), CovMdCLab::cleanup);
        steps.add("Mob je Art: Wither – mob_reaction_range 48, mob_griefing aus: in 36 m kein Schädel, in 28 m Wither-Schädel auf die Drohne", 3600,
                Script.create()
                        .server("Drohne schwebt auf 4 m", hover(4.0, 0))
                        .server("Reichweite 48, mob_griefing aus", (server, state) -> {
                            Arena.rule(server, CovMdCLab.RULE_RANGE, String.valueOf(WITHER_RANGE_RULE));
                            Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false");
                        })
                        .then("Drohne in der Luft", until(600, CovMdCLab.airborne(3.5)))
                        .server("Wither 36 m daneben und 4 m höher", (server, state) -> {
                            CovMdCLab.freeze(CovMdCLab.mob(server, "wither", EntityTypes.WITHER, WITHER_FAR, 0.0, 4.0));
                            CovMdCLab.watch(true);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("in 36 m kein Schädel", until(QUIET_TICKS + 100, server -> quiet("wither", "wither_skull", QUIET_TICKS)))
                        .server("Wither auf 28 m", (server, state) -> {
                            Mob wither = CovMdCLab.mob("wither");
                            CovMdCLab.teleport(wither, WITHER_NEAR, 0.0, 4.0);
                            lab().since = CovMdCLab.now(server);
                        })
                        .then("Wither-Schädel fliegen auf die Drohne", until(1200, CovMdCKindSteps::skulls)), CovMdCLab::cleanup);
        steps.add("Mob je Art: Silberfischchen und Endermite – Endermite beißt die Drohne auf 0,7 m, auf 3 m bleibt sie ruhig", 3600, Script.create()
                .server("Drohne schwebt auf 0,7 m", hover(0.7, 0))
                .then("Drohne tief in der Luft", until(600, CovMdCLab.band(0.3, 0.95)))
                .server("Endermite 4 m daneben", (server, state) -> {
                    CovMdCLab.mob(server, "endermite", EntityTypes.ENDERMITE, 4.0, 0.0, 0.0);
                    lab().since = CovMdCLab.now(server);
                })
                .then("Endermite läuft hin und beißt", until(900, server -> allHit("endermite")))
                .server("Drohne steigt um 2,5 m", (server, state) -> CovMdCLab.climb(server, 2.5))
                .then("auf 3 m keine Reaktion", until(900, server -> released(2.6, "endermite"))), CovMdCLab::cleanup);
    }

    private static Script.ServerAction hover(double metres, int damagePercent) {
        return (server, state) -> {
            CovMdCLab.reset(server, damagePercent);
            CovMdCLab.hover(server, CovMdCLab.SCENE_X, CovMdCLab.SCENE_Z, metres, "CovMdC Art");
        };
    }

    private static Poll allHit(String... keys) {
        StringBuilder note = new StringBuilder();
        boolean all = true;
        for (String key : keys) {
            Mob mob = CovMdCLab.mob(key);
            boolean assigned = CovMdCLab.everAssigned(mob);
            int hits = CovMdCLab.hitsBy(key).size();
            all &= assigned && hits > 0;
            note.append(format("%s%s %s %.1f m, %d Treffer", note.isEmpty() ? "" : "; ", key, assigned ? "zugeordnet" : "frei", CovMdCLab.flat(mob), hits));
        }
        note.append(format(", Drohne %.1f m hoch", CovMdCLab.height()));
        return all ? Poll.done(note.toString()) : Poll.waiting(note.toString());
    }

    private static Poll released(double minHeight, String... keys) {
        double height = CovMdCLab.height();
        boolean any = false;
        StringBuilder note = new StringBuilder(format("Drohne %.1f m hoch", height));
        for (String key : keys) {
            Mob mob = CovMdCLab.mob(key);
            boolean assigned = CovMdCLab.assignedTo(mob);
            any |= assigned;
            note.append(format("; %s %s", key, assigned ? "zugeordnet" : "frei"));
        }
        if (height < minHeight || any) {
            lab().checks = 0;
            return Poll.waiting(note.toString());
        }
        return ++lab().checks >= 30 ? Poll.done(note + ", 30 Prüfungen in Folge ohne Zuordnung") : Poll.waiting(note.toString());
    }

    private static Poll quiet(String key, String shotType, int ticks) {
        Mob mob = CovMdCLab.mob(key);
        if (CovMdCLab.assignedTo(mob)) {
            throw new IllegalStateException(format("%s in %.1f m der Drohne zugeordnet", key, CovMdCLab.flat(mob)));
        }
        List<CovMdCLab.Shot> shots = CovMdCLab.shots(shot -> shot.owner().equals(key) && shot.type().contains(shotType));
        if (!shots.isEmpty()) {
            throw new IllegalStateException(format("%s schießt aus %.1f m: %d × %s", key, CovMdCLab.flat(mob), shots.size(), shots.getFirst().type()));
        }
        long waited = CovMdCLab.now(mob.level().getServer()) - lab().since;
        String note = format("%s in %.1f m: %d Ticks ohne Zuordnung und ohne %s", key, CovMdCLab.flat(mob), waited, shotType);
        return waited >= ticks ? Poll.done(note) : Poll.waiting(note);
    }

    private static void closer(String key, double distance) {
        Mob mob = CovMdCLab.mob(key);
        CovMdCLab.teleport(mob, distance, 0.0, 0.0);
        lab().since = mob.level().getGameTime();
        lab().checks = 0;
    }

    private static void pool(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos ground = lab().ground;
        Arena.fill(level, ground.offset(-4, -2, -4), ground.offset(10, 0, 4), Blocks.WATER.defaultBlockState());
        Mob drowned = CovMdCLab.mob(server, "drowned", EntityTypes.DROWNED, 7.0, 1.5, -2.6);
        drowned.setBaby(false);
        drowned.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        drowned.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        CovMdCLab.mob(server, "zombie_nautilus", EntityTypes.ZOMBIE_NAUTILUS, 7.0, -1.5, -2.6);
        lab().since = CovMdCLab.now(server);
        lab().values.put("closest.drowned", Double.MAX_VALUE);
        lab().values.put("closest.zombie_nautilus", Double.MAX_VALUE);
    }

    private static Poll pooled(MinecraftServer server) {
        StringBuilder note = new StringBuilder();
        boolean all = true;
        for (String key : List.of("drowned", "zombie_nautilus")) {
            Mob mob = CovMdCLab.mob(key);
            double closest = Math.min((double) lab().values.get("closest." + key), CovMdCLab.flat(mob));
            lab().values.put("closest." + key, closest);
            boolean assigned = CovMdCLab.everAssigned(mob);
            int hits = CovMdCLab.hitsBy(key).size();
            all &= assigned && hits > 0 && closest <= 2.0;
            note.append(format("%s%s %s, im Wasser %s, nächster Abstand %.1f m, %d Treffer", note.isEmpty() ? "" : "; ", key, assigned ? "zugeordnet" : "frei",
                    mob.isInWater() ? "ja" : "nein", closest, hits));
        }
        if (DroneReactions.kind(CovMdCLab.mob("drowned")).filter("chase"::equals).isEmpty()) {
            throw new IllegalStateException("Ertrunkener ohne Dreizack hat die Art " + DroneReactions.kind(CovMdCLab.mob("drowned")).orElse("-"));
        }
        note.append(format(", Drohne %.1f m über dem Boden", CovMdCLab.height()));
        return all ? Poll.done(note.toString()) : Poll.waiting(note.toString());
    }

    private static void tridentFar(MinecraftServer server, Map<String, Object> state) {
        Mob drowned = CovMdCLab.mob(server, "drowned", EntityTypes.DROWNED, TRIDENT_FAR, 0.0, 0.0);
        drowned.setBaby(false);
        drowned.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        drowned.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        CovMdCLab.freeze(drowned);
        String kind = DroneReactions.kind(drowned).orElse("-");
        if (!"trident".equals(kind)) {
            throw new IllegalStateException("Ertrunkener mit Dreizack hat die Art " + kind + " statt trident");
        }
        CovMdCLab.watch(true);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll tridentNear(MinecraftServer server) {
        Mob drowned = CovMdCLab.mob("drowned");
        List<CovMdCLab.Shot> tridents = CovMdCLab.shots(shot -> shot.owner().equals("drowned") && shot.type().equals("trident") && shot.tick() >= lab().since);
        long aimed = tridents.stream().filter(shot -> shot.flatAim() < AIM_DEGREES && shot.toTarget().length() <= StageTwoWindows.TRIDENT_MAX + 1.0).count();
        long hits = CovMdCLab.hitsBy("drowned").stream().filter(hit -> hit.direct().equals("trident")).count();
        String note = format("Ertrunkener %s in %.1f m, %d Dreizacke (%d auf die Drohne gerichtet), %d Treffer, %s", CovMdCLab.everAssigned(drowned) ? "zugeordnet"
                : "frei", CovMdCLab.flat(drowned), tridents.size(), aimed, hits, CovMdCLab.causes());
        return aimed > 0 && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll piglinCalm(MinecraftServer server) {
        Mob piglin = CovMdCLab.mob("zombified_piglin");
        boolean angry = piglin instanceof NeutralMob neutral && neutral.isAngry();
        if (angry || CovMdCLab.assignedTo(piglin) || !CovMdCLab.hitsBy("zombified_piglin").isEmpty()) {
            throw new IllegalStateException(format("Piglin reagiert ohne Zorn (wütend %s, Treffer %s)", angry, CovMdCLab.causes()));
        }
        long waited = CovMdCLab.now(server) - lab().since;
        String note = format("Piglin %.1f m neben der Drohne, %d Ticks ruhig ohne Zuordnung", CovMdCLab.flat(piglin), waited);
        return waited >= QUIET_TICKS ? Poll.done(note) : Poll.waiting(note);
    }

    private static void provoke(MinecraftServer server, Map<String, Object> state) {
        Mob piglin = CovMdCLab.mob("zombified_piglin");
        Vec3 at = new Vec3(piglin.getX() + 2.0, lab().groundY, piglin.getZ());
        ServerPlayer guest = Guests.place(server, "CovMdC_Spieler", Arena.level(server), at, 90.0F);
        Guests.ready(guest, 20.0F);
        guest.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 600, 4, false, false), null);
        float health = piglin.getHealth();
        guest.attack(piglin);
        CovMdCLab.unfreeze(piglin);
        if (!(piglin.getHealth() < health)) {
            throw new IllegalStateException(format("Schlag des Testspielers trifft nicht (Gesundheit %.1f → %.1f)", health, piglin.getHealth()));
        }
        lab().values.put("guest", guest);
        lab().values.put("moved", false);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll piglinAngry(MinecraftServer server) {
        Mob piglin = CovMdCLab.mob("zombified_piglin");
        boolean angry = piglin instanceof NeutralMob neutral && neutral.isAngry();
        ServerPlayer guest = (ServerPlayer) lab().values.get("guest");
        if (angry && !(boolean) lab().values.get("moved")) {
            guest.snapTo(piglin.getX() + 14.0, lab().groundY, piglin.getZ(), 90.0F, 0.0F);
            guest.setDeltaMovement(Vec3.ZERO);
            lab().values.put("moved", true);
        }
        boolean assigned = CovMdCLab.everAssigned(piglin);
        int hits = CovMdCLab.hitsBy("zombified_piglin").size();
        String note = format("Piglin %s, %s, Ziel %s, %d Treffer an der Drohne", angry ? "wütend" : "ruhig", assigned ? "zugeordnet" : "frei",
                piglin.getTarget() == null ? "-" : piglin.getTarget().getName().getString(), hits);
        return angry && assigned && hits > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static List<CovMdCLab.Push> pushesSince(String nearest) {
        long since = lab().since;
        return CovMdCLab.pushes().stream().filter(push -> push.cause() == DroneKnockback.Cause.OTHER && push.scale() > 0.0 && push.tick() >= since
                && push.nearest().equals(nearest)).toList();
    }

    private static Poll roared(MinecraftServer server) {
        Mob ravager = CovMdCLab.mob("ravager");
        boolean assigned = CovMdCLab.everAssigned(ravager);
        List<CovMdCLab.Push> pushes = pushesSince("ravager");
        List<CovMdCLab.Push> roars = pushes.stream().filter(push -> push.nearestDistance() > 2.5 && push.horizontal() > 0.0
                && push.horizontal() >= 2.0 * Math.abs(push.delta().y)).toList();
        String note = format("Verwüster %s %.1f m, %d Stöße, davon %d waagerecht aus mehr als 2,5 m (Brüllen)%s", assigned ? "zugeordnet" : "frei",
                CovMdCLab.flat(ravager), pushes.size(), roars.size(), roars.isEmpty() ? "" : format(", Δ waagerecht %.2f, senkrecht %.2f, aus %.1f m",
                        roars.getFirst().horizontal(), roars.getFirst().delta().y, roars.getFirst().nearestDistance()));
        return assigned && !roars.isEmpty() ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll thrown(String key) {
        Mob mob = CovMdCLab.mob(key);
        boolean assigned = CovMdCLab.everAssigned(mob);
        int hits = (int) CovMdCLab.hitsBy(key).stream().filter(hit -> hit.tick() >= lab().since).count();
        List<CovMdCLab.Push> up = pushesSince(key).stream().filter(push -> push.delta().y > 0.0).toList();
        String note = format("%s %s %.1f m, %d Treffer, %d Stöße nach oben%s", key, assigned ? "zugeordnet" : "frei", CovMdCLab.flat(mob), hits, up.size(),
                up.isEmpty() ? "" : format(" (Δ senkrecht %.2f, waagerecht %.2f)", up.getFirst().delta().y, up.getFirst().horizontal()));
        return assigned && hits > 0 && !up.isEmpty() ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll archers(MinecraftServer server) {
        StringBuilder note = new StringBuilder();
        boolean all = true;
        for (String key : List.of("pillager", "illusioner", "piglin")) {
            Mob mob = CovMdCLab.mob(key);
            boolean assigned = CovMdCLab.everAssigned(mob);
            List<CovMdCLab.Shot> arrows = CovMdCLab.shots(shot -> shot.owner().equals(key) && shot.type().contains("arrow"));
            long aimed = arrows.stream().filter(shot -> shot.flatAim() < AIM_DEGREES).count();
            int hits = CovMdCLab.hitsBy(key).size();
            all &= assigned && aimed > 0;
            note.append(format("%s%s %s, %d Geschosse (%s), %d auf die Drohne gerichtet, %d Treffer", note.isEmpty() ? "" : "; ", key,
                    assigned ? "zugeordnet" : "frei", arrows.size(), arrows.isEmpty() ? "-" : arrows.getFirst().type(), aimed, hits));
        }
        return all ? Poll.done(note.toString()) : Poll.waiting(note.toString());
    }

    private static Poll splashed(MinecraftServer server) {
        Mob witch = CovMdCLab.mob("witch");
        List<CovMdCLab.Hit> hits = CovMdCLab.hitsBy("witch").stream().filter(hit -> hit.direct().contains("potion")).toList();
        List<CovMdCLab.Shot> potions = CovMdCLab.shots(shot -> shot.owner().equals("witch") && shot.type().contains("potion"));
        PotionReaction.Counts counts = PotionReaction.counts();
        String note = format("Hexe %s, %d Tränke, %d Treffer, letztes Platzen %.2f m von der Drohne", CovMdCLab.everAssigned(witch) ? "zugeordnet" : "frei",
                potions.size(), hits.size(), counts.lastDistance());
        if (hits.isEmpty()) {
            return Poll.waiting(note);
        }
        CovMdCLab.Hit hit = hits.getFirst();
        double distance = counts.lastDistance();
        double expected = StageTwoWindows.potionDamage(distance);
        if (!(distance <= StageTwoWindows.POTION_RADIUS) || Math.abs(hit.original() - expected) > 0.05) {
            throw new IllegalStateException(format("Trank-Schaden %.2f bei %.2f m, erwartet %.2f (6 × (1 − d/4), nur bis 2 m)", hit.original(), distance, expected));
        }
        return Poll.done(note + format(", Schaden %.2f = 6 × (1 − %.2f/4)", hit.original(), distance));
    }

    private static Poll evokerQuiet(MinecraftServer server) {
        Mob evoker = CovMdCLab.mob("evoker");
        ServerLevel level = Arena.level(server);
        int fangs = CovMdCLab.owned(level, EvokerFangs.class, evoker).size();
        int vexes = level.getEntitiesOfClass(Vex.class, Arena.box(level), vex -> vex.getOwner() == evoker).size();
        if (fangs > 0 || vexes > 0) {
            throw new IllegalStateException(format("Magier in %.1f m zaubert (%d Fangzähne, %d Plagegeister)", CovMdCLab.flat(evoker), fangs, vexes));
        }
        return quiet("evoker", "fangs", QUIET_TICKS);
    }

    private static Poll fanged(MinecraftServer server) {
        Mob evoker = CovMdCLab.mob("evoker");
        ServerLevel level = Arena.level(server);
        List<EvokerFangs> fangs = CovMdCLab.owned(level, EvokerFangs.class, evoker);
        double nearest = fangs.stream().mapToDouble(CovMdCLab::flat).min().orElse(Double.NaN);
        if (!Double.isNaN(nearest)) {
            lab().values.merge("fangs.nearest", nearest, (a, b) -> Math.min((double) a, (double) b));
        }
        double best = (double) lab().values.getOrDefault("fangs.nearest", Double.NaN);
        long bites = CovMdCLab.hitsBy("evoker").stream().filter(hit -> hit.direct().equals("evoker_fangs")).count();
        String note = format("Magier %s, %d Fangzähne, nächster %.1f m unter der Drohne, %d Bisse, Drohne %.1f m hoch", CovMdCLab.everAssigned(evoker) ? "zugeordnet"
                : "frei", fangs.size(), best, bites, CovMdCLab.height());
        return best <= 1.0 && bites > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    @SuppressWarnings("unchecked")
    private static Poll vexed(MinecraftServer server) {
        Mob evoker = CovMdCLab.mob("evoker");
        ServerLevel level = Arena.level(server);
        DroneState state = CovMdCLab.state();
        long now = CovMdCLab.now(server);
        Set<UUID> seen = (Set<UUID>) lab().values.computeIfAbsent("fangs.seen", key -> new HashSet<UUID>());
        List<EvokerFangs> fangs = CovMdCLab.owned(level, EvokerFangs.class, evoker);
        if (state.height() > StageTwoWindows.CASTER_FANGS_HEIGHT + 0.2) {
            lab().values.putIfAbsent(SINCE_HIGH, now);
        }
        Object high = lab().values.get(SINCE_HIGH);
        for (EvokerFangs fang : fangs) {
            if (seen.add(fang.getUUID()) && high != null && now - (long) high > FANGS_GRACE) {
                throw new IllegalStateException(format("neue Fangzähne, obwohl die Drohne %.1f m hoch fliegt", state.height()));
            }
        }
        List<Vex> vexes = level.getEntitiesOfClass(Vex.class, Arena.box(level), vex -> vex.getOwner() == evoker);
        for (Vex vex : vexes) {
            Arena.track(vex);
        }
        long hunting = vexes.stream().filter(CovMdCLab::assignedTo).count();
        String note = format("Drohne %.1f m hoch, %d Plagegeister, %d jagen die Drohne, Treffer %s", state.height(), vexes.size(), hunting, CovMdCLab.causes());
        return high != null && now - (long) high > FANGS_GRACE && hunting > 0 ? Poll.done(note) : Poll.waiting(note);
    }

    private static void creeper(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos ground = lab().ground;
        lab().values.put("blocks", CovMdCLab.solidBlocks(level, ground.offset(-8, -5, -8), ground.offset(8, 4, 8)));
        CovMdCLab.mob(server, "creeper", EntityTypes.CREEPER, 7.0, 0.0, 0.0);
        lab().since = CovMdCLab.now(server);
    }

    private static Poll noCrater(MinecraftServer server) {
        Mob creeper = lab().mobs.get("creeper");
        boolean gone = creeper == null || creeper.isRemoved();
        List<CovMdCLab.Push> blasts = CovMdCLab.pushes().stream().filter(push -> push.cause() == DroneKnockback.Cause.EXPLOSION).toList();
        String note = format("Creeper %s, %d Explosionsstöße%s", gone ? "explodiert" : format("noch da (%.1f m)", CovMdCLab.flat(creeper)), blasts.size(),
                blasts.isEmpty() ? "" : format(" (Skala %.1f)", blasts.getFirst().scale()));
        if (!gone || blasts.isEmpty()) {
            return Poll.waiting(note);
        }
        ServerLevel level = Arena.level(server);
        BlockPos ground = lab().ground;
        int before = (int) lab().values.get("blocks");
        int after = CovMdCLab.solidBlocks(level, ground.offset(-8, -5, -8), ground.offset(8, 4, 8));
        if (after != before || blasts.getFirst().scale() != 1.0) {
            throw new IllegalStateException(format("Blöcke %d → %d, Skala %.1f", before, after, blasts.getFirst().scale()));
        }
        return Poll.done(note + format(", Blöcke %d → %d", before, after));
    }

    private static void cube(MinecraftServer server, String key, net.minecraft.world.entity.EntityType<? extends AbstractCubeMob> type, double dx, double dz) {
        AbstractCubeMob cube = CovMdCLab.mob(server, key, type, dx, dz, 0.0);
        cube.setSize(2, true);
    }

    private static Poll sulfur(MinecraftServer server) {
        Mob cube = CovMdCLab.mob("sulfur_cube");
        boolean assigned = CovMdCLab.everAssigned(cube);
        List<CovMdCLab.Push> bumps = pushesSince("sulfur_cube");
        int hits = CovMdCLab.hitsBy("sulfur_cube").size();
        if (hits > 0) {
            throw new IllegalStateException("friedlicher Schwefelwürfel schadet der Drohne: " + CovMdCLab.causes());
        }
        String note = format("Schwefelwürfel %s %.1f m, %d Stöße, kein Schaden", assigned ? "zugeordnet" : "frei", CovMdCLab.flat(cube), bumps.size());
        return assigned && !bumps.isEmpty() ? Poll.done(note) : Poll.waiting(note);
    }

    private static void wall(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = CovMdCLab.drone();
        AABB box = drone.getBoundingBox();
        double half = box.getXsize() / 2.0;
        int wallX = (int) Math.floor(drone.getX() + half + 0.5);
        double x = wallX - half - WALL_GAP;
        BlockPos base = new BlockPos(wallX, lab().ground.getY() + 1, (int) Math.floor(drone.getZ()));
        Arena.fill(level, base.offset(0, 0, -2), base.offset(0, 12, 2), Blocks.STONE.defaultBlockState());
        CovMdCLab.start(server, drone, x, lab().holdY, drone.getZ(), false);
        CovMdCLab.teleport(CovMdCLab.mob("spider"), -6.0, 2.0, 0.0);
        CovMdCLab.teleport(CovMdCLab.mob("cave_spider"), -6.0, -2.0, 0.0);
        lab().since = CovMdCLab.now(server);
        lab().values.put("wall.x", wallX);
        lab().values.put("max.spider", 0.0);
        lab().values.put("max.cave_spider", 0.0);
        lab().values.put("climb.spider", false);
        lab().values.put("climb.cave_spider", false);
    }

    private static Poll climbed(MinecraftServer server) {
        StringBuilder note = new StringBuilder(format("Drohne %.1f m hoch, %.2f m vor der Wand", CovMdCLab.height(),
                (int) lab().values.get("wall.x") - CovMdCLab.drone().getBoundingBox().maxX));
        boolean all = true;
        int hitsHigh = 0;
        for (String key : List.of("spider", "cave_spider")) {
            Mob mob = CovMdCLab.mob(key);
            double up = mob.getY() - lab().groundY;
            double max = Math.max((double) lab().values.get("max." + key), up);
            lab().values.put("max." + key, max);
            boolean climbing = mob instanceof Spider spider && spider.isClimbing() && up > 0.5;
            if (climbing) {
                lab().values.put("climb." + key, true);
            }
            boolean assigned = CovMdCLab.everAssigned(mob);
            long hits = CovMdCLab.hitsBy(key).stream().filter(hit -> hit.tick() >= lab().since).count();
            hitsHigh += (int) hits;
            all &= assigned && max >= CLIMB_HEIGHT && (boolean) lab().values.get("climb." + key);
            note.append(format("; %s %s, höchstens %.1f m, klettert %s, %d Treffer (waagerecht %.2f m zur Drohne, %.2f m zur Wand, am Boden %s, Stoß %s, "
                            + "Weg %s)", key, assigned ? "zugeordnet" : "frei", max, (boolean) lab().values.get("climb." + key) ? "ja" : "nein", hits,
                    CovMdCLab.flat(mob), (int) lab().values.get("wall.x") - mob.getBoundingBox().maxX, mob.onGround() ? "ja" : "nein",
                    mob.horizontalCollision ? "ja" : "nein", mob.getNavigation().isDone() ? "fertig" : "läuft"));
        }
        return all && hitsHigh > 0 ? Poll.done(note.toString()) : Poll.waiting(note.toString());
    }

    private static void guardian(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos ground = lab().ground;
        Arena.fill(level, ground.offset(-3, -2, -3), ground.offset(9, 0, 3), Blocks.WATER.defaultBlockState());
        CovMdCLab.mob(server, "guardian", EntityTypes.GUARDIAN, 6.0, 0.0, -1.8);
        lab().since = CovMdCLab.now(server);
        lab().mark = -1;
    }

    private static Poll beamed(MinecraftServer server) {
        Mob guardian = CovMdCLab.mob("guardian");
        long now = CovMdCLab.now(server);
        if (lab().mark < 0 && CovMdCLab.assignedTo(guardian)) {
            lab().mark = now;
        }
        List<CovMdCLab.Hit> hits = CovMdCLab.hitsBy("guardian");
        String note = format("Wächter %s, im Wasser %s, Laser seit %d Ticks, %d Treffer", lab().mark < 0 ? "frei" : "zugeordnet", guardian.isInWater() ? "ja"
                : "nein", lab().mark < 0 ? 0 : now - lab().mark, hits.size());
        if (hits.isEmpty()) {
            return Poll.waiting(note);
        }
        if (lab().mark < 0) {
            throw new IllegalStateException("Treffer vor der beobachteten Zuordnung: " + note);
        }
        long delay = hits.getFirst().tick() - lab().mark;
        if (delay < BEAM_DELAY) {
            throw new IllegalStateException("erster Laser-Schaden schon nach " + delay + " Ticks");
        }
        return Poll.done(note + format(", erster Schaden %d Ticks nach Beginn (Ursache %s/%s)", delay, hits.getFirst().cause(), hits.getFirst().direct()));
    }

    private static CameraPose pose() {
        return PropwashApi.drones().cameraPose(CovMdCLab.drone()).orElseThrow(() -> new IllegalStateException("Drohne ohne Kamerablick (cameraPose leer)"));
    }

    private static Vec3 flatLook() {
        Vec3 look = pose().look();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        return flat.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
    }

    private static void behind(MinecraftServer server, String key, double metres, boolean create) {
        Vec3 flat = flatLook();
        Entity drone = CovMdCLab.drone();
        Vec3 at = new Vec3(drone.getX() - flat.x * metres, lab().groundY, drone.getZ() - flat.z * metres);
        Mob mob;
        if (create) {
            mob = CovMdCLab.mobAt(server, key, EntityTypes.CREAKING, at, drone.position());
        } else {
            mob = CovMdCLab.mob(key);
            CovMdCLab.teleport(mob, at.x - drone.getX(), at.z - drone.getZ(), 0.0);
        }
        lab().values.put("start", CovMdCLab.flat(mob));
        lab().since = CovMdCLab.now(server);
        lab().checks = 0;
    }

    private static void intoGaze() {
        Mob mob = CovMdCLab.mob("creaking");
        Vec3 flat = flatLook();
        CovMdCLab.teleport(mob, flat.x * GAZE_MIN, flat.z * GAZE_MIN, 0.0);
        lab().spot = null;
        lab().since = -1;
        lab().checks = 0;
    }

    private static Poll creakingCloser(double metres) {
        Creaking creaking = (Creaking) CovMdCLab.mob("creaking");
        double start = (double) lab().values.get("start");
        double distance = CovMdCLab.flat(creaking);
        boolean assigned = CovMdCLab.everAssigned(creaking);
        String note = format("Knarz %s, Abstand %.1f → %.1f m, %s, Kamerablick %s", assigned ? "zugeordnet" : "frei", start, distance,
                creaking.canMove() ? "beweglich" : "erstarrt", StareReaction.frozen(creaking) ? "ja" : "nein");
        return assigned && start - distance >= metres ? Poll.done(note) : Poll.waiting(note);
    }

    private static Poll creakingStill(MinecraftServer server) {
        Creaking creaking = (Creaking) CovMdCLab.mob("creaking");
        long now = CovMdCLab.now(server);
        double distance = CovMdCLab.flat(creaking);
        String note = format("Knarz %s, Kamerablick %s, Abstand %.2f m", creaking.canMove() ? "beweglich" : "erstarrt", StareReaction.frozen(creaking) ? "ja" : "nein",
                distance);
        if (creaking.canMove()) {
            lab().since = -1;
            return Poll.waiting(note);
        }
        if (lab().since < 0) {
            lab().since = now;
            lab().values.put("still", distance);
            lab().spot = creaking.position();
            return Poll.waiting(note);
        }
        double start = (double) lab().values.get("still");
        Vec3 spot = lab().spot == null ? creaking.position() : lab().spot;
        if (Math.abs(distance - start) > 0.25 || creaking.position().distanceTo(spot) > 0.25) {
            throw new IllegalStateException(format("Knarz bewegt sich unter dem Kamerablick: %.2f → %.2f m", start, distance));
        }
        return now - lab().since >= 60 ? Poll.done(note + format(", %d Ticks ohne Bewegung", now - lab().since)) : Poll.waiting(note);
    }

    private static Poll skulls(MinecraftServer server) {
        Mob wither = CovMdCLab.mob("wither");
        List<CovMdCLab.Shot> skulls = CovMdCLab.shots(shot -> shot.owner().equals("wither") && shot.type().equals("wither_skull") && shot.tick() >= lab().since);
        long aimed = skulls.stream().filter(shot -> shot.aim() < AIM_DEGREES && shot.toTarget().length() <= StageTwoWindows.BOSS_MAX).count();
        long blasts = CovMdCLab.pushes().stream().filter(push -> push.cause() == DroneKnockback.Cause.EXPLOSION).count();
        String note = format("Wither %s in %.1f m, %d Schädel (%d auf die Drohne gerichtet, Abstand %s), %d Treffer, %d Explosionsstöße",
                CovMdCLab.everAssigned(wither) ? "zugeordnet" : "frei", CovMdCLab.flat(wither), skulls.size(), aimed,
                skulls.isEmpty() ? "-" : format("%.1f m, Winkel %.1f°", skulls.getFirst().toTarget().length(), skulls.getFirst().aim()),
                CovMdCLab.hitsBy("wither").size(), blasts);
        return aimed > 0 ? Poll.done(note) : Poll.waiting(note);
    }
}
