package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class D4Scene {
    public static final String ENV = "MOBDEFENSE_D4";
    public static final String DRONE_NAME = "D4 Booster";
    public static final int DRONE_BLOCKS = 2;
    public static final double SIDE_METRES = 1.4;
    public static final int SCENE_OFFSET = -20;
    private static final String TAG = "[Mob-Defense-D4]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final Set<UUID> PLACED = new HashSet<>();
    private static volatile @Nullable Vec3 stand;
    private static volatile @Nullable Entity drone;

    private D4Scene() {
    }

    public static boolean enabled() {
        String value = System.getenv(ENV);
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true"));
    }

    public static String area(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos base = base(level);
        int forced = 0;
        for (BlockPos pos : new BlockPos[]{base, base.offset(0, 0, DRONE_BLOCKS)}) {
            if (level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true)) {
                forced++;
            }
        }
        return "Szene bei " + base.toShortString() + ", " + forced + " Chunks dauerhaft geladen";
    }

    private static BlockPos base(ServerLevel level) {
        return level.getRespawnData().globalPos().pos().offset(SCENE_OFFSET, 0, SCENE_OFFSET);
    }

    public static String start(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, base(level));
        BlockPos ground = top.offset(0, 0, DRONE_BLOCKS);
        level.setBlockAndUpdate(ground, Blocks.POLISHED_ANDESITE.defaultBlockState());
        Entity placed = BoosterRig.place(level, null, BoosterRig.buildWithSpear(), DRONE_NAME, ground);
        drone = placed;
        Vec3 forward = BoosterRig.view(placed).forward();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        side = side.lengthSqr() > 1.0E-9 ? side.normalize() : new Vec3(1.0, 0.0, 0.0);
        stand = new Vec3(placed.getX(), top.getY(), placed.getZ()).add(side.scale(SIDE_METRES));
        BoosterRig.load(placed, BoosterRig.rocket(1, 0));
        String spear = "Speer-Halterung ohne Verhalten (md-spear nicht gemergt)";
        if (PropwashApi.accessories().behavior(MobDefense.SPEAR_MOUNT).isPresent()) {
            Optional<AccessoryInventory> bottom = PropwashApi.accessories().inventory(placed, AccessorySlot.BOTTOM);
            if (bottom.isPresent() && bottom.get().size() > 0) {
                bottom.get().set(0, new ItemStack(Items.IRON_SPEAR));
                spear = "Eisenspeer unten";
            }
        }
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(D4Scene::tick);
        }
        String summary = "Drohne " + placed.getUUID() + " bei " + ground.above().toShortString() + " mit Rakete oben, " + spear
                + ", abgestellt ohne Pilot (ein Pilot muss in der Spielerliste stehen, also ein zweiter echter Client sein)";
        MobDefense.LOG.info("{} {}", TAG, summary);
        return summary;
    }

    private static void tick(MinecraftServer server) {
        Vec3 at = stand;
        Entity scene = drone;
        if (at == null || scene == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!PLACED.add(player.getUUID())) {
                continue;
            }
            Vec3 feet = at;
            Vec3 look = scene.position().subtract(feet.add(0.0, player.getEyeHeight(), 0.0));
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
            player.teleportTo(server.overworld(), feet.x, feet.y, feet.z, Set.<Relative>of(), yaw, pitch, true);
            boolean tracked = !scene.isRemoved();
            MobDefense.LOG.info("{} Zuschauer {} bei {} sieht zur Drohne (Pilot {}, Drohne {})", TAG, player.getGameProfile().name(), BlockPos.containing(at).toShortString(),
                    PropwashApi.drones().view(scene).flatMap(DroneView::pilot).map(UUID::toString).orElse("keiner"), tracked ? "da" : "weg");
        }
    }
}
