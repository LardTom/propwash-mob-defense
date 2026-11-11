package de.lardtom.mobdefense.dev.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CleanupStep {
    public static final String NAME = "Aufräumen – Arena leer, Gamerules wie vorher, Inventar leer";
    private static final String PROBLEMS = "aufräumen.problems";

    private CleanupStep() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add(NAME, 600, Script.create()
                .server("Welt prüfen", CleanupStep::checkServer)
                .then("Client prüfen", (ctx, ticks) -> checkClient(ctx)));
    }

    private static void checkServer(MinecraftServer server, Map<String, Object> state) {
        List<String> problems = new ArrayList<>();
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        List<Entity> leftovers = Arena.leftovers(level);
        if (!leftovers.isEmpty()) {
            problems.add(leftovers.size() + " Entities übrig, zuerst " + BuiltInRegistries.ENTITY_TYPE.getKey(leftovers.getFirst().getType()));
        }
        if (Arena.changedBlocks() > 0) {
            problems.add(Arena.changedBlocks() + " gesetzte Blöcke nicht zurückgesetzt");
        }
        problems.addAll(Arena.changedRules(server));
        for (Identifier id : BuiltInRegistries.GAME_RULE.keySet()) {
            if (MobDefense.MOD_ID.equals(id.getNamespace())) {
                GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
                if (rule != null && !server.getGameRules().getAsString(rule).equals(serialized(rule))) {
                    problems.add("Gamerule " + id + " ist " + server.getGameRules().getAsString(rule) + " statt Standard " + serialized(rule));
                }
            }
        }
        if (!player.getInventory().isEmpty()) {
            problems.add("Inventar nicht leer");
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                problems.add("Ausrüstung " + slot.getName() + " nicht leer");
            }
        }
        if (player.gameMode() != GameType.CREATIVE) {
            problems.add("Spielmodus " + player.gameMode());
        }
        state.put(PROBLEMS, problems);
    }

    private static <T> String serialized(GameRule<T> rule) {
        return rule.serialize(rule.defaultValue());
    }

    @SuppressWarnings("unchecked")
    private static StepResult checkClient(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        List<String> problems = new ArrayList<>((List<String>) ctx.state().getOrDefault(PROBLEMS, List.of()));
        if (PropwashClientApi.input().flying()) {
            problems.add("Flug läuft noch");
        }
        if (mc.gui.screen() != null) {
            problems.add("Fenster offen: " + mc.gui.screen().getClass().getSimpleName());
        }
        if (!Arena.startLanguage().equals(mc.getLanguageManager().getSelected())) {
            problems.add("Sprache " + mc.getLanguageManager().getSelected() + " statt " + Arena.startLanguage());
        }
        for (KeyMapping key : mc.options.keyMappings) {
            if (key.isDown()) {
                problems.add("Taste gedrückt: " + key.getName());
            }
        }
        if (!Arena.clientDrones(mc).isEmpty()) {
            problems.add(Arena.clientDrones(mc).size() + " Drohnen am Client");
        }
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems));
        }
        return StepResult.pass("Arena leer, Gamerules wie beim Start, Inventar leer, Kreativmodus, Sprache " + Arena.startLanguage()
                + ", keine Taste gedrückt");
    }
}
