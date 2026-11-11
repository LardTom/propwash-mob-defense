package de.lardtom.mobdefense.dev.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Arena {
    public static final double RADIUS = 128.0;
    private static final String TAG = "[Mob-Defense-Selbsttest]";
    private static final String STAGE = "arena.stage";
    private static final String SINCE = "arena.since";
    private static final int CLIENT_TICKS = 100;
    private static final Map<BlockPos, BlockState> PLACED = new LinkedHashMap<>();
    private static final Set<UUID> SPAWNED = new LinkedHashSet<>();
    private static final Map<Identifier, String> RULES = new LinkedHashMap<>();
    private static Vec3 spawn = Vec3.ZERO;
    private static BlockPos ground = BlockPos.ZERO;
    private static String language = Shots.ENGLISH;

    private Arena() {
    }

    static void remember(Minecraft mc) {
        if (mc.player != null) {
            spawn = mc.player.position();
            ground = BlockPos.containing(spawn.x, spawn.y - 1.0, spawn.z);
        }
        language = mc.getLanguageManager().getSelected();
        MobDefense.LOG.info("{} Arena bei {} (Boden {}), Sprache {}", TAG, spawn, ground.toShortString(), language);
    }

    static void rememberRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        RULES.clear();
        rules.availableRules().forEach(rule -> RULES.put(rule.getIdentifier(), rules.getAsString(rule)));
    }

    public static Vec3 spawn() {
        return spawn;
    }

    public static BlockPos ground() {
        return ground;
    }

    public static BlockPos ground(int dx, int dz) {
        return ground.offset(dx, 0, dz);
    }

    public static String startLanguage() {
        return language;
    }

    public static ServerPlayer player(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new IllegalStateException("kein Spieler am Server");
        }
        return players.getFirst();
    }

    public static ServerLevel level(MinecraftServer server) {
        return player(server).level();
    }

    public static void place(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos key = pos.immutable();
        PLACED.putIfAbsent(key, level.getBlockState(key));
        level.setBlockAndUpdate(key, state);
    }

    public static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            place(level, pos, state);
        }
    }

    public static <T extends Entity> T track(T entity) {
        SPAWNED.add(entity.getUUID());
        return entity;
    }

    public static <T extends Entity> T spawn(ServerLevel level, T entity) {
        if (!level.addFreshEntity(entity)) {
            throw new IllegalStateException(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()) + " nicht gespawnt");
        }
        return track(entity);
    }

    public static int changedBlocks() {
        return PLACED.size();
    }

    public static ItemStack stack(String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException("Item " + id + " fehlt"));
        return new ItemStack(item);
    }

    public static void rule(MinecraftServer server, Identifier id, String value) {
        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(id);
        if (rule == null) {
            throw new IllegalStateException("Gamerule " + id + " fehlt");
        }
        set(server, rule, value);
        String now = server.getGameRules().getAsString(rule);
        if (!value.equals(now)) {
            throw new IllegalStateException("Gamerule " + id + " ist " + now + " statt " + value);
        }
    }

    private static <T> void set(MinecraftServer server, GameRule<T> rule, String value) {
        T parsed = rule.deserialize(value).result().orElseThrow(() -> new IllegalStateException("Wert " + value + " passt nicht zu " + rule.id()));
        server.getGameRules().set(rule, parsed, server);
    }

    public static List<String> changedRules(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        List<String> out = new ArrayList<>();
        for (Map.Entry<Identifier, String> entry : RULES.entrySet()) {
            GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(entry.getKey());
            if (rule != null && !entry.getValue().equals(rules.getAsString(rule))) {
                out.add(entry.getKey() + "=" + rules.getAsString(rule) + " statt " + entry.getValue());
            }
        }
        return out;
    }

    public static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public static AABB box(ServerLevel level) {
        return new AABB(spawn.x - RADIUS, level.getMinY(), spawn.z - RADIUS, spawn.x + RADIUS, level.getMaxY(), spawn.z + RADIUS);
    }

    public static List<Entity> drones(ServerLevel level) {
        return level.getEntities((Entity) null, box(level), entity -> PropwashApi.drones().isDrone(entity));
    }

    public static List<Entity> leftovers(ServerLevel level) {
        return level.getEntities((Entity) null, box(level), entity -> !(entity instanceof Player)
                && (SPAWNED.contains(entity.getUUID()) || entity instanceof ItemEntity || PropwashApi.drones().isDrone(entity)));
    }

    public static List<Entity> clientDrones(Minecraft mc) {
        List<Entity> out = new ArrayList<>();
        if (mc.level == null) {
            return out;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (PropwashApi.drones().isDrone(entity)) {
                out.add(entity);
            }
        }
        return out;
    }

    public static StepResult reset(SelfTest.StepContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        int stage = (int) ctx.state().getOrDefault(STAGE, 0);
        int since = (int) ctx.state().computeIfAbsent(SINCE, key -> ctx.tick());
        int ticks = ctx.tick() - since;
        switch (stage) {
            case 0 -> {
                KeyMapping.releaseAll();
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                Shots.restoreWindow(mc);
                Shots.restoreLanguage(mc);
                Shots.clearOverlays(mc);
                next(ctx, 1);
            }
            case 1 -> {
                StepResult cleared = Script.server(ctx, ticks, (server, state) -> clearServer(server));
                if (!cleared.done() || cleared.failed()) {
                    return cleared;
                }
                next(ctx, 2);
            }
            case 2 -> {
                if (ticks < 5) {
                    return StepResult.pending();
                }
                String left = clientProblem(mc);
                if (left != null) {
                    return ticks < CLIENT_TICKS ? StepResult.pending() : StepResult.fail(left);
                }
                if (mc.gui.screen() != null) {
                    mc.gui.setScreen(null);
                }
                return StepResult.pass();
            }
            default -> {
                return StepResult.fail("unbekannte Stufe " + stage);
            }
        }
        return StepResult.pending();
    }

    private static @Nullable String clientProblem(Minecraft mc) {
        if (PropwashClientApi.input().flying()) {
            return "Flug endet nicht";
        }
        List<Entity> drones = clientDrones(mc);
        if (!drones.isEmpty()) {
            return drones.size() + " Drohnen bleiben am Client";
        }
        return null;
    }

    private static void next(SelfTest.StepContext ctx, int stage) {
        ctx.state().put(STAGE, stage);
        ctx.state().put(SINCE, ctx.tick() + 1);
    }

    private static void clearServer(MinecraftServer server) {
        ServerPlayer player = player(server);
        ServerLevel level = player.level();
        player.closeContainer();
        int entities = 0;
        for (Entity entity : leftovers(level)) {
            entity.discard();
            entities++;
        }
        SPAWNED.clear();
        int blocks = PLACED.size();
        List<Map.Entry<BlockPos, BlockState>> placed = new ArrayList<>(PLACED.entrySet());
        for (int i = placed.size() - 1; i >= 0; i--) {
            level.setBlockAndUpdate(placed.get(i).getKey(), placed.get(i).getValue());
        }
        PLACED.clear();
        GameRules rules = server.getGameRules();
        int changed = 0;
        for (Map.Entry<Identifier, String> entry : RULES.entrySet()) {
            GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(entry.getKey());
            if (rule != null && !entry.getValue().equals(rules.getAsString(rule))) {
                set(server, rule, entry.getValue());
                changed++;
            }
        }
        for (Entity entity : leftovers(level)) {
            entity.discard();
            entities++;
        }
        if (entities + blocks + changed > 0 || !player.getInventory().isEmpty()) {
            MobDefense.LOG.info("{} Arena zurückgesetzt: {} Entities, {} Blöcke, {} Gamerules, Inventar {}", TAG, entities, blocks, changed,
                    player.getInventory().isEmpty() ? "leer" : "geleert");
        }
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.removeAllEffects();
        player.setGameMode(GameType.CREATIVE);
        player.setHealth(player.getMaxHealth());
        player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.<Relative>of(), 0.0F, 0.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
    }
}
