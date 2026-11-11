package de.lardtom.mobdefense.dev.ui.client;

import com.mojang.blaze3d.platform.Window;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.MobDefenseGuide;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.OsdNotices;
import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.CoreBuild;
import de.lardtom.mobdefense.dev.core.client.CoreSteps;
import de.lardtom.mobdefense.dev.ui.UiChecks;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.net.Notices;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.AccessoryRegistry;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UiSteps {
    public static final String OSD_TAB = "osd";
    public static final int[][] EDITOR_SIZES = {{854, 480, 2}, {320, 240, 3}};
    private static final String DRONE = "ui.drone";
    private static final String SPEAR = "ui.spear";
    private static final String RECEIVED = "ui.received";
    private static final String BURST = "ui.burst";
    private static final int ARROWS = 12;
    private static final int CHARGES = 3;
    private static final int SPEAR_WEAR_PERCENT = 13;
    private static final int THREAT_TICKS = 10;
    private static final int BURST_SIZE = 10;
    private static final int SCROLLS = 60;
    private static final double LIST_X = 0.8;
    private static final double LIST_Y = 0.3;
    private static final double PAGE_Y = 0.75;
    private static final int SCENE_TICKS = 100;

    private UiSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        Script s11 = Script.create()
                .check("OSD-Elemente und Warnungen angemeldet", UiSteps::registered)
                .server("Drohne mit allen drei Teilen und Inhalt", UiSteps::prepare)
                .then("Inhalt kommt am Client an", UiSteps::contents)
                .then("Elemente im Sichtflug", UiSteps::lineOfSight)
                .server("Warnung EIMER GESPERRT an den Piloten", (server, state) -> notice(server, state, Notice.BUCKET_LOCKED, Notice.MAX_TICKS))
                .then("Warnung am Client, Englisch", (ctx, ticks) -> warning(ctx, ticks, Notice.BUCKET_LOCKED, "BUCKET LOCKED"))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Warnung am Client, Deutsch", (ctx, ticks) -> warning(ctx, ticks, Notice.BUCKET_LOCKED, "EIMER GESPERRT"))
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .check("Warnung nur für die eigene Drohne", UiSteps::otherDrone)
                .sleep(Notices.WINDOW_TICKS + 10)
                .server("BEDROHUNG zehnmal im selben Tick", UiSteps::burst)
                .then("höchstens vier je Sekunde am Client", UiSteps::received)
                .then("BEDROHUNG verschwindet nach der Dauer", UiSteps::threatGone);
        for (int[] size : EDITOR_SIZES) {
            String name = "ui_osd_editor_" + size[0] + "x" + size[1];
            s11.then("Fenster für GUI " + size[0] + "x" + size[1], (ctx, ticks) -> Shots.window(ctx, ticks, size[0] * size[2], size[1] * size[2], size[2]))
                    .check("GUI " + size[0] + "x" + size[1], () -> guiSize(size[0], size[1]))
                    .then("OSD-Editor öffnen", UiSteps::openEditor)
                    .then("Elemente in der Editor-Vorschau", UiSteps::previewed)
                    .append(Shots.photos(name, CoreSteps::parkMouse))
                    .append(Shots.photos(name + "_list", UiSteps::showAddons))
                    .run("Editor schließen", () -> Minecraft.getInstance().gui.setScreen(null));
        }
        s11.run("Fenster zurück", () -> Shots.restoreWindow(Minecraft.getInstance()));
        steps.add("S11 OSD und Editor – Elemente in der Gruppe „Add-ons“, Warnungen über A2, GUI 320×240 und 854×480, DE und EN", 6000, s11);
        steps.add("S11b Flugschule – drei Szenen von Mob Defense, Halten von M auf den Teilen, Fotos DE und EN", 3000, Script.create()
                .check("Szenen angemeldet und mit den Teilen verknüpft", UiSteps::scenes)
                .then("Szene Dispenser öffnen", (ctx, ticks) -> openScene(ctx, ticks, MobDefenseGuide.DISPENSER))
                .append(Shots.photos("ui_flight_school_dispenser", CoreSteps::parkMouse))
                .then("Szene Speer öffnen", (ctx, ticks) -> openScene(ctx, ticks, MobDefenseGuide.SPEAR))
                .append(Shots.photos("ui_flight_school_spear", CoreSteps::parkMouse))
                .then("Szene Raketen-Booster öffnen", (ctx, ticks) -> openScene(ctx, ticks, MobDefenseGuide.BOOSTER))
                .append(Shots.photos("ui_flight_school_booster", CoreSteps::parkMouse))
                .run("Flugschule schließen", () -> Minecraft.getInstance().gui.setScreen(null)));
        steps.add("S11c Fortschritte – root und vier Fortschritte geladen, im Code vergeben und zurückgenommen", 1200, Script.create()
                .server("Fortschritte am Server", (server, state) -> state.put("ui.advancements", UiChecks.advancements(server)))
                .server("Luftunterstützung vergeben und zurücknehmen", UiSteps::awardAndRevoke)
                .then("Fortschritte", (ctx, ticks) -> StepResult.pass((String) ctx.state().get("ui.advancements"))));
    }

    private static StepResult registered() {
        List<Identifier> registered = MobDefenseOsd.registered();
        List<String> missing = new ArrayList<>();
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            if (!registered.contains(spec.id())) {
                missing.add(spec.id().toString());
            }
            if (spec.group() != OsdGroup.ADDON) {
                return StepResult.fail(spec.id() + " in Gruppe " + spec.group());
            }
        }
        for (Notice notice : Notice.values()) {
            if (!registered.contains(notice.id())) {
                missing.add(notice.id().toString());
            }
        }
        if (!missing.isEmpty()) {
            return StepResult.fail("nicht angemeldet: " + missing);
        }
        return StepResult.pass(MobDefenseOsd.specs().size() + " Elemente in der Gruppe „Add-ons“ und " + Notice.values().length + " Warnungen angemeldet");
    }

    private static void standIn(AccessoryRegistry accessories, Identifier part, int slots) {
        if (accessories.behavior(part).isEmpty()) {
            accessories.register(part, new AccessoryBehavior() {
                @Override
                public int slots() {
                    return slots;
                }
            });
            MobDefense.LOG.info("[Mob-Defense-Selbsttest] Ersatz-Verhalten für {} mit {} Slots (Strang noch nicht da)", part, slots);
        }
    }

    private static void prepare(MinecraftServer server, Map<String, Object> state) {
        AccessoryRegistry accessories = PropwashApi.accessories();
        standIn(accessories, MobDefense.DISPENSER_MOUNT, 4);
        standIn(accessories, MobDefense.SPEAR_MOUNT, 1);
        standIn(accessories, MobDefense.ROCKET_BOOSTER, 1);
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos ground = Arena.ground(3, 0);
        ItemStack item = PropwashApi.builds().createDrone(DroneData.of(CoreBuild.full()));
        Vec3 click = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, item, new BlockHitResult(click, Direction.UP, ground, false)) {
        };
        InteractionResult placed = item.getItem().useOn(context);
        if (!placed.consumesAction()) {
            throw new IllegalStateException("Drohne nicht abgestellt: " + placed);
        }
        DroneView view = PropwashApi.drones().dronesNear(level, click, 3.0, false).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("abgestellte Drohne nicht gefunden"));
        Entity drone = Arena.track(view.entity());
        AccessoryInventory front = inventory(accessories, drone, AccessorySlot.FRONT);
        front.set(0, Arena.stack("minecraft:arrow").copyWithCount(ARROWS));
        front.set(1, Arena.stack("minecraft:splash_potion"));
        front.set(2, Arena.stack("minecraft:wind_charge").copyWithCount(CHARGES));
        ItemStack spear = Arena.stack("minecraft:iron_spear");
        spear.setDamageValue(spear.getMaxDamage() * SPEAR_WEAR_PERCENT / 100);
        inventory(accessories, drone, AccessorySlot.BOTTOM).set(0, spear);
        inventory(accessories, drone, AccessorySlot.TOP).set(0, Arena.stack("minecraft:firework_rocket"));
        state.put(DRONE, drone.getId());
        state.put(SPEAR, new int[]{spear.getMaxDamage(), spear.getDamageValue()});
    }

    private static AccessoryInventory inventory(AccessoryRegistry accessories, Entity drone, AccessorySlot slot) {
        return accessories.inventory(drone, slot).orElseThrow(() -> new IllegalStateException("kein Inventar " + slot + " an der Drohne"));
    }

    private static @Nullable Entity drone(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        Object id = ctx.state().get(DRONE);
        return mc.level == null || id == null ? null : mc.level.getEntity((int) id);
    }

    private static Map<Identifier, String> expected(SelfTest.StepContext ctx) {
        int[] spear = (int[]) ctx.state().get(SPEAR);
        Map<Identifier, String> out = new LinkedHashMap<>();
        out.put(MobDefenseOsd.DISPENSER, "DSP 1 ARW " + ARROWS);
        out.put(MobDefenseOsd.DISPENSER_SLOTS, "[ARW]SPL WND ---");
        out.put(MobDefenseOsd.BOOSTER, OsdLines.BOOSTER_READY);
        out.put(MobDefenseOsd.SPEAR, OsdLines.spear(spear[0], spear[1]));
        return out;
    }

    private static Map<Identifier, String> shown(OsdContext context) {
        Map<Identifier, String> out = new LinkedHashMap<>();
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            OsdText text = spec.content().text(context);
            out.put(spec.id(), text == null ? null : text.text());
        }
        return out;
    }

    private static StepResult contents(SelfTest.StepContext ctx, int ticks) {
        return compare(ctx, ticks, true, false, "FPV");
    }

    private static StepResult lineOfSight(SelfTest.StepContext ctx, int ticks) {
        return compare(ctx, ticks, false, true, "Sichtflug");
    }

    private static StepResult compare(SelfTest.StepContext ctx, int ticks, boolean fpv, boolean lineOfSight, String label) {
        Entity drone = drone(ctx);
        if (drone == null) {
            return ticks > 200 ? StepResult.fail("Drohne nicht am Client") : StepResult.pending();
        }
        Map<Identifier, String> expected = expected(ctx);
        Map<Identifier, String> shown = shown(new TestContext(drone, fpv, lineOfSight, false, drone.level().getGameTime()));
        if (!expected.equals(shown)) {
            return ticks > 200 ? StepResult.fail(label + ": " + shown + " statt " + expected) : StepResult.pending();
        }
        for (String text : shown.values()) {
            if (!OsdLines.printableAscii(text)) {
                return StepResult.fail("kein ASCII: " + text);
            }
        }
        Map<Identifier, String> preview = shown(new TestContext(null, false, false, true, 0L));
        if (preview.containsValue(null)) {
            return StepResult.fail("Editor-Vorschau ohne Text: " + preview);
        }
        return StepResult.pass(label + ": " + String.join(" | ", shown.values()));
    }

    private static void notice(MinecraftServer server, Map<String, Object> state, Notice notice, int ticks) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Arena.level(server).getEntity((int) state.get(DRONE));
        if (drone == null) {
            throw new IllegalStateException("Drohne am Server verschwunden");
        }
        state.put(RECEIVED, OsdNotices.received());
        if (!Notices.send(player, drone, notice, ticks)) {
            throw new IllegalStateException(notice + " nicht gesendet (Grenze?)");
        }
    }

    private static StepResult warning(SelfTest.StepContext ctx, int ticks, Notice notice, String text) {
        Entity drone = drone(ctx);
        if (drone == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        OsdText shown = MobDefenseOsd.warningText(notice, new TestContext(drone, true, false, false, drone.level().getGameTime()));
        if (shown == null || !text.equals(shown.text())) {
            return ticks > 100 ? StepResult.fail("Warnung " + (shown == null ? "fehlt" : "„" + shown.text() + "“") + " statt „" + text + "“")
                    : StepResult.pending();
        }
        if (!text.equals(I18n.get(notice.translationKey()))) {
            return StepResult.fail("Sprachdatei liefert „" + I18n.get(notice.translationKey()) + "“");
        }
        return StepResult.pass(text + " (" + shown.tone() + ")");
    }

    private static StepResult otherDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        OsdText shown = MobDefenseOsd.warningText(Notice.BUCKET_LOCKED, new TestContext(mc.player, true, false, false, mc.player.level().getGameTime()));
        return shown == null ? StepResult.pass("andere Drohne ohne Warnung") : StepResult.fail("Warnung erscheint auch bei fremder Drohne");
    }

    private static void burst(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = Arena.level(server).getEntity((int) state.get(DRONE));
        if (drone == null) {
            throw new IllegalStateException("Drohne am Server verschwunden");
        }
        state.put(RECEIVED, OsdNotices.received());
        int sent = 0;
        for (int i = 0; i < BURST_SIZE; i++) {
            if (Notices.threat(player, drone, THREAT_TICKS)) {
                sent++;
            }
        }
        state.put(BURST, sent);
        if (sent != Notices.PER_SECOND) {
            throw new IllegalStateException(sent + " von " + BURST_SIZE + " gesendet statt " + Notices.PER_SECOND);
        }
    }

    private static StepResult received(SelfTest.StepContext ctx, int ticks) {
        int before = (int) ctx.state().get(RECEIVED);
        int now = OsdNotices.received() - before;
        if (now > Notices.PER_SECOND) {
            return StepResult.fail(now + " Pakete am Client in einer Sekunde");
        }
        if (ticks < 30) {
            return StepResult.pending();
        }
        if (now != Notices.PER_SECOND) {
            return StepResult.fail(now + " statt " + Notices.PER_SECOND + " Pakete am Client");
        }
        return StepResult.pass(ctx.state().get(BURST) + " von " + BURST_SIZE + " gesendet, " + now + " am Client");
    }

    private static StepResult threatGone(SelfTest.StepContext ctx, int ticks) {
        Entity drone = drone(ctx);
        if (drone == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        OsdText shown = MobDefenseOsd.warningText(Notice.THREAT, new TestContext(drone, true, false, false, drone.level().getGameTime()));
        if (shown != null) {
            return ticks > THREAT_TICKS + 40 ? StepResult.fail("BEDROHUNG bleibt stehen") : StepResult.pending();
        }
        return StepResult.pass("BEDROHUNG nach " + THREAT_TICKS + " Ticks weg");
    }

    private static StepResult guiSize(int width, int height) {
        Window window = Minecraft.getInstance().getWindow();
        if (window.getGuiScaledWidth() != width || window.getGuiScaledHeight() != height) {
            return StepResult.fail("GUI " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight() + " statt " + width + "x" + height);
        }
        return StepResult.pass("GUI " + width + "x" + height + " (Fenster " + window.getWidth() + "x" + window.getHeight() + ")");
    }

    private static StepResult openEditor(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(null);
            MobDefenseOsd.resetPreviews();
            if (!PropwashClientApi.openConfigTab(OSD_TAB, null)) {
                return StepResult.fail("openConfigTab findet den Tab " + OSD_TAB + " nicht");
            }
            return StepResult.pending();
        }
        return mc.gui.screen() != null ? StepResult.pass() : ticks > 200 ? StepResult.fail("OSD-Editor öffnet nicht") : StepResult.pending();
    }

    private static StepResult previewed(SelfTest.StepContext ctx, int ticks) {
        List<String> drawn = new ArrayList<>();
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            if (MobDefenseOsd.previews(spec.id()) > 0) {
                drawn.add(spec.id().getPath());
            }
        }
        boolean enabledDrawn = MobDefenseOsd.previews(MobDefenseOsd.DISPENSER) > 0 && MobDefenseOsd.previews(MobDefenseOsd.BOOSTER) > 0;
        if (!enabledDrawn) {
            return ticks > 200 ? StepResult.fail("Editor-Vorschau zeichnet nur " + drawn) : StepResult.pending();
        }
        return StepResult.pass("Vorschau zeichnet " + drawn);
    }

    private static void showAddons() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null) {
            for (int i = 0; i < SCROLLS; i++) {
                screen.mouseScrolled(screen.width * LIST_X, screen.height * LIST_Y, 0.0, -1.0);
            }
            for (int i = 0; i < SCROLLS; i++) {
                screen.mouseScrolled(screen.width * LIST_X, screen.height * PAGE_Y, 0.0, 1.0);
            }
        }
        CoreSteps.parkMouse();
    }

    private static StepResult scenes() {
        List<String> registered = MobDefenseGuide.registered();
        if (!registered.containsAll(MobDefenseGuide.SCENES)) {
            return StepResult.fail("Szenen angemeldet: " + registered);
        }
        Map<Identifier, String> links = Map.of(MobDefense.DISPENSER_MOUNT, MobDefenseGuide.DISPENSER, MobDefense.SPEAR_MOUNT, MobDefenseGuide.SPEAR,
                MobDefense.ROCKET_BOOSTER, MobDefenseGuide.BOOSTER);
        List<String> notes = new ArrayList<>();
        for (Map.Entry<Identifier, String> link : links.entrySet()) {
            List<String> scenes = PropwashClientApi.flightSchoolScenes(PropwashApi.partItems().stack(link.getKey()));
            if (!scenes.contains(link.getValue())) {
                return StepResult.fail(link.getKey() + " öffnet " + scenes + " ohne " + link.getValue());
            }
            notes.add(link.getKey().getPath() + " → " + scenes);
        }
        for (String scene : MobDefenseGuide.SCENES) {
            String prefix = scene.replace(':', '.').replaceFirst("\\.", ".guide.scene.");
            if (!Language.getInstance().has(prefix + ".title")) {
                return StepResult.fail(prefix + ".title fehlt");
            }
        }
        return StepResult.pass(String.join("; ", notes));
    }

    private static StepResult openScene(SelfTest.StepContext ctx, int ticks, String scene) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(null);
            if (!PropwashClientApi.openFlightSchoolScene(scene, null)) {
                return StepResult.fail("openFlightSchoolScene(" + scene + ") liefert false");
            }
            return StepResult.pending();
        }
        if (mc.gui.screen() == null) {
            return ticks > 100 ? StepResult.fail("Szene " + scene + " öffnet nicht") : StepResult.pending();
        }
        return ticks < SCENE_TICKS ? StepResult.pending() : StepResult.pass(scene + " offen: " + mc.gui.screen().getClass().getSimpleName());
    }

    private static void awardAndRevoke(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        AdvancementHolder holder = server.getAdvancements().get(MobDefense.id("air_support"));
        if (holder == null) {
            throw new IllegalStateException("Fortschritt air_support fehlt");
        }
        if (!player.getAdvancements().award(holder, "done") || !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            throw new IllegalStateException("air_support lässt sich nicht vergeben");
        }
        player.getAdvancements().revoke(holder, "done");
        if (player.getAdvancements().getOrStartProgress(holder).isDone()) {
            throw new IllegalStateException("air_support lässt sich nicht zurücknehmen");
        }
        state.put("ui.advancements", state.get("ui.advancements") + "; air_support vergeben und zurückgenommen");
    }

    private record TestContext(@Nullable Entity entity, boolean fpv, boolean lineOfSight, boolean preview, long gameTime) implements OsdContext {
        @Override
        public Optional<Entity> drone() {
            return Optional.ofNullable(this.entity);
        }

        @Override
        public FlightTelemetry telemetry() {
            Vec3 pos = this.entity == null ? Vec3.ZERO : this.entity.position();
            return new FlightTelemetry(true, "ACRO", Optional.empty(), pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 16.8, 4.2, 1.0, false, 0.0, 0.0,
                    0.0, false, 0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        @Override
        public float partialTick() {
            return 0.0F;
        }
    }
}
