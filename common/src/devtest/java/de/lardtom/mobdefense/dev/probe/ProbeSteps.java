package de.lardtom.mobdefense.dev.probe;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;

import java.util.Map;

public final class ProbeSteps {
    public static final String KEY = "mobdefense.description";
    private static final String ENGLISH = "probe.en";

    private ProbeSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Probe – Add-on geladen, flache Kreativwelt, Texte EN/DE, Aufräumen", 1200, Script.create()
                .server("Welt prüfen", ProbeSteps::world)
                .check("Mod geladen", ProbeSteps::loaded)
                .run("Sprache Englisch", () -> Shots.language(Minecraft.getInstance(), Shots.ENGLISH))
                .then("Text Englisch", (ctx, ticks) -> english(ctx))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Text Deutsch", (ctx, ticks) -> german(ctx))
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .server("Unordnung anlegen", ProbeSteps::mess)
                .append(Shots.photos("probe_world", ProbeSteps::overlay)));
    }

    private static void world(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        if (!(level.getChunkSource().getGenerator() instanceof FlatLevelSource)) {
            throw new IllegalStateException("keine flache Welt: " + level.getChunkSource().getGenerator().getClass().getSimpleName());
        }
        if (player.gameMode() != GameType.CREATIVE) {
            throw new IllegalStateException("Spielmodus " + player.gameMode());
        }
        if (server.getGameRules().get(GameRules.SPAWN_MOBS) || server.getGameRules().get(GameRules.ADVANCE_TIME)) {
            throw new IllegalStateException("Mobs oder Tageszeit laufen");
        }
    }

    private static StepResult loaded() {
        if (!MobDefense.initialized()) {
            return StepResult.fail("MobDefense.init() lief nicht");
        }
        if (!Services.PLATFORM.isModLoaded("propwash")) {
            return StepResult.fail("Propwash nicht geladen");
        }
        return StepResult.pass("Mob Defense " + Services.PLATFORM.modVersion(MobDefense.MOD_ID).orElse("?") + " auf " + Services.PLATFORM.name());
    }

    private static StepResult english(SelfTest.StepContext ctx) {
        if (!Language.getInstance().has(KEY)) {
            return StepResult.fail("Text " + KEY + " fehlt auf Englisch");
        }
        ctx.state().put(ENGLISH, I18n.get(KEY));
        return StepResult.pass();
    }

    private static StepResult german(SelfTest.StepContext ctx) {
        if (!Language.getInstance().has(KEY)) {
            return StepResult.fail("Text " + KEY + " fehlt auf Deutsch");
        }
        String german = I18n.get(KEY);
        if (german.equals(ctx.state().get(ENGLISH))) {
            return StepResult.fail("deutscher und englischer Text gleich: " + german);
        }
        if (!german.matches(".*[äöüÄÖÜß].*")) {
            return StepResult.fail("deutscher Text ohne Umlaut: " + german);
        }
        return StepResult.pass("Texte EN und DE geladen");
    }

    private static void mess(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Arena.place(level, Arena.ground(3, 0).above(), Blocks.TARGET.defaultBlockState());
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        if (stand == null) {
            throw new IllegalStateException("Rüstungsständer nicht erzeugt");
        }
        stand.snapTo(Arena.spawn().x + 3.0, Arena.spawn().y, Arena.spawn().z + 2.0, 0.0F, 0.0F);
        Arena.spawn(level, stand);
        player.getInventory().add(Arena.stack("minecraft:arrow"));
        Arena.rule(server, GameRules.MOB_GRIEFING.getIdentifier(), "false");
    }

    private static void overlay() {
        Minecraft.getInstance().gui.hud.setOverlayMessage(Component.translatable(KEY), false);
    }
}
