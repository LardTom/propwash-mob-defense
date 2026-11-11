package de.lardtom.mobdefense.dev.core.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.booster.BoosterRig;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.CoreBuild;
import de.lardtom.mobdefense.dev.dispenser.CovMdBLanes;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.share.ShareOptions;
import de.lardtom.propwash.api.share.ShareResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CovMdBCoreSteps {
    public static final String EXPECTED_SECTION = "Mob Defense";
    public static final String DIRECTORY = "/data/propwash/propwash_share/directory.json";
    public static final String SHARE_BUTTON = "propwash.share.workbench.code";
    public static final String ADDON_INFO = "propwash.share.export.addon_info";
    public static final String COPY = "propwash.share.export.copy";
    private static final String NOTE = "covmdb.core.note";
    private static final String CODES = "covmdb.core.codes";
    private static final String DATA = "covmdb.core.data";
    private static final String CLIPBOARD = "covmdb.core.clipboard";
    private static boolean hudHidden;

    private CovMdBCoreSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        CovMdBWorkbench.register();
        steps.add("Welt-Regeln: Abschnittsname im Tab EN und DE – Überschrift „Mob Defense“ wie ohne Sprachschlüssel, getrennt von Propwashs Regeln (Foto)", 2400,
                Script.create()
                        .then("Tab Welt-Regeln öffnen", CovMdBCoreSteps::openRules)
                        .run("Sprache Englisch", () -> Shots.language(Minecraft.getInstance(), Shots.ENGLISH))
                        .then("Überschrift Englisch", (ctx, ticks) -> heading(ctx, "en"))
                        .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                        .then("Überschrift Deutsch", (ctx, ticks) -> heading(ctx, "de"))
                        .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                        .append(Shots.photos("cov_world_rules_section", () -> {
                            CoreSteps.scrollToSection();
                            CoreSteps.parkMouse();
                        }))
                        .then("Ergebnis", CovMdBCoreSteps::headingResult));
        steps.add("Sharecode – Export mit und ohne Add-on-Infos, Häkchen standardmäßig an, ohne Infos kürzer, Import mit drei Teilen und leeren Fächern",
                3000, Script.create()
                        .server("Verzeichnis-Abzug ohne Mob Defense", (server, state) -> state.put(NOTE, directory()))
                        .then("Verzeichnis", CovMdBCoreSteps::note)
                        .server("beladene Drohne mit allen drei Teilen aufheben, Werkbank mit dem Bau", CovMdBCoreSteps::loadedDrone)
                        .then("Werkbank", (ctx, ticks) -> CovMdBWorkbench.shown(ctx, ticks, CoreBuild.full()))
                        .then("Knopf Sharecode", CovMdBCoreSteps::openShare)
                        .then("Häkchen Add-on-Infos", CovMdBCoreSteps::infoToggle)
                        .append(Shots.photos("cov_sharecode_export", CoreSteps::parkMouse))
                        .then("Code mit und ohne Add-on-Infos kopieren", CovMdBCoreSteps::copyCodes)
                        .then("Codes prüfen", CovMdBCoreSteps::checkCodes)
                        .run("Fenster zu", () -> Minecraft.getInstance().gui.setScreen(null))
                        .server("Import beider Codes mit /propwash code give", CovMdBCoreSteps::importCodes)
                        .then("Import", CovMdBCoreSteps::note), CovMdBCoreSteps::cleanup);
        steps.add("bildschirmfotos: Modelle – die drei Teile in der Werkbank, an der Drohne und im Inventar neben vergleichbaren Propwash-Teilen", 3000,
                Script.create()
                        .server("Werkbank mit Mob-Defense-Bau", (server, state) -> CovMdBWorkbench.open(server, Arena.ground(-4, 0).above(), CoreBuild.full()))
                        .then("Werkbank Mob Defense", (ctx, ticks) -> CovMdBWorkbench.shown(ctx, ticks, CoreBuild.full()))
                        .append(Shots.photos("cov_showcase_workbench_mobdefense", CoreSteps::parkMouse))
                        .server("Werkbank mit Propwash-Zubehör", (server, state) -> CovMdBWorkbench.open(server, Arena.ground(-4, 0).above(), propwashBuild()))
                        .then("Werkbank Propwash", (ctx, ticks) -> CovMdBWorkbench.shown(ctx, ticks, propwashBuild()))
                        .append(Shots.photos("cov_showcase_workbench_propwash", CoreSteps::parkMouse))
                        .run("Werkbank zu", () -> Minecraft.getInstance().gui.setScreen(null))
                        .server("Werkbank am Server zu, zwei Drohnen nebeneinander", CovMdBCoreSteps::showcaseDrones)
                        .sleep(20)
                        .append(Shots.photos("cov_showcase_drones", CovMdBCoreSteps::hideHud))
                        .run("HUD zurück", CovMdBCoreSteps::restoreHud)
                        .server("Teile ins Inventar", (server, state) -> {
                            Arena.player(server).setGameMode(GameType.CREATIVE);
                            showcaseItems(server, state);
                        })
                        .sleep(10)
                        .run("Inventar öffnen", () -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.gui.setScreen(new InventoryScreen(mc.player));
                        })
                        .sleep(5)
                        .append(Shots.photos("cov_showcase_inventory", CoreSteps::parkMouse))
                        .run("Inventar zu", () -> Minecraft.getInstance().gui.setScreen(null)), CovMdBCoreSteps::cleanup);
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static StepResult openRules(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(null);
            if (!PropwashClientApi.openConfigTab(CoreSteps.WORLD_RULES_TAB, null)) {
                return StepResult.fail("Tab " + CoreSteps.WORLD_RULES_TAB + " fehlt");
            }
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        String first = I18n.get("gamerule." + MobDefense.MOD_ID + ".mobs_react_to_drones");
        boolean found = screen != null && !CovMdBWorkbench.widgets(screen, widget -> widget.getMessage().getString().startsWith(first)).isEmpty();
        if (!found) {
            return ticks > 200 ? StepResult.fail("Tab Welt-Regeln ohne Mob-Defense-Regeln") : StepResult.pending();
        }
        return ticks > 10 ? StepResult.pass() : StepResult.pending();
    }

    public static String fallbackName() {
        String name = MobDefense.MOD_NAME.strip();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : List.of("propwash fpv:", "propwash:")) {
            if (lower.startsWith(prefix)) {
                return name.substring(prefix.length()).strip();
            }
        }
        return name;
    }

    private static StepResult heading(SelfTest.StepContext ctx, String language) {
        Language lang = Language.getInstance();
        boolean translated = lang.has(CoreSteps.SECTION_KEY) && !lang.getOrDefault(CoreSteps.SECTION_KEY).isBlank();
        String shown = translated ? lang.getOrDefault(CoreSteps.SECTION_KEY).strip() : fallbackName();
        ctx.state().put("covmdb.core.heading." + language, shown);
        ctx.state().put("covmdb.core.translated." + language, translated);
        return StepResult.pass(language.toUpperCase(Locale.ROOT) + ": Überschrift „" + shown + "“ (" + (translated ? "aus dem Sprachschlüssel "
                + CoreSteps.SECTION_KEY : "ohne Sprachschlüssel aus dem Mod-Namen") + ", Rückfall ohne Schlüssel wäre „" + fallbackName() + "“)");
    }

    private static StepResult headingResult(SelfTest.StepContext ctx, int ticks) {
        String en = (String) ctx.state().get("covmdb.core.heading.en");
        String de = (String) ctx.state().get("covmdb.core.heading.de");
        String report = "Abschnitt im Tab Welt-Regeln: EN „" + en + "“, DE „" + de + "“, erwartet „" + EXPECTED_SECTION + "“; ohne Sprachschlüssel stünde dort „"
                + fallbackName() + "“ (Mod-Name ohne „Propwash:“)";
        if (!EXPECTED_SECTION.equals(en) || !EXPECTED_SECTION.equals(de) || !EXPECTED_SECTION.equals(fallbackName())) {
            return StepResult.fail(report);
        }
        return StepResult.pass(report);
    }

    private static String directory() {
        try (InputStream in = PropwashApi.class.getResourceAsStream(DIRECTORY)) {
            if (in == null) {
                throw new IllegalStateException("Verzeichnis-Abzug " + DIRECTORY + " fehlt im Propwash-Jar");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (json.contains("\"" + MobDefense.MOD_ID + "\"")) {
                throw new IllegalStateException("Mob Defense steht schon im Verzeichnis-Abzug – Häkchen wäre aus");
            }
            return "Verzeichnis-Abzug (" + json.length() + " Zeichen) ohne mobdefense, also Häkchen „Add-on-Infos“ nötig; ShareOptions.defaults().addonInfo() "
                    + ShareOptions.defaults().addonInfo();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void loadedDrone(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        float yaw = CovMdBLanes.FACING.toYRot();
        player.setYRot(yaw);
        Entity drone = Arena.track(DispenserRig.place(level, player, CoreBuild.full(), "CovMdB Sharecode", Arena.ground(-4, -6)));
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 12), new ItemStack(Items.WIND_CHARGE, 4));
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
        BoosterRig.load(drone, BoosterRig.rocket(2, 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
        ItemStack item = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (PropwashApi.builds().isDrone(player.getInventory().getItem(i))) {
                item = player.getInventory().getItem(i);
            }
        }
        if (item.isEmpty()) {
            throw new IllegalStateException("Drohne nicht aufgehoben");
        }
        int loaded = 0;
        for (AccessorySlot slot : List.of(AccessorySlot.FRONT, AccessorySlot.BOTTOM, AccessorySlot.TOP)) {
            loaded += (int) PropwashApi.accessories().contents(item, slot).stream().filter(stack -> !stack.isEmpty()).count();
        }
        DroneData data = PropwashApi.builds().read(item).orElseThrow(() -> new IllegalStateException("Drohnen-Item ohne Daten"));
        if (loaded < 4 || !data.build().accessories().equals(CoreBuild.full().accessories())) {
            throw new IllegalStateException("Drohnen-Item: " + loaded + " belegte Fächer, Teile " + data.build().accessories());
        }
        state.put(DATA, data);
        player.getInventory().clearContent();
        CovMdBWorkbench.open(server, Arena.ground(-4, 0).above(), data.build());
    }

    private static List<AbstractWidget> labelled(Screen screen, String... keys) {
        List<String> labels = new ArrayList<>();
        for (String key : keys) {
            labels.add(I18n.get(key));
        }
        return CovMdBWorkbench.widgets(screen, widget -> labels.contains(widget.getMessage().getString()));
    }

    private static StepResult openShare(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Werkbank geschlossen");
        }
        if (!ctx.state().containsKey("covmdb.core.bench")) {
            List<AbstractWidget> buttons = labelled(screen, SHARE_BUTTON, SHARE_BUTTON + ".short", SHARE_BUTTON + ".icon_tooltip");
            if (buttons.isEmpty()) {
                return ticks > 60 ? StepResult.fail("Knopf „" + I18n.get(SHARE_BUTTON) + "“ fehlt in " + screen.getClass().getSimpleName()) : StepResult.pending();
            }
            ctx.state().put("covmdb.core.bench", screen);
            CovMdBWorkbench.click(buttons.getFirst());
            return StepResult.pending();
        }
        if (screen != ctx.state().get("covmdb.core.bench")) {
            return StepResult.pass("Sharecode-Fenster offen: " + screen.getClass().getSimpleName());
        }
        return ticks > 100 ? StepResult.fail("Sharecode-Fenster öffnet nicht") : StepResult.pending();
    }

    private static @Nullable AbstractWidget single(String key) {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen == null) {
            return null;
        }
        List<AbstractWidget> found = labelled(screen, key);
        return found.isEmpty() ? null : found.getFirst();
    }

    private static StepResult infoToggle(SelfTest.StepContext ctx, int ticks) {
        AbstractWidget toggle = single(ADDON_INFO);
        if (toggle == null) {
            return ticks > 60 ? StepResult.fail("kein Häkchen „" + I18n.get(ADDON_INFO) + "“ im Sharecode-Fenster") : StepResult.pending();
        }
        return StepResult.pass("Häkchen „" + I18n.get(ADDON_INFO) + "“ sichtbar (Mob Defense nicht im Verzeichnis)");
    }

    @SuppressWarnings("unchecked")
    private static StepResult copyCodes(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> state = ctx.state();
        if (!state.containsKey(CLIPBOARD)) {
            state.put(CLIPBOARD, mc.keyboardHandler.getClipboard());
            state.put(CODES, new ArrayList<String>());
        }
        List<String> codes = (List<String>) state.get(CODES);
        AbstractWidget copy = single(COPY);
        AbstractWidget info = single(ADDON_INFO);
        if (copy == null || info == null) {
            return StepResult.fail("Knöpfe fehlen: Kopieren " + (copy != null) + ", Add-on-Infos " + (info != null));
        }
        int phase = (int) state.getOrDefault("covmdb.core.phase", 0);
        if (ticks % 5 != 0) {
            return StepResult.pending();
        }
        switch (phase) {
            case 0 -> {
                mc.keyboardHandler.setClipboard("");
                CovMdBWorkbench.click(copy);
            }
            case 1 -> {
                codes.add(mc.keyboardHandler.getClipboard());
                CovMdBWorkbench.click(info);
            }
            case 2 -> {
                mc.keyboardHandler.setClipboard("");
                CovMdBWorkbench.click(copy);
            }
            case 3 -> {
                codes.add(mc.keyboardHandler.getClipboard());
                CovMdBWorkbench.click(info);
                mc.keyboardHandler.setClipboard((String) state.get(CLIPBOARD));
            }
            default -> {
                return StepResult.pass("zwei Codes kopiert, Häkchen wieder an, Zwischenablage zurück");
            }
        }
        state.put("covmdb.core.phase", phase + 1);
        return StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult checkCodes(SelfTest.StepContext ctx, int ticks) {
        List<String> codes = (List<String>) ctx.state().get(CODES);
        String with = codes.get(0).strip();
        String without = codes.get(1).strip();
        ShareResult a = PropwashApi.shareCodes().decode(with);
        ShareResult b = PropwashApi.shareCodes().decode(without);
        Optional<ShareResult.NamespaceInfo> infoA = namespace(a);
        Optional<ShareResult.NamespaceInfo> infoB = namespace(b);
        String report = String.format(Locale.ROOT, "Code mit Häkchen (Standard) %d Zeichen, %s, Mob Defense %s; Code ohne Add-on-Infos %d Zeichen, %s, Mob Defense %s",
                with.length(), a.status(), describe(infoA), without.length(), b.status(), describe(infoB));
        if (!a.ok() || !b.ok() || infoA.isEmpty() || infoB.isEmpty() || infoA.get().name().isEmpty() || infoB.get().name().isPresent()
                || without.length() >= with.length()) {
            return StepResult.fail(report);
        }
        DroneData data = a.data().orElseThrow();
        if (!data.build().accessories().equals(CoreBuild.full().accessories())) {
            return StepResult.fail("Code enthält " + data.build().accessories() + " | " + report);
        }
        return StepResult.pass(report + ", ohne Infos " + (with.length() - without.length()) + " Zeichen kürzer");
    }

    private static Optional<ShareResult.NamespaceInfo> namespace(ShareResult result) {
        return result.namespaces().stream().filter(info -> info.namespace().equals(MobDefense.MOD_ID)).findFirst();
    }

    private static String describe(Optional<ShareResult.NamespaceInfo> info) {
        return info.map(value -> "Name " + value.name().orElse("-") + ", Link " + value.link().orElse("-")).orElse("nicht im Code");
    }

    @SuppressWarnings("unchecked")
    private static void importCodes(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        player.getInventory().clearContent();
        List<String> codes = (List<String>) state.get(CODES);
        List<String> notes = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            int before = drones(player).size();
            Arena.command(server, "propwash code give " + codes.get(i).strip() + " " + player.getGameProfile().name());
            List<ItemStack> drones = drones(player);
            if (drones.size() != before + 1) {
                throw new IllegalStateException("Import " + (i + 1) + ": " + (drones.size() - before) + " Drohnen-Items");
            }
            ItemStack item = drones.getLast();
            DroneData data = PropwashApi.builds().read(item).orElseThrow();
            int filled = 0;
            for (AccessorySlot slot : List.of(AccessorySlot.FRONT, AccessorySlot.BOTTOM, AccessorySlot.TOP)) {
                filled += (int) PropwashApi.accessories().contents(item, slot).stream().filter(stack -> !stack.isEmpty()).count();
            }
            if (!data.build().accessories().equals(CoreBuild.full().accessories()) || filled != 0) {
                throw new IllegalStateException("Import " + (i + 1) + ": Teile " + data.build().accessories() + ", " + filled + " belegte Fächer");
            }
            notes.add((i == 0 ? "mit" : "ohne") + " Add-on-Infos: Drohne mit Dispenser-Halterung, Speer-Halterung und Raketen-Booster, alle Fächer leer");
        }
        player.getInventory().clearContent();
        state.put(NOTE, "Import über /propwash code give: " + String.join("; ", notes));
    }

    private static List<ItemStack> drones(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (PropwashApi.builds().isDrone(stack)) {
                out.add(stack);
            }
        }
        return out;
    }

    private static BuildSpec propwashBuild() {
        return CoreBuild.base().withAccessory(AccessorySlot.TOP, CoreBuild.propwash("action_cam_light"))
                .withAccessory(AccessorySlot.BOTTOM, CoreBuild.propwash("gripper"));
    }

    private static void showcaseDrones(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        ServerLevel level = player.level();
        float yaw = CovMdBLanes.FACING.toYRot();
        player.setYRot(yaw);
        BlockPos left = Arena.ground(-4, -10);
        Entity ours = Arena.track(DispenserRig.place(level, player, CoreBuild.full(), "Mob Defense", left));
        DispenserRig.load(ours, new ItemStack(Items.ARROW, 12));
        PropwashApi.accessories().inventory(ours, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
        BoosterRig.load(ours, BoosterRig.rocket(1, 0));
        Entity theirs = Arena.track(DispenserRig.place(level, player, propwashBuild(), "Propwash", left.offset(-1, 0, 0)));
        Vec3 middle = ours.position().add(theirs.position()).scale(0.5).add(0.0, 0.08, 0.0);
        Vec3 eye = middle.add(0.15, 0.22, 0.8);
        Vec3 feet = eye.subtract(0.0, player.getEyeHeight(), 0.0);
        Vec3 look = middle.subtract(eye);
        float viewYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.<Relative>of(), viewYaw, pitch, true);
    }

    private static void showcaseItems(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.getInventory().clearContent();
        int slot = 0;
        for (Identifier part : MobDefense.PARTS) {
            player.getInventory().setItem(slot++, PropwashApi.partItems().stack(part));
        }
        for (String path : List.of("gripper", "led_strip", "action_cam_light", "action_cam_heavy", "action_cam_flashback")) {
            player.getInventory().setItem(slot++, PropwashApi.partItems().stack(CoreBuild.propwash(path)));
        }
        player.getInventory().setItem(9, new ItemStack(Items.IRON_SPEAR));
        player.getInventory().setItem(10, BoosterRig.rocket(1, 0));
        player.inventoryMenu.broadcastChanges();
    }

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
        CoreSteps.parkMouse();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hudHidden = true;
        }
    }

    private static void restoreHud() {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hudHidden = false;
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        restoreHud();
        Object clipboard = ctx.state().get(CLIPBOARD);
        int since = (int) ctx.state().computeIfAbsent("covmdb.core.cleanup", key -> ctx.tick());
        if (ctx.tick() == since && clipboard instanceof String text) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            ServerPlayer player = Arena.player(server);
            player.closeContainer();
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }
}
