package de.lardtom.mobdefense.dev.dispenser.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.client.CoreSteps;
import de.lardtom.mobdefense.dev.dispenser.CovMdAWatch;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.Rules;
import de.lardtom.mobdefense.dispenser.DispenserHolds;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.FlightState;
import de.lardtom.propwash.ui.client.config.StepSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class OwnMSteps {
    public static final String TAB_ID = "mobdefense:settings";
    public static final String VANILLA_FRONT = "minecraft:block/dispenser_front";
    public static final String CONFIG_FILE = "mobdefense-client.json";
    public static final String DELAY_KEY = "dispenser_delay_ticks";
    public static final String INTERVAL_KEY = "dispenser_interval_ticks";
    public static final int DELAY = 10;
    public static final int INTERVAL = 4;
    public static final int KEY_PRESS = 5;
    public static final int SHORT_PRESS = 8;
    public static final int LONG_PRESS = 40;
    public static final int SET_DELAY = 15;
    public static final int SET_INTERVAL = 10;
    public static final int RULE_COOLDOWN = 20;
    public static final double POTION_KG = 0.35;
    public static final double MAX_RATE_DPS = 670.0;
    public static final double MAX_TILT_DEGREES = 30.0;
    public static final double MIN_POTION_TRAVEL = 3.0;
    public static final int POTIONS = 4;
    public static final int[][] TAB_SIZES = {{854, 480, 2}, {320, 240, 2}};
    private static final String START = "ownm.start";
    private static final String SHOTS = "ownm.shots";
    private static final String MASS = "ownm.mass";
    private static final String WATCH = "ownm.watch";
    private static final String RESULT = "ownm.result";
    private static final String RULES = "ownm.rules";
    private static final String HELD = "ownm.held";

    private OwnMSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("Einzelschuss – Ziffernblock 9 " + KEY_PRESS + " Ticks und Taster der Funke " + SHORT_PRESS + " Ticks gedrückt (kurzer Druck): genau ein "
                + "Schuss", 3000,
                CovMdAFlightSteps.flight(DispenserRig.fiveInch(), "OwnM Einzelschuss", 3.0, () -> new ItemStack(Items.ARROW, 16))
                        .server("vorher", OwnMSteps::mark)
                        .append(Pilot.press(Pilot.ACTION_1, KEY_PRESS))
                        .sleep(16)
                        .server("Taste: Schüsse", (server, state) -> state.put(RESULT + ".key", shots(server, state, 1, 1, -1, -1, "Taste " + KEY_PRESS
                                + " Ticks")))
                        .then("Taste", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT + ".key")))
                        .server("Taster der Funke an", (server, state) -> {
                            mark(server, state);
                            CovMdAFlightSteps.trigger(server, state, true);
                            CovMdAWatch.later(SHORT_PRESS, () -> CovMdAFlightSteps.trigger(server, state, false));
                        })
                        .sleep(SHORT_PRESS + 16)
                        .server("Taster: Schüsse", (server, state) -> state.put(RESULT + ".button", shots(server, state, 1, 1, -1, -1, "Taster " + SHORT_PRESS
                                + " Ticks")))
                        .then("Taster", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT + ".button"))), OwnMSteps::cleanup);
        steps.add("Anlauf beim Halten – Ziffernblock 9 " + LONG_PRESS + " Ticks gehalten: erster Schuss sofort, der zweite nach " + DELAY
                + " Ticks Anlauf, danach alle " + INTERVAL + " Ticks", 3000,
                CovMdAFlightSteps.flight(DispenserRig.fiveInch(), "OwnM Anlauf", 3.0, () -> new ItemStack(Items.ARROW, 16))
                        .server("vorher", OwnMSteps::mark)
                        .append(Pilot.press(Pilot.ACTION_1, LONG_PRESS))
                        .sleep(10)
                        .server("Takt", (server, state) -> state.put(RESULT, shots(server, state, 8, 10, DELAY, INTERVAL, "Taste " + LONG_PRESS + " Ticks")))
                        .then("Ergebnis", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT))), OwnMSteps::cleanup);
        steps.add("Tab „Mob Defense“ im K-Menü – Anlauf und Abstand einstellen und speichern, Dauerfeuer im neuen Takt, die Welt-Regel "
                + "dispenser_cooldown_ticks ist die Untergrenze, GUI 854×480 und 320×240, DE und EN", 9000, tab(), OwnMSteps::cleanup);
        steps.add("Wurftrank im Schwebeflug – " + POTIONS + " Würfe nacheinander bis zum letzten Trank: Rückstoß aus Masse und Tempo des Tranks, die 5″ "
                + "bleibt nach jedem Wurf steuerbar, kein Trank trifft die eigene Drohne", 12000, potion(), OwnMSteps::cleanup);
        steps.add("Vorderseite der Dispenser-Halterung – Verweis auf " + VANILLA_FRONT + " im Modell, Foto von vorn", 1500, Script.create()
                .check("Verweis im geladenen Modell", OwnMSteps::frontReference)
                .server("5″ mit Dispenser von vorn", OwnMSteps::front)
                .sleep(20)
                .append(Shots.photos("ownm_dispenser_front")), OwnMSteps::cleanup);
    }

    private static StepResult frontReference() {
        Identifier model = Identifier.fromNamespaceAndPath("mobdefense", "models/part/accessory/dispenser_mount.json");
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(model)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var element : json.getAsJsonArray("elements")) {
                JsonObject object = element.getAsJsonObject();
                if (!"front_plate".equals(object.get("name").getAsString())) {
                    continue;
                }
                String reference = object.getAsJsonObject("faces").getAsJsonObject("north").get("texture").getAsString();
                String sprite = json.getAsJsonObject("textures").get(reference.substring(1)).getAsString();
                return sprite.equals(VANILLA_FRONT) ? StepResult.pass("front_plate Nord: " + reference + " = " + sprite)
                        : StepResult.fail("front_plate Nord zeigt " + sprite + " statt " + VANILLA_FRONT);
            }
            return StepResult.fail("kein Element front_plate");
        } catch (IOException | RuntimeException e) {
            return StepResult.fail("Modell nicht lesbar: " + e);
        }
    }

    private static void front(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Arena.track(DispenserRig.place(player.level(), player, DispenserRig.fiveInch(), "OwnM Vorderseite", Arena.ground(0, -4)));
        state.put(Pilot.DRONE, drone.getUUID());
        DroneView view = DispenserRig.view(drone);
        Vec3 forward = new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 center = view.center();
        Vec3 eye = center.add(forward.scale(0.55)).add(side.scale(0.25)).add(0.0, 0.18, 0.0);
        double dx = center.x - eye.x;
        double dz = center.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(center.y - eye.y, Math.hypot(dx, dz)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.level(), eye.x, eye.y - player.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch, true);
    }

    private static Script tab() {
        Script script = Script.create();
        for (int[] size : TAB_SIZES) {
            String name = "ownm_tab_" + size[0] + "x" + size[1];
            script.then("Fenster für GUI " + size[0] + "x" + size[1], (ctx, ticks) -> Shots.window(ctx, ticks, size[0] * size[2], size[1] * size[2], size[2]))
                    .then("Tab öffnen (" + size[0] + "x" + size[1] + ")", OwnMSteps::openTab)
                    .then("Regler sichtbar (" + size[0] + "x" + size[1] + ")", OwnMSteps::slidersVisible)
                    .append(Shots.photos(name, CoreSteps::parkMouse))
                    .run("Tab schließen", () -> Minecraft.getInstance().gui.setScreen(null));
        }
        return script.run("Fenster zurück", () -> Shots.restoreWindow(Minecraft.getInstance()))
                .then("Tab öffnen", OwnMSteps::openTab)
                .then("Anlauf " + SET_DELAY + " und Abstand " + SET_INTERVAL + " Ticks", (ctx, ticks) -> select(SET_DELAY, SET_INTERVAL))
                .then("speichern (Strg+S)", (ctx, ticks) -> save(ticks, SET_DELAY, SET_INTERVAL))
                .append(CovMdAFlightSteps.flight(DispenserRig.fiveInch(), "OwnM Tab", 3.0, () -> new ItemStack(Items.ARROW, 16)))
                .sleep(10)
                .server("vorher", OwnMSteps::mark)
                .append(Pilot.press(Pilot.ACTION_1, 60))
                .sleep(10)
                .server("Takt aus dem Tab", (server, state) -> state.put(RESULT + ".tab", shots(server, state, 5, 7, SET_DELAY, SET_INTERVAL, "eingestellt "
                        + SET_DELAY + "/" + SET_INTERVAL + " Ticks, 60 Ticks gehalten")))
                .then("Takt aus dem Tab", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT + ".tab")))
                .server("Welt-Regel dispenser_cooldown_ticks " + RULE_COOLDOWN, (server, state) -> {
                    Rules rules = new Rules(server);
                    rules.set(RuleDefaults.DISPENSER_COOLDOWN_TICKS, RULE_COOLDOWN);
                    state.put(RULES, rules);
                })
                .sleep(10)
                .server("vorher", OwnMSteps::mark)
                .append(Pilot.press(Pilot.ACTION_1, 60))
                .sleep(10)
                .server("Takt mit Welt-Regel", (server, state) -> {
                    state.put(RESULT + ".rule", shots(server, state, 3, 4, RULE_COOLDOWN, RULE_COOLDOWN, "Welt-Regel " + RULE_COOLDOWN
                            + " Ticks, 60 Ticks gehalten"));
                    ((Rules) state.remove(RULES)).restore();
                })
                .then("Takt mit Welt-Regel", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT + ".rule")))
                .run("Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
                .server("Flug beenden", (server, state) -> Pilot.release(server))
                .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                .then("Tab öffnen", OwnMSteps::openTab)
                .then("Standard " + DELAY + "/" + INTERVAL + " Ticks", (ctx, ticks) -> select(DELAY, INTERVAL))
                .then("speichern (Strg+S)", (ctx, ticks) -> save(ticks, DELAY, INTERVAL));
    }

    static String potionLabel(int shot) {
        return "Wurftrank " + shot + " von " + POTIONS + " (" + (POTIONS - shot + 1) + " geladen)";
    }

    private static Script potion() {
        Script script = CovMdAFlightSteps.flight(DispenserRig.fiveInch(), "OwnM Wurftrank", 4.0, () -> DispenserSteps.potion(Items.SPLASH_POTION, Potions.WATER))
                .server(POTIONS + " Wurftränke", (server, state) -> {
                    ItemStack[] potions = new ItemStack[POTIONS];
                    for (int i = 0; i < POTIONS; i++) {
                        potions[i] = DispenserSteps.potion(Items.SPLASH_POTION, Potions.WATER);
                    }
                    DispenserRig.load(Pilot.serverDrone(server, state), potions);
                });
        for (int shot = 1; shot <= POTIONS; shot++) {
            if (shot > 1) {
                script.run("Position halten vor " + potionLabel(shot), CovMdAFlightSteps::hold);
            }
            script.append(measure(potionLabel(shot), entity -> entity instanceof AbstractThrownPotion));
        }
        return script.run("Flughilfe aus", () -> Pilot.land(Minecraft.getInstance()))
                .server("Flug beenden", (server, state) -> Pilot.release(server))
                .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                .server("Drohne weg", (server, state) -> Pilot.serverDrone(server, state).discard())
                .sleep(10)
                .append(CovMdAFlightSteps.flight(DispenserRig.fiveInch(), "OwnM Windkugel", 4.0, () -> new ItemStack(Items.WIND_CHARGE, 4)))
                .append(measure("Windkugel zum Vergleich", entity -> entity instanceof WindCharge))
                .then("Vergleich", OwnMSteps::compare);
    }

    private static Script measure(String label, Predicate<Entity> kind) {
        return Script.create()
                .then("ruhig (" + label + ")", OwnMSteps::calm)
                .run("Flughilfe nur Höhe (" + label + ")", () -> HoverAssist.hover(HoverAssist.targetY()))
                .sleep(20)
                .run("Messung beginnt (" + label + ")", () -> Pilot.drone().flatMap(d -> PropwashApi.drones().view(d))
                        .ifPresent(view -> HoverAssist.track(view.forward().x, view.forward().z)))
                .server(label + " feuern", (server, state) -> {
                    Entity drone = Pilot.serverDrone(server, state);
                    state.put(MASS + "." + label, DispenserRig.view(drone).massKg());
                    int before = DispenserLog.counts(drone).dispensed();
                    CovMdAFlightSteps.pulse(server, state);
                    if (DispenserLog.counts(drone).dispensed() != before + 1) {
                        throw new IllegalStateException(label + ": kein Schuss (" + DispenserLog.last(drone).map(DispenserLog.Entry::status).orElse("-") + ")");
                    }
                    DispenserLog.Entry entry = DispenserLog.last(drone).orElseThrow();
                    Entity shot = entry.result().entity().orElseThrow(() -> new IllegalStateException(label + ": kein Geschoss"));
                    if (!kind.test(shot)) {
                        throw new IllegalStateException(label + ": " + CovMdAWatch.type(shot));
                    }
                    state.put(SHOTS + "." + label, entry);
                    state.put(WATCH + "." + label, CovMdAWatch.watch(shot, entry.forward(), 60));
                })
                .sleep(40)
                .then("Lage nach dem Schuss (" + label + ")", (ctx, ticks) -> {
                    double[] motion = {-HoverAssist.minAlong(), HoverAssist.maxTiltChange(), HoverAssist.maxRateDps()};
                    ctx.state().put(RESULT + "." + label, motion);
                    return StepResult.pass(String.format(Locale.ROOT, "%s: %.2f m/s zurück, Neigung +%.1f°, Drehrate bis %.0f °/s", label, motion[0], motion[1],
                            motion[2]));
                })
                .server("Geschoss und Drohne (" + label + ")", (server, state) -> state.put(RESULT + ".shot." + label, shot(server, state, label)))
                .then("Geschoss (" + label + ")", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT + ".shot." + label)));
    }

    private static String shot(MinecraftServer server, Map<String, Object> state, String label) {
        Entity drone = Pilot.serverDrone(server, state);
        DispenserLog.Entry entry = (DispenserLog.Entry) state.get(SHOTS + "." + label);
        CovMdAWatch.Watch watch = (CovMdAWatch.Watch) state.get(WATCH + "." + label);
        double relative = entry.launch().scale(20.0).subtract(entry.droneVelocity()).length();
        double recoil = entry.result().recoilNs();
        double travel = watch.last().subtract(watch.start()).dot(entry.forward().normalize());
        List<String> own = new ArrayList<>();
        for (CovMdAWatch.Damage damage : CovMdAWatch.damages(drone)) {
            if (damage.direct() == watch.entity() || damage.cause() == watch.entity()) {
                own.add(damage.source() + " " + damage.amount());
            }
        }
        String text = String.format(Locale.ROOT, "%s: %.1f m/s gegen die Drohne, Rückstoß %.2f N·s, Drohne %.2f kg, %s nach %.1f m", label, relative, recoil,
                (double) state.get(MASS + "." + label), watch.removedAt() < 0 ? "fliegt noch" : "zerbrochen nach " + watch.removedAt() + " Ticks", travel);
        if (!own.isEmpty()) {
            throw new IllegalStateException(text + " – trifft die eigene Drohne: " + own);
        }
        if (watch.removedAt() >= 0 && travel < MIN_POTION_TRAVEL) {
            throw new IllegalStateException(text + " – zerbricht an der eigenen Drohne (unter " + MIN_POTION_TRAVEL + " m)");
        }
        if (entry.result().entity().orElse(null) instanceof AbstractThrownPotion && Math.abs(recoil - POTION_KG * relative) > 0.05 * POTION_KG * relative + 0.01) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s – Rückstoß nicht aus Masse (%.2f kg) und Tempo: erwartet %.2f N·s", text, POTION_KG,
                    POTION_KG * relative));
        }
        return text;
    }

    private static StepResult compare(SelfTest.StepContext ctx, int ticks) {
        List<String> lines = new ArrayList<>();
        List<String> wild = new ArrayList<>();
        for (int shot = 1; shot <= POTIONS; shot++) {
            double[] potion = (double[]) ctx.state().get(RESULT + "." + potionLabel(shot));
            lines.add(String.format(Locale.ROOT, "Wurf %d (%d geladen, %.2f kg) %.2f m/s zurück, +%.1f°, %.0f °/s", shot, POTIONS - shot + 1,
                    (double) ctx.state().get(MASS + "." + potionLabel(shot)), potion[0], potion[1], potion[2]));
            if (potion[2] > MAX_RATE_DPS || potion[1] > MAX_TILT_DEGREES) {
                wild.add("Wurf " + shot);
            }
        }
        double[] wind = (double[]) ctx.state().get(RESULT + ".Windkugel zum Vergleich");
        lines.add(String.format(Locale.ROOT, "Windkugel %.2f m/s zurück, +%.1f°, %.0f °/s", wind[0], wind[1], wind[2]));
        String text = String.join("; ", lines);
        if (!wild.isEmpty()) {
            return StepResult.fail(text + " – nach " + String.join(", ", wild) + " nicht steuerbar (höchstens " + Math.round(MAX_RATE_DPS)
                    + " °/s wie der volle Knüppel der Standard-Rates und " + Math.round(MAX_TILT_DEGREES) + "° Neigung)");
        }
        return StepResult.pass(text);
    }

    private static StepResult calm(SelfTest.StepContext ctx, int ticks) {
        FlightState state = HoverAssist.last();
        if (state != null && ticks > 30 && Math.hypot(state.vx(), state.vz()) < CovMdAFlightSteps.CALM_SPEED && Math.abs(state.vy()) < CovMdAFlightSteps.CALM_CLIMB) {
            return StepResult.pass();
        }
        return ticks > 800 ? StepResult.fail("kommt nicht zur Ruhe") : StepResult.pending();
    }

    static void mark(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        state.put(START, drone.level().getGameTime());
        AtomicInteger held = new AtomicInteger();
        state.put(HELD, held);
        CovMdAWatch.every(any -> {
            if (DispenserHolds.holding(drone)) {
                held.incrementAndGet();
            }
        });
    }

    static String shots(MinecraftServer server, Map<String, Object> state, int min, int max, int firstGap, int gap, String label) {
        Entity drone = Pilot.serverDrone(server, state);
        long start = (long) state.get(START);
        List<Long> times = new ArrayList<>();
        for (DispenserLog.Entry entry : DispenserLog.history(drone)) {
            if (entry.gameTime() >= start && entry.result() != null && entry.result().success()) {
                if (!(entry.result().entity().orElse(null) instanceof AbstractArrow)) {
                    throw new IllegalStateException(label + ": " + CovMdAWatch.type(entry.result().entity().orElse(null)) + " statt Pfeil");
                }
                times.add(entry.gameTime());
            }
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            gaps.add(times.get(i) - times.get(i - 1));
        }
        Object held = state.get(HELD);
        String text = label + ": " + times.size() + (times.size() == 1 ? " Schuss" : " Schüsse") + (gaps.isEmpty() ? "" : ", Abstände " + gaps + " Ticks")
                + (held instanceof AtomicInteger ticks && ticks.get() > 0 ? " (am Server " + ticks.get() + " Ticks gehalten)" : "");
        if (times.size() < min || times.size() > max) {
            throw new IllegalStateException(text + " – erwartet " + (min == max ? String.valueOf(min) : min + "–" + max));
        }
        if (firstGap > 0 && !gaps.isEmpty() && gaps.getFirst() != firstGap) {
            throw new IllegalStateException(text + " – zwischen erstem und zweitem Schuss " + firstGap + " Ticks erwartet");
        }
        if (gap > 0) {
            for (long later : gaps.subList(Math.min(1, gaps.size()), gaps.size())) {
                if (later != gap) {
                    throw new IllegalStateException(text + " – danach " + gap + " Ticks erwartet");
                }
            }
        }
        return text;
    }

    private static StepResult openTab(SelfTest.StepContext ctx, int ticks) {
        if (ticks == 0) {
            if (!PropwashClientApi.openConfigTab(TAB_ID, null)) {
                return StepResult.fail("kein Tab " + TAB_ID + " im K-Menü");
            }
            return StepResult.pending();
        }
        if (ticks < 10) {
            return StepResult.pending();
        }
        return sliders().size() >= 2 ? StepResult.pass() : StepResult.fail("Tab " + TAB_ID + " ohne Regler für Anlauf und Abstand");
    }

    private static List<StepSlider> sliders() {
        Screen screen = Minecraft.getInstance().gui.screen();
        List<StepSlider> out = new ArrayList<>();
        if (screen == null) {
            return out;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof StepSlider slider && slider.visible) {
                out.add(slider);
            }
        }
        out.sort(Comparator.comparingInt(StepSlider::getY));
        return out;
    }

    private static StepResult slidersVisible(SelfTest.StepContext ctx, int ticks) {
        Screen screen = Minecraft.getInstance().gui.screen();
        List<StepSlider> sliders = sliders();
        if (screen == null || sliders.size() < 2) {
            return StepResult.fail("Regler fehlen");
        }
        for (StepSlider slider : sliders.subList(0, 2)) {
            if (slider.getY() < 0 || slider.getBottom() > screen.height || slider.getX() < 0 || slider.getRight() > screen.width) {
                return StepResult.fail(String.format(Locale.ROOT, "Regler bei %d,%d–%d,%d außerhalb von %dx%d", slider.getX(), slider.getY(), slider.getRight(),
                        slider.getBottom(), screen.width, screen.height));
            }
        }
        return StepResult.pass(String.format(Locale.ROOT, "%dx%d: Anlauf „%s“, Abstand „%s“", screen.width, screen.height, sliders.get(0).getMessage().getString(),
                sliders.get(1).getMessage().getString()));
    }

    private static StepResult select(int delay, int interval) {
        List<StepSlider> sliders = sliders();
        if (sliders.size() < 2) {
            return StepResult.fail("Regler fehlen");
        }
        sliders.get(0).select(delay);
        sliders.get(1).select(interval);
        return StepResult.pass(String.format(Locale.ROOT, "Anlauf „%s“, Abstand „%s“", sliders.get(0).getMessage().getString(),
                sliders.get(1).getMessage().getString()));
    }

    private static StepResult save(int ticks, int delay, int interval) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            Screen screen = mc.gui.screen();
            if (screen == null) {
                return StepResult.fail("K-Menü geschlossen");
            }
            screen.keyPressed(new KeyEvent(InputConstants.KEY_S, 0, InputConstants.MOD_CONTROL));
            return StepResult.pending();
        }
        Path file = Services.PLATFORM.configDir().resolve(CONFIG_FILE);
        String saved = "";
        if (Files.isRegularFile(file)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (json.has(DELAY_KEY) && json.has(INTERVAL_KEY)) {
                    saved = json.get(DELAY_KEY).getAsInt() + "/" + json.get(INTERVAL_KEY).getAsInt();
                }
            } catch (IOException | RuntimeException e) {
                saved = e.toString();
            }
        }
        if (mc.gui.screen() == null && saved.equals(delay + "/" + interval)) {
            return StepResult.pass(CONFIG_FILE + ": " + DELAY_KEY + "=" + delay + ", " + INTERVAL_KEY + "=" + interval);
        }
        return ticks > 60 ? StepResult.fail("nicht gespeichert (" + (mc.gui.screen() == null ? "geschlossen" : mc.gui.screen().getClass().getSimpleName())
                + ", Datei " + (saved.isEmpty() ? "ohne Werte" : saved) + ")") : StepResult.pending();
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("Tasten los, Flughilfe aus, Fenster zurück", () -> {
                Pilot.land(Minecraft.getInstance());
                Minecraft.getInstance().gui.setScreen(null);
                Shots.restoreWindow(Minecraft.getInstance());
            })
            .server("Flug beenden, Geschosse weg, Regeln zurück", (server, state) -> {
                if (state.remove(RULES) instanceof Rules rules) {
                    rules.restore();
                }
                Pilot.release(server);
                CovMdAShotSteps.sweep(server);
            });

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }
}
