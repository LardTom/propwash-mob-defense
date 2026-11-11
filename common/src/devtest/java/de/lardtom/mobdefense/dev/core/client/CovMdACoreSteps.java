package de.lardtom.mobdefense.dev.core.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.MobDefenseTab;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.CoreBuild;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdAFlightSteps;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.ui.client.CovMdAScreenText;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.build.PaintSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import de.lardtom.propwash.api.registry.ExtensionTab;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

public final class CovMdACoreSteps {
    public static final Identifier MAIN_TAB = Identifier.fromNamespaceAndPath("propwash", "main");
    public static final Identifier PART_COMPATIBILITY = Identifier.fromNamespaceAndPath("propwash", "part_compatibility");
    public static final Identifier GRIPPER = CoreBuild.propwash("gripper");
    public static final Identifier LED_STRIP = CoreBuild.propwash("led_strip");
    public static final Identifier ACTION_CAM = CoreBuild.propwash("action_cam_light");
    public static final int SERVO_COLOR = 0x2E86C1;
    public static final int HOUSING_COLOR = 0xC0392B;
    private static final AtomicReference<@Nullable WorkbenchContext> CONTEXT = new AtomicReference<>();
    private static final String NOTES = "covmda.core.notes";
    private static final String SCREEN = "covmda.core.screen";
    private static final String SPEC = "covmda.core.spec";
    private static final int SERVICE_ACCESSORY_BUTTON = 3000;
    private static boolean registered;

    private CovMdACoreSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        listen();
        steps.add("Kreativ-Reiter – Reiter „Mob Defense“ mit genau den drei Teilen, nicht im Propwash-Reiter, Fotos beider Reiter", 2000, Script.create()
                .then("Reiter neu aufbauen", CovMdACoreSteps::rebuildTabs)
                .check("Reiter Mob Defense", CovMdACoreSteps::ownTab)
                .check("Propwash-Reiter", CovMdACoreSteps::mainTab)
                .then("Kreativ-Inventar mit Reiter Mob Defense", (ctx, ticks) -> creative(ctx, ticks, true))
                .append(Shots.photos("covmda_creative_tab_mobdefense", CoreSteps::parkMouse))
                .then("Kreativ-Inventar mit Propwash-Reiter", (ctx, ticks) -> creative(ctx, ticks, false))
                .append(Shots.photos("covmda_creative_tab_propwash", CovMdACoreSteps::scrollToAccessories))
                .run("Inventar zu", () -> Minecraft.getInstance().gui.setScreen(null)));
        steps.add("Rezeptbuch – im Überlebensmodus ohne Zutat keine Rezepte, mit Eisennugget Dispenser- und Speer-Halterung in der Gruppe von Greifer und "
                + "LED-Streifen, mit Kupfernugget der Booster", 2400, Script.create()
                .server("Überlebensmodus, Rezepte und Freischaltungen zurück", (server, state) -> recipes(server, true))
                .then("ohne Zutat keine Mob-Defense-Rezepte", (ctx, ticks) -> book(ctx, ticks, Set.of()))
                .server("Eisennugget aufheben", (server, state) -> Arena.player(server).getInventory().add(new ItemStack(Items.IRON_NUGGET)))
                .then("mit Eisennugget", (ctx, ticks) -> book(ctx, ticks, Set.of("dispenser_mount", "spear_mount")))
                .then("Gruppe wie Greifer und LED-Streifen", CovMdACoreSteps::group)
                .server("Kupfernugget aufheben", (server, state) -> Arena.player(server).getInventory().add(new ItemStack(Items.COPPER_NUGGET)))
                .then("mit Kupfernugget", (ctx, ticks) -> book(ctx, ticks, Set.of("dispenser_mount", "spear_mount", "rocket_booster")))
                .then("Ergebnis", CovMdACoreSteps::notes), CovMdACoreSteps::cleanupRecipes);
        steps.add("Werkbank belegte Plätze – unten Greifer bzw. LED-Streifen, oben Action-Cam: Speer-Halterung und Booster ersetzen sie, das alte Teil geht ins "
                + "Inventar, je Halterung ein Zubehör", 2400, Script.create()
                .server("Werkbank mit 5″, Greifer unten und Action-Cam oben", (server, state) -> open(server, state,
                        CoreBuild.base().withAccessory(AccessorySlot.BOTTOM, GRIPPER).withAccessory(AccessorySlot.TOP, ACTION_CAM)))
                .then("Werkbank zeigt den Bau", CovMdACoreSteps::shown)
                .server("Speer-Halterung auf den Greifer", (server, state) -> swap(server, state, GRIPPER, MobDefense.SPEAR_MOUNT))
                .server("Booster auf die Action-Cam", (server, state) -> swap(server, state, ACTION_CAM, MobDefense.ROCKET_BOOSTER))
                .then("Bauplan: Speer unten, Booster oben", (ctx, ticks) -> plan(ctx, ticks, Map.of(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT,
                        AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER)))
                .server("LED-Streifen auf die Speer-Halterung", (server, state) -> swap(server, state, MobDefense.SPEAR_MOUNT, LED_STRIP))
                .then("Bauplan: LED-Streifen unten", (ctx, ticks) -> plan(ctx, ticks, Map.of(AccessorySlot.BOTTOM, LED_STRIP, AccessorySlot.TOP,
                        MobDefense.ROCKET_BOOSTER)))
                .server("Speer-Halterung auf den LED-Streifen", (server, state) -> swap(server, state, LED_STRIP, MobDefense.SPEAR_MOUNT))
                .then("Bauplan: Speer unten", (ctx, ticks) -> plan(ctx, ticks, Map.of(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT, AccessorySlot.TOP,
                        MobDefense.ROCKET_BOOSTER)))
                .append(Shots.photos("covmda_workbench_replaced", CoreSteps::parkMouse))
                .server("Werkbank zu, alte Teile im Inventar", CovMdACoreSteps::oldPartsBack)
                .then("Ergebnis", CovMdACoreSteps::notes));
        steps.add("Whoop ohne Halterung vorne – Hinweis „Dieser Rahmen hat hier keine Halterung“ vorn und oben, Dispenser macht den Bau ungültig, Speer unten "
                + "geht; mit „Teile müssen zusammenpassen“ aus erlaubt mit Hinweis, Regel danach zurück", 3000, Script.create()
                .server("Werkbank mit Whoop", (server, state) -> open(server, state, whoop()))
                .then("Werkbank zeigt die Whoop", CovMdACoreSteps::shown)
                .then("Hinweis vorn", (ctx, ticks) -> hint(ctx, ticks, MobDefense.DISPENSER_MOUNT, "accessory_unsupported"))
                .then("Hinweis oben", (ctx, ticks) -> hint(ctx, ticks, MobDefense.ROCKET_BOOSTER, "accessory_unsupported"))
                .then("unten frei", (ctx, ticks) -> hint(ctx, ticks, MobDefense.SPEAR_MOUNT, "accessory_optional"))
                .append(Shots.photos("covmda_whoop_hint", () -> hover(MobDefense.DISPENSER_MOUNT)))
                .server("Dispenser vorn einsetzen", (server, state) -> place(server, state, MobDefense.DISPENSER_MOUNT))
                .then("Bau ungültig", (ctx, ticks) -> buildable(ctx, ticks, whoop().withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT), false))
                .server("Dispenser raus, Speer unten", (server, state) -> {
                    take(server, state, MobDefense.DISPENSER_MOUNT);
                    place(server, state, MobDefense.SPEAR_MOUNT);
                })
                .then("Whoop mit Speer gültig", (ctx, ticks) -> buildable(ctx, ticks, whoop().withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT), true))
                .server("Regel „Teile müssen zusammenpassen“ aus, Werkbank neu", (server, state) -> {
                    Arena.rule(server, PART_COMPATIBILITY, "false");
                    Arena.player(server).closeContainer();
                })
                .sleep(20)
                .server("Werkbank mit Whoop und Speer", (server, state) -> open(server, state, whoop().withAccessory(AccessorySlot.BOTTOM,
                        MobDefense.SPEAR_MOUNT)))
                .then("Werkbank zeigt die Whoop", CovMdACoreSteps::shown)
                .then("Hinweis vorn mit Regel aus", (ctx, ticks) -> hint(ctx, ticks, MobDefense.DISPENSER_MOUNT, "accessory_unsupported_free"))
                .append(Shots.photos("covmda_whoop_hint_free", () -> hover(MobDefense.DISPENSER_MOUNT)))
                .server("Dispenser vorn einsetzen", (server, state) -> place(server, state, MobDefense.DISPENSER_MOUNT))
                .then("Whoop mit Dispenser erlaubt", (ctx, ticks) -> buildable(ctx, ticks, whoop().withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT)
                        .withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT), true))
                .server("Regel zurück", (server, state) -> {
                    Arena.rule(server, PART_COMPATIBILITY, "true");
                    Arena.player(server).closeContainer();
                })
                .then("Ergebnis", CovMdACoreSteps::notes));
        steps.add("Lackierung – Dispenser-Halterung mit eigener Servo- und Gehäusefarbe, beide Farben getrennt und nach dem Bauen erhalten, Foto", 2400,
                Script.create()
                        .server("Werkbank mit lackierter Dispenser-Halterung", CovMdACoreSteps::paintBench)
                        .then("Werkbank zeigt die Farben", CovMdACoreSteps::paintShown)
                        .server("Drohne bauen und Farben lesen", CovMdACoreSteps::paintBuilt)
                        .sleep(20)
                        .append(Shots.photos("covmda_paint_dispenser", CovMdACoreSteps::hideHud))
                        .run("HUD zurück", CovMdACoreSteps::restoreHud)
                        .then("Ergebnis", CovMdACoreSteps::notes));
        steps.add("Just More Parts – jeder JMP-Rahmen mit Halterung vorn nimmt den Dispenser (+150 g), Speer und Booster nach Halterung (nur im Lauf mit JMP-Jar)",
                1600, Script.create()
                        .server("Rahmen von Just More Parts", CovMdACoreSteps::justMoreParts)
                        .then("Ergebnis", CovMdACoreSteps::notes));
        steps.add("Zubehör-Inventar – Slots 4/1/1, Beladung … g, Shift-Klick eines vollen Stapels: höchstens 4 TNT und 16 Pfeile je Slot, Rest bleibt im Inventar",
                3000, Script.create()
                        .server("5″ mit allen drei Teilen", (server, state) -> Pilot.spawn(server, state, CoreBuild.full(), "CovMdA Inventar", Arena.ground(0, -6),
                                180.0F))
                        .append(openAccessories())
                        .server("Slots je Halterung", CovMdACoreSteps::slotRows)
                        .then("Beladung 0 g", (ctx, ticks) -> CovMdAFlightSteps.payloadLabel(ctx, ticks, "leer", 0))
                        .server("Shift-Klick 64 TNT", (server, state) -> quickMove(server, state, new ItemStack(Items.TNT, 64), 16, 48))
                        .then("Beladung 9600 g", (ctx, ticks) -> CovMdAFlightSteps.payloadLabel(ctx, ticks, "16 TNT", 9600))
                        .server("Dispenser leeren, Shift-Klick 64 Pfeile", (server, state) -> {
                            emptyDispenser(server, state);
                            quickMove(server, state, new ItemStack(Items.ARROW, 64), 64, 0);
                        })
                        .then("Beladung 1280 g", (ctx, ticks) -> CovMdAFlightSteps.payloadLabel(ctx, ticks, "4 × 16 Pfeile", 1280))
                        .append(Shots.photos("covmda_accessory_inventory", CoreSteps::parkMouse))
                        .server("noch 64 Pfeile", (server, state) -> quickMove(server, state, new ItemStack(Items.ARROW, 64), 0, 64))
                        .run("Inventar zu", () -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.player.closeContainer();
                            }
                            mc.gui.setScreen(null);
                        })
                        .server("Menü am Server zu", (server, state) -> Arena.player(server).closeContainer())
                        .then("Ergebnis", CovMdACoreSteps::notes));
    }

    private static synchronized void listen() {
        if (registered) {
            return;
        }
        registered = true;
        PropwashClientApi.registerWorkbenchExtension(new WorkbenchExtension() {
            @Override
            public Identifier id() {
                return MobDefense.id("selftest_cov_md_a_workbench");
            }

            @Override
            public void init(WorkbenchContext context) {
                CONTEXT.set(context);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void note(Map<String, Object> state, String note) {
        ((List<String>) state.computeIfAbsent(NOTES, key -> new ArrayList<String>())).add(note);
    }

    @SuppressWarnings("unchecked")
    private static StepResult notes(SelfTest.StepContext ctx, int ticks) {
        return StepResult.pass(String.join("; ", (List<String>) ctx.state().getOrDefault(NOTES, List.of())));
    }

    private static String id(ItemStack stack) {
        return PropwashApi.partItems().partOf(stack).map(Identifier::toString).orElse(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static boolean operatorTab() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.operatorItemsTab().get();
    }

    private static StepResult rebuildTabs(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return StepResult.fail("keine Welt");
        }
        CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks() && operatorTab(),
                mc.level.registryAccess());
        return StepResult.pass();
    }

    private static List<String> displayed(Identifier tab) {
        CreativeModeTab creative = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tab);
        if (creative == null) {
            throw new IllegalStateException("Kreativ-Reiter " + tab + " fehlt");
        }
        List<String> out = new ArrayList<>();
        for (ItemStack stack : creative.getDisplayItems()) {
            out.add(id(stack));
        }
        return out;
    }

    private static List<String> parts() {
        List<String> out = new ArrayList<>();
        MobDefense.PARTS.forEach(part -> out.add(part.toString()));
        return out;
    }

    private static StepResult ownTab() {
        Optional<ExtensionTab> tab = MobDefenseTab.tab();
        if (tab.isEmpty() || !tab.get().registered()) {
            return StepResult.fail("extensionTab(mobdefense) nicht registriert");
        }
        List<String> api = new ArrayList<>();
        tab.get().contents().forEach(stack -> api.add(id(stack)));
        List<String> shown = displayed(tab.get().tabId());
        if (!new TreeSet<>(shown).equals(new TreeSet<>(parts())) || shown.size() != 3 || !new TreeSet<>(api).equals(new TreeSet<>(parts()))) {
            return StepResult.fail("Reiter " + tab.get().tabId() + " zeigt " + shown + " (API " + api + ") statt " + parts());
        }
        return StepResult.pass("Reiter " + tab.get().tabId() + " („" + I18n.get(tab.get().nameKey()) + "“) zeigt genau " + shown);
    }

    private static List<String> propwashAccessories() {
        return List.of(GRIPPER.toString(), LED_STRIP.toString());
    }

    private static List<String> listedParts(List<String> shown) {
        return parts().stream().filter(shown::contains).toList();
    }

    private static StepResult mainTab() {
        List<String> shown = displayed(MAIN_TAB);
        List<String> listed = listedParts(shown);
        if (!listed.isEmpty()) {
            return StepResult.fail("Propwash-Reiter listet " + listed + " trotz eigenem Reiter");
        }
        for (String accessory : propwashAccessories()) {
            if (!shown.contains(accessory)) {
                return StepResult.fail("Propwash-Reiter mit " + shown.size() + " Einträgen ohne " + accessory);
            }
        }
        return StepResult.pass("Propwash-Reiter mit " + shown.size() + " Einträgen ohne die drei Teile, Greifer und LED-Streifen an Platz "
                + propwashAccessories().stream().map(shown::indexOf).toList());
    }

    private static Field selected() throws ReflectiveOperationException {
        Field field = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
        field.setAccessible(true);
        return field;
    }

    private static StepResult creative(SelfTest.StepContext ctx, int ticks, boolean own) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return StepResult.fail("kein Spieler");
        }
        Identifier id = own ? MobDefenseTab.tab().map(ExtensionTab::tabId).orElse(MAIN_TAB) : MAIN_TAB;
        CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(id);
        if (ticks == 0) {
            try {
                mc.gui.setScreen(null);
                selected().set(null, tab);
                mc.gui.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), operatorTab()));
            } catch (ReflectiveOperationException e) {
                return StepResult.fail("Reiter nicht wählbar: " + e);
            }
            return StepResult.pending();
        }
        if (ticks < 10) {
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (!(screen instanceof CreativeModeInventoryScreen inventory)) {
            return StepResult.fail("Kreativ-Inventar zu");
        }
        Object now;
        try {
            now = selected().get(null);
        } catch (ReflectiveOperationException e) {
            return StepResult.fail("Reiter nicht lesbar: " + e);
        }
        List<String> shown = new ArrayList<>();
        for (ItemStack stack : inventory.getMenu().items) {
            if (!stack.isEmpty()) {
                shown.add(id(stack));
            }
        }
        if (now != tab) {
            return StepResult.fail("Kreativ-Inventar zeigt Reiter " + BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) now) + " statt " + id);
        }
        for (String part : own ? parts() : propwashAccessories()) {
            if (!shown.contains(part)) {
                return ticks > 60 ? StepResult.fail(id + " zeigt im Inventar " + shown.size() + " Einträge ohne " + part) : StepResult.pending();
            }
        }
        if (own) {
            return StepResult.pass(id + " im Kreativ-Inventar: " + shown);
        }
        List<String> listed = listedParts(shown);
        if (!listed.isEmpty()) {
            return StepResult.fail(id + " zeigt im Inventar " + listed + " trotz eigenem Reiter");
        }
        return StepResult.pass(id + " im Kreativ-Inventar: " + shown.size() + " Einträge ohne die drei Teile, Greifer und LED-Streifen an Platz "
                + propwashAccessories().stream().map(shown::indexOf).toList());
    }

    private static void scrollToAccessories() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof CreativeModeInventoryScreen inventory && mc.player != null) {
            for (int i = 0; i < 200 && !accessoriesVisible(inventory, mc); i++) {
                inventory.mouseScrolled(inventory.width / 2.0, inventory.height / 2.0, 0.0, -1.0);
            }
        }
        CoreSteps.parkMouse();
    }

    private static boolean accessoriesVisible(CreativeModeInventoryScreen inventory, Minecraft mc) {
        Set<String> visible = new TreeSet<>();
        for (Slot slot : inventory.getMenu().slots) {
            if (slot.hasItem() && slot.container != mc.player.getInventory()) {
                visible.add(id(slot.getItem()));
            }
        }
        return visible.containsAll(propwashAccessories());
    }

    private static List<ResourceKey<Recipe<?>>> recipeKeys() {
        List<ResourceKey<Recipe<?>>> out = new ArrayList<>();
        for (Identifier part : MobDefense.PARTS) {
            out.add(ResourceKey.create(Registries.RECIPE, part));
        }
        return out;
    }

    private static void recipes(MinecraftServer server, boolean survival) {
        ServerPlayer player = Arena.player(server);
        player.getInventory().clearContent();
        if (survival) {
            player.setGameMode(GameType.SURVIVAL);
        }
        List<RecipeHolder<?>> holders = new ArrayList<>();
        for (ResourceKey<Recipe<?>> key : recipeKeys()) {
            holders.add(server.getRecipeManager().byKey(key).orElseThrow(() -> new IllegalStateException("Rezept " + key.identifier() + " fehlt")));
        }
        player.resetRecipes(holders);
        for (Identifier part : MobDefense.PARTS) {
            AdvancementHolder unlock = server.getAdvancements().get(MobDefense.id("recipes/tools/" + part.getPath()));
            if (unlock == null) {
                throw new IllegalStateException("Freischaltung für " + part + " fehlt");
            }
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(unlock);
            for (String criterion : progress.getCompletedCriteria()) {
                player.getAdvancements().revoke(unlock, criterion);
            }
        }
        if (survival) {
            List<RecipeHolder<?>> propwash = new ArrayList<>();
            for (Identifier id : List.of(GRIPPER, LED_STRIP)) {
                server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).ifPresent(propwash::add);
            }
            if (propwash.size() != 2) {
                throw new IllegalStateException("Rezepte Greifer und LED-Streifen fehlen");
            }
            player.awardRecipes(propwash);
        }
    }

    private static Map<String, RecipeDisplayEntry> known() {
        Minecraft mc = Minecraft.getInstance();
        Map<String, RecipeDisplayEntry> out = new TreeMap<>();
        if (mc.player == null || mc.level == null) {
            return out;
        }
        mc.player.getRecipeBook().rebuildCollections();
        for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                for (ItemStack result : entry.resultItems(SlotDisplayContext.fromLevel(mc.level))) {
                    out.put(id(result), entry);
                }
            }
        }
        return out;
    }

    private static StepResult book(SelfTest.StepContext ctx, int ticks, Set<String> expected) {
        Set<String> seen = new TreeSet<>();
        for (String id : known().keySet()) {
            if (id.startsWith(MobDefense.MOD_ID + ":")) {
                seen.add(id.substring(MobDefense.MOD_ID.length() + 1));
            }
        }
        if (!seen.equals(expected)) {
            return ticks > 200 ? StepResult.fail("Rezeptbuch zeigt " + seen + " statt " + expected) : StepResult.pending();
        }
        if (ticks < 20) {
            return StepResult.pending();
        }
        note(ctx.state(), "Rezeptbuch " + (expected.isEmpty() ? "ohne Mob-Defense-Rezepte" : "mit " + seen));
        return StepResult.pass();
    }

    private static StepResult group(SelfTest.StepContext ctx, int ticks) {
        Map<String, RecipeDisplayEntry> known = known();
        RecipeDisplayEntry dispenser = known.get(MobDefense.DISPENSER_MOUNT.toString());
        RecipeDisplayEntry gripper = known.get(GRIPPER.toString());
        RecipeDisplayEntry led = known.get(LED_STRIP.toString());
        if (dispenser == null || gripper == null || led == null) {
            return ticks > 100 ? StepResult.fail("Rezepte fehlen: Dispenser " + (dispenser != null) + ", Greifer " + (gripper != null) + ", LED " + (led != null))
                    : StepResult.pending();
        }
        OptionalInt group = dispenser.group();
        if (group.isEmpty() || !group.equals(gripper.group()) || !group.equals(led.group()) || !dispenser.category().equals(gripper.category())) {
            return StepResult.fail("Gruppe Dispenser " + group + ", Greifer " + gripper.group() + ", LED " + led.group());
        }
        boolean together = false;
        for (RecipeCollection collection : Minecraft.getInstance().player.getRecipeBook().getCollections()) {
            List<RecipeDisplayEntry> entries = collection.getRecipes();
            if (entries.contains(dispenser) && entries.contains(gripper)) {
                together = true;
            }
        }
        if (!together) {
            return StepResult.fail("Dispenser-Halterung und Greifer nicht in einer Rezeptbuch-Sammlung");
        }
        note(ctx.state(), "Dispenser-Halterung, Greifer und LED-Streifen in derselben Gruppe " + group.getAsInt() + " und Sammlung");
        return StepResult.pass();
    }

    private static final SelfTest.ClientStep RECIPE_CLEANUP = Script.create()
            .server("Rezepte und Freischaltungen zurück", (server, state) -> recipes(server, false));

    private static StepResult cleanupRecipes(SelfTest.StepContext ctx) {
        return RECIPE_CLEANUP.tick(ctx);
    }

    static BuildSpec whoop() {
        return BuildSpec.of(CoreBuild.propwash("whoop_65"), CoreBuild.propwash("aio_whoop"), CoreBuild.propwash("m0802_22000"), CoreBuild.propwash("p31_4b"),
                CoreBuild.propwash("analog_nano"), CoreBuild.propwash("b1s_300_hv"));
    }

    private static void open(MinecraftServer server, Map<String, Object> state, BuildSpec spec) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        player.getInventory().clearContent();
        ServerLevel level = player.level();
        BlockPos pos = Arena.ground(-3, 2).above();
        Arena.place(level, pos, BuiltInRegistries.BLOCK.getValue(CoreBuild.propwash("drone_workbench")).defaultBlockState());
        var provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        CONTEXT.set(null);
        ImportResult imported = PropwashApi.builds().applyImport(player, ImportRequest.wholeBuild(DroneData.of(spec)));
        if (!imported.status().success()) {
            throw new IllegalStateException("Bau nicht in die Werkbank übernommen: " + imported);
        }
        state.put(SPEC, spec);
    }

    private static StepResult shown(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        WorkbenchContext context = CONTEXT.get();
        BuildSpec spec = (BuildSpec) ctx.state().get(SPEC);
        if (context == null || context.screen() != mc.gui.screen() || context.result().isEmpty() || !context.result().get().build().equals(spec)) {
            return ticks > 400 ? StepResult.fail("Werkbank zeigt den Bau nicht: " + (context == null ? "kein Kontext" : context.result())) : StepResult.pending();
        }
        return StepResult.pass("Werkbank zeigt " + spec.frame().getPath() + " " + spec.accessories());
    }

    private static List<Slot> benchSlots(AbstractContainerMenu menu, ServerPlayer player) {
        List<Slot> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container != player.getInventory()) {
                out.add(slot);
            }
        }
        return out;
    }

    private static Slot holding(ServerPlayer player, Identifier part) {
        for (Slot slot : benchSlots(player.containerMenu, player)) {
            if (slot.hasItem() && PropwashApi.partItems().partOf(slot.getItem()).filter(part::equals).isPresent()) {
                return slot;
            }
        }
        throw new IllegalStateException("kein Werkbank-Platz mit " + part);
    }

    private static Slot accepting(ServerPlayer player, Identifier part) {
        ItemStack stack = PropwashApi.partItems().stack(part);
        for (Slot slot : benchSlots(player.containerMenu, player)) {
            if (slot.mayPlace(stack) && !PropwashApi.builds().isDrone(slot.getItem())) {
                return slot;
            }
        }
        throw new IllegalStateException("kein Werkbank-Platz nimmt " + part);
    }

    private static void swap(MinecraftServer server, Map<String, Object> state, Identifier old, Identifier part) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        Slot slot = holding(player, old);
        menu.setCarried(PropwashApi.partItems().stack(part));
        menu.clicked(slot.index, 0, ContainerInput.PICKUP, player);
        menu.broadcastChanges();
        ItemStack carried = menu.getCarried();
        Optional<Identifier> nowIn = PropwashApi.partItems().partOf(slot.getItem());
        Optional<Identifier> back = PropwashApi.partItems().partOf(carried);
        if (nowIn.filter(part::equals).isEmpty() || back.filter(old::equals).isEmpty() || carried.getCount() != 1) {
            throw new IllegalStateException("Platz hält " + nowIn + ", in der Hand " + back + " statt " + part + " bzw. " + old);
        }
        menu.setCarried(ItemStack.EMPTY);
        if (!player.getInventory().add(carried)) {
            throw new IllegalStateException(old + " passt nicht ins Inventar");
        }
        menu.broadcastChanges();
        note(state, old.getPath() + " → " + part.getPath() + " (" + old.getPath() + " zurück ins Inventar)");
    }

    private static StepResult plan(SelfTest.StepContext ctx, int ticks, Map<AccessorySlot, Identifier> expected) {
        WorkbenchContext context = CONTEXT.get();
        Optional<DroneData> result = context == null ? Optional.empty() : context.result();
        Map<AccessorySlot, Identifier> shown = result.map(data -> data.build().accessories()).orElse(Map.of());
        if (!shown.equals(expected)) {
            return ticks > 200 ? StepResult.fail("Bauplan " + shown + " statt " + expected) : StepResult.pending();
        }
        return StepResult.pass("Bauplan mit je einem Zubehör: " + shown);
    }

    private static void oldPartsBack(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        Map<String, Integer> found = new TreeMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                found.merge(id(stack), stack.getCount(), Integer::sum);
            }
        }
        for (Identifier part : List.of(GRIPPER, ACTION_CAM, LED_STRIP)) {
            if (found.getOrDefault(part.toString(), 0) != 1) {
                throw new IllegalStateException(part + " nicht genau einmal im Inventar: " + found);
            }
        }
        note(state, "Inventar nach dem Tausch: " + found);
    }

    private static @Nullable Slot clientSlot(Identifier part) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen) || mc.player == null) {
            return null;
        }
        ItemStack stack = PropwashApi.partItems().stack(part);
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container != mc.player.getInventory() && slot.mayPlace(stack) && !slot.hasItem()) {
                return slot;
            }
        }
        return null;
    }

    private static void hover(Identifier part) {
        Minecraft mc = Minecraft.getInstance();
        Slot slot = clientSlot(part);
        if (slot != null && mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            int[] at = CovMdAScreenText.slotCenter(screen, slot);
            double sx = mc.getWindow().getScreenWidth() / (double) mc.getWindow().getGuiScaledWidth();
            double sy = mc.getWindow().getScreenHeight() / (double) mc.getWindow().getGuiScaledHeight();
            mc.mouseHandler.onMove(mc.getWindow().handle(), at[0] * sx, at[1] * sy, 0.0, 0.0);
        }
    }

    private static StepResult hint(SelfTest.StepContext ctx, int ticks, Identifier part, String key) {
        Minecraft mc = Minecraft.getInstance();
        Slot slot = clientSlot(part);
        if (slot == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            return ticks > 100 ? StepResult.fail("freier Werkbank-Platz für " + part + " fehlt") : StepResult.pending();
        }
        int[] at = CovMdAScreenText.slotCenter(screen, slot);
        List<String> lines = CovMdAScreenText.lines(screen, at[0], at[1]);
        String joined = String.join(" ", lines).replaceAll("\\s+", " ");
        String expected = I18n.get("propwash.content.workbench.slot_hint." + key);
        String unsupported = I18n.get("propwash.content.workbench.slot_hint.accessory_unsupported");
        String free = I18n.get("propwash.content.workbench.slot_hint.accessory_unsupported_free");
        String optional = I18n.get("propwash.content.workbench.slot_hint.accessory_optional");
        boolean ok = switch (key) {
            case "accessory_unsupported" -> joined.contains(unsupported) && !joined.contains(free) && !joined.contains(optional);
            case "accessory_unsupported_free" -> joined.contains(free) && !joined.contains(optional);
            default -> joined.contains(optional) && !joined.contains(unsupported);
        };
        if (!ok) {
            return ticks > 100 ? StepResult.fail(part.getPath() + ": Hinweis „" + expected + "“ fehlt; Zeilen am Platz: " + lines.stream().filter(line -> !line
                    .isBlank()).skip(Math.max(0, lines.size() - 14)).toList() + ", Regel " + PropwashApi.builds().partCompatibility()) : StepResult.pending();
        }
        note(ctx.state(), part.getPath() + " (Platz " + slot.index + "): „" + expected + "“ (" + key + ")");
        return StepResult.pass();
    }

    private static void place(MinecraftServer server, Map<String, Object> state, Identifier part) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        Slot slot = accepting(player, part);
        if (slot.hasItem()) {
            throw new IllegalStateException("Platz für " + part + " schon belegt");
        }
        menu.setCarried(PropwashApi.partItems().stack(part));
        menu.clicked(slot.index, 0, ContainerInput.PICKUP, player);
        menu.broadcastChanges();
        if (!menu.getCarried().isEmpty() || PropwashApi.partItems().partOf(slot.getItem()).filter(part::equals).isEmpty()) {
            menu.setCarried(ItemStack.EMPTY);
            throw new IllegalStateException(part + " nicht eingesetzt");
        }
        note(state, part.getPath() + " eingesetzt (Platz " + slot.index + ")");
    }

    private static void take(MinecraftServer server, Map<String, Object> state, Identifier part) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        Slot slot = holding(player, part);
        menu.clicked(slot.index, 0, ContainerInput.PICKUP, player);
        ItemStack carried = menu.getCarried();
        menu.setCarried(ItemStack.EMPTY);
        menu.broadcastChanges();
        if (PropwashApi.partItems().partOf(carried).filter(part::equals).isEmpty()) {
            throw new IllegalStateException(part + " nicht herausgenommen");
        }
    }

    private static StepResult buildable(SelfTest.StepContext ctx, int ticks, BuildSpec spec, boolean expected) {
        WorkbenchContext context = CONTEXT.get();
        Optional<DroneData> result = context == null ? Optional.empty() : context.result();
        boolean present = result.filter(data -> data.build().equals(spec)).isPresent();
        BuildCheck check = PropwashApi.builds().check(spec);
        boolean rule = PropwashApi.builds().partCompatibility();
        if (present != expected) {
            return ticks > 200 ? StepResult.fail("Werkbank-Ergebnis " + result.map(data -> data.build().accessories().toString()).orElse("keins") + " statt "
                    + (expected ? spec.accessories() : "keins") + "; Prüfung " + check.problems()) : StepResult.pending();
        }
        note(ctx.state(), String.format(Locale.ROOT, "%s %s: Ergebnis %s, Probleme %s, Warnungen %s", spec.frame().getPath(), spec.accessories(),
                present ? "da" : "keins", check.problems(), check.warnings()));
        return StepResult.pass();
    }

    private static void paintBench(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        player.getInventory().clearContent();
        ServerLevel level = player.level();
        BlockPos pos = Arena.ground(-3, 2).above();
        Arena.place(level, pos, BuiltInRegistries.BLOCK.getValue(CoreBuild.propwash("drone_workbench")).defaultBlockState());
        var provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        CONTEXT.set(null);
        DroneData data = DroneData.of(DispenserRig.fiveInch()).withPaint(PaintSpec.DEFAULT.with("tpu", HOUSING_COLOR).with("accessories", SERVO_COLOR));
        ImportResult imported = PropwashApi.builds().applyImport(player, ImportRequest.wholeBuild(data));
        if (!imported.status().success() || !imported.applied().contains(de.lardtom.propwash.api.build.ImportLayer.PAINT)) {
            throw new IllegalStateException("Lack nicht übernommen: " + imported);
        }
    }

    private static boolean painted(PaintSpec paint) {
        return paint.color("tpu").filter(rgb -> rgb == HOUSING_COLOR).isPresent() && paint.color("accessories").filter(rgb -> rgb == SERVO_COLOR).isPresent();
    }

    private static StepResult paintShown(SelfTest.StepContext ctx, int ticks) {
        WorkbenchContext context = CONTEXT.get();
        Optional<DroneData> result = context == null ? Optional.empty() : context.result();
        if (result.isEmpty() || !painted(result.get().paint())) {
            return ticks > 300 ? StepResult.fail("Werkbank-Ergebnis ohne beide Farben: " + result.map(DroneData::paint)) : StepResult.pending();
        }
        note(ctx.state(), "Werkbank: " + colors(result.get().paint()));
        return StepResult.pass();
    }

    private static String colors(PaintSpec paint) {
        return String.format(Locale.ROOT, "Gehäuse (tpu) #%06X, Servo (accessories) #%06X", paint.color("tpu").orElse(-1), paint.color("accessories").orElse(-1));
    }

    private static void paintBuilt(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        Slot result = null;
        for (Slot slot : benchSlots(menu, player)) {
            if (slot.hasItem() && PropwashApi.builds().isDrone(slot.getItem()) && !slot.mayPlace(slot.getItem())) {
                result = slot;
            }
        }
        if (result == null) {
            throw new IllegalStateException("kein fertiges Ergebnis in der Werkbank");
        }
        menu.clicked(result.index, 0, ContainerInput.PICKUP, player);
        ItemStack drone = menu.getCarried().copy();
        menu.setCarried(ItemStack.EMPTY);
        player.closeContainer();
        DroneData data = PropwashApi.builds().read(drone).orElseThrow(() -> new IllegalStateException("gebaute Drohne ohne Daten"));
        if (!painted(data.paint()) || data.build().accessory(AccessorySlot.FRONT).filter(MobDefense.DISPENSER_MOUNT::equals).isEmpty()) {
            throw new IllegalStateException("gebaute Drohne: " + data.paint() + ", " + data.build().accessories());
        }
        if (data.paint().colors().size() != 2) {
            throw new IllegalStateException("weitere Farben: " + data.paint().colors());
        }
        note(state, "gebaut: " + colors(data.paint()));
        ServerLevel level = player.level();
        BlockPos ground = Arena.ground(0, -4);
        player.setYRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, drone);
        drone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground,
                false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Entity placed = null;
        for (var view : PropwashApi.drones().dronesNear(level, Vec3.atCenterOf(ground.above()), 3.0, false)) {
            placed = Arena.track(view.entity());
        }
        if (placed == null) {
            throw new IllegalStateException("lackierte Drohne nicht abgestellt");
        }
        Vec3 nose = DispenserRig.view(placed).forward();
        Vec3 eye = placed.position().add(nose.x * 0.9 + 0.35, 0.35, nose.z * 0.9 + 0.35);
        double dx = placed.getX() - eye.x;
        double dz = placed.getZ() - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(placed.getY() + 0.05 - eye.y, Math.hypot(dx, dz)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.<Relative>of(), yaw, pitch, true);
    }

    private static boolean hudHidden;

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
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

    private static void justMoreParts(MinecraftServer server, Map<String, Object> state) {
        List<FrameDefinition> frames = new ArrayList<>();
        for (PartDefinition definition : PropwashApi.parts().all(PartKind.FRAME)) {
            if (definition instanceof FrameDefinition frame && frame.id().getNamespace().equals("justmoreparts")) {
                frames.add(frame);
            }
        }
        if (frames.isEmpty()) {
            note(state, "Just More Parts nicht geladen – Prüfung nur im Lauf mit JMP-Jar (JUnit CovMdAJustMorePartsTest prüft die 22 Rahmen von 1.2.0)");
            return;
        }
        List<String> front = new ArrayList<>();
        Map<String, String> others = new LinkedHashMap<>();
        for (FrameDefinition frame : frames) {
            BuildSpec base = CoreBuild.base().withPart(PartKind.FRAME, frame.id());
            BuildStats without = PropwashApi.builds().analyze(base).orElseThrow(() -> new IllegalStateException(frame.id() + ": keine Analyse"));
            List<String> plain = PropwashApi.builds().check(base).problems();
            for (Map.Entry<Identifier, AccessorySlot> part : CoreBuild.SLOTS.entrySet()) {
                BuildSpec mounted = base.withAccessory(part.getValue(), part.getKey());
                List<String> added = new ArrayList<>(PropwashApi.builds().check(mounted).problems());
                added.removeAll(plain);
                boolean fits = frame.accessoryMounts().contains(part.getValue());
                if (fits != added.isEmpty()) {
                    throw new IllegalStateException(frame.id() + " mit " + part.getKey() + ": Halterung " + fits + ", neue Probleme " + added);
                }
                if (fits) {
                    double grams = PropwashApi.builds().analyze(mounted).orElseThrow().massGrams() - without.massGrams();
                    if (Math.abs(grams - CoreBuild.GRAMS.get(part.getKey())) > 0.5) {
                        throw new IllegalStateException(frame.id() + " mit " + part.getKey() + ": +" + grams + " g");
                    }
                    if (part.getValue() == AccessorySlot.FRONT) {
                        front.add(frame.id().getPath());
                    }
                }
            }
            others.put(frame.id().getPath(), frame.accessoryMounts().toString());
        }
        note(state, frames.size() + " JMP-Rahmen geladen, mit Halterung vorn: " + front + "; Halterungen " + others);
    }

    private static Script openAccessories() {
        return Script.create()
                .server("Wartung öffnen", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    Entity drone = Pilot.serverDrone(server, state);
                    player.closeContainer();
                    player.getInventory().clearContent();
                    player.setShiftKeyDown(true);
                    player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
                    player.setShiftKeyDown(false);
                })
                .await("Wartung am Client", 100, () -> Minecraft.getInstance().gui.screen() != null)
                .then("Wartung merken", (ctx, ticks) -> {
                    ctx.state().put(SCREEN, Minecraft.getInstance().gui.screen().getClass().getName());
                    return StepResult.pass();
                })
                .server("Zubehör öffnen", (server, state) -> {
                    ServerPlayer player = Arena.player(server);
                    if (!player.containerMenu.clickMenuButton(player, SERVICE_ACCESSORY_BUTTON)) {
                        throw new IllegalStateException("Wartung nimmt den Knopf Zubehör nicht an");
                    }
                })
                .then("Zubehör-Inventar offen", (ctx, ticks) -> {
                    Screen screen = Minecraft.getInstance().gui.screen();
                    if (screen != null && !screen.getClass().getName().equals(ctx.state().get(SCREEN))) {
                        return StepResult.pass(screen.getClass().getSimpleName());
                    }
                    return ticks > 100 ? StepResult.fail("Zubehör-Inventar öffnet nicht") : StepResult.pending();
                });
    }

    private static void slotRows(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Map<Integer, Integer> rows = new TreeMap<>();
        for (Slot slot : benchSlots(player.containerMenu, player)) {
            rows.merge(slot.y, 1, Integer::sum);
        }
        List<Integer> sizes = new ArrayList<>(rows.values());
        if (!sizes.equals(List.of(1, 1, 4))) {
            throw new IllegalStateException("Slots je Halterung " + sizes + " statt [1, 1, 4] (oben Booster, unten Speer, vorn Dispenser)");
        }
        note(state, "Zubehör-Inventar: Booster 1, Speer 1, Dispenser 4 Slots");
    }

    private static int inventorySlot(ServerPlayer player, int container) {
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == container) {
                return slot.index;
            }
        }
        throw new IllegalStateException("Inventar-Platz " + container + " fehlt im Menü");
    }

    private static void quickMove(MinecraftServer server, Map<String, Object> state, ItemStack stack, int moved, int left) {
        ServerPlayer player = Arena.player(server);
        AbstractContainerMenu menu = player.containerMenu;
        int container = 9;
        while (!player.getInventory().getItem(container).isEmpty()) {
            container++;
        }
        player.getInventory().setItem(container, stack.copy());
        menu.broadcastChanges();
        menu.clicked(inventorySlot(player, container), 0, ContainerInput.QUICK_MOVE, player);
        menu.broadcastChanges();
        int rest = player.getInventory().getItem(container).getCount();
        List<Integer> dispenser = new ArrayList<>();
        Entity drone = Pilot.serverDrone(server, state);
        for (ItemStack slot : PropwashApi.accessories().contents(drone, AccessorySlot.FRONT)) {
            dispenser.add(slot.is(stack.getItem()) ? slot.getCount() : 0);
        }
        int inside = dispenser.stream().mapToInt(Integer::intValue).sum();
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int before = inside - moved;
        if (rest != left || before < 0 || dispenser.stream().anyMatch(count -> count > (stack.is(Items.TNT) ? 4 : 16))) {
            throw new IllegalStateException("Shift-Klick " + stack.getCount() + " " + name + ": Dispenser " + dispenser + ", Rest im Inventar " + rest + " statt " + left);
        }
        note(state, "Shift-Klick " + stack.getCount() + " " + name + ": Dispenser " + dispenser + ", Rest " + rest);
    }

    private static void emptyDispenser(MinecraftServer server, Map<String, Object> state) {
        DispenserRig.load(Pilot.serverDrone(server, state));
        Arena.player(server).getInventory().clearContent();
        Arena.player(server).containerMenu.broadcastChanges();
    }
}
