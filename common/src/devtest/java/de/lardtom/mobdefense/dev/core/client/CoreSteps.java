package de.lardtom.mobdefense.dev.core.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.CoreBuild;
import de.lardtom.mobdefense.dev.core.CoreChecks;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildStats;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.ImportRequest;
import de.lardtom.propwash.api.build.ImportResult;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.WorkbenchContext;
import de.lardtom.propwash.api.client.WorkbenchExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class CoreSteps {
    public static final String WORLD_RULES_TAB = "world_rules";
    public static final String SECTION_KEY = MobDefense.MOD_ID + ".world_rules.section";
    public static final String SECTION = "Mob Defense";
    private static final AtomicReference<@Nullable WorkbenchContext> WORKBENCH = new AtomicReference<>();
    private static final String TEXTS_EN = "core.texts.en";
    private static final int SCROLLS = 80;

    private CoreSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        PropwashClientApi.registerWorkbenchExtension(new WorkbenchExtension() {
            @Override
            public Identifier id() {
                return MobDefense.id("selftest_workbench");
            }

            @Override
            public void init(WorkbenchContext context) {
                WORKBENCH.set(context);
            }
        });
        steps.add("S1 Teile und Rezepte – drei Zubehörteile mit Item und Rezept, Werkbank zeigt Masse und Stirnfläche", 3000, Script.create()
                .server("Teile, Items und Rezepte am Server", (server, state) -> state.put("core.parts", CoreChecks.parts(server)))
                .then("Teile, Items und Rezepte", (ctx, ticks) -> StepResult.pass((String) ctx.state().get("core.parts")))
                .check("Analyse mit Masse und Stirnfläche", CoreSteps::analysis)
                .server("Werkbank mit Mob-Defense-Bau", CoreSteps::openWorkbench)
                .then("Werkbank zeigt die drei Teile", (ctx, ticks) -> workbenchShown(ctx, ticks))
                .check("Tooltips mit Masse", CoreSteps::tooltips)
                .append(Shots.photos("core_workbench", CoreSteps::parkMouse)));
        steps.add("S12 Welt-Regeln – Abschnitt „Mob Defense“ mit allen Regeln, Texte DE und EN, Operator ändert, Voreinstellung Training",
                4000, Script.create()
                        .server("Regeln am Server", (server, state) -> CoreChecks.rules(server))
                        .run("Sprache Englisch", () -> Shots.language(Minecraft.getInstance(), Shots.ENGLISH))
                        .then("Texte Englisch", (ctx, ticks) -> texts(ctx, false))
                        .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                        .then("Texte Deutsch", (ctx, ticks) -> texts(ctx, true))
                        .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                        .server("Operator ändert zwei Regeln", CoreSteps::operatorChanges)
                        .await("Änderung kommt am Client an", 200, () -> clientFlag(RuleDefaults.ALLOW_TNT) == Boolean.FALSE
                                && clientNumber(RuleDefaults.DISPENSER_COOLDOWN_TICKS) == 10)
                        .server("Voreinstellung Training", (server, state) -> preset(server, "training", false))
                        .await("Training kommt am Client an", 200, () -> clientFlag(RuleDefaults.MOBS_REACT_TO_DRONES) == Boolean.FALSE)
                        .server("Voreinstellung Realistisch", (server, state) -> preset(server, "realistic", true))
                        .await("Realistisch kommt am Client an", 200, () -> clientFlag(RuleDefaults.MOBS_REACT_TO_DRONES) == Boolean.TRUE)
                        .server("Regeln zurück auf Standard", CoreSteps::resetRules)
                        .await("Standard kommt am Client an", 200, () -> clientFlag(RuleDefaults.ALLOW_TNT) == Boolean.TRUE
                                && clientNumber(RuleDefaults.DISPENSER_COOLDOWN_TICKS) == RuleDefaults.DISPENSER_COOLDOWN_TICKS.defaultValue())
                        .then("Tab Welt-Regeln öffnen", (ctx, ticks) -> openRulesTab(ctx, ticks))
                        .then("Abschnitt mit allen Regeln", (ctx, ticks) -> section(ctx, ticks))
                        .append(Shots.photos("core_world_rules", () -> {
                            scrollToSection();
                            parkMouse();
                        }))
                        .append(Shots.photos("core_world_rules_end", () -> {
                            scrollToEnd();
                            parkMouse();
                        })));
    }

    private static StepResult analysis() {
        Optional<BuildStats> base = PropwashApi.builds().analyze(CoreBuild.base());
        Optional<BuildStats> full = PropwashApi.builds().analyze(CoreBuild.full());
        if (base.isEmpty() || full.isEmpty()) {
            return StepResult.fail("Analyse liefert nichts für freestyle_5 mit und ohne Zubehör");
        }
        double grams = full.get().massGrams() - base.get().massGrams();
        if (Math.abs(grams - CoreBuild.ACCESSORY_GRAMS) > 0.5) {
            return StepResult.fail("Zubehör wiegt in der Analyse " + grams + " g statt " + CoreBuild.ACCESSORY_GRAMS + " g");
        }
        double slower = base.get().topSpeedKmh() - full.get().topSpeedKmh();
        if (!(slower > 0.0)) {
            return StepResult.fail("Stirnfläche bremst nicht: Höchsttempo " + base.get().topSpeedKmh() + " → " + full.get().topSpeedKmh() + " km/h");
        }
        String power = "";
        if (Double.isFinite(full.get().avionicsPowerW()) && Double.isFinite(base.get().avionicsPowerW())) {
            double watts = full.get().avionicsPowerW() - base.get().avionicsPowerW();
            if (Math.abs(watts - CoreBuild.ACCESSORY_WATTS) > 0.05) {
                return StepResult.fail("Zubehör braucht in der Analyse " + watts + " W statt " + CoreBuild.ACCESSORY_WATTS + " W");
            }
            power = String.format(Locale.ROOT, ", +%.1f W", watts);
        }
        return StepResult.pass(String.format(Locale.ROOT, "Analyse freestyle_5: +%.0f g, Höchsttempo −%.1f km/h durch die Stirnfläche%s", grams, slower, power));
    }

    private static void openWorkbench(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        player.closeContainer();
        ServerLevel level = player.level();
        BlockPos pos = Arena.ground(2, 0).above();
        BlockState bench = BuiltInRegistries.BLOCK.getValue(CoreBuild.propwash("drone_workbench")).defaultBlockState();
        Arena.place(level, pos, bench);
        for (Identifier part : MobDefense.PARTS) {
            player.getInventory().add(PropwashApi.partItems().stack(part));
        }
        MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
        if (provider == null || player.openMenu(provider).isEmpty()) {
            throw new IllegalStateException("Werkbank-Menü nicht geöffnet");
        }
        WORKBENCH.set(null);
        ImportResult imported = PropwashApi.builds().applyImport(player, ImportRequest.wholeBuild(DroneData.of(CoreBuild.full())));
        if (!imported.status().success()) {
            throw new IllegalStateException("Mob-Defense-Bau nicht in die Werkbank übernommen: " + imported);
        }
    }

    private static StepResult workbenchShown(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        WorkbenchContext context = WORKBENCH.get();
        if (context == null || context.screen() != mc.gui.screen() || context.result().isEmpty()) {
            return ticks > 400 ? StepResult.fail("Werkbank erscheint nicht: " + mc.gui.screen()) : StepResult.pending();
        }
        DroneData result = context.result().get();
        if (!CoreBuild.mounts().equals(result.build().accessories())) {
            return ticks > 400 ? StepResult.fail("Werkbank zeigt " + result.build().accessories()) : StepResult.pending();
        }
        return StepResult.pass("Werkbank mit Dispenser-Halterung vorn, Speer-Halterung unten, Raketen-Booster oben");
    }

    private static StepResult tooltips() {
        Minecraft mc = Minecraft.getInstance();
        List<String> seen = new ArrayList<>();
        PropwashClientApi.forceShiftHelp(Boolean.TRUE);
        try {
            for (Identifier part : MobDefense.PARTS) {
                String text = tooltip(mc, part);
                String grams = Long.toString(Math.round(CoreBuild.GRAMS.get(part)));
                if (!text.contains(grams + " g")) {
                    return StepResult.fail(part + ": Tooltip ohne " + grams + " g: " + text.replace('\n', '|'));
                }
                String name = I18n.get("part." + part.getNamespace() + "." + part.getPath());
                String summary = I18n.get(part.getNamespace() + ".help." + part.getPath() + ".summary");
                if (!text.startsWith(name) || !text.contains(summary)) {
                    return StepResult.fail(part + ": Tooltip ohne Name „" + name + "“ oder Kurztext „" + summary + "“: " + text.replace('\n', '|'));
                }
                seen.add(name + " " + grams + " g");
            }
        } finally {
            PropwashClientApi.forceShiftHelp(null);
        }
        return StepResult.pass("Tooltips mit Kurztext und Masse: " + String.join(", ", seen));
    }

    private static String tooltip(Minecraft mc, Identifier part) {
        StringBuilder text = new StringBuilder();
        for (Component line : PropwashApi.partItems().stack(part).getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)) {
            text.append(line.getString()).append('\n');
        }
        return text.toString();
    }

    private static List<String> textKeys() {
        List<String> keys = new ArrayList<>(List.of(SECTION_KEY, "itemGroup." + MobDefense.MOD_ID));
        for (RuleSpec spec : RuleDefaults.CORE) {
            keys.add("gamerule." + MobDefense.MOD_ID + "." + spec.path());
            keys.add("gamerule." + MobDefense.MOD_ID + "." + spec.path() + ".description");
        }
        return keys;
    }

    @SuppressWarnings("unchecked")
    private static StepResult texts(SelfTest.StepContext ctx, boolean german) {
        Language language = Language.getInstance();
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : textKeys()) {
            if (!language.has(key)) {
                return StepResult.fail(key + " fehlt auf " + (german ? "Deutsch" : "Englisch"));
            }
            values.put(key, language.getOrDefault(key));
        }
        if (!german) {
            ctx.state().put(TEXTS_EN, values);
            return StepResult.pass(values.size() + " Texte auf Englisch");
        }
        Map<String, String> english = (Map<String, String>) ctx.state().get(TEXTS_EN);
        int same = 0;
        for (RuleSpec spec : RuleDefaults.CORE) {
            String key = "gamerule." + MobDefense.MOD_ID + "." + spec.path();
            if (values.get(key).equals(english.get(key))) {
                same++;
            }
        }
        if (same > 0) {
            return StepResult.fail(same + " Regelnamen auf Deutsch gleich wie auf Englisch");
        }
        if (!SECTION.equals(values.get(SECTION_KEY)) || !SECTION.equals(english.get(SECTION_KEY))) {
            return StepResult.fail("Abschnitt heißt „" + values.get(SECTION_KEY) + "“ / „" + english.get(SECTION_KEY) + "“");
        }
        return StepResult.pass(values.size() + " Texte auf Deutsch, Abschnitt „" + SECTION + "“ in beiden Sprachen");
    }

    private static void command(MinecraftServer server, CommandSourceStack source, String command) {
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static void operatorChanges(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();
        command(server, source, "gamerule " + MobDefense.id(RuleDefaults.ALLOW_TNT.path()) + " false");
        command(server, source, "gamerule " + MobDefense.id(RuleDefaults.DISPENSER_COOLDOWN_TICKS.path()) + " 10");
        command(server, source, "gamerule " + MobDefense.id(RuleDefaults.DISPENSER_COOLDOWN_TICKS.path()) + " 101");
        Level level = server.overworld();
        if (MobDefenseRules.flag(level, RuleDefaults.ALLOW_TNT)) {
            throw new IllegalStateException("Spieler mit Operator-Recht kann allow_tnt nicht ausschalten");
        }
        int cooldown = MobDefenseRules.number(level, RuleDefaults.DISPENSER_COOLDOWN_TICKS);
        if (cooldown != 10) {
            throw new IllegalStateException("dispenser_cooldown_ticks ist " + cooldown + " statt 10 (101 liegt außerhalb und darf nichts ändern)");
        }
    }

    private static void preset(MinecraftServer server, String preset, boolean mobs) {
        ServerPlayer player = Arena.player(server);
        command(server, player.createCommandSourceStack().withSuppressedOutput(), "propwash rules preset " + preset);
        boolean now = MobDefenseRules.flag(server.overworld(), RuleDefaults.MOBS_REACT_TO_DRONES);
        if (now != mobs) {
            throw new IllegalStateException("Voreinstellung " + preset + ": mobs_react_to_drones ist " + now + " statt " + mobs);
        }
    }

    private static void resetRules(MinecraftServer server, Map<String, Object> state) {
        Arena.rule(server, MobDefense.id(RuleDefaults.ALLOW_TNT.path()), "true");
        Arena.rule(server, MobDefense.id(RuleDefaults.DISPENSER_COOLDOWN_TICKS.path()),
                Integer.toString(RuleDefaults.DISPENSER_COOLDOWN_TICKS.defaultValue()));
    }

    private static @Nullable Boolean clientFlag(RuleSpec spec) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : MobDefenseRules.flag(mc.level, spec);
    }

    private static int clientNumber(RuleSpec spec) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? Integer.MIN_VALUE : MobDefenseRules.number(mc.level, spec);
    }

    private static StepResult openRulesTab(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks == 0) {
            mc.gui.setScreen(null);
            if (!PropwashClientApi.openConfigTab(WORLD_RULES_TAB, null)) {
                return StepResult.fail("openConfigTab findet den Tab " + WORLD_RULES_TAB + " nicht");
            }
            return StepResult.pending();
        }
        Screen screen = mc.gui.screen();
        if (screen == null || ruleWidgets(screen).isEmpty()) {
            return ticks > 200 ? StepResult.fail("Tab Welt-Regeln ohne Regeln von Mob Defense: " + screen) : StepResult.pending();
        }
        return StepResult.pass();
    }

    private static StepResult section(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return StepResult.fail("Tab Welt-Regeln geschlossen");
        }
        Map<String, AbstractWidget> found = ruleWidgets(screen);
        List<String> missing = new ArrayList<>();
        for (RuleSpec spec : RuleDefaults.CORE) {
            if (!found.containsKey(spec.path())) {
                missing.add(spec.path());
            }
        }
        if (!missing.isEmpty()) {
            return ticks > 40 ? StepResult.fail("im Tab fehlen " + missing) : StepResult.pending();
        }
        if (!scrollToEnd()) {
            return StepResult.fail("letzte Regel " + RuleDefaults.CORE.getLast().path() + " lässt sich nicht ins Bild scrollen");
        }
        if (!scrollToSection()) {
            return StepResult.fail("Abschnitt „" + SECTION + "“ lässt sich nicht ins Bild scrollen");
        }
        AbstractWidget first = ruleWidgets(screen).get(RuleDefaults.CORE.getFirst().path());
        return StepResult.pass("alle " + found.size() + " Regeln im Tab, " + (first.active ? "bearbeitbar" : "nur lesbar")
                + ", Abschnitt bei y=" + first.getY() + " von " + screen.height);
    }

    public static void parkMouse() {
        Minecraft mc = Minecraft.getInstance();
        mc.mouseHandler.onMove(mc.getWindow().handle(), 1.0, 1.0, 0.0, 0.0);
    }

    public static boolean scrollToEnd() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return false;
        }
        String last = RuleDefaults.CORE.getLast().path();
        for (int i = 0; i < SCROLLS; i++) {
            AbstractWidget widget = ruleWidgets(screen).get(last);
            if (widget == null) {
                return false;
            }
            if (widget.visible) {
                return true;
            }
            if (!screen.mouseScrolled(widget.getX() + 2.0, screen.height / 2.0, 0.0, -1.0)) {
                return false;
            }
        }
        return false;
    }

    public static boolean scrollToSection() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            return false;
        }
        String first = RuleDefaults.CORE.getFirst().path();
        for (int i = 0; i < SCROLLS; i++) {
            AbstractWidget widget = ruleWidgets(screen).get(first);
            if (widget == null) {
                return false;
            }
            int top = screen.height / 6;
            if (widget.visible && widget.getY() >= top && widget.getY() <= screen.height / 2) {
                return true;
            }
            double direction = widget.getY() > screen.height / 2 ? -1.0 : 1.0;
            if (!screen.mouseScrolled(widget.getX() + 2.0, screen.height / 2.0, 0.0, direction)) {
                return widget.visible;
            }
        }
        AbstractWidget widget = ruleWidgets(screen).get(first);
        return widget != null && widget.visible;
    }

    private static Map<String, AbstractWidget> ruleWidgets(Screen screen) {
        Map<String, String> names = new LinkedHashMap<>();
        for (RuleSpec spec : RuleDefaults.CORE) {
            names.put(spec.path(), I18n.get("gamerule." + MobDefense.MOD_ID + "." + spec.path()));
        }
        Map<String, AbstractWidget> out = new LinkedHashMap<>();
        collect(screen.children(), names, out);
        return out;
    }

    private static void collect(List<? extends GuiEventListener> children, Map<String, String> names, Map<String, AbstractWidget> out) {
        for (GuiEventListener child : children) {
            if (child instanceof AbstractWidget widget) {
                String message = widget.getMessage().getString();
                for (Map.Entry<String, String> name : names.entrySet()) {
                    if (!out.containsKey(name.getKey()) && matches(message, name.getValue())) {
                        out.put(name.getKey(), widget);
                    }
                }
            }
            if (child instanceof ContainerEventHandler container) {
                collect(container.children(), names, out);
            }
        }
    }

    static boolean matches(String message, String name) {
        if (message.isEmpty()) {
            return false;
        }
        if (message.equals(name) || message.startsWith(name + ":")) {
            return true;
        }
        String cut = message.endsWith("…") ? message.substring(0, message.length() - 1) : message.endsWith("...")
                ? message.substring(0, message.length() - 3) : null;
        return cut != null && cut.length() >= 6 && name.startsWith(cut);
    }
}
