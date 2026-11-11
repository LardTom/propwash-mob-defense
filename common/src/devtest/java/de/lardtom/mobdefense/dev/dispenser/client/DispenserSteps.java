package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.booster.BoosterRig;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.ExpectedRecoil;
import de.lardtom.mobdefense.dev.dispenser.Guests;
import de.lardtom.mobdefense.dev.dispenser.MotionSampler;
import de.lardtom.mobdefense.dev.dispenser.PvpMatrix;
import de.lardtom.mobdefense.dev.dispenser.Rules;
import de.lardtom.mobdefense.dispenser.DispenserAmmo;
import de.lardtom.mobdefense.client.OsdNotices;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.dispenser.DispenserNotices;
import de.lardtom.mobdefense.dispenser.DispenserState;
import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;
import de.lardtom.mobdefense.payload.Payloads;
import de.lardtom.mobdefense.payload.TntGuard;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.DispenseResult;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class DispenserSteps {
    public static final double MAX_SINGLE_DEGREES = 10.0;
    public static final double MAX_MEAN_DEGREES = 5.0;
    public static final int MEAN_SHOTS = 3;
    public static final double MAX_FLYABLE_TILT_DEGREES = 30.0;
    public static final int FLYABLE_TICKS = 40;
    private static final String COUNT = "dispenser.count";
    private static final String MATRIX = "dispenser.matrix";
    private static final String TNT_BLOCKS = "dispenser.tnt.blocks";
    private static final String NOTES = "dispenser.notes";
    private static final String LAST = "dispenser.last";
    private static final String NOTICE = "dispenser.notice";
    private static final String SAMPLER = "dispenser.sampler";
    private static final String SHOTS = "dispenser.shots";
    private static final String MASS = "dispenser.mass";
    private static final int WATER_REACH = 4;
    private static final int PLATFORM_RADIUS = 5;
    private static final int PLATFORM_HEIGHT = 14;
    private static final int DROP_HEIGHT = 18;
    private static final int SERVICE_ACCESSORY_BUTTON = 3000;
    private static final String PLATFORM = "dispenser.platform.";
    private static final String TAMED = "dispenser.tamed";
    private static final String S13_CONTENTS = "dispenser.s13.contents";
    private static final String S13_ROCKET = "dispenser.s13.rocket";
    private static final String S13_FOUND = "dispenser.s13.found";
    private static final int S13_FLIGHT = 2;
    private static final String DISTURBED_DEGREES = "45";
    private static final CompassProperty DISTURBED_COMPASS = new CompassProperty(DISTURBED_DEGREES);
    private static final int COMPASS_HEIGHT = 12;

    private DispenserSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("S2 Dispenser beladen und feuern – Pfeil, Heiltrank, Windkugel, TNT in Slots 1–4, Richtung, Verbrauch, Rückstoß", 4000,
                Script.create()
                        .server("5″ mit Dispenser setzen und beladen", (server, state) -> {
                            Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "S2 Dispenser", Arena.ground(0, -6), 180.0F);
                            DispenserRig.load(drone, new ItemStack(Items.ARROW, 16), potion(Items.SPLASH_POTION, Potions.HEALING),
                                    new ItemStack(Items.WIND_CHARGE, 16), new ItemStack(Items.TNT, 4));
                        })
                        .append(service("dispenser_service"))
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(4.0))
                        .then("Slot 1 am Client", (ctx, ticks) -> osd(ticks, 0, "ARW", 16))
                        .append(Shots.photos("dispenser_fpv_osd"))
                        .append(fire("Dauerfeuer Pfeile", 16, 2, 4, entity -> entity instanceof AbstractArrow, 0.44))
                        .then("Pfeile verbraucht", (ctx, ticks) -> slotCount(ctx, 0, 16))
                        .append(Pilot.press(Pilot.ACTION_2, 2))
                        .then("Aktion 2 wählt Slot 2", (ctx, ticks) -> selected(ticks, 1))
                        .append(fire("Heiltrank", 1, 1, 1, entity -> entity instanceof AbstractThrownPotion, 0.35 * 27.5))
                        .append(fire("Windkugel aus dem nächsten belegten Slot", 1, 1, 1, entity -> entity instanceof WindCharge, ExpectedRecoil.windChargeNs()))
                        .then("Windkugel stößt die 5″ zurück", (ctx, ticks) -> pushedBack(ctx, ticks, 0.5))
                        .append(Pilot.press(Pilot.ACTION_2, 2))
                        .then("Aktion 2 wählt Slot 4", (ctx, ticks) -> selected(ticks, 3))
                        .append(fire("TNT", 1, 1, 1, entity -> entity instanceof PrimedTnt, 0.0))
                        .server("TNT fällt mit der Drohne und wird entschärft", DispenserSteps::defuse)
                        .then("Slot 4 am Client", (ctx, ticks) -> osd(ticks, 3, "TNT", 3))
                        .append(Shots.photos("dispenser_fpv")),
                CLEANUP);
        steps.add("S3 Eimer gesperrt – Wassereimer REFUSED mit Warnung, mit Regel wird Wasser gesetzt", 3000,
                Script.create()
                        .server("5″ mit Wassereimer", (server, state) -> {
                            Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "S3 Eimer", Arena.ground(0, -6), 180.0F);
                            DispenserRig.load(drone, new ItemStack(Items.WATER_BUCKET));
                        })
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(3.0))
                        .run("Position halten", () -> Pilot.drone().ifPresent(d -> HoverAssist.hold(d.getX(), HoverAssist.targetY(), d.getZ())))
                        .append(fire("Wassereimer ohne Regel", 1, 1, 1, null, 0.0))
                        .then("Ergebnis BUCKET_LOCKED", (ctx, ticks) -> lastOutcome(ctx, FireGate.Outcome.BUCKET_LOCKED))
                        .then("Warnung im OSD", (ctx, ticks) -> notice(ctx, ticks, FireGate.Notice.BUCKET_LOCKED))
                        .append(Shots.photos("dispenser_bucket_locked"))
                        .server("mit allow_fluid_buckets wird Wasser gesetzt", DispenserSteps::water)
                        .then("Wasser", (ctx, ticks) -> StepResult.pass(String.valueOf(ctx.state().get(NOTES + ".water")))),
                CLEANUP);
        steps.add("S4 Windkugel-Rückstoß – schwebender 3″-Cinewhoop wird spürbar zurückgestoßen und bleibt fliegbar", 3000,
                Script.create()
                        .server("Cinewhoop mit Windkugeln", (server, state) -> {
                            Entity drone = Pilot.spawn(server, state, DispenserRig.cinewhoop(), "S4 Cinewhoop", Arena.ground(0, -6), 180.0F);
                            DispenserRig.load(drone, new ItemStack(Items.WIND_CHARGE, 2));
                        })
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(4.0))
                        .append(fire("Windkugel", 1, 1, 1, entity -> entity instanceof WindCharge, ExpectedRecoil.windChargeNs()))
                        .then("Cinewhoop spürbar zurück, kein Überschlag, Neigung unter 30°", DispenserSteps::staysFlyable)
                        .append(Shots.photos("dispenser_wind")),
                CLEANUP);
        steps.add("S5 Tränke – Wurf-Heiltrank schadet einem Zombie, Verweiltrank legt eine Wolke", 2000,
                Script.create()
                        .server("Drohne auf einem Sockel, Zombie davor", DispenserSteps::potionRange)
                        .server("Heiltrank auf den Zombie", (server, state) -> shoot(server, state, potion(Items.SPLASH_POTION, Potions.HEALING)))
                        .sleep(20)
                        .server("Zombie verletzt", DispenserSteps::zombieHurt)
                        .server("Verweiltrank", (server, state) -> shoot(server, state, potion(Items.LINGERING_POTION, Potions.REGENERATION)))
                        .sleep(30)
                        .server("Wolke liegt", DispenserSteps::cloud)
                        .append(Shots.photos("dispenser_cloud")),
                CLEANUP);
        steps.add("S6 Payloads gegen Spieler – zweiter Spieler: Regel aus kein Schaden, Heiltrank heilt; Regel an mit PvP Schaden", 1500,
                Script.create()
                        .server("Bahnen mit Testspielern", DispenserSteps::matrix)
                        .then("alle vier Kombinationen", (ctx, ticks) -> matrixDone(ctx, ticks)),
                CLEANUP);
        steps.add("S7 TNT und Griefing – Regel aus REFUSED, TNT fällt mit Drohnentempo, ohne drone_griefing kein Krater", 8000,
                Script.create()
                        .server("5″ mit TNT", DispenserSteps::tntRange)
                        .server("allow_tnt aus", (server, state) -> new Rules(server).set(RuleDefaults.ALLOW_TNT, false))
                        .append(Pilot.fly())
                        .append(fire("TNT ohne Regel", 1, 1, 1, null, 0.0))
                        .then("Ergebnis TNT_LOCKED", (ctx, ticks) -> lastOutcome(ctx, FireGate.Outcome.TNT_LOCKED))
                        .then("Warnung im OSD", (ctx, ticks) -> notice(ctx, ticks, FireGate.Notice.TNT_LOCKED))
                        .append(Shots.photos("dispenser_tnt_locked"))
                        .server("allow_tnt an", (server, state) -> new Rules(server).set(RuleDefaults.ALLOW_TNT, true))
                        .run("Steigen", () -> Pilot.drone().ifPresent(d -> HoverAssist.hover(d.getY() + DROP_HEIGHT + PLATFORM_HEIGHT)))
                        .then("scharf schalten", Pilot::arm)
                        .then("Steigflug", (ctx, ticks) -> climbed(ticks, DROP_HEIGHT + PLATFORM_HEIGHT - 2))
                        .run("Vorwärtsflug", () -> cruise(6.0))
                        .then("Tempo erreicht", (ctx, ticks) -> speed(ticks, 4.0))
                        .server("Tempo am Server messen", (server, state) -> state.put(SAMPLER, MotionSampler.start(Pilot.serverDrone(server, state), 40)))
                        .then("Tempo am Server", DispenserSteps::sampled)
                        .append(fire("TNT im Vorwärtsflug (Dauerfeuer)", 16, 2, 4, entity -> entity instanceof PrimedTnt, 0.0))
                        .server("TNT erbt das Drohnentempo", DispenserSteps::tntSpeed)
                        .then("TNT-Tempo", (ctx, ticks) -> tntNote(ctx))
                        .server("drone_griefing aus", (server, state) -> new Rules(server).set(RuleDefaults.DRONE_GRIEFING, false))
                        .append(bomb(0, false))
                        .run("seitlich weiter", () -> cruise(5.0))
                        .sleep(80)
                        .server("drone_griefing an", (server, state) -> new Rules(server).set(RuleDefaults.DRONE_GRIEFING, true))
                        .append(bomb(1, true))
                        .run("Flug beenden", () -> Pilot.land(Minecraft.getInstance()))
                        .server("Funke aus", (server, state) -> Pilot.release(server))
                        .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                        .server("Blick auf beide Plattformen", DispenserSteps::lookAtPlatforms)
                        .append(Shots.photos("dispenser_crater")),
                CLEANUP);
        steps.add("Kompass fest – auch wenn Propwash einen Kompassfehler von 45° würfelt, startet der Selbsttest-Flug mit festem Kompass und die 5″ mit TNT hält nach 6 m/s Vorwärtsflug still", 3000,
                Script.create()
                        .run("Propwash würfelt " + DISTURBED_DEGREES + "° Kompassfehler", DISTURBED_COMPASS::set)
                        .server("5″ mit TNT", (server, state) -> {
                            Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "Kompass", Arena.ground(0, -6), 180.0F);
                            DispenserRig.load(drone, new ItemStack(Items.TNT, 2));
                        })
                        .append(Pilot.fly())
                        .run("Steigen", () -> Pilot.drone().ifPresent(d -> HoverAssist.hover(d.getY() + COMPASS_HEIGHT)))
                        .then("scharf schalten", Pilot::arm)
                        .then("Steigflug", (ctx, ticks) -> climbed(ticks, COMPASS_HEIGHT - 2))
                        .run("Vorwärtsflug", () -> cruise(6.0))
                        .then("Tempo erreicht", (ctx, ticks) -> speed(ticks, 4.0))
                        .sleep(100)
                        .run("auf der Stelle halten", () -> Pilot.drone().ifPresent(d -> HoverAssist.hold(d.getX(), HoverAssist.targetY(), d.getZ())))
                        .then("ruhig", (ctx, ticks) -> calm(ticks))
                        .then("Kompass des Flugs", (ctx, ticks) -> steadyCompass()),
                cleanup().run("Kompassfehler wieder gewürfelt", DISTURBED_COMPASS::restore));
        steps.add("S13 Speichern und Aufheben – Munition, gewählter Slot und Rakete bleiben über Neustart, Aufheben und Werkbank", 6000,
                Script.create()
                        .server("5″ mit Dispenser und Raketen-Booster laden, Slot 2 wählen", DispenserSteps::loadForRestart)
                        .append(WorldRestart.script())
                        .then("Drohne nach dem Neustart geladen", DispenserSteps::reloaded)
                        .server("nach dem Neustart am Server", DispenserSteps::afterRestart)
                        .then("nach dem Neustart am Client", DispenserSteps::clientAfterRestart)
                        .server("aufheben, wieder abstellen, Werkbank", DispenserSteps::selectAndKeep)
                        .then("Ergebnis", (ctx, ticks) -> StepResult.pass(String.join("; ", s13Notes(ctx.state())))),
                CLEANUP);
        UnderFrameSteps.register(steps);
    }

    static ItemStack potion(Item item, Holder<Potion> potion) {
        return PotionContents.createItemStack(item, potion);
    }

    private static final SelfTest.ClientStep CLEANUP = cleanup();

    private static Script cleanup() {
        return Script.create()
                .run("Tasten los, Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
                .server("Flug beenden, Testspieler weg", (server, state) -> {
                    Pilot.release(server);
                    Guests.removeAll();
                });
    }

    private static Script service(String photo) {
        return Script.create()
                .server("Wartung öffnen (Schleichen und Rechtsklick)", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    Entity drone = Pilot.serverDrone(server, state);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setShiftKeyDown(true);
                    player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
                    player.setShiftKeyDown(false);
                })
                .await("Wartung am Client", 100, () -> Minecraft.getInstance().gui.screen() != null)
                .then("Knopf Zubehör", DispenserSteps::accessoryButton)
                .server("Zubehör öffnen", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    if (!player.containerMenu.clickMenuButton(player, SERVICE_ACCESSORY_BUTTON)) {
                        throw new IllegalStateException("Wartung nimmt den Knopf Zubehör nicht an");
                    }
                })
                .then("Zubehör-Inventar", DispenserSteps::accessoryScreen)
                .append(Shots.photos(photo))
                .run("Wartung schließen", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    mc.gui.setScreen(null);
                })
                .server("Menü am Server zu", (server, state) -> Arena.player(server).closeContainer())
                .sleep(5);
    }

    private static StepResult accessoryButton(SelfTest.StepContext ctx, int ticks) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return StepResult.fail("Wartung geschlossen");
        }
        String label = I18n.get("propwash.accessory.service.button");
        ctx.state().put("dispenser.service.screen", screen.getClass().getName());
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(label)) {
                return StepResult.pass("Knopf „" + label + "“ in " + screen.getClass().getSimpleName());
            }
        }
        return ticks > 40 ? StepResult.fail("Knopf „" + label + "“ fehlt in " + screen.getClass().getSimpleName()) : StepResult.pending();
    }

    private static StepResult accessoryScreen(SelfTest.StepContext ctx, int ticks) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null && !screen.getClass().getName().equals(ctx.state().get("dispenser.service.screen"))) {
            return StepResult.pass("Zubehör-Inventar offen: " + screen.getClass().getSimpleName());
        }
        return ticks > 100 ? StepResult.fail("Zubehör-Inventar öffnet nicht") : StepResult.pending();
    }

    private static Script fire(String label, int holdTicks, int minShots, int maxShots, @Nullable Predicate<Entity> kind, double recoilNs) {
        return Script.create()
                .server(label + ": vorher", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    state.put(COUNT, DispenserLog.counts(drone).attempts());
                    state.put(MASS, DispenserRig.view(drone).massKg());
                })
                .run(label + ": Rückstoß messen", () -> Pilot.drone().flatMap(d -> PropwashApi.drones().view(d))
                        .ifPresent(view -> HoverAssist.track(view.forward().x, view.forward().z)))
                .append(holdTicks > 1 ? Pilot.press(Pilot.ACTION_1, holdTicks) : Script.create()
                        .server(label + ": Aktion 1 am Server", (server, state) -> {
                            Entity drone = Pilot.serverDrone(server, state);
                            ServerPlayer player = Arena.player(server);
                            PropwashApi.accessories().triggerAction(drone, 1, true, player);
                            PropwashApi.accessories().triggerAction(drone, 1, false, player);
                        }))
                .sleep(4)
                .server(label + ": prüfen", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    int before = (int) state.get(COUNT);
                    int now = DispenserLog.counts(drone).attempts();
                    List<DispenserLog.Entry> history = DispenserLog.history(drone);
                    List<DispenserLog.Entry> shots = history.subList(Math.max(0, history.size() - (now - before)), history.size());
                    state.put(SHOTS, List.copyOf(shots));
                    state.put(LAST, shots.isEmpty() ? "keine Reaktion" : shots.getLast().status());
                    state.put(NOTICE, shots.isEmpty() ? "keiner" : shots.getLast().notice().name());
                    state.put(NOTES + "." + label, check(label, shots, minShots, maxShots, kind, recoilNs));
                })
                .then(label, (ctx, ticks) -> StepResult.pass((String) ctx.state().get(NOTES + "." + label)));
    }

    static String check(String label, List<DispenserLog.Entry> shots, int minShots, int maxShots, @Nullable Predicate<Entity> kind, double recoilNs) {
        if (shots.size() < minShots || shots.size() > maxShots) {
            throw new IllegalStateException(label + ": " + shots.size() + " Schüsse statt " + minShots + "–" + maxShots);
        }
        if (kind == null) {
            return label + ": " + shots.getLast().status();
        }
        double sumX = 0.0;
        double sumY = 0.0;
        double sumZ = 0.0;
        double worst = 0.0;
        Vec3 forward = shots.getFirst().forward().normalize();
        List<String> statuses = new ArrayList<>();
        for (DispenserLog.Entry shot : shots) {
            DispenseResult result = shot.result();
            if (result == null || !result.success() || result.entity().isEmpty() || !kind.test(result.entity().get())) {
                throw new IllegalStateException(label + ": " + shot.status() + " " + (result == null ? "" : result.entity()));
            }
            statuses.add(shot.status());
            if (recoilNs > 0.0 && !ExpectedRecoil.matches(result.recoilNs(), recoilNs)) {
                throw new IllegalStateException(String.format(Locale.ROOT, "%s: Rückstoß %.2f N·s statt %.2f", label, result.recoilNs(), recoilNs));
            }
            if (result.entity().get() instanceof PrimedTnt) {
                continue;
            }
            Vec3 own = shot.launch().subtract(shot.droneVelocity().scale(1.0 / 20.0));
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, own.normalize().dot(shot.forward().normalize())))));
            worst = Math.max(worst, angle);
            if (angle > MAX_SINGLE_DEGREES) {
                throw new IllegalStateException(String.format(Locale.ROOT, "%s: Geschoss %.1f° neben forward", label, angle));
            }
            Vec3 unit = own.normalize();
            sumX += unit.x;
            sumY += unit.y;
            sumZ += unit.z;
        }
        double mean = sumX == 0.0 && sumY == 0.0 && sumZ == 0.0 ? 0.0
                : Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, new Vec3(sumX, sumY, sumZ).normalize().dot(forward)))));
        if (shots.size() >= MEAN_SHOTS && mean > MAX_MEAN_DEGREES) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s: Mittel %.1f° neben forward", label, mean));
        }
        return String.format(Locale.ROOT, "%s: %d× %s, Richtung im Mittel %.1f° (höchstens %.1f°) von forward, Rückstoß %.2f N·s", label, shots.size(),
                String.join("/", statuses.stream().distinct().toList()), mean, worst, shots.getLast().result().recoilNs());
    }

    private static StepResult osd(int ticks, int slot, String code, int count) {
        Entity drone = Pilot.drone().orElse(null);
        if (drone == null) {
            return StepResult.fail("keine Drohne am Client");
        }
        List<ItemStack> contents = PropwashApi.accessories().contents(drone, AccessorySlot.FRONT);
        boolean[] filled = new boolean[SlotCycle.SLOTS];
        for (int i = 0; i < Math.min(SlotCycle.SLOTS, contents.size()); i++) {
            filled[i] = !contents.get(i).isEmpty();
        }
        int firing = SlotCycle.firing(DispenserState.selection(drone).selected(), filled);
        String seen = firing < 0 ? "leer" : "Slot " + (firing + 1) + " " + DispenserAmmo.code(contents.get(firing)) + " " + contents.get(firing).getCount();
        String expected = "Slot " + (slot + 1) + " " + code + " " + count;
        if (seen.equals(expected)) {
            return StepResult.pass("Client sieht " + seen + " (Daten für mobdefense:dispenser im OSD)");
        }
        return ticks > 100 ? StepResult.fail("Client sieht " + seen + " statt " + expected) : StepResult.pending();
    }

    private static StepResult selected(int ticks, int slot) {
        Entity drone = Pilot.drone().orElse(null);
        int now = drone == null ? -1 : DispenserState.selection(drone).selected();
        if (now == slot) {
            return StepResult.pass("Slot " + (slot + 1) + " gewählt");
        }
        return ticks > 60 ? StepResult.fail("Slot " + (now + 1) + " statt " + (slot + 1)) : StepResult.pending();
    }

    private static StepResult slotCount(SelfTest.StepContext ctx, int slot, int before) {
        Entity drone = Pilot.drone().orElse(null);
        List<ItemStack> contents = drone == null ? List.of() : PropwashApi.accessories().contents(drone, AccessorySlot.FRONT);
        int count = contents.size() > slot ? contents.get(slot).getCount() : -1;
        return count >= 0 && count < before ? StepResult.pass("Slot " + (slot + 1) + ": " + before + " → " + count)
                : StepResult.fail("Slot " + (slot + 1) + " zeigt " + count + " statt weniger als " + before);
    }

    private static StepResult pushedBack(SelfTest.StepContext ctx, int ticks, double minimum) {
        if (ticks < 10) {
            return StepResult.pending();
        }
        double back = -HoverAssist.minAlong();
        double tilt = HoverAssist.maxTiltChange();
        if (back >= minimum) {
            return StepResult.pass(String.format(Locale.ROOT, "%.2f m/s zurück, Neigung +%.1f°", back, tilt));
        }
        return ticks > 40 ? StepResult.fail(String.format(Locale.ROOT, "nur %.2f m/s zurück (nötig %.1f), Neigung +%.1f°", back, minimum, tilt))
                : StepResult.pending();
    }

    static StepResult staysFlyable(SelfTest.StepContext ctx, int ticks) {
        double change = HoverAssist.backChange();
        double tilt = HoverAssist.maxTiltDegrees();
        double recoil = lastRecoilNs(ctx);
        double mass = ctx.state().get(MASS) instanceof Double value ? value : 0.0;
        double minimum = ExpectedRecoil.noticeableBackMps(recoil, mass);
        String seen = String.format(Locale.ROOT, "Tempo %.2f m/s zurück (nötig %.2f m/s = %.0f %% von %.2f N·s / %.3f kg), Neigung höchstens %.1f°, "
                        + "Drehrate höchstens %.0f °/s", change, minimum, ExpectedRecoil.NOTICEABLE_SHARE * 100.0, recoil, mass, tilt,
                HoverAssist.maxRateDps());
        if (!(tilt < MAX_FLYABLE_TILT_DEGREES)) {
            return StepResult.fail(String.format(Locale.ROOT, "%s: nicht mehr fliegbar (Grenze %.0f°)", seen, MAX_FLYABLE_TILT_DEGREES));
        }
        if (ticks < FLYABLE_TICKS) {
            return StepResult.pending();
        }
        return ExpectedRecoil.noticeable(change, recoil, mass) ? StepResult.pass(seen) : StepResult.fail(seen + ": Rückstoß nicht spürbar");
    }

    private static double lastRecoilNs(SelfTest.StepContext ctx) {
        if (ctx.state().get(SHOTS) instanceof List<?> shots && !shots.isEmpty() && shots.getLast() instanceof DispenserLog.Entry entry
                && entry.result() != null) {
            return entry.result().recoilNs();
        }
        return 0.0;
    }

    private static StepResult lastOutcome(SelfTest.StepContext ctx, FireGate.Outcome expected) {
        String last = String.valueOf(ctx.state().get(LAST));
        return last.equals(expected.name()) ? StepResult.pass(expected.name()) : StepResult.fail(last + " statt " + expected.name());
    }

    private static StepResult notice(SelfTest.StepContext ctx, int ticks, FireGate.Notice expected) {
        String seen = String.valueOf(ctx.state().get(NOTICE));
        if (!seen.equals(expected.name())) {
            return StepResult.fail("OSD-Hinweis " + seen + " statt " + expected);
        }
        Entity drone = Pilot.drone().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        if (drone != null && mc.level != null && OsdNotices.active(DispenserNotices.notice(expected), drone.getId(), mc.level.getGameTime())) {
            return StepResult.pass("Warnung " + expected + " im OSD des Piloten");
        }
        return ticks > 30 ? StepResult.fail("Warnung " + expected + " kommt nicht im OSD an") : StepResult.pending();
    }

    private static void defuse(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        DispenserLog.Entry entry = DispenserLog.last(drone).orElseThrow();
        Entity tnt = entry.result() == null ? null : entry.result().entity().orElse(null);
        if (!(tnt instanceof PrimedTnt primed)) {
            throw new IllegalStateException("kein TNT");
        }
        if (!Payloads.is(primed)) {
            throw new IllegalStateException("TNT nicht als Payload markiert");
        }
        primed.discard();
    }

    private static void water(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = Pilot.serverDrone(server, state);
        BlockPos around = BlockPos.containing(DispenserRig.view(drone).center());
        BlockPos from = around.offset(-WATER_REACH, -WATER_REACH, -WATER_REACH);
        BlockPos to = around.offset(WATER_REACH, WATER_REACH, WATER_REACH);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockState(pos).isAir()) {
                Arena.place(level, pos.immutable(), Blocks.AIR.defaultBlockState());
            }
        }
        new Rules(server).set(RuleDefaults.ALLOW_FLUID_BUCKETS, true);
        BlockPos water = null;
        DispenserLog.Entry used = null;
        try {
            used = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (level.getFluidState(pos).is(Fluids.WATER) && level.getFluidState(pos).isSource()) {
                    water = pos.immutable();
                }
            }
        } finally {
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (!level.getFluidState(pos).isEmpty()) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
            }
        }
        if (!"USED".equals(used.status()) || water == null) {
            throw new IllegalStateException("mit Regel: " + used.status() + ", Wasser " + (water == null ? "fehlt" : "bei " + water.toShortString()));
        }
        ItemStack back = DispenserRig.inventory(drone).get(0);
        state.put(NOTES + ".water", "mit Regel USED, Wasserquelle bei " + water.toShortString() + " vor der Mündung " + BlockPos.containing(used.muzzle())
                .toShortString() + ", im Slot " + back.getItem());
        if (!back.is(Items.BUCKET)) {
            throw new IllegalStateException("leerer Eimer kommt nicht zurück: " + back);
        }
    }

    private static void potionRange(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos pillar = Arena.ground(0, -6).above();
        Entity drone = Arena.track(DispenserRig.onPillar(level, player, DispenserRig.fiveInch(), "S5 Tränke", pillar, Direction.NORTH,
                (pos, block) -> Arena.place(level, pos, block)));
        state.put(Pilot.DRONE, drone.getUUID());
        Vec3 at = DispenserRig.ahead(drone, 3.0);
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        zombie.snapTo(at.x, Arena.ground().getY() + 1.0, at.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        Arena.spawn(level, zombie);
        state.put("dispenser.zombie", zombie.getUUID());
        state.put("dispenser.zombie.health", zombie.getHealth());
        Vec3 view = drone.position().add(DispenserRig.ahead(drone, -4.0).subtract(drone.position()));
        player.teleportTo(level, view.x, Arena.ground().getY() + 1.0, view.z, Set.<Relative>of(), yawTowards(view, at), 15.0F, true);
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private static void shoot(MinecraftServer server, Map<String, Object> state, ItemStack ammo) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Pilot.serverDrone(server, state).discard();
        Entity drone = Arena.track(DispenserRig.onPillar(level, player, DispenserRig.fiveInch(), "S5 Tränke", Arena.ground(0, -6).above(), Direction.NORTH,
                (pos, block) -> {
                }));
        state.put(Pilot.DRONE, drone.getUUID());
        DispenserRig.load(drone, ammo);
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        if (!"FIRED".equals(entry.status())) {
            throw new IllegalStateException(entry.status());
        }
    }

    private static void zombieHurt(MinecraftServer server, Map<String, Object> state) {
        Entity zombie = Arena.level(server).getEntity((UUID) state.get("dispenser.zombie"));
        float before = (float) state.get("dispenser.zombie.health");
        if (!(zombie instanceof Zombie living) || living.getHealth() >= before) {
            throw new IllegalStateException("Zombie unverletzt: " + (zombie instanceof Zombie living ? living.getHealth() : "weg"));
        }
        state.put(NOTES + ".zombie", String.format(Locale.ROOT, "Zombie %.0f → %.0f", before, living.getHealth()));
    }

    private static void cloud(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        List<AreaEffectCloud> clouds = Arena.level(server).getEntitiesOfClass(AreaEffectCloud.class, drone.getBoundingBox().inflate(10.0));
        if (clouds.isEmpty()) {
            throw new IllegalStateException("keine Wolke");
        }
        for (AreaEffectCloud cloud : clouds) {
            Arena.track(cloud);
            if (!Payloads.is(cloud)) {
                throw new IllegalStateException("Wolke nicht als Payload markiert");
            }
        }
    }

    private static void matrix(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        PvpMatrix run = PvpMatrix.start(server, level, Arena.ground(10, -10).above(), player, List.of(PvpMatrix.Payload.ARROW,
                PvpMatrix.Payload.HARMING, PvpMatrix.Payload.HEALING), PvpMatrix.COMBOS, (pos, block) -> Arena.place(level, pos, block));
        state.put(MATRIX, run);
    }

    private static StepResult matrixDone(SelfTest.StepContext ctx, int ticks) {
        PvpMatrix run = (PvpMatrix) ctx.state().get(MATRIX);
        if (run == null || !run.done()) {
            return ticks > 1200 ? StepResult.fail("Bahnen werden nicht fertig") : StepResult.pending();
        }
        return run.failure() == null ? StepResult.pass(run.summary()) : StepResult.fail(run.failure());
    }

    private static BlockPos platform(Map<String, Object> state, int index) {
        Object pos = state.get(PLATFORM + index);
        if (!(pos instanceof BlockPos center)) {
            throw new IllegalStateException("Plattform " + (index + 1) + " fehlt");
        }
        return center;
    }

    private static void tntRange(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "S7 TNT", Arena.ground(0, -6), 180.0F);
        DispenserRig.load(drone, DispenserRig.tntRun().toArray(ItemStack[]::new));
    }

    private static void buildPlatform(MinecraftServer server, Map<String, Object> state, int index) {
        ServerLevel level = Arena.level(server);
        Entity drone = Pilot.serverDrone(server, state);
        BlockPos center = BlockPos.containing(drone.getX(), Arena.ground().getY() + 1 + PLATFORM_HEIGHT, drone.getZ());
        if (drone.getY() - center.getY() < DROP_HEIGHT - 4) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Drohne nur %.1f m über der Plattform", drone.getY() - center.getY()));
        }
        Arena.fill(level, center.offset(-PLATFORM_RADIUS, 0, -PLATFORM_RADIUS), center.offset(PLATFORM_RADIUS, 0, PLATFORM_RADIUS),
                Blocks.STONE.defaultBlockState());
        state.put(PLATFORM + index, center);
    }

    private static Script bomb(int index, boolean crater) {
        String label = crater ? "TNT mit Griefing" : "TNT ohne Griefing";
        return Script.create()
                .run("auf der Stelle halten", () -> Pilot.drone().ifPresent(d -> HoverAssist.hold(d.getX(), HoverAssist.targetY(), d.getZ())))
                .then("ruhig", (ctx, ticks) -> calm(ticks))
                .server("Plattform " + (index + 1) + " unter der Drohne", (server, state) -> buildPlatform(server, state, index))
                .server("Plattform " + (index + 1) + " zählen", (server, state) -> countPlatform(server, state, index))
                .append(fire(label, 1, 1, 1, entity -> entity instanceof PrimedTnt, 0.0))
                .then(label + " explodiert", DispenserSteps::exploded)
                .server(crater ? "Krater" : "kein Krater", (server, state) -> crater(server, state, index, crater));
    }

    private static StepResult calm(int ticks) {
        FlightState state = HoverAssist.last();
        if (state != null && ticks > 30 && Math.hypot(state.vx(), state.vz()) < 0.6 && Math.abs(state.vy()) < 0.4) {
            return StepResult.pass(String.format(Locale.ROOT, "hält bei %.1f m/s, Kompassfehler %+.1f°", Math.hypot(state.vx(), state.vz()),
                    state.compassErrorDeg()));
        }
        return ticks > 600 ? StepResult.fail("kommt nicht zur Ruhe" + (state == null ? "" : String.format(Locale.ROOT,
                ": %.2f m/s waagerecht, %.2f m/s senkrecht, Kompassfehler %+.1f°", Math.hypot(state.vx(), state.vz()), state.vy(),
                state.compassErrorDeg()))) : StepResult.pending();
    }

    private static StepResult steadyCompass() {
        FlightState state = HoverAssist.last();
        if (state == null) {
            return StepResult.fail("keine Flugdaten");
        }
        String property = CompassProperty.current();
        if (!state.compassHealthy()) {
            return StepResult.fail(String.format(Locale.ROOT, "Flug mit gestörtem Kompass: Kompassfehler %+.1f°", state.compassErrorDeg()));
        }
        if (!DISTURBED_DEGREES.equals(property)) {
            return StepResult.fail("Test-Eigenschaft nach dem Flugstart " + property + " statt " + DISTURBED_DEGREES);
        }
        return StepResult.pass(String.format(Locale.ROOT, "Kompassfehler im Flug %+.1f° (gesund), Test-Eigenschaft nach dem Flugstart wieder %s°",
                state.compassErrorDeg(), property));
    }

    private static StepResult climbed(int ticks, double metres) {
        FlightState state = HoverAssist.last();
        double height = state == null ? 0.0 : state.y() - (Arena.ground().getY() + 1.0);
        if (state != null && height > metres) {
            return StepResult.pass(String.format(Locale.ROOT, "%.1f m über Grund", height));
        }
        return ticks > 900 ? StepResult.fail(String.format(Locale.ROOT, "steigt nicht über %.1f m", height)) : StepResult.pending();
    }

    private static void cruise(double speed) {
        Entity drone = Pilot.drone().orElse(null);
        if (drone == null) {
            return;
        }
        PropwashApi.drones().view(drone).ifPresent(view -> {
            Vec3 flat = new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
            HoverAssist.cruise(HoverAssist.mode() == HoverAssist.Mode.OFF ? drone.getY() : HoverAssist.targetY(), flat.x * speed, flat.z * speed);
        });
    }

    private static StepResult speed(int ticks, double minimum) {
        FlightState state = HoverAssist.last();
        double now = state == null ? 0.0 : Math.hypot(state.vx(), state.vz());
        if (now >= minimum && ticks > 20) {
            return StepResult.pass(String.format(Locale.ROOT, "%.1f m/s", now));
        }
        return ticks > 400 ? StepResult.fail(String.format(Locale.ROOT, "nur %.1f m/s", now)) : StepResult.pending();
    }

    private static StepResult sampled(SelfTest.StepContext ctx, int ticks) {
        MotionSampler sampler = (MotionSampler) ctx.state().get(SAMPLER);
        if (sampler == null || !sampler.done()) {
            return ticks > 200 ? StepResult.fail("Messung am Server endet nicht") : StepResult.pending();
        }
        return StepResult.pass(sampler.summary());
    }

    @SuppressWarnings("unchecked")
    private static void tntSpeed(MinecraftServer server, Map<String, Object> state) {
        List<DispenserLog.Entry> shots = (List<DispenserLog.Entry>) state.get(SHOTS);
        List<String> seen = new ArrayList<>();
        int moving = 0;
        for (DispenserLog.Entry entry : shots) {
            Vec3 drift = new Vec3(entry.launch().x, 0.0, entry.launch().z);
            Vec3 expected = new Vec3(entry.droneVelocity().x, 0.0, entry.droneVelocity().z).scale(1.0 / 20.0);
            entry.result().entity().ifPresent(Entity::discard);
            seen.add(String.format(Locale.ROOT, "TNT %.2f m/s bei Propwash-Tempo %.2f m/s", drift.length() * 20.0, expected.length() * 20.0));
            if (expected.length() >= 0.1) {
                moving++;
                if (drift.subtract(expected).length() > 0.05) {
                    throw new IllegalStateException("TNT erbt das Tempo nicht: " + String.join("; ", seen));
                }
            }
        }
        if (moving == 0) {
            throw new IllegalStateException("Propwash meldet bei keinem Abwurf Tempo (API-Befund): " + String.join("; ", seen));
        }
        state.put(NOTES + ".tnt", String.join("; ", seen));
    }

    private static StepResult tntNote(SelfTest.StepContext ctx) {
        return StepResult.pass(String.valueOf(ctx.state().get(NOTES + ".tnt")));
    }

    private static Vec3 platformCenter(Map<String, Object> state, int index) {
        return Vec3.atBottomCenterOf(platform(state, index).above());
    }

    private static void countPlatform(MinecraftServer server, Map<String, Object> state, int index) {
        state.put(TNT_BLOCKS, platformBlocks(server, state, index));
        state.put(TAMED, TntGuard.tamed());
    }

    private static int platformBlocks(MinecraftServer server, Map<String, Object> state, int index) {
        ServerLevel level = Arena.level(server);
        BlockPos center = platform(state, index);
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-PLATFORM_RADIUS - 2, -4, -PLATFORM_RADIUS - 2),
                center.offset(PLATFORM_RADIUS + 2, 4, PLATFORM_RADIUS + 2))) {
            if (!level.getBlockState(pos).isAir()) {
                count++;
            }
        }
        return count;
    }

    private static StepResult exploded(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        boolean primed = false;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PrimedTnt) {
                primed = true;
            }
        }
        if (!primed && ticks > 20) {
            return StepResult.pass("explodiert nach " + ticks + " Ticks");
        }
        return ticks > 300 ? StepResult.fail("TNT explodiert nicht") : StepResult.pending();
    }

    private static void crater(MinecraftServer server, Map<String, Object> state, int index, boolean expected) {
        int before = (int) state.get(TNT_BLOCKS);
        int after = platformBlocks(server, state, index);
        int tamed = TntGuard.tamed() - (int) state.get(TAMED);
        if (expected ? after >= before : after != before) {
            throw new IllegalStateException("Blöcke " + before + " → " + after + (expected ? ", kein Krater" : ", Krater trotz drone_griefing aus"));
        }
        if (tamed != (expected ? 0 : 1)) {
            throw new IllegalStateException("TntGuard hat " + tamed + " Explosionen gezähmt");
        }
        Entity drone = Pilot.serverDrone(server, state);
        if (drone.isRemoved()) {
            throw new IllegalStateException("Drohne von der Explosion zerstört");
        }
        state.put(NOTES + ".crater." + index, "Plattform " + (index + 1) + ": " + before + " → " + after + " Blöcke");
    }

    private static void lookAtPlatforms(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Vec3 center = platformCenter(state, 0).add(platformCenter(state, 1)).scale(0.5);
        Vec3 eye = center.add(0.0, 9.0, 14.0);
        double dx = center.x - eye.x;
        double dz = center.z - eye.z;
        double dy = center.y - eye.y;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, Set.<Relative>of(), yaw, pitch, true);
    }

    private static void loadForRestart(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BuildSpec build = DispenserRig.fiveInch().withAccessory(AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER);
        Entity drone = Arena.track(DispenserRig.place(level, player, build, "S13 Speichern", Arena.ground(0, -6)));
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 5), potion(Items.SPLASH_POTION, Potions.HEALING), null, new ItemStack(Items.TNT, 2));
        BoosterRig.load(drone, BoosterRig.rocket(S13_FLIGHT, 1));
        PropwashApi.accessories().triggerAction(drone, 2, true, player);
        PropwashApi.accessories().triggerAction(drone, 2, false, player);
        if (DispenserState.selection(drone).selected() != 1) {
            throw new IllegalStateException("Aktion 2 wählt Slot " + (DispenserState.selection(drone).selected() + 1) + " statt 2");
        }
        String contents = DispenserRig.counts(PropwashApi.accessories().contents(drone, AccessorySlot.FRONT));
        String rocket = rocket(PropwashApi.accessories().contents(drone, AccessorySlot.TOP));
        if (!rocket.startsWith("1×firework_rocket")) {
            throw new IllegalStateException("Raketen-Booster nicht geladen: " + rocket);
        }
        state.put(Pilot.DRONE, drone.getUUID());
        state.put(S13_CONTENTS, contents);
        state.put(S13_ROCKET, rocket);
        s13Notes(state).add("vor dem Neustart: " + contents + ", Slot 2, Rakete " + rocket);
    }

    private static StepResult reloaded(SelfTest.StepContext ctx, int ticks) {
        StepResult probe = Script.server(ctx, ticks, (server, state) -> state.put(S13_FOUND, Arena.level(server).getEntity((UUID) state.get(Pilot.DRONE)) != null));
        if (!probe.done() || probe.failed()) {
            return probe;
        }
        if (Boolean.TRUE.equals(ctx.state().remove(S13_FOUND))) {
            return StepResult.pass("Drohne nach " + ticks + " Ticks aus dem Speicherstand geladen");
        }
        return ticks > 400 ? StepResult.fail("Drohne " + ctx.state().get(Pilot.DRONE) + " nach dem Neustart nicht geladen") : StepResult.pending();
    }

    private static void afterRestart(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Arena.track(Pilot.serverDrone(server, state));
        String contents = (String) state.get(S13_CONTENTS);
        expect("nach dem Neustart", drone, contents, 1);
        expectRocket("nach dem Neustart", PropwashApi.accessories().contents(drone, AccessorySlot.TOP), state);
        s13Notes(state).add("nach dem Neustart am Server: " + contents + ", Slot 2, Rakete " + state.get(S13_ROCKET));
    }

    private static StepResult clientAfterRestart(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Entity drone = mc.level == null ? null : mc.level.getEntity((UUID) ctx.state().get(Pilot.DRONE));
        String expected = ctx.state().get(S13_CONTENTS) + ", Slot 2, Rakete " + ctx.state().get(S13_ROCKET);
        String seen = drone == null ? "keine Drohne"
                : DispenserRig.counts(PropwashApi.accessories().contents(drone, AccessorySlot.FRONT)) + ", Slot " + (DispenserState.selection(drone).selected() + 1)
                + ", Rakete " + rocket(PropwashApi.accessories().contents(drone, AccessorySlot.TOP));
        if (seen.equals(expected)) {
            return StepResult.pass("nach dem Neustart am Client: " + seen);
        }
        return ticks > 100 ? StepResult.fail("Client sieht " + seen + " statt " + expected) : StepResult.pending();
    }

    private static void selectAndKeep(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Entity drone = Pilot.serverDrone(server, state);
        String contents = (String) state.get(S13_CONTENTS);
        List<String> notes = s13Notes(state);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
        if (!drone.isRemoved()) {
            throw new IllegalStateException("Drohne lässt sich nicht aufheben");
        }
        ItemStack item = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (PropwashApi.builds().isDrone(player.getInventory().getItem(i))) {
                item = player.getInventory().getItem(i);
            }
        }
        if (item.isEmpty()) {
            throw new IllegalStateException("Drohnen-Item fehlt im Inventar");
        }
        String itemContents = DispenserRig.counts(PropwashApi.accessories().contents(item, AccessorySlot.FRONT));
        if (!itemContents.equals(contents) || DispenserState.selection(item).selected() != 1) {
            throw new IllegalStateException("Item: " + itemContents + ", Slot " + (DispenserState.selection(item).selected() + 1));
        }
        expectRocket("Aufheben", PropwashApi.accessories().contents(item, AccessorySlot.TOP), state);
        notes.add("Aufheben: Item trägt " + itemContents + ", Slot 2, Rakete " + state.get(S13_ROCKET));
        ItemStack placed = item.copy();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, placed);
        BlockPos ground = Arena.ground(3, -6);
        placed.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        Entity again = null;
        for (DroneView view : PropwashApi.drones().dronesNear(level, Vec3.atCenterOf(ground.above()), 3.0, false)) {
            again = Arena.track(view.entity());
        }
        if (again == null) {
            throw new IllegalStateException("Drohne nicht wieder abgestellt");
        }
        expect("wieder abgestellt", again, contents, 1);
        expectRocket("wieder abgestellt", PropwashApi.accessories().contents(again, AccessorySlot.TOP), state);
        notes.add("wieder abgestellt: " + contents + ", Slot 2, Rakete " + state.get(S13_ROCKET));
        notes.add(workbench(player, level, item.copy(), (String) state.get(S13_ROCKET)));
    }

    @SuppressWarnings("unchecked")
    private static List<String> s13Notes(Map<String, Object> state) {
        return (List<String>) state.computeIfAbsent(NOTES + ".s13", key -> new ArrayList<String>());
    }

    private static String rocket(List<ItemStack> contents) {
        ItemStack stack = contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst();
        if (stack.isEmpty()) {
            return "keine";
        }
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        return stack.getCount() + "×" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()
                + (fireworks == null ? " ohne Feuerwerksdaten" : " (Flugdauer " + fireworks.flightDuration() + ", Sterne " + fireworks.explosions().size() + ")");
    }

    private static void expectRocket(String label, List<ItemStack> contents, Map<String, Object> state) {
        String now = rocket(contents);
        if (!now.equals(state.get(S13_ROCKET))) {
            throw new IllegalStateException(label + ": Rakete " + now + " statt " + state.get(S13_ROCKET));
        }
    }

    private static String workbench(ServerPlayer player, ServerLevel level, ItemStack droneItem, String expectedRocket) {
        player.getInventory().clearContent();
        BlockPos pos = Arena.ground(-3, -6).above();
        Arena.place(level, pos, BuiltInRegistries.BLOCK.getValue(DispenserRig.propwash("drone_workbench")).defaultBlockState());
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        Slot salvage = null;
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != player.getInventory() && slot.mayPlace(droneItem)) {
                salvage = slot;
            }
        }
        if (salvage == null) {
            player.closeContainer();
            throw new IllegalStateException("kein Werkbank-Slot nimmt die Drohne");
        }
        salvage.set(droneItem);
        player.containerMenu.broadcastChanges();
        int arrows = 0;
        int tnt = 0;
        int potions = 0;
        List<ItemStack> rockets = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            arrows += stack.is(Items.ARROW) ? stack.getCount() : 0;
            tnt += stack.is(Items.TNT) ? stack.getCount() : 0;
            potions += stack.is(Items.SPLASH_POTION) ? stack.getCount() : 0;
            if (stack.is(Items.FIREWORK_ROCKET)) {
                rockets.add(stack.copy());
            }
        }
        player.closeContainer();
        player.getInventory().clearContent();
        String rocket = rockets.size() == 1 ? rocket(rockets) : rockets.size() + " Raketenstapel";
        if (arrows != 5 || tnt != 2 || potions != 1 || !rocket.equals(expectedRocket)) {
            throw new IllegalStateException("Werkbank gibt " + arrows + " Pfeile, " + potions + " Tränke, " + tnt + " TNT, Rakete " + rocket
                    + " zurück statt 5, 1, 2, Rakete " + expectedRocket);
        }
        return "Werkbank zerlegt die Drohne und gibt Munition und Rakete zurück (5 Pfeile, 1 Trank, 2 TNT, Rakete " + rocket + ")";
    }

    private static void expect(String label, Entity drone, String contents, int slot) {
        String now = DispenserRig.counts(PropwashApi.accessories().contents(drone, AccessorySlot.FRONT));
        int selected = DispenserState.selection(drone).selected();
        if (!now.equals(contents) || selected != slot) {
            throw new IllegalStateException(label + ": " + now + ", Slot " + (selected + 1) + " statt " + contents + ", Slot " + (slot + 1));
        }
    }
}
