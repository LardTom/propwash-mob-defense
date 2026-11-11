package de.lardtom.mobdefense.dev.ui.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.OsdNotices;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.booster.BoosterRig;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.CoreBuild;
import de.lardtom.mobdefense.dev.core.client.CoreSteps;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.Guests;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdAShotSteps;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.spear.SpearChecks;
import de.lardtom.mobdefense.dispenser.DispenserBehavior;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.net.Notices;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.OsdLayoutSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CovMdAUiSteps {
    public static final String MODES_TAB = "modes";
    public static final String OSD_TAB = "osd";
    public static final String NAMED_KEY = "propwash.input.modes.accessory_named";
    public static final String FLIGHT_SCHOOL_KEY = "key.propwash.flight_school";
    public static final Identifier PROBE = MobDefense.id("covmda_probe");
    public static final List<String> AWARDED = List.of("air_support", "recoil", "afterburner");
    public static final String LINE_OF_SIGHT_AFTER = "0.6.0-dev.7";
    private static final String SEEN = "covmda.ui.seen";
    private static final String NOTES = "covmda.ui.notes";
    private static final String BEFORE = "covmda.ui.before";
    private static final String GUEST = "covmda.ui.guest";
    private static final String TARGET = "covmda.ui.target";
    private static final String ROWS = "covmda.ui.rows";
    private static final String SENT = "covmda.ui.sent";
    private static final String DROPPED = "covmda.ui.dropped";
    private static final int SCROLLS = 30;
    private static boolean probeRegistered;
    private static volatile @Nullable Probe probe;

    public record Probe(boolean fpv, boolean lineOfSight, int drone, long gameTime, @Nullable String dispenser, @Nullable String slots) {
    }

    private CovMdAUiSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Zubehör-Aktionen im Tab Modi – „Zubehör-Aktion 1 – Dispenser feuern“, „2 – Nächster Slot“, „3 – Booster zünden“, Aktion 4 ohne Zusatz, "
                + "EN und DE", 3600, Script.create()
                .then("Fenster für GUI 854x480", (ctx, ticks) -> Shots.window(ctx, ticks, 854 * 2, 480 * 2, 2))
                .then("Tab Modi öffnen", (ctx, ticks) -> openTab(ctx, ticks, MODES_TAB))
                .append(Shots.photos("covmda_modes_actions", CovMdAUiSteps::scrollToActions))
                .run("Sprache Englisch", () -> Shots.language(Minecraft.getInstance(), Shots.ENGLISH))
                .then("Zeilen Englisch, GUI 854x480", (ctx, ticks) -> actionRows(ctx, ticks, "EN"))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Zeilen Deutsch, GUI 854x480", (ctx, ticks) -> actionRows(ctx, ticks, "DE"))
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .run("Tab zu", () -> Minecraft.getInstance().gui.setScreen(null))
                .run("Fenster zurück", () -> Shots.restoreWindow(Minecraft.getInstance()))
                .then("Ergebnis", CovMdAUiSteps::actionResult));
        steps.add("OSD-Standard – Haken im Layout nach dem ersten Flug: Dispenser an Spalte 1/Zeile 12, Booster an 1/13, Dispenser-Slots und Speer aus, "
                + "Foto des Editors", 3000, Script.create()
                .server("5″ mit allen drei Teilen", (server, state) -> Pilot.spawn(server, state, CoreBuild.full(), "CovMdA OSD", Arena.ground(0, -6), 180.0F))
                .append(Pilot.fly())
                .sleep(20)
                .check("Haken im OSD-Layout", CovMdAUiSteps::defaults)
                .run("Flug beenden", () -> Pilot.land(Minecraft.getInstance()))
                .server("Funke aus", (server, state) -> Pilot.release(server))
                .await("Flug beendet", 200, () -> !PropwashClientApi.input().flying())
                .then("OSD-Editor öffnen", (ctx, ticks) -> openTab(ctx, ticks, OSD_TAB))
                .append(Shots.photos("covmda_osd_editor_defaults", CovMdAUiSteps::showAddons))
                .run("Editor zu", () -> Minecraft.getInstance().gui.setScreen(null))
                .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
        steps.add("Slot-Übersicht – Element Dispenser-Slots eingeschaltet zeigt [ARW]SPL WND ---, der gewählte Slot steht in Klammern, Foto", 3000, Script.create()
                .run("Messfühler", CovMdAUiSteps::registerProbe)
                .run("Element Dispenser-Slots einschalten", () -> enable(MobDefenseOsd.DISPENSER_SLOTS))
                .server("5″ mit Pfeilen, Wurftrank und Windkugeln", (server, state) -> {
                    Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "CovMdA Slots", Arena.ground(0, -6), 180.0F);
                    DispenserRig.load(drone, new ItemStack(Items.ARROW, 16), PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING),
                            new ItemStack(Items.WIND_CHARGE, 16));
                })
                .append(Pilot.fly())
                .then("Slot-Übersicht im FPV", (ctx, ticks) -> probed(ctx, ticks, true, false, null, "[ARW]SPL WND ---"))
                .check("Element im Layout an", () -> layoutOn(MobDefenseOsd.DISPENSER_SLOTS, true))
                .append(Shots.photos("covmda_osd_dispenser_slots"))
                .server("Zubehör-Aktion 2", (server, state) -> action(server, state, 2))
                .then("gewählter Slot 2", (ctx, ticks) -> probed(ctx, ticks, true, false, null, "ARW[SPL]WND ---"))
                .server("Zubehör-Aktion 2", (server, state) -> action(server, state, 2))
                .then("gewählter Slot 3", (ctx, ticks) -> probed(ctx, ticks, true, false, null, "ARW SPL[WND]---"))
                .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
        steps.add("Flugschule über M – mit jedem Mob-Defense-Teil in der Hand zeigt M die passende Szene „Mob Defense: …“, EN und DE, Fotos", 3600,
                Script.create()
                        .append(school(MobDefense.DISPENSER_MOUNT, "dispenser", "EN"))
                        .append(Shots.photos("covmda_flight_school_m", CoreSteps::parkMouse))
                        .append(school(MobDefense.SPEAR_MOUNT, "spear", "EN"))
                        .append(school(MobDefense.ROCKET_BOOSTER, "booster", "EN"))
                        .append(school(MobDefense.DISPENSER_MOUNT, "dispenser", "DE"))
                        .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                        .run("Flugschule zu", () -> Minecraft.getInstance().gui.setScreen(null))
                        .then("Ergebnis", CovMdAUiSteps::notes));
        steps.add("Fortschritte – Heiltrank auf einen Testspieler, Windkugel und Booster-Zündung: „Luftunterstützung“, „Rückstoß“ und „Nachbrenner“ erledigt",
                3600, Script.create()
                        .server("Fortschritte zurücknehmen", (server, state) -> revoke(server))
                        .server("Drohne auf dem Sockel, Testspieler mit 6 Leben davor", CovMdAUiSteps::healRange)
                        .server("Wurf-Heiltrank", (server, state) -> shoot(server, state, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING)))
                        .sleep(30)
                        .server("Luftunterstützung", CovMdAUiSteps::healed)
                        .server("Windkugel", (server, state) -> {
                            Guests.removeAll();
                            shoot(server, state, new ItemStack(Items.WIND_CHARGE));
                        })
                        .sleep(5)
                        .server("Rückstoß", (server, state) -> done(server, state, "recoil"))
                        .server("Booster-Drohne schwebt", CovMdAUiSteps::boosterHover)
                        .then("in der Luft", (ctx, ticks) -> airborne(ctx, ticks))
                        .server("Booster zünden", CovMdAUiSteps::ignite)
                        .server("Nachbrenner", (server, state) -> done(server, state, "afterburner"))
                        .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
    }

    public static void registerLineOfSight(SelfTest.Steps steps) {
        String propwash = Services.PLATFORM.modVersion(PropwashApi.MOD_ID).orElse("");
        if (!newer(propwash, LINE_OF_SIGHT_AFTER)) {
            MobDefense.LOG.info("[Mob-Defense-Selbsttest] Schritt „Anzeigen im Sichtflug“ nicht angemeldet: Propwash {} ruft die OSD-Elemente der Add-ons "
                    + "im Sichtflug nicht auf (cov-md-a-2, Propwash-Teil), angemeldet ab der Version nach {}", propwash, LINE_OF_SIGHT_AFTER);
            return;
        }
        steps.add("Anzeigen im Sichtflug – ohne Brille fliegen: OsdContext meldet Sichtflug, mobdefense:dispenser ist an und zählt beim Schuss herunter, Foto",
                3600, Script.create()
                        .run("Messfühler", CovMdAUiSteps::registerProbe)
                        .server("5″ mit 16 Pfeilen, Brille ab", (server, state) -> {
                            Entity drone = Pilot.spawn(server, state, DispenserRig.fiveInch(), "CovMdA Sichtflug", Arena.ground(0, -6), 180.0F);
                            DispenserRig.load(drone, new ItemStack(Items.ARROW, 16));
                            Arena.player(server).setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        })
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(3.0))
                        .then("Sichtflug mit Dispenser-Anzeige", (ctx, ticks) -> probed(ctx, ticks, false, true, "DSP 1 ARW 16", null))
                        .check("Element im Layout an", () -> layoutOn(MobDefenseOsd.DISPENSER, true))
                        .server("Schuss", (server, state) -> action(server, state, 1))
                        .then("zählt herunter", (ctx, ticks) -> probed(ctx, ticks, false, true, "DSP 1 ARW 15", null))
                        .append(Shots.photos("covmda_line_of_sight_osd"))
                        .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
    }

    public static boolean newer(String version, String than) {
        String[] a = version.trim().split("-", 2);
        String[] b = than.trim().split("-", 2);
        if (a[0].isEmpty()) {
            return true;
        }
        int base = compareParts(a[0], b[0]);
        if (base != 0) {
            return base > 0;
        }
        boolean releaseA = a.length < 2 || a[1].isEmpty();
        boolean releaseB = b.length < 2 || b[1].isEmpty();
        if (releaseA || releaseB) {
            return releaseA && !releaseB;
        }
        return compareParts(a[1], b[1]) > 0;
    }

    private static int compareParts(String a, String b) {
        String[] x = a.split("[.+]");
        String[] y = b.split("[.+]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "";
            String q = i < y.length ? y[i] : "";
            int c = p.matches("\\d+") && q.matches("\\d+") ? Long.compare(Long.parseLong(p), Long.parseLong(q)) : p.compareTo(q);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    public static void registerLanceFlight(SelfTest.Steps steps) {
        steps.add("Fortschritt Lanzenflug – Pilot besiegt einen Zombie mit der Speer-Halterung: „Lanzenflug“ erledigt", 1600, Script.create()
                .server("Fortschritte zurücknehmen", (server, state) -> revoke(server))
                .server("Speer-Drohne, Zombie mit 1 Leben, Stich mit Pilot", CovMdAUiSteps::lance)
                .sleep(5)
                .server("Lanzenflug", (server, state) -> done(server, state, "lance_flight"))
                .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
    }

    public static void registerThreat(SelfTest.Steps steps) {
        steps.add("Warnung BEDROHUNG – ein Skelett zielt auf die schwebende Drohne: THREAT/BEDROHUNG beim Piloten, höchstens 4 Pakete je Sekunde, ohne Ziel weg",
                3600, Script.create()
                        .server("5″ schwebt", (server, state) -> Pilot.spawn(server, state, DispenserRig.fiveInch(), "CovMdA Bedrohung", Arena.ground(0, -6),
                                180.0F))
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(4.0))
                        .server("Skelett 10 m daneben, Mob-Schaden 0 %", (server, state) -> {
                            Arena.rule(server, MobDefense.id("mob_damage_to_drones_percent"), "0");
                            skeleton(server, state);
                        })
                        .then("BEDROHUNG am Client", (ctx, ticks) -> threat(ctx, ticks, true))
                        .then("höchstens 4 Pakete je Sekunde", CovMdAUiSteps::threatRate)
                        .server("Skelett weg", (server, state) -> {
                            Object id = state.get(TARGET);
                            Entity skeleton = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
                            if (skeleton != null) {
                                skeleton.discard();
                            }
                        })
                        .then("BEDROHUNG verschwindet", (ctx, ticks) -> threat(ctx, ticks, false))
                        .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
    }

    public static void registerThreatUnderFire(SelfTest.Steps steps) {
        steps.add("Warnung BEDROHUNG bei Dauerfeuer ins Leere – Ziffernblock 9 am leeren Dispenser gehalten, dann zielt ein Skelett: "
                        + "BEDROHUNG und KEINE MUNITION beim Piloten, höchstens 4 Pakete je Sekunde",
                3600, Script.create()
                        .server("5″ mit leerem Dispenser schwebt", (server, state) -> Pilot.spawn(server, state, DispenserRig.fiveInch(),
                                "CovMdA Bedrohung im Dauerfeuer", Arena.ground(0, -6), 180.0F))
                        .append(Pilot.fly())
                        .append(Pilot.takeOff(4.0))
                        .run("Ziffernblock 9 halten", () -> Pilot.hold(Pilot.ACTION_1, true))
                        .then("KEINE MUNITION am Client", (ctx, ticks) -> notice(ctx, ticks, Notice.NO_AMMO))
                        .sleep(Notices.WINDOW_TICKS * 2)
                        .server("Skelett 10 m daneben, Mob-Schaden 0 %", (server, state) -> {
                            Arena.rule(server, MobDefense.id("mob_damage_to_drones_percent"), "0");
                            skeleton(server, state);
                            state.put(SENT, Notices.sent());
                            state.put(DROPPED, Notices.dropped());
                        })
                        .then("BEDROHUNG am Client trotz Dauerfeuer", (ctx, ticks) -> threat(ctx, ticks, true))
                        .then("KEINE MUNITION bleibt", (ctx, ticks) -> notice(ctx, ticks, Notice.NO_AMMO))
                        .then("höchstens 4 Pakete je Sekunde", CovMdAUiSteps::threatRate)
                        .then("BEDROHUNG bleibt im Dauerfeuer", CovMdAUiSteps::threatStays)
                        .server("Pakete am Server", (server, state) -> note(state, "am Server " + (Notices.sent() - (int) state.get(SENT))
                                + " gesendet, " + (Notices.dropped() - (int) state.get(DROPPED)) + " verworfen"))
                        .run("Ziffernblock 9 los", () -> Pilot.hold(Pilot.ACTION_1, false))
                        .then("Ergebnis", CovMdAUiSteps::notes), CovMdAUiSteps::cleanup);
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("Tasten los, Layout zurück", () -> {
                Pilot.land(Minecraft.getInstance());
                restoreLayout();
            })
            .server("Flug beenden, Testspieler weg", (server, state) -> {
                Pilot.release(server);
                Guests.removeAll();
                CovMdAShotSteps.sweep(server);
            });

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }

    @SuppressWarnings("unchecked")
    static void note(Map<String, Object> state, String note) {
        ((List<String>) state.computeIfAbsent(NOTES, key -> new ArrayList<String>())).add(note);
    }

    @SuppressWarnings("unchecked")
    static StepResult notes(SelfTest.StepContext ctx, int ticks) {
        return StepResult.pass(String.join("; ", (List<String>) ctx.state().getOrDefault(NOTES, List.of())));
    }

    static StepResult openTab(SelfTest.StepContext ctx, int ticks, String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(null);
            if (!PropwashClientApi.openConfigTab(tab, null)) {
                return StepResult.fail("Tab " + tab + " fehlt");
            }
            return StepResult.pending();
        }
        if (mc.gui.screen() == null) {
            return ticks > 200 ? StepResult.fail("Tab " + tab + " öffnet nicht") : StepResult.pending();
        }
        return ticks > 10 ? StepResult.pass() : StepResult.pending();
    }

    private static String named(int number, String key) {
        return I18n.get(NAMED_KEY, I18n.get("key.propwash.accessory_action_" + number), I18n.get(key));
    }

    private static String plain(int number) {
        return I18n.get("key.propwash.accessory_action_" + number);
    }

    @SuppressWarnings("unchecked")
    private static StepResult actionRows(SelfTest.StepContext ctx, int ticks, String label) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Modi zu");
        }
        int stage = (int) ctx.state().getOrDefault(SEEN + label + ".stage", 0);
        List<String> names = List.of(named(1, DispenserBehavior.FIRE_KEY), named(2, DispenserBehavior.NEXT_SLOT_KEY), named(3, BoosterBehavior.IGNITE_KEY),
                plain(4));
        List<CovMdAScreenText.Text> texts = CovMdAScreenText.texts(screen, -1000, -1000);
        if (stage == 0) {
            List<String> rows = new ArrayList<>();
            for (CovMdAScreenText.Text text : texts) {
                if (text.text().startsWith(plain(4).substring(0, plain(4).length() - 1))) {
                    rows.add(text.text());
                }
            }
            if (rows.size() < 4) {
                if (ticks > SCROLLS * 3) {
                    return StepResult.fail(label + ": Zeilen der Zubehör-Aktionen nicht gefunden: " + rows);
                }
                if (ticks % 3 == 0) {
                    screen.mouseScrolled(screen.width * 0.3, screen.height * 0.5, 0.0, -1.0);
                }
                return StepResult.pending();
            }
            ((List<String>) ctx.state().computeIfAbsent(ROWS, key -> new ArrayList<String>())).add(label + " Liste: " + rows);
            ctx.state().put(SEEN + label + ".stage", 1);
            return StepResult.pending();
        }
        int number = (stage + 1) / 2;
        if (number > 4) {
            return StepResult.pass();
        }
        if (stage % 2 == 1) {
            for (CovMdAScreenText.Text text : texts) {
                if (text.text().equals(plain(number)) || text.text().equals(names.get(number - 1))) {
                    screen.mouseClicked(new MouseButtonEvent(text.x() + 4.0, text.y() + 4.0, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                    ctx.state().put(SEEN + label + ".stage", stage + 1);
                    return StepResult.pending();
                }
            }
            return StepResult.fail(label + ": Zeile „" + plain(number) + "“ nicht anklickbar");
        }
        List<String> lines = new ArrayList<>();
        texts.forEach(text -> lines.add(text.text()));
        String expected = names.get(number - 1);
        boolean shown = lines.contains(expected);
        boolean extra = number == 4 && lines.stream().anyMatch(line -> line.startsWith(plain(4) + " "));
        ((List<String>) ctx.state().computeIfAbsent(ROWS, key -> new ArrayList<String>())).add(label + " Aktion " + number + (shown && !extra ? ": „" + expected + "“"
                : ": fehlt „" + expected + "“"));
        ((List<Boolean>) ctx.state().computeIfAbsent(ROWS + ".ok", key -> new ArrayList<Boolean>())).add(shown && !extra);
        ctx.state().put(SEEN + label + ".stage", stage + 1);
        return StepResult.pending();
    }

    private static List<String> namesOf(int number) {
        List<String> out = new ArrayList<>();
        for (Identifier id : PropwashApi.accessories().registered()) {
            PropwashApi.accessories().behavior(id).flatMap(behavior -> behavior.actionNameKey(number)).ifPresent(key -> out.add(number + "=" + key));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static StepResult actionResult(SelfTest.StepContext ctx, int ticks) {
        List<String> rows = (List<String>) ctx.state().getOrDefault(ROWS, List.of());
        List<Boolean> ok = (List<Boolean>) ctx.state().getOrDefault(ROWS + ".ok", List.of());
        String names = "actionNameKey: " + namesOf(1) + namesOf(2) + namesOf(3) + namesOf(4);
        if (ok.size() != 8 || ok.contains(Boolean.FALSE) || rows.stream().noneMatch(row -> row.contains("„Zubehör-Aktion 1 – Dispenser feuern“"))) {
            return StepResult.fail(String.join("; ", rows) + "; " + names);
        }
        return StepResult.pass(String.join("; ", rows) + "; " + names);
    }

    private static void scrollToActions() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null) {
            for (int i = 0; i < SCROLLS; i++) {
                screen.mouseScrolled(screen.width * 0.3, screen.height * 0.5, 0.0, -1.0);
            }
        }
        CoreSteps.parkMouse();
    }

    private static JsonObject elements() {
        OsdLayoutSpec layout = PropwashClientApi.osd().current();
        return JsonParser.parseString(layout.json()).getAsJsonObject().getAsJsonObject("elements");
    }

    private static StepResult defaults() {
        JsonObject elements = elements();
        List<String> seen = new ArrayList<>();
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            JsonElement entry = elements.get(spec.id().toString());
            if (entry == null || !entry.isJsonObject()) {
                return StepResult.fail(spec.id() + " fehlt im OSD-Layout nach dem ersten Flug");
            }
            JsonObject values = entry.getAsJsonObject();
            boolean on = values.get("on").getAsBoolean();
            int col = values.get("col").getAsInt();
            int row = values.get("row").getAsInt();
            boolean wanted = spec.id().equals(MobDefenseOsd.DISPENSER) || spec.id().equals(MobDefenseOsd.BOOSTER);
            if (on != wanted) {
                return StepResult.fail(spec.id() + " ist " + (on ? "an" : "aus") + " statt " + (wanted ? "an" : "aus"));
            }
            if (spec.id().equals(MobDefenseOsd.DISPENSER) && (col != 1 || row != 12) || spec.id().equals(MobDefenseOsd.BOOSTER) && (col != 1 || row != 13)) {
                return StepResult.fail(spec.id() + " auf " + col + "/" + row);
            }
            seen.add(spec.id().getPath() + (on ? " an " : " aus ") + col + "/" + row);
        }
        return StepResult.pass("OSD-Layout: " + String.join(", ", seen));
    }

    private static StepResult layoutOn(Identifier id, boolean wanted) {
        JsonElement entry = elements().get(id.toString());
        boolean on = entry != null && entry.isJsonObject() && entry.getAsJsonObject().get("on").getAsBoolean();
        return on == wanted ? StepResult.pass(id + (on ? " im Layout an" : " im Layout aus")) : StepResult.fail(id + " im Layout " + (on ? "an" : "aus"));
    }

    private static void enable(Identifier id) {
        OsdLayoutSpec current = PropwashClientApi.osd().current();
        if (layout == null) {
            layout = current;
        }
        JsonObject root = JsonParser.parseString(current.json()).getAsJsonObject();
        JsonObject elements = root.getAsJsonObject("elements");
        JsonObject entry = elements.has(id.toString()) ? elements.getAsJsonObject(id.toString()) : new JsonObject();
        entry.addProperty("on", true);
        elements.add(id.toString(), entry);
        if (!PropwashClientApi.osd().apply(new OsdLayoutSpec(current.version(), root.toString()))) {
            throw new IllegalStateException("OSD-Layout mit " + id + " nicht übernommen");
        }
    }

    private static @Nullable OsdLayoutSpec layout;

    private static void restoreLayout() {
        OsdLayoutSpec saved = layout;
        layout = null;
        if (saved != null) {
            PropwashClientApi.osd().apply(saved);
        }
    }

    private static void showAddons() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null) {
            for (int i = 0; i < 60; i++) {
                screen.mouseScrolled(screen.width * 0.8, screen.height * 0.3, 0.0, -1.0);
            }
        }
        CoreSteps.parkMouse();
    }

    private static synchronized void registerProbe() {
        if (probeRegistered) {
            return;
        }
        probeRegistered = true;
        PropwashClientApi.registerOsdElement(OsdElementSpec.builder(PROBE)
                .nameKey(MobDefense.MOD_ID + ".name")
                .position(29, 15)
                .align(OsdAlign.LEFT)
                .group(OsdGroup.ADDON)
                .enabledByDefault(true)
                .maxChars(1)
                .content(CovMdAUiSteps::sample)
                .build());
    }

    private static @Nullable OsdText sample(OsdContext context) {
        if (context.preview()) {
            return null;
        }
        Entity drone = context.drone().orElse(null);
        String dispenser = null;
        String slots = null;
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            OsdText text = spec.content().text(context);
            if (spec.id().equals(MobDefenseOsd.DISPENSER)) {
                dispenser = text == null ? null : text.text();
            } else if (spec.id().equals(MobDefenseOsd.DISPENSER_SLOTS)) {
                slots = text == null ? null : text.text();
            }
        }
        probe = new Probe(context.fpv(), context.lineOfSight(), drone == null ? -1 : drone.getId(), context.gameTime(), dispenser, slots);
        return null;
    }

    private static StepResult probed(SelfTest.StepContext ctx, int ticks, boolean fpv, boolean lineOfSight, @Nullable String dispenser, @Nullable String slots) {
        Probe seen = probe;
        Minecraft mc = Minecraft.getInstance();
        boolean fresh = seen != null && mc.level != null && mc.level.getGameTime() - seen.gameTime() < 5;
        boolean match = fresh && seen.fpv() == fpv && seen.lineOfSight() == lineOfSight && seen.drone() >= 0
                && (dispenser == null || seen.dispenser() != null && seen.dispenser().replace("*", "").equals(dispenser))
                && (slots == null || slots.equals(seen.slots()));
        if (!match) {
            String last = seen == null ? "nie" : (mc.level == null ? "?" : (mc.level.getGameTime() - seen.gameTime()) + " Ticks her") + " mit " + seen;
            return ticks > 200 ? StepResult.fail("Propwash ruft die OSD-Elemente der Add-ons nicht wie erwartet auf (erwartet FPV " + fpv + ", Sichtflug "
                    + lineOfSight + ", Dispenser " + dispenser + ", Slots " + slots + "; letzter Aufruf " + last + ")") : StepResult.pending();
        }
        String text = (lineOfSight ? "Sichtflug" : "FPV") + ": Propwash ruft die Add-on-Elemente mit lineOfSight=" + seen.lineOfSight() + " auf, "
                + (dispenser != null ? "mobdefense:dispenser „" + seen.dispenser() + "“" : "mobdefense:dispenser_slots „" + seen.slots() + "“");
        note(ctx.state(), text);
        return StepResult.pass();
    }

    private static void action(MinecraftServer server, Map<String, Object> state, int number) {
        Entity drone = Pilot.serverDrone(server, state);
        ServerPlayer player = Arena.player(server);
        if (!PropwashApi.accessories().triggerAction(drone, number, true, player)) {
            throw new IllegalStateException("Zubehör-Aktion " + number + " nicht angenommen");
        }
        PropwashApi.accessories().triggerAction(drone, number, false, player);
    }

    private static Script school(Identifier part, String scene, String language) {
        return Script.create()
                .run("Flugschule zu", () -> Minecraft.getInstance().gui.setScreen(null))
                .run("Sprache " + language, () -> Shots.language(Minecraft.getInstance(), language.equals("DE") ? Shots.GERMAN : Shots.ENGLISH))
                .server(part.getPath() + " in die Hand", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    player.getInventory().clearContent();
                    player.setItemInHand(InteractionHand.MAIN_HAND, PropwashApi.partItems().stack(part));
                })
                .sleep(10)
                .run("Taste M", () -> Pilot.click(FLIGHT_SCHOOL_KEY))
                .await("Flugschule offen", 100, () -> Minecraft.getInstance().gui.screen() != null)
                .sleep(10)
                .then("Szene " + scene + " (" + language + ")", (ctx, ticks) -> scenes(ctx, ticks, language + " " + part.getPath(), part, scene));
    }

    @SuppressWarnings("unchecked")
    private static StepResult scenes(SelfTest.StepContext ctx, int ticks, String label, Identifier part, String scene) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return StepResult.fail("Flugschule zu");
        }
        Set<String> seen = (Set<String>) ctx.state().computeIfAbsent(SEEN + label, key -> new LinkedHashSet<String>());
        seen.addAll(CovMdAScreenText.lines(screen));
        String joined = String.join(" ", seen).replaceAll("\\s+", " ");
        String title = I18n.get(MobDefense.MOD_ID + ".guide.scene." + scene + ".title");
        String item = I18n.get("part." + part.getNamespace() + "." + part.getPath());
        if (!joined.contains(title) || !joined.contains(item)) {
            if (ticks < SCROLLS * 3) {
                if (ticks % 3 == 0) {
                    screen.mouseScrolled(screen.width * 0.5, screen.height * 0.5, 0.0, -1.0);
                }
                return StepResult.pending();
            }
            return StepResult.fail(label + ": „" + title + "“ fehlt in " + screen.getClass().getSimpleName() + ": " + seen.stream().filter(line -> !line
                    .isBlank()).limit(40).toList());
        }
        for (int i = 0; i < SCROLLS; i++) {
            screen.mouseScrolled(screen.width * 0.5, screen.height * 0.5, 0.0, 1.0);
        }
        String heading = seen.stream().filter(line -> line.contains(item)).findFirst().orElse(item);
        List<String> other = new ArrayList<>();
        for (String id : List.of("dispenser", "spear", "booster")) {
            String otherTitle = I18n.get(MobDefense.MOD_ID + ".guide.scene." + id + ".title");
            if (!id.equals(scene) && joined.contains(otherTitle)) {
                other.add(otherTitle);
            }
        }
        note(ctx.state(), label + ": " + screen.getClass().getSimpleName() + " „" + heading + "“ mit „" + title + "“" + (other.isEmpty() ? ""
                : ", dazu " + other));
        return StepResult.pass();
    }

    private static void revoke(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        for (String id : List.of("air_support", "recoil", "afterburner", "lance_flight")) {
            AdvancementHolder holder = server.getAdvancements().get(MobDefense.id(id));
            if (holder == null) {
                throw new IllegalStateException("Fortschritt " + id + " fehlt");
            }
            for (String criterion : player.getAdvancements().getOrStartProgress(holder).getCompletedCriteria()) {
                player.getAdvancements().revoke(holder, criterion);
            }
        }
    }

    private static void done(MinecraftServer server, Map<String, Object> state, String id) {
        ServerPlayer player = Arena.player(server);
        AdvancementHolder holder = server.getAdvancements().get(MobDefense.id(id));
        if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            throw new IllegalStateException("Fortschritt " + id + " nicht erledigt");
        }
        String title = holder.value().display().map(display -> display.title().getString()).orElse(id);
        note(state, "„" + title + "“ (" + id + ") erledigt");
    }

    private static void healRange(MinecraftServer server, Map<String, Object> state) {
        Entity drone = CovMdAShotSteps.range(server, state, null, 0.0);
        Vec3 at = DispenserRig.ahead(drone, 3.0);
        ServerPlayer guest = Guests.place(server, "CovMdA_Gast", Arena.level(server), new Vec3(at.x, Arena.ground().getY() + 1.0, at.z), 0.0F);
        Guests.ready(guest, 6.0F);
        state.put(GUEST, guest);
    }

    private static void shoot(MinecraftServer server, Map<String, Object> state, ItemStack ammo) {
        CovMdAShotSteps.shoot(server, state, ammo, Entity.class, false);
    }

    private static void healed(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer guest = (ServerPlayer) state.get(GUEST);
        if (guest == null || !(guest.getHealth() > 6.0F)) {
            throw new IllegalStateException("Testspieler nicht geheilt: " + (guest == null ? "weg" : guest.getHealth()));
        }
        note(state, String.format(Locale.ROOT, "Testspieler 6 → %.0f Leben", guest.getHealth()));
        done(server, state, "air_support");
    }

    private static void boosterHover(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Entity drone = Arena.track(BoosterRig.place(level, player, BoosterRig.build(), "CovMdA Nachbrenner", Arena.ground(6, -6)));
        BoosterRig.load(drone, BoosterRig.rocket(1, 0));
        BoosterRig.hover(drone, player, 4.0);
        state.put(TARGET, drone.getUUID());
    }

    private static StepResult airborne(SelfTest.StepContext ctx, int ticks) {
        Object id = ctx.state().get(TARGET);
        MinecraftServer server = ctx.server();
        if (server == null || !(id instanceof UUID uuid)) {
            return StepResult.fail("keine Booster-Drohne");
        }
        if (ticks < 60) {
            return StepResult.pending();
        }
        return StepResult.pass();
    }

    private static void ignite(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Arena.level(server).getEntity((UUID) state.get(TARGET));
        if (drone == null) {
            throw new IllegalStateException("Booster-Drohne weg");
        }
        boolean flying = PropwashApi.drones().view(drone).map(DroneView::flying).orElse(false);
        if (!BoosterRig.ignite(drone, Arena.player(server))) {
            throw new IllegalStateException("Booster zündet nicht (fliegt " + flying + ")");
        }
    }

    private static void lance(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer pilot = Arena.player(server);
        ServerLevel level = pilot.level();
        Entity drone = Arena.track(SpearChecks.placeDrone(level, Arena.ground(0, 6), SpearChecks.build()));
        Vec3 nose = SpearChecks.nose(drone);
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        Vec3 at = drone.position().add(nose.scale(1.0));
        zombie.snapTo(at.x, Arena.ground().getY() + 1.0, at.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 1200, 0, false, false));
        Arena.spawn(level, zombie);
        zombie.setHealth(1.0F);
        ItemStack spear = new ItemStack(Items.IRON_SPEAR);
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, spear);
        SpearStrikes.Hit hit = SpearStrikes.stab(level, drone, pilot, spear, zombie, nose, nose.scale(SpearChecks.TEST_SPEED));
        if (zombie.isAlive()) {
            throw new IllegalStateException("Zombie überlebt den Stich: " + hit);
        }
        note(state, "Zombie mit dem Speer besiegt, Verursacher Pilot");
    }

    private static void skeleton(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = Pilot.serverDrone(server, state);
        Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
        if (skeleton == null) {
            throw new IllegalStateException("Skelett nicht erzeugt");
        }
        Vec3 at = drone.position().add(10.0, 0.0, 0.0);
        skeleton.snapTo(at.x, Arena.ground().getY() + 1.0, at.z, 90.0F, 0.0F);
        skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        skeleton.setPersistenceRequired();
        Arena.spawn(level, skeleton);
        state.put(TARGET, skeleton.getUUID());
        state.put(BEFORE, OsdNotices.received());
    }

    private static StepResult threat(SelfTest.StepContext ctx, int ticks, boolean wanted) {
        Entity drone = Pilot.drone().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.level == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        boolean active = OsdNotices.active(Notice.THREAT, drone.getId(), mc.level.getGameTime());
        if (active != wanted) {
            return ticks > (wanted ? 600 : 200) ? StepResult.fail("BEDROHUNG " + (active ? "bleibt" : "kommt nicht") + " (Pakete " + OsdNotices.received() + ")")
                    : StepResult.pending();
        }
        note(ctx.state(), "BEDROHUNG " + (wanted ? "erscheint als „" + I18n.get(Notice.THREAT.translationKey()) + "“" : "verschwindet ohne Ziel"));
        return StepResult.pass();
    }

    private static StepResult notice(SelfTest.StepContext ctx, int ticks, Notice notice) {
        Entity drone = Pilot.drone().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.level == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        if (OsdNotices.active(notice, drone.getId(), mc.level.getGameTime())) {
            return StepResult.pass(I18n.get(notice.translationKey()));
        }
        return ticks > 100 ? StepResult.fail(I18n.get(notice.translationKey()) + " kommt nicht (Pakete " + OsdNotices.received() + ")")
                : StepResult.pending();
    }

    private static StepResult threatStays(SelfTest.StepContext ctx, int ticks) {
        Entity drone = Pilot.drone().orElse(null);
        Minecraft mc = Minecraft.getInstance();
        if (drone == null || mc.level == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        long now = mc.level.getGameTime();
        if (!OsdNotices.active(Notice.THREAT, drone.getId(), now)) {
            return StepResult.fail("BEDROHUNG weg nach " + ticks + " Ticks Dauerfeuer (Pakete " + OsdNotices.received() + ")");
        }
        if (!OsdNotices.active(Notice.NO_AMMO, drone.getId(), now)) {
            return StepResult.fail("KEINE MUNITION weg nach " + ticks + " Ticks Dauerfeuer");
        }
        if (ticks < 100) {
            return StepResult.pending();
        }
        note(ctx.state(), "BEDROHUNG und KEINE MUNITION 100 Ticks lang zugleich sichtbar");
        return StepResult.pass();
    }

    private static StepResult threatRate(SelfTest.StepContext ctx, int ticks) {
        if (ticks == 0) {
            ctx.state().put(BEFORE, OsdNotices.received());
            return StepResult.pending();
        }
        if (ticks < 20) {
            return StepResult.pending();
        }
        int received = OsdNotices.received() - (int) ctx.state().get(BEFORE);
        if (received > 4) {
            return StepResult.fail(received + " Pakete in einer Sekunde");
        }
        note(ctx.state(), received + " Pakete in einer Sekunde");
        return StepResult.pass();
    }
}
