package de.lardtom.mobdefense.dev.dispenser.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.CovMdAWatch;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.ui.client.CovMdAScreenText;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class CovMdAFlightSteps {
    public static final int COOLDOWN = 4;
    public static final int PULSE_TICKS = 2;
    public static final double CALM_SPEED = 0.6;
    public static final double CALM_CLIMB = 0.4;
    public static final double ARROW_RECOIL_NS = 0.44;
    public static final double MIN_HIT_RATE = 0.125;
    public static final double MAX_TILT_DEGREES = 10.0;
    public static final double MAX_HEIGHT_CHANGE = 1.0;
    public static final double OWN_SHOT_SPEED = 25.0;
    public static final int OWN_SHOT_PROTECTION_TICKS = 10;
    public static final double CAUGHT_DISTANCE = 3.0;
    public static final double CAUGHT_CLOSING_SPEED = 10.0;
    public static final double ZOMBIE_HALF_HEIGHT = 1.0;
    private static final String START = "covmda.flight.start";
    private static final String COUNT = "covmda.flight.count";
    private static final String NOTES = "covmda.flight.notes";
    private static final String SAMPLES = "covmda.flight.samples";
    private static final String ZOMBIES = "covmda.flight.zombies";
    private static final String BEFORE = "covmda.flight.before";
    private static final String LABEL = "covmda.flight.label";
    private static final String LAUNCH = "covmda.flight.launch";
    private static final String BACK = "covmda.flight.back";
    private static final String MOVER = "covmda.flight.mover";
    private static final String RANGING = "covmda.flight.ranging";
    private static final String OWN = "covmda.flight.own";
    private static final String LAUNCHED = "covmda.flight.launched";
    private static final String CAUGHT = "covmda.flight.caught";
    private static final int SERVICE_ACCESSORY_BUTTON = 3000;

    private CovMdAFlightSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("Taster der Funke (simuliert über triggerAction) – ein Puls auf Zubehör-Aktion 1: genau ein Pfeil und der Dispenser-Ton", 3000,
                flight(DispenserRig.fiveInch(), "CovMdA Taster", 3.0, () -> new ItemStack(Items.ARROW, 16))
                        .run("Töne mitschreiben", () -> {
                            CovMdASounds.listen();
                            CovMdASounds.clear();
                        })
                        .server("Puls 2 Ticks", (server, state) -> {
                            mark(server, state);
                            trigger(server, state, true);
                            CovMdAWatch.later(PULSE_TICKS, () -> trigger(server, state, false));
                        })
                        .sleep(14)
                        .server("genau ein Schuss", (server, state) -> note(state, shots(server, state, 1, 1, "Puls")))
                        .then("Dispenser-Ton am Client", (ctx, ticks) -> sound(ctx, ticks, 1))
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Schalter der Funke (simuliert über triggerAction) – 40 Ticks an: erster Schuss sofort, nach 10 Ticks Anlauf im Takt von 4 Ticks "
                + "(9 Schuss), aus oder leer: kein weiterer Schuss",
                3600, flight(DispenserRig.fiveInch(), "CovMdA Schalter", 3.0, () -> new ItemStack(Items.ARROW, 16))
                        .server("Schalter an", (server, state) -> {
                            mark(server, state);
                            trigger(server, state, true);
                        })
                        .sleep(40)
                        .server("Schalter aus", (server, state) -> {
                            trigger(server, state, false);
                            note(state, shots(server, state, 8, 10, "40 Ticks an"));
                            mark(server, state);
                        })
                        .sleep(20)
                        .server("nach dem Ausschalten kein Schuss", (server, state) -> note(state, shots(server, state, 0, 0, "aus")))
                        .server("6 Pfeile, Schalter an", (server, state) -> {
                            DispenserRig.load(Pilot.serverDrone(server, state), new ItemStack(Items.ARROW, 6));
                            mark(server, state);
                            trigger(server, state, true);
                        })
                        .sleep(44)
                        .server("Munition leer, Schalter aus", (server, state) -> {
                            trigger(server, state, false);
                            note(state, shots(server, state, 6, 6, "6 Pfeile, Schalter 44 Ticks an") + ", danach " + outcomes(server, state));
                        })
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Tastatur – Zubehör-Aktion 1 liegt auf Ziffernblock 9, kurz gedrückt ein Schuss, 40 Ticks gehalten nach 10 Ticks Anlauf Dauerfeuer im "
                + "Takt der Abklingzeit",
                3600, flight(DispenserRig.fiveInch(), "CovMdA Tastatur", 3.0, () -> new ItemStack(Items.ARROW, 16))
                        .check("Standardtaste Ziffernblock 9", CovMdAFlightSteps::numpadNine)
                        .server("vorher", CovMdAFlightSteps::mark)
                        .run("Ziffernblock 9 drücken", () -> Pilot.hold(Pilot.ACTION_1, true))
                        .run("Ziffernblock 9 los", () -> Pilot.hold(Pilot.ACTION_1, false))
                        .sleep(10)
                        .server("kurz gedrückt", (server, state) -> note(state, shots(server, state, 1, 1, "Taste 1 Tick")))
                        .server("vorher", CovMdAFlightSteps::mark)
                        .append(Pilot.press(Pilot.ACTION_1, 40))
                        .sleep(8)
                        .server("gehalten", (server, state) -> note(state, shots(server, state, 8, 10, "Taste 40 Ticks")))
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Drohne wird leichter – 16 Pfeile einzeln: je Schuss 20 g weniger Beladung und weniger Masse im Flugmodell, Wartung zeigt die Beladung",
                5000, Script.create()
                        .server("5″ mit 16 Pfeilen", (server, state) -> {
                            Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "CovMdA leichter", Arena.ground(0, -6), 180.0F);
                            DispenserRig.load(drone, new ItemStack(Items.ARROW, 16));
                        })
                        .append(service("vorher", 320))
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(3.0))
                        .run("Position halten", CovMdAFlightSteps::hold)
                        .server("Messreihe beginnen", (server, state) -> state.put(SAMPLES, new ArrayList<double[]>()))
                        .then("16 Schüsse", CovMdBEach.each(state -> 16, 6, (server, state, index) -> {
                            sample(server, state);
                            pulse(server, state);
                        }))
                        .sleep(6)
                        .server("letzte Messung", CovMdAFlightSteps::sample)
                        .server("Beladung und Masse sinken je Schuss", CovMdAFlightSteps::lighter)
                        .run("Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
                        .server("Flug beenden", (server, state) -> Pilot.release(server))
                        .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                        .sleep(40)
                        .server("Drohne zurück auf den Boden", CovMdAFlightSteps::ground)
                        .sleep(10)
                        .append(service("nachher", 0))
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Dauerfeuer auf Zombies – 5″ schwebt 10 m vor neun Zombies, 16 Pfeile im Dauerfeuer: Trefferquote, Rückstoß je Schuss, Lage und Höhe danach",
                5000, flight(DispenserRig.fiveInch(), "CovMdA Dauerfeuer", 4.5, () -> new ItemStack(Items.ARROW, 18))
                        .then("ruhig", CovMdAFlightSteps::calm)
                        .server("Probeschuss", CovMdAFlightSteps::ranging)
                        .sleep(30)
                        .server("zweiter Probeschuss, falls der erste vor 10 m landete", (server, state) -> {
                            CovMdAWatch.Watch probe = (CovMdAWatch.Watch) state.get(RANGING);
                            if (probe == null || Double.isNaN(probe.heightAt(10.0))) {
                                note(state, String.format(Locale.ROOT, "erster Probeschuss nach %.1f m am Boden", probe == null ? 0.0 : probe.maxAlong()));
                                ranging(server, state);
                            }
                        })
                        .sleep(30)
                        .server("neun Zombies 9,5 bis 11,5 m voraus in Höhe des Probeschusses", CovMdAFlightSteps::zombies)
                        .then("ruhig vor der Salve", CovMdAFlightSteps::calm)
                        .run("Flughilfe hält Höhe und Position, Knüppel mittig", CovMdAFlightSteps::hold)
                        .sleep(5)
                        .then("Lage vorher", (ctx, ticks) -> attitude(ctx, BEFORE))
                        .server("genau 16 Pfeile, vorher", (server, state) -> {
                            DispenserRig.load(Pilot.serverDrone(server, state), new ItemStack(Items.ARROW, 16));
                            mark(server, state);
                        })
                        .append(Pilot.press(Pilot.ACTION_1, 80))
                        .sleep(50)
                        .then("Lage nachher", CovMdAFlightSteps::attitudeAfter)
                        .run("Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
                        .server("Flug beenden", (server, state) -> Pilot.release(server))
                        .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                        .server("Blick auf die Zombies", CovMdAFlightSteps::lookAtZombies)
                        .append(Shots.photos("covmda_volley_zombies"))
                        .server("Treffer und Rückstoß", CovMdAFlightSteps::volley)
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Drohnentempo addiert sich – Pfeil im Vorwärtsflug mit 8 m/s ist schneller als im Schwebeflug", 4000,
                flight(DispenserRig.fiveInch(), "CovMdA Tempo", 4.0, () -> new ItemStack(Items.ARROW, 16))
                        .then("ruhig", CovMdAFlightSteps::calm)
                        .server("Pfeil im Schwebeflug", (server, state) -> launch(server, state, "Schwebeflug"))
                        .run("Vorwärtsflug 8 m/s", () -> cruise(8.0))
                        .then("Tempo erreicht", (ctx, ticks) -> speed(ticks, 6.5))
                        .server("Pfeil im Vorwärtsflug", (server, state) -> launch(server, state, "Vorwärtsflug"))
                        .server("Vergleich", CovMdAFlightSteps::launchFaster)
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Größen im Vergleich – dieselbe Windkugel stößt eine 7″ weniger zurück als eine 5″", 6000,
                flight(DispenserRig.fiveInch(), "CovMdA Wind 5", 4.0, () -> new ItemStack(Items.WIND_CHARGE, 2))
                        .append(wind("5″"))
                        .run("Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
                        .server("Flug beenden", (server, state) -> Pilot.release(server))
                        .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                        .server("5″ weg", (server, state) -> Pilot.serverDrone(server, state).discard())
                        .sleep(10)
                        .append(flight(sevenInch(), "CovMdA Wind 7", 4.0, () -> new ItemStack(Items.WIND_CHARGE, 2)))
                        .append(wind("7″"))
                        .server("Vergleich", CovMdAFlightSteps::windCompare)
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
        steps.add("Kein Treffer an der eigenen Drohne – mit 25 m/s vorwärts und im steilen Steigflug je 10 Pfeile und Schneebälle, kein Schaden durch eigene "
                + "Geschosse", 3000, Script.create()
                .server("5″ in 6 m Höhe", CovMdAFlightSteps::ownRig)
                .append(ownRun("Pfeile vorwärts", () -> new ItemStack(Items.ARROW, 16), new Vec3(0.0, 0.0, -1.0)))
                .append(ownRun("Schneebälle vorwärts", () -> new ItemStack(Items.SNOWBALL, 16), new Vec3(0.0, 0.0, -1.0)))
                .append(ownRun("Pfeile im Steigflug", () -> new ItemStack(Items.ARROW, 16), new Vec3(0.0, 0.94, -0.34)))
                .append(ownRun("Schneebälle im Steigflug", () -> new ItemStack(Items.SNOWBALL, 16), new Vec3(0.0, 0.94, -0.34)))
                .server("kein Schaden durch eigene Geschosse", CovMdAFlightSteps::noOwnHits)
                .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
    }

    public static void registerOwnWindCharges(SelfTest.Steps steps) {
        steps.add("Kein Treffer an der eigenen Drohne – eine eingeholte eigene Windkugel fliegt durch die Drohne ohne Zusammenstoß, dazu mit 25 m/s "
                + "vorwärts und im steilen Steigflug je 10 Windkugeln: kein Schaden durch eigene Windkugeln, die Drohne bleibt", 2400, Script.create()
                        .server("5″ in 6 m Höhe", CovMdAFlightSteps::ownRig)
                        .append(caughtUp())
                        .append(ownRun("Windkugeln vorwärts", () -> new ItemStack(Items.WIND_CHARGE, 16), new Vec3(0.0, 0.0, -1.0)))
                        .append(ownRun("Windkugeln im Steigflug", () -> new ItemStack(Items.WIND_CHARGE, 16), new Vec3(0.0, 0.94, -0.34)))
                        .server("kein Schaden durch eigene Windkugeln", CovMdAFlightSteps::noOwnHits)
                        .then("Ergebnis", CovMdAFlightSteps::notes), CovMdAFlightSteps::cleanup);
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("Tasten los, Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
            .server("Flug beenden, Geschosse weg", (server, state) -> {
                Pilot.release(server);
                CovMdAShotSteps.sweep(server);
            });

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }

    static BuildSpec sevenInch() {
        return BuildSpec.of(DispenserRig.propwash("long_range_7"), DispenserRig.propwash("stack_30"), DispenserRig.propwash("m2807_1300"),
                DispenserRig.propwash("p7_2b"), DispenserRig.propwash("digital_hd"), DispenserRig.propwash("b6s_4000_lion"))
                .withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
    }

    static Script flight(BuildSpec spec, String name, double metres, Supplier<ItemStack> ammo) {
        return Script.create()
                .server(name + " setzen und beladen", (server, state) -> {
                    Entity drone = Pilot.spawn(server, state, spec, name, Arena.ground(0, -6), 180.0F);
                    DispenserRig.load(drone, ammo.get());
                })
                .append(Pilot.fly())
                .append(Pilot.takeOff(metres))
                .run("Position halten", CovMdAFlightSteps::hold);
    }

    static void hold() {
        Pilot.drone().ifPresent(d -> HoverAssist.hold(d.getX(), HoverAssist.targetY(), d.getZ()));
    }

    static void trigger(MinecraftServer server, Map<String, Object> state, boolean pressed) {
        Entity drone = Pilot.serverDrone(server, state);
        if (!PropwashApi.accessories().triggerAction(drone, 1, pressed, Arena.player(server)) && pressed) {
            throw new IllegalStateException("Zubehör-Aktion 1 nicht angenommen");
        }
    }

    static void pulse(MinecraftServer server, Map<String, Object> state) {
        trigger(server, state, true);
        trigger(server, state, false);
    }

    static void mark(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        state.put(START, drone.level().getGameTime());
        state.put(COUNT, DispenserLog.counts(drone).dispensed());
    }

    @SuppressWarnings("unchecked")
    static void note(Map<String, Object> state, String note) {
        ((List<String>) state.computeIfAbsent(NOTES, key -> new ArrayList<String>())).add(note);
    }

    @SuppressWarnings("unchecked")
    static StepResult notes(SelfTest.StepContext ctx, int ticks) {
        return StepResult.pass(String.join("; ", (List<String>) ctx.state().getOrDefault(NOTES, List.of())));
    }

    static List<DispenserLog.Entry> since(Entity drone, long start) {
        List<DispenserLog.Entry> out = new ArrayList<>();
        for (DispenserLog.Entry entry : DispenserLog.history(drone)) {
            if (entry.gameTime() >= start) {
                out.add(entry);
            }
        }
        return out;
    }

    static String shots(MinecraftServer server, Map<String, Object> state, int min, int max, String label) {
        Entity drone = Pilot.serverDrone(server, state);
        long start = (long) state.get(START);
        int fired = DispenserLog.counts(drone).dispensed() - (int) state.get(COUNT);
        List<Long> times = new ArrayList<>();
        for (DispenserLog.Entry entry : since(drone, start)) {
            if (entry.result() != null && entry.result().success()) {
                if (!(entry.result().entity().orElse(null) instanceof AbstractArrow)) {
                    throw new IllegalStateException(label + ": Geschoss " + CovMdAWatch.type(entry.result().entity().orElse(null)) + " statt Pfeil");
                }
                times.add(entry.gameTime());
            }
        }
        if (fired < min || fired > max) {
            throw new IllegalStateException(label + ": " + fired + " Schüsse statt " + (min == max ? String.valueOf(min) : min + "–" + max) + " (Zeiten "
                    + times + ")");
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            long gap = times.get(i) - times.get(i - 1);
            gaps.add(gap);
            if (gap < COOLDOWN) {
                throw new IllegalStateException(label + ": Abstand " + gap + " Ticks unter der Abklingzeit " + COOLDOWN + " (" + times + ")");
            }
        }
        return label + ": " + fired + (fired == 1 ? " Schuss" : " Schüsse") + (gaps.isEmpty() ? "" : ", Abstände " + gaps.stream().distinct().sorted().toList()
                + " Ticks");
    }

    static String outcomes(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        List<String> after = new ArrayList<>();
        boolean empty = false;
        for (DispenserLog.Entry entry : since(drone, (long) state.get(START))) {
            if (entry.result() != null && entry.result().success()) {
                if (empty) {
                    throw new IllegalStateException("Schuss nach leerem Dispenser");
                }
            } else {
                empty = true;
                after.add(entry.status());
            }
        }
        if (after.isEmpty() || !after.stream().allMatch(FireGate.Outcome.EMPTY.name()::equals)) {
            throw new IllegalStateException("nach dem letzten Pfeil " + after + " statt EMPTY");
        }
        return after.size() + "× EMPTY (NO_AMMO), kein Geschoss";
    }

    private static StepResult sound(SelfTest.StepContext ctx, int ticks, int expected) {
        List<String> heard = CovMdASounds.dispenser();
        long shots = heard.stream().filter(sound -> sound.equals("launch") || sound.equals("dispense")).count();
        if (shots < expected) {
            return ticks > 60 ? StepResult.fail("Dispenser-Ton fehlt am Client: " + heard) : StepResult.pending();
        }
        if (shots > expected || heard.contains("fail")) {
            return StepResult.fail("Töne " + heard + " statt genau " + expected + "× launch/dispense");
        }
        return StepResult.pass("am Client gehört: block.dispenser." + String.join(", block.dispenser.", heard)
                + " (Vanilla spielt für Geschosse launch, für Gegenstände dispense)");
    }

    private static StepResult numpadNine() {
        KeyMapping key = KeyMapping.get(Pilot.ACTION_1);
        if (key == null) {
            return StepResult.fail("Taste " + Pilot.ACTION_1 + " fehlt");
        }
        InputConstants.Key fallback = key.getDefaultKey();
        if (fallback.getType() != InputConstants.Type.KEYBOARD || fallback.getValue() != InputConstants.KEY_NUMPAD9) {
            return StepResult.fail("Standardtaste " + fallback.getName() + " statt Ziffernblock 9");
        }
        return StepResult.pass("Zubehör-Aktion 1: Standard " + fallback.getDisplayName().getString() + ", belegt " + key.getTranslatedKeyMessage().getString());
    }

    @SuppressWarnings("unchecked")
    private static void sample(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        double grams = DispenserRig.inventory(drone).payloadGrams();
        double mass = DispenserRig.view(drone).massKg();
        ((List<double[]>) state.get(SAMPLES)).add(new double[]{grams, mass});
    }

    @SuppressWarnings("unchecked")
    private static void lighter(MinecraftServer server, Map<String, Object> state) {
        List<double[]> samples = (List<double[]>) state.get(SAMPLES);
        if (samples.size() != 17) {
            throw new IllegalStateException(samples.size() + " Messungen statt 17");
        }
        List<String> masses = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            double grams = samples.get(i)[0];
            double expected = (16 - i) * 20.0;
            if (Math.abs(grams - expected) > 0.01) {
                throw new IllegalStateException(String.format(Locale.ROOT, "nach %d Schüssen Beladung %.0f g statt %.0f g", i, grams, expected));
            }
            masses.add(String.format(Locale.ROOT, "%.3f", samples.get(i)[1]));
            if (i > 0) {
                double drop = samples.get(i - 1)[1] - samples.get(i)[1];
                if (Math.abs(drop - 0.020) > 0.006) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "Schuss %d: Masse im Flugmodell %.3f → %.3f kg (−%.1f g statt −20 g); Reihe %s", i,
                            samples.get(i - 1)[1], samples.get(i)[1], drop * 1000.0, masses));
                }
            }
        }
        note(state, String.format(Locale.ROOT, "Beladung 320 → 0 g in 16 Schritten zu 20 g, Masse im Flugmodell %.3f → %.3f kg", samples.getFirst()[1],
                samples.getLast()[1]));
    }

    private static void ground(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        Vec3 at = Vec3.atBottomCenterOf(Arena.ground(0, -6).above());
        drone.setDeltaMovement(Vec3.ZERO);
        drone.teleportTo(at.x, at.y, at.z);
    }

    static Script service(String label, int grams) {
        return Script.create()
                .server("Wartung öffnen (" + label + ")", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    Entity drone = Pilot.serverDrone(server, state);
                    player.closeContainer();
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setShiftKeyDown(true);
                    player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
                    player.setShiftKeyDown(false);
                })
                .await("Wartung am Client", 100, () -> Minecraft.getInstance().gui.screen() != null)
                .then("Knopf Zubehör", (ctx, ticks) -> {
                    Screen screen = Minecraft.getInstance().gui.screen();
                    ctx.state().put(LABEL, screen == null ? "" : screen.getClass().getName());
                    return screen == null ? StepResult.fail("Wartung zu") : StepResult.pass();
                })
                .server("Zubehör öffnen", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    if (!player.containerMenu.clickMenuButton(player, SERVICE_ACCESSORY_BUTTON)) {
                        throw new IllegalStateException("Wartung nimmt den Knopf Zubehör nicht an");
                    }
                })
                .then("Beladung im Zubehör-Inventar (" + label + ")", (ctx, ticks) -> payloadLabel(ctx, ticks, label, grams))
                .run("Zubehör-Inventar schließen", () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null) {
                        mc.player.closeContainer();
                    }
                    mc.gui.setScreen(null);
                })
                .server("Menü am Server zu", (server, state) -> Arena.player(server).closeContainer())
                .sleep(5);
    }

    static Pattern loadPattern() {
        String text = I18n.get("propwash.accessory.menu.load", "§§");
        int at = text.indexOf("§§");
        return Pattern.compile(Pattern.quote(text.substring(0, at)) + "([0-9][0-9.,]*)" + Pattern.quote(text.substring(at + 2)));
    }

    public static StepResult payloadLabel(SelfTest.StepContext ctx, int ticks, String label, int grams) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null || screen.getClass().getName().equals(ctx.state().get(LABEL)) || ticks < 10) {
            return ticks > 120 ? StepResult.fail("Zubehör-Inventar öffnet nicht") : StepResult.pending();
        }
        Pattern pattern = loadPattern();
        List<String> lines = CovMdAScreenText.lines(screen);
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find()) {
                labels.add(line);
                values.add(Integer.parseInt(m.group(1).replace(".", "").replace(",", "")));
            }
        }
        if (labels.isEmpty()) {
            return ticks > 120 ? StepResult.fail(label + ": keine Beladung im Zubehör-Inventar: " + lines) : StepResult.pending();
        }
        boolean shown = grams == 0 ? values.stream().allMatch(value -> value == 0) : values.contains(grams);
        if (!shown) {
            return ticks > 120 ? StepResult.fail(label + ": " + labels + " statt " + grams + " g") : StepResult.pending();
        }
        return StepResult.pass(label + ": " + labels);
    }

    private static void zombies(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = Pilot.serverDrone(server, state);
        DroneView view = DispenserRig.view(drone);
        Vec3 forward = new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double[][] places = {{9.5, -0.7}, {9.5, 0.0}, {9.5, 0.7}, {10.5, -0.7}, {10.5, 0.0}, {10.5, 0.7}, {11.5, -0.7}, {11.5, 0.0}, {11.5, 0.7}};
        CovMdAWatch.Watch probe = (CovMdAWatch.Watch) state.get(RANGING);
        double arrival = probe == null ? Double.NaN : probe.heightAt(10.0);
        if (Double.isNaN(arrival)) {
            throw new IllegalStateException("Probeschuss erreicht 10 m nicht (" + (probe == null ? "kein Pfeil" : probe.maxAlong() + " m") + ")");
        }
        double feet = Math.max(Arena.ground().getY() + 1.0, Math.floor(arrival - ZOMBIE_HALF_HEIGHT));
        if (feet > Arena.ground().getY() + 1.0) {
            Vec3 middle = new Vec3(drone.getX(), feet - 1.0, drone.getZ()).add(forward.scale(10.5));
            BlockPos center = BlockPos.containing(middle);
            Arena.fill(level, center.offset(-2, 0, -3), center.offset(2, 0, 3), Blocks.STONE.defaultBlockState());
        }
        note(state, String.format(Locale.ROOT, "Mündung %.1f m über Grund, Probeschuss nach 10 m auf %.1f m, Zombies stehen %.0f m über Grund",
                view.center().y - (Arena.ground().getY() + 1.0), arrival - (Arena.ground().getY() + 1.0), feet - (Arena.ground().getY() + 1.0)));
        List<CovMdAWatch.Watch> watches = new ArrayList<>();
        for (double[] place : places) {
            Vec3 at = new Vec3(drone.getX(), feet, drone.getZ()).add(forward.scale(place[0])).add(right.scale(place[1]));
            Zombie zombie = CovMdAShotSteps.spawn(level, EntityTypes.ZOMBIE, at);
            watches.add(CovMdAWatch.watch(zombie, forward, 400));
        }
        state.put(ZOMBIES, watches);
    }

    private static void ranging(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        int before = DispenserLog.counts(drone).dispensed();
        pulse(server, state);
        DispenserLog.Entry entry = DispenserLog.last(drone).orElseThrow();
        Entity arrow = entry.result() == null ? null : entry.result().entity().orElse(null);
        if (DispenserLog.counts(drone).dispensed() != before + 1 || arrow == null) {
            throw new IllegalStateException("Probeschuss: " + entry.status());
        }
        Vec3 forward = entry.forward();
        state.put(RANGING, CovMdAWatch.watch(arrow, new Vec3(forward.x, 0.0, forward.z), 60));
        Arena.track(arrow);
    }

    private static StepResult calm(SelfTest.StepContext ctx, int ticks) {
        FlightState state = HoverAssist.last();
        if (state != null && ticks > 30 && Math.hypot(state.vx(), state.vz()) < CALM_SPEED && Math.abs(state.vy()) < CALM_CLIMB) {
            return StepResult.pass(String.format(Locale.ROOT, "ruhig bei %.2f m/s", Math.hypot(state.vx(), state.vz())));
        }
        return ticks > 800 ? StepResult.fail("kommt nicht zur Ruhe" + (state == null ? "" : String.format(Locale.ROOT, ": %.2f m/s waagerecht, %.2f m/s senkrecht",
                Math.hypot(state.vx(), state.vz()), state.vy()))) : StepResult.pending();
    }

    private static double tilt(FlightState state) {
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, state.upY()))));
    }

    private static StepResult attitude(SelfTest.StepContext ctx, String key) {
        FlightState state = HoverAssist.last();
        if (state == null) {
            return StepResult.fail("keine Flugdaten");
        }
        ctx.state().put(key, new double[]{state.y(), tilt(state)});
        return StepResult.pass(String.format(Locale.ROOT, "Höhe %.2f, Neigung %.1f°", state.y(), tilt(state)));
    }

    private static StepResult attitudeAfter(SelfTest.StepContext ctx, int ticks) {
        FlightState state = HoverAssist.last();
        double[] before = (double[]) ctx.state().get(BEFORE);
        if (state == null || before == null) {
            return StepResult.fail("keine Flugdaten");
        }
        double dy = state.y() - before[0];
        double tilt = tilt(state);
        String text = String.format(Locale.ROOT, "3 s nach der Salve Höhe %+.2f m, Neigung %.1f° (vorher %.1f°)", dy, tilt, before[1]);
        if (Math.abs(dy) > MAX_HEIGHT_CHANGE || tilt > MAX_TILT_DEGREES) {
            return StepResult.fail(text);
        }
        note(ctx.state(), text);
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static void volley(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        List<DispenserLog.Entry> entries = since(drone, (long) state.get(START));
        List<Double> recoils = new ArrayList<>();
        for (DispenserLog.Entry entry : entries) {
            if (entry.result() != null && entry.result().success()) {
                recoils.add(entry.result().recoilNs());
                if (Math.abs(entry.result().recoilNs() - ARROW_RECOIL_NS) > 0.1 * ARROW_RECOIL_NS) {
                    throw new IllegalStateException(String.format(Locale.ROOT, "Rückstoß %.3f N·s statt %.2f ±10 %%", entry.result().recoilNs(), ARROW_RECOIL_NS));
                }
            }
        }
        if (recoils.size() != 16) {
            throw new IllegalStateException(recoils.size() + " Pfeile statt 16");
        }
        Vec3 forward = DispenserRig.view(drone).forward();
        Vec3 flat = new Vec3(forward.x, 0.0, forward.z).normalize();
        List<String> landed = new ArrayList<>();
        for (DispenserLog.Entry entry : entries) {
            if (entry.result() == null) {
                continue;
            }
            entry.result().entity().ifPresent(arrow -> landed.add(String.format(Locale.ROOT, "%.1f/%.1f", arrow.position().subtract(entry.muzzle()).dot(flat),
                    arrow.getY() - (Arena.ground().getY() + 1.0))));
        }
        int hits = 0;
        List<String> perZombie = new ArrayList<>();
        for (CovMdAWatch.Watch watch : (List<CovMdAWatch.Watch>) state.get(ZOMBIES)) {
            hits += watch.hurts();
            perZombie.add(String.valueOf(watch.hurts()));
        }
        double rate = hits / 16.0;
        String text = String.format(Locale.ROOT, "16 Pfeile, %d Treffer mit Lebensverlust (%.0f %%, je Zombie %s), Rückstoß je Schuss %.2f N·s; Pfeile "
                + "(Weite/Höhe über Grund) %s", hits, rate * 100.0, perZombie, recoils.getFirst(), landed);
        if (rate < MIN_HIT_RATE) {
            throw new IllegalStateException(text + " – Trefferquote unter " + Math.round(MIN_HIT_RATE * 100.0) + " %");
        }
        note(state, text);
    }

    @SuppressWarnings("unchecked")
    private static void lookAtZombies(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Pilot.serverDrone(server, state);
        List<CovMdAWatch.Watch> watches = (List<CovMdAWatch.Watch>) state.get(ZOMBIES);
        Vec3 center = watches.get(1).entity().position();
        Vec3 back = new Vec3(drone.getX() - center.x, 0.0, drone.getZ() - center.z).normalize();
        Vec3 side = new Vec3(-back.z, 0.0, back.x);
        Vec3 eye = center.add(back.scale(4.0)).add(side.scale(2.5)).add(0.0, 2.2, 0.0);
        double dx = center.x - eye.x;
        double dz = center.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(center.y + 1.0 - eye.y, Math.hypot(dx, dz)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch, true);
    }

    private static void cruise(double speed) {
        Pilot.drone().flatMap(d -> PropwashApi.drones().view(d)).ifPresent(view -> {
            Vec3 flat = new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
            HoverAssist.cruise(HoverAssist.targetY(), flat.x * speed, flat.z * speed);
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

    @SuppressWarnings("unchecked")
    private static void launch(MinecraftServer server, Map<String, Object> state, String label) {
        Entity drone = Pilot.serverDrone(server, state);
        int before = DispenserLog.counts(drone).dispensed();
        pulse(server, state);
        if (DispenserLog.counts(drone).dispensed() != before + 1) {
            throw new IllegalStateException(label + ": kein Schuss");
        }
        DispenserLog.Entry entry = DispenserLog.last(drone).orElseThrow();
        Vec3 forward = entry.forward().normalize();
        double arrow = entry.launch().dot(forward) * 20.0;
        double flight = entry.droneVelocity().dot(forward);
        ((Map<String, double[]>) state.computeIfAbsent(LAUNCH, key -> new java.util.LinkedHashMap<String, double[]>())).put(label, new double[]{arrow, flight});
        note(state, String.format(Locale.ROOT, "%s: Pfeil %.1f m/s entlang der Nase, Drohne %.1f m/s", label, arrow, flight));
        entry.result().entity().ifPresent(Arena::track);
    }

    @SuppressWarnings("unchecked")
    private static void launchFaster(MinecraftServer server, Map<String, Object> state) {
        Map<String, double[]> launches = (Map<String, double[]>) state.get(LAUNCH);
        double[] hover = launches.get("Schwebeflug");
        double[] cruise = launches.get("Vorwärtsflug");
        double gain = cruise[0] - hover[0];
        if (cruise[1] < 3.0 || gain < 0.5 * cruise[1]) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Pfeil im Vorwärtsflug %.1f m/s, im Schwebeflug %.1f m/s (Drohne %.1f m/s)", cruise[0], hover[0],
                    cruise[1]));
        }
        note(state, String.format(Locale.ROOT, "im Vorwärtsflug %.1f m/s schneller", gain));
    }

    private static Script wind(String label) {
        return Script.create()
                .then("ruhig (" + label + ")", CovMdAFlightSteps::calm)
                .run("Flughilfe nur Höhe halten (" + label + ")", () -> Pilot.drone().ifPresent(d -> HoverAssist.hover(HoverAssist.targetY())))
                .sleep(10)
                .run("Rückstoß messen (" + label + ")", () -> Pilot.drone().flatMap(d -> PropwashApi.drones().view(d))
                        .ifPresent(view -> HoverAssist.track(view.forward().x, view.forward().z)))
                .server("Windkugel (" + label + ")", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    int before = DispenserLog.counts(drone).dispensed();
                    pulse(server, state);
                    if (DispenserLog.counts(drone).dispensed() != before + 1) {
                        throw new IllegalStateException(label + ": keine Windkugel");
                    }
                    state.put(BACK + ".mass." + label, DispenserRig.view(drone).massKg());
                })
                .sleep(30)
                .then("zurückgestoßen (" + label + ")", (ctx, ticks) -> {
                    double back = -HoverAssist.minAlong();
                    ctx.state().put(BACK + "." + label, back);
                    return StepResult.pass(String.format(Locale.ROOT, "%s %.2f m/s zurück, Neigung +%.1f°", label, back, HoverAssist.maxTiltChange()));
                });
    }

    private static void windCompare(MinecraftServer server, Map<String, Object> state) {
        double five = (double) state.get(BACK + ".5″");
        double seven = (double) state.get(BACK + ".7″");
        double fiveMass = (double) state.get(BACK + ".mass.5″");
        double sevenMass = (double) state.get(BACK + ".mass.7″");
        String text = String.format(Locale.ROOT, "Windkugel: 5″ (%.2f kg) %.2f m/s zurück, 7″ (%.2f kg) %.2f m/s zurück", fiveMass, five, sevenMass, seven);
        if (!(five > 0.2) || !(seven < five)) {
            throw new IllegalStateException(text);
        }
        note(state, text);
    }

    private static void ownRig(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        player.setYRot(180.0F);
        player.setYHeadRot(180.0F);
        Entity drone = Arena.track(DispenserRig.place(level, player, DispenserRig.fiveInch(), "CovMdA eigene Geschosse", Arena.ground(0, 20)));
        state.put(Pilot.DRONE, drone.getUUID());
        state.put(OWN, drone);
        Vec3 forward = DispenserRig.view(drone).forward();
        if (forward.z > -0.9) {
            throw new IllegalStateException("Drohne schaut nach " + forward + " statt nach Norden");
        }
        CovMdAWatch.install();
        CovMdAWatch.clear();
    }

    private static Script ownRun(String label, Supplier<ItemStack> ammo, Vec3 direction) {
        return Script.create()
                .server(label + ": Start", (server, state) -> {
                    Entity drone = own(state, label);
                    drone.setDeltaMovement(Vec3.ZERO);
                    Vec3 start = Vec3.atBottomCenterOf(Arena.ground(0, 20)).add(0.0, 6.0, 0.0);
                    drone.setNoGravity(true);
                    drone.teleportTo(start.x, start.y, start.z);
                    DispenserRig.load(drone, ammo.get().copyWithCount(10));
                    state.put(MOVER, CovMdAWatch.move(drone, direction.normalize().scale(OWN_SHOT_SPEED), 60));
                    state.put(COUNT, DispenserLog.counts(drone).dispensed());
                    state.put(START, drone.level().getGameTime());
                })
                .sleep(8)
                .then(label, CovMdBEach.each(state -> 10, COOLDOWN, (server, state, index) -> {
                    Entity drone = own(state, label);
                    int before = DispenserLog.counts(drone).dispensed();
                    pulse(server, state);
                    if (DispenserLog.counts(drone).dispensed() > before) {
                        DispenserLog.last(drone).map(DispenserLog.Entry::result).flatMap(result -> result.entity()).ifPresent(shot -> launched(state).add(shot
                                .getUUID()));
                    }
                }))
                .server(label + ": Ende", (server, state) -> {
                    Entity drone = own(state, label);
                    ((CovMdAWatch.Mover) state.get(MOVER)).stop();
                    int fired = DispenserLog.counts(drone).dispensed() - (int) state.get(COUNT);
                    double speed = 0.0;
                    Set<UUID> launched = launched(state);
                    for (DispenserLog.Entry entry : since(drone, (long) state.get(START))) {
                        speed = Math.max(speed, entry.droneVelocity().length());
                        if (entry.result() != null) {
                            entry.result().entity().ifPresent(shot -> {
                                Arena.track(shot);
                                launched.add(shot.getUUID());
                            });
                        }
                    }
                    if (fired != 10) {
                        throw new IllegalStateException(label + ": " + fired + " Schüsse statt 10");
                    }
                    note(state, String.format(Locale.ROOT, "%s: 10 Schüsse, Drohne laut Propwash %.1f m/s", label, speed));
                })
                .sleep(10);
    }

    private static Script caughtUp() {
        String label = "Windkugel eingeholt";
        return Script.create()
                .server(label + ": Schuss im Schweben", (server, state) -> {
                    Entity drone = own(state, label);
                    Vec3 start = Vec3.atBottomCenterOf(Arena.ground(0, 20)).add(0.0, 6.0, 0.0);
                    drone.setNoGravity(true);
                    drone.setDeltaMovement(Vec3.ZERO);
                    drone.teleportTo(start.x, start.y, start.z);
                    state.put(MOVER, CovMdAWatch.move(drone, Vec3.ZERO, 3 * OWN_SHOT_PROTECTION_TICKS + 40));
                    DispenserRig.load(drone, new ItemStack(Items.WIND_CHARGE, 1));
                    int before = DispenserLog.counts(drone).dispensed();
                    pulse(server, state);
                    if (DispenserLog.counts(drone).dispensed() != before + 1) {
                        throw new IllegalStateException(label + ": keine Windkugel");
                    }
                    Entity shot = DispenserLog.last(drone).map(DispenserLog.Entry::result).flatMap(result -> result.entity())
                            .orElseThrow(() -> new IllegalStateException(label + ": Schuss ohne Geschoss"));
                    Arena.track(shot);
                    launched(state).add(shot.getUUID());
                    state.put(CAUGHT, shot);
                })
                .sleep(OWN_SHOT_PROTECTION_TICKS + 2)
                .server(label + ": Windkugel vor der Drohne, Abstand schrumpft mit " + (int) CAUGHT_CLOSING_SPEED + " m/s", (server, state) -> {
                    Entity drone = own(state, label);
                    Entity shot = (Entity) state.get(CAUGHT);
                    if (shot.isRemoved()) {
                        throw new IllegalStateException(label + ": Windkugel schon vor dem Test weg");
                    }
                    Vec3 forward = new Vec3(0.0, 0.0, -1.0);
                    Vec3 at = drone.getBoundingBox().getCenter().add(forward.scale(CAUGHT_DISTANCE));
                    shot.setPos(at.x, at.y, at.z);
                    shot.setDeltaMovement(forward.scale(-CAUGHT_CLOSING_SPEED / 20.0));
                    if (shot instanceof AbstractHurtingProjectile hurting) {
                        hurting.accelerationPower = 0.0;
                    }
                })
                .sleep(20)
                .server(label + ": Ende", (server, state) -> {
                    Entity drone = own(state, label);
                    ((CovMdAWatch.Mover) state.get(MOVER)).stop();
                    Entity shot = (Entity) state.get(CAUGHT);
                    List<CovMdAWatch.Damage> hits = new ArrayList<>();
                    for (CovMdAWatch.Damage damage : CovMdAWatch.damages(drone)) {
                        if (damage.direct() == shot && !damage.cancelled()) {
                            hits.add(damage);
                        }
                    }
                    if (!hits.isEmpty()) {
                        throw new IllegalStateException(label + ": die eigene Windkugel trifft die Drohne: " + describe(hits.get(0)));
                    }
                    double behind = -shot.position().subtract(drone.getBoundingBox().getCenter()).dot(new Vec3(0.0, 0.0, -1.0));
                    if (shot.isRemoved() || behind < 1.0) {
                        throw new IllegalStateException(label + ": die Windkugel ist nicht durch die Drohne geflogen (" + (shot.isRemoved() ? "an der Drohne zerplatzt"
                                : String.format(Locale.ROOT, "%.1f m hinter der Drohne", behind)) + ")");
                    }
                    note(state, String.format(Locale.ROOT, "%s: Windkugel 3 m vor der Drohne mit %.0f m/s eingeholt, fliegt durch die Hülle und liegt %.1f m "
                            + "dahinter, kein Zusammenstoß, kein Schaden, Drohne bleibt", label, CAUGHT_CLOSING_SPEED, behind));
                })
                .sleep(5);
    }

    private static String describe(CovMdAWatch.Damage damage) {
        return String.format(Locale.ROOT, "%s %.1f %s", damage.source(), damage.amount(), damage.cancelled() ? "abgewehrt" : "angekommen");
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> launched(Map<String, Object> state) {
        return (Set<UUID>) state.computeIfAbsent(LAUNCHED, key -> new HashSet<UUID>());
    }

    private static Entity own(Map<String, Object> state, String label) {
        Entity drone = (Entity) state.get(OWN);
        if (drone.isRemoved()) {
            Set<UUID> launched = launched(state);
            List<String> damages = new ArrayList<>();
            for (CovMdAWatch.Damage damage : CovMdAWatch.damages(drone)) {
                damages.add(String.format(Locale.ROOT, "%s durch %s (%s, Verursacher %s) %.1f%s", damage.source(), CovMdAWatch.type(damage.direct()),
                        damage.direct() != null && launched.contains(damage.direct().getUUID()) ? "eigenes Geschoss" : "fremdes Geschoss",
                        CovMdAWatch.type(damage.cause()), damage.amount(), damage.cancelled() ? " abgewehrt" : ""));
            }
            throw new IllegalStateException(label + ": Drohne entfernt (" + drone.getRemovalReason() + ") bei " + drone.position() + ", Schäden " + damages);
        }
        return drone;
    }

    private static void noOwnHits(MinecraftServer server, Map<String, Object> state) {
        Entity drone = own(state, "Auswertung");
        List<String> own = new ArrayList<>();
        List<String> other = new ArrayList<>();
        int blocked = 0;
        Set<UUID> launched = launched(state);
        for (CovMdAWatch.Damage damage : CovMdAWatch.damages(drone)) {
            String text = damage.source() + " durch " + CovMdAWatch.type(damage.direct());
            if (damage.direct() != null && launched.contains(damage.direct().getUUID())) {
                if (damage.cancelled()) {
                    blocked++;
                } else {
                    own.add(text);
                }
            } else {
                other.add(text);
            }
        }
        if (!own.isEmpty()) {
            throw new IllegalStateException("eigene Drohne von eigenem Geschoss beschädigt: " + own + (other.isEmpty() ? "" : ", dazu fremd " + other));
        }
        note(state, "kein Schaden durch die " + launched.size() + " eigenen Geschosse" + (blocked == 0 ? "" : " (" + blocked + " Treffer abgewehrt)")
                + (other.isEmpty() ? "" : ", fremde Schäden " + other));
    }
}
