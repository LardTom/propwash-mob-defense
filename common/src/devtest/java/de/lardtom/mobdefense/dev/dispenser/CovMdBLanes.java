package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class CovMdBLanes {
    public static final Direction FACING = Direction.EAST;

    private CovMdBLanes() {
    }

    public record Lane(Entity drone, BlockPos pillar, BlockPos muzzle, BlockPos target) {
        public ItemStack slot() {
            List<ItemStack> contents = PropwashApi.accessories().contents(this.drone, AccessorySlot.FRONT);
            return contents.isEmpty() ? ItemStack.EMPTY : contents.getFirst();
        }
    }

    public static BlockState block(String id) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException("Block " + id + " fehlt"))
                .defaultBlockState();
    }

    public static Entity pillarDrone(MinecraftServer server, BlockPos ground, int height, BuildSpec spec, String name) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        for (int i = 1; i < height; i++) {
            Arena.place(level, ground.above(i), Blocks.STONE.defaultBlockState());
        }
        BlockPos top = ground.above(height);
        return Arena.track(DispenserRig.onPillar(level, player, spec, name, top, FACING, (pos, state) -> Arena.place(level, pos, state)));
    }

    public static Lane probe(MinecraftServer server, Entity drone, BlockPos pillarTop) {
        ServerPlayer player = Arena.player(server);
        DispenserRig.load(drone);
        DispenserLog.Entry entry = DispenserRig.fire(drone, player).orElseThrow(() -> new IllegalStateException("Probe-Schuss ohne Eintrag"));
        Vec3 front = entry.muzzle();
        Direction facing = nearest(entry.forward());
        if (facing != FACING) {
            throw new IllegalStateException("Mündung zeigt nach " + facing + " statt " + FACING);
        }
        int y = Math.max((int) Math.floor(front.y), pillarTop.getY() + 1);
        BlockPos muzzle = new BlockPos((int) Math.floor(front.x), y, (int) Math.floor(front.z));
        ServerLevel level = Arena.level(server);
        if (!level.getBlockState(muzzle).isAir()) {
            throw new IllegalStateException("Mündungsblock " + muzzle.toShortString() + " ist " + level.getBlockState(muzzle));
        }
        return new Lane(drone, pillarTop, muzzle, muzzle.relative(facing));
    }

    public static Direction nearest(Vec3 forward) {
        Direction best = Direction.NORTH;
        double dot = Double.NEGATIVE_INFINITY;
        for (Direction direction : Direction.values()) {
            double d = direction.getStepX() * forward.x + direction.getStepY() * forward.y + direction.getStepZ() * forward.z;
            if (d > dot) {
                dot = d;
                best = direction;
            }
        }
        return best;
    }

    public static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }
}
