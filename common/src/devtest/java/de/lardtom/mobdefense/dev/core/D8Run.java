package de.lardtom.mobdefense.dev.core;

import com.mojang.serialization.Codec;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.DevResult;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

public final class D8Run {
    public static final String PROPERTY = "mobdefense.d8";
    public static final String PREPARE = "prepare";
    public static final String VERIFY = "verify";
    public static final String MARKER = "d8-prepare.properties";
    public static final String NO_ADDON = "d8-noaddon.txt";
    public static final String DATA_VALUE = "D8: Daten von Mob Defense an der Drohne";
    public static final String DRONE_NAME = "D8 Drohne";
    public static final String CHEST_NAME = "D8 Kiste";
    public static final int PLACE_TICK = 20;
    public static final int SETTLE_TICKS = 40;
    public static final int WAIT_TICKS = 600;
    private static final String TAG = "[Mob-Defense-D8]";

    private static @Nullable DroneDataKey<String> key;
    private static String phase = "";
    private static int ticks = -1;
    private static boolean done;
    private static final Properties MARKER_VALUES = new Properties();

    private D8Run() {
    }

    public static void init() {
        phase = System.getProperty(PROPERTY, "");
        if (!PREPARE.equals(phase) && !VERIFY.equals(phase)) {
            return;
        }
        key = PropwashApi.droneData().register(MobDefense.id("d8_probe"), Codec.STRING, "", DroneDataKey.Sync.NONE);
        MobDefense.onServerStarted(D8Run::started);
        MobDefense.onServerTick(D8Run::tick);
        MobDefense.LOG.info("{} Phase {} aktiv", TAG, phase);
    }

    private static Path dir() {
        return Services.PLATFORM.gameDir();
    }

    private static void started(MinecraftServer server) {
        ticks = 0;
        try {
            if (PREPARE.equals(phase)) {
                ServerLevel level = server.overworld();
                if (!level.setChunkForced(0, 0, true) && !level.getForceLoadedChunks().contains(0L)) {
                    throw new IllegalStateException("Chunk 0,0 lässt sich nicht dauerhaft laden");
                }
            } else {
                Properties stored = new Properties();
                try (Reader reader = Files.newBufferedReader(dir().resolve(MARKER), StandardCharsets.UTF_8)) {
                    stored.load(reader);
                }
                MARKER_VALUES.putAll(stored);
                MobDefense.LOG.info("{} Welt aus Phase prepare, warte auf Drohne {}", TAG, MARKER_VALUES.getProperty("drone"));
            }
        } catch (Exception e) {
            finish(server, "FAIL D8 " + phase + ": " + message(e));
        }
    }

    private static void tick(MinecraftServer server) {
        if (ticks < 0 || done) {
            return;
        }
        ticks++;
        try {
            if (PREPARE.equals(phase)) {
                if (ticks == PLACE_TICK) {
                    prepare(server);
                } else if (ticks == PLACE_TICK + SETTLE_TICKS) {
                    finish(server, settled(server));
                }
                return;
            }
            Entity drone = server.overworld().getEntity(UUID.fromString(MARKER_VALUES.getProperty("drone")));
            if (drone != null && ticks >= SETTLE_TICKS) {
                finish(server, verify(server, drone));
            } else if (ticks >= WAIT_TICKS) {
                finish(server, "FAIL D8 verify: Drohne " + MARKER_VALUES.getProperty("drone") + " nach " + WAIT_TICKS + " Ticks nicht geladen");
            }
        } catch (Exception e) {
            finish(server, "FAIL D8 " + phase + ": " + message(e));
        }
    }

    private static void prepare(MinecraftServer server) throws IOException {
        ServerLevel level = server.overworld();
        if (!level.getForceLoadedChunks().contains(0L)) {
            throw new IllegalStateException("Chunk 0,0 ist nicht dauerhaft geladen");
        }
        level.getChunk(0, 0);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
        BlockPos ground = new BlockPos(8, y - 1, 8);
        BlockPos chest = new BlockPos(12, y, 8);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        BlockEntity entity = level.getBlockEntity(chest);
        if (!(entity instanceof ChestBlockEntity box)) {
            throw new IllegalStateException("keine Kiste bei " + chest.toShortString());
        }
        box.setItem(0, PropwashApi.builds().createDrone(DroneData.of(CoreBuild.full()).withName(CHEST_NAME)));
        box.setChanged();
        ItemStack item = PropwashApi.builds().createDrone(DroneData.of(CoreBuild.full()).withName(DRONE_NAME));
        Vec3 click = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, item, new BlockHitResult(click, Direction.UP, ground, false)) {
        };
        InteractionResult placed = item.getItem().useOn(context);
        if (!placed.consumesAction()) {
            throw new IllegalStateException("Drohne nicht abgestellt: " + placed);
        }
        DroneView view = PropwashApi.drones().dronesNear(level, click, 3.0, false).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("abgestellte Drohne bei " + ground.above().toShortString() + " nicht gefunden"));
        PropwashApi.droneData().set(view.entity(), probe(), DATA_VALUE);
        rule(server).ifPresent(rule -> server.getGameRules().set(rule, Boolean.FALSE, server));
        MARKER_VALUES.setProperty("drone", view.entity().getUUID().toString());
        MARKER_VALUES.setProperty("chest", chest.getX() + "," + chest.getY() + "," + chest.getZ());
        MobDefense.LOG.info("{} Drohne {} bei {} mit {}, Kiste bei {}, allow_tnt aus", TAG, view.entity().getUUID(),
                view.entity().blockPosition().toShortString(), view.accessories(), chest.toShortString());
    }

    private static String settled(MinecraftServer server) throws IOException {
        Entity drone = server.overworld().getEntity(UUID.fromString(MARKER_VALUES.getProperty("drone")));
        if (drone == null) {
            throw new IllegalStateException("Drohne nach " + SETTLE_TICKS + " Ticks verschwunden");
        }
        DroneView view = PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Drohne"));
        String problem = accessories(view);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        MARKER_VALUES.setProperty("mass_kg", Double.toString(view.massKg()));
        MARKER_VALUES.setProperty("data", PropwashApi.droneData().get(drone, probe()));
        try (Writer writer = Files.newBufferedWriter(dir().resolve(MARKER), StandardCharsets.UTF_8)) {
            MARKER_VALUES.store(writer, null);
        }
        return "PASS D8 prepare: Drohne " + drone.getUUID() + " mit " + mounts(view) + ", " + format(view.massKg()) + " kg, Daten „"
                + MARKER_VALUES.getProperty("data") + "“, Drohnen-Item in der Kiste, Chunk 0,0 dauerhaft geladen, allow_tnt aus";
    }

    private static String verify(MinecraftServer server, Entity drone) throws IOException {
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        Optional<DroneView> view = PropwashApi.drones().view(drone);
        if (view.isEmpty()) {
            problems.add("Entity " + drone.getUUID() + " ist keine Drohne mehr");
        } else {
            String problem = accessories(view.get());
            double mass = Double.parseDouble(MARKER_VALUES.getProperty("mass_kg"));
            if (problem != null) {
                problems.add(problem);
            }
            if (Math.abs(view.get().massKg() - mass) > 1.0e-6) {
                problems.add("Masse " + format(view.get().massKg()) + " kg statt " + format(mass) + " kg");
            }
            if (problem == null && Math.abs(view.get().massKg() - mass) <= 1.0e-6) {
                notes.add("Drohne mit " + mounts(view.get()) + ", " + format(view.get().massKg()) + " kg");
            }
        }
        String data = PropwashApi.droneData().get(drone, probe());
        if (!DATA_VALUE.equals(data)) {
            problems.add("Drohnen-Daten „" + data + "“ statt „" + DATA_VALUE + "“");
        } else {
            notes.add("Drohnen-Daten erhalten");
        }
        String[] xyz = MARKER_VALUES.getProperty("chest").split(",");
        BlockPos chest = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        BlockEntity entity = server.overworld().getBlockEntity(chest);
        if (!(entity instanceof ChestBlockEntity box)) {
            problems.add("Kiste bei " + chest.toShortString() + " fehlt");
        } else {
            Optional<DroneData> item = PropwashApi.builds().read(box.getItem(0));
            if (item.isEmpty()) {
                problems.add("Drohnen-Item in der Kiste fehlt: " + box.getItem(0));
            } else if (!item.get().build().equals(CoreBuild.full())) {
                problems.add("Drohnen-Item mit " + item.get().build() + " statt " + CoreBuild.full());
            } else if (!item.get().name().orElse("").equals(CHEST_NAME)) {
                problems.add("Drohnen-Item heißt " + item.get().name());
            } else {
                notes.add("Drohnen-Item in der Kiste vollständig");
            }
        }
        Optional<GameRule<Boolean>> rule = rule(server);
        rule.ifPresent(value -> notes.add("allow_tnt " + (Boolean.FALSE.equals(server.getGameRules().get(value))
                ? "behält den Wert aus (Gamerule ohne Add-on erhalten)" : "steht wieder auf dem Standard (Gamerule ohne Add-on verworfen)")));
        Path noAddon = dir().resolve(NO_ADDON);
        if (Files.isRegularFile(noAddon)) {
            notes.add("Lauf ohne Mob Defense: " + String.join(", ", Files.readAllLines(noAddon, StandardCharsets.UTF_8)));
        } else {
            problems.add(NO_ADDON + " fehlt (Lauf ohne Mob Defense nicht gelaufen)");
        }
        if (!problems.isEmpty()) {
            return "FAIL D8 verify: " + String.join("; ", problems) + (notes.isEmpty() ? "" : " | in Ordnung: " + String.join(" | ", notes));
        }
        return "PASS D8 verify: " + String.join(" | ", notes);
    }

    private static @Nullable String accessories(DroneView view) {
        Map<AccessorySlot, Identifier> expected = CoreBuild.mounts();
        if (!expected.equals(view.accessories())) {
            return "Zubehör " + view.accessories() + " statt " + expected;
        }
        return null;
    }

    private static String mounts(DroneView view) {
        List<String> out = new ArrayList<>();
        new TreeMap<>(view.accessories()).forEach((slot, part) -> out.add(slot.key() + "=" + part.getPath()));
        return String.join(" ", out);
    }

    @SuppressWarnings("unchecked")
    private static Optional<GameRule<Boolean>> rule(MinecraftServer server) {
        GameRule<?> rule = BuiltInRegistries.GAME_RULE.getValue(MobDefense.id(RuleDefaults.ALLOW_TNT.path()));
        return rule == null || rule.valueClass() != Boolean.class ? Optional.empty() : Optional.of((GameRule<Boolean>) rule);
    }

    private static DroneDataKey<String> probe() {
        DroneDataKey<String> probe = key;
        if (probe == null) {
            throw new IllegalStateException("Schlüssel d8_probe nicht angemeldet");
        }
        return probe;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String message(Exception e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    private static void finish(MinecraftServer server, String result) {
        if (done) {
            return;
        }
        done = true;
        if (result.startsWith("PASS")) {
            MobDefense.LOG.info("{} {}", TAG, result);
        } else {
            MobDefense.LOG.error("{} {}", TAG, result);
        }
        DevResult.write(result);
        server.halt(false);
    }
}
