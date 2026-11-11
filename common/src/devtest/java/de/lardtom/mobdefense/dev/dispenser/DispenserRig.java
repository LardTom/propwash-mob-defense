package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class DispenserRig {
    private DispenserRig() {
    }

    public static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    public static BuildSpec fiveInch() {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300")).withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
    }

    public static BuildSpec cinewhoop() {
        return BuildSpec.of(propwash("cinewhoop_3"), propwash("aio_20"), propwash("m1404_4600"), propwash("p3_3b"), propwash("digital_lite"),
                propwash("b4s_650")).withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
    }

    public static ItemStack item(String id, int count) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new IllegalStateException("Item " + id + " fehlt"));
        return new ItemStack(item, count);
    }

    public static ItemStack droneItem(BuildSpec spec, String name) {
        return PropwashApi.builds().createDrone(DroneData.of(spec).withName(name));
    }

    public static final double OVERHANG = 0.1;
    public static final double MUZZLE_BEYOND = 0.028;
    public static final double MIN_SUPPORT = 0.03;

    public static double inset(BuildSpec spec) {
        double reach = muzzleReach(spec);
        return Double.isNaN(reach) ? OVERHANG : Math.max(MIN_SUPPORT, reach - MUZZLE_BEYOND);
    }

    public static double muzzleReach(BuildSpec spec) {
        Optional<Identifier> front = spec.accessory(AccessorySlot.FRONT);
        if (front.isEmpty()) {
            return Double.NaN;
        }
        Optional<AccessoryDefinition> accessory = PropwashApi.parts().get(AccessoryDefinition.class, front.get());
        Optional<FrameDefinition> frame = PropwashApi.parts().get(FrameDefinition.class, spec.part(PartKind.FRAME));
        if (accessory.isEmpty() || frame.isEmpty()) {
            return Double.NaN;
        }
        AnchorSpec anchor = frame.get().geometry().accessoryAnchors().get(AccessorySlot.FRONT);
        double anchorZ = anchor == null ? -0.3 * frame.get().wheelbaseMm() : anchor.z();
        return (-anchorZ + accessory.get().depthMm() - accessory.get().recessMm()) / 1000.0;
    }

    public static Entity onPillar(ServerLevel level, ServerPlayer placer, BuildSpec spec, String name, BlockPos pillar, Direction facing,
                                  BiConsumer<BlockPos, BlockState> builder) {
        builder.accept(pillar, Blocks.STONE.defaultBlockState());
        float yaw = facing.toYRot();
        placer.setYRot(yaw);
        placer.setYHeadRot(yaw);
        double inset = inset(spec);
        Vec3 click = new Vec3(pillar.getX() + 0.5 + facing.getStepX() * (0.5 - inset), pillar.getY() + 1.0,
                pillar.getZ() + 0.5 + facing.getStepZ() * (0.5 - inset));
        Entity drone = place(level, placer, spec, name, pillar, click);
        Vec3 forward = view(drone).forward();
        if (forward.x * facing.getStepX() + forward.z * facing.getStepZ() < 0.9) {
            throw new IllegalStateException("Drohne " + name + " schaut nach " + forward + " statt " + facing);
        }
        return drone;
    }

    public static Entity place(ServerLevel level, @Nullable ServerPlayer placer, BuildSpec spec, String name, BlockPos ground) {
        return place(level, placer, spec, name, ground, new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5));
    }

    public static Entity place(ServerLevel level, @Nullable ServerPlayer placer, BuildSpec spec, String name, BlockPos ground, Vec3 click) {
        return place(level, placer, droneItem(spec, name), name, ground, click);
    }

    public static Entity place(ServerLevel level, @Nullable ServerPlayer placer, ItemStack stack, String name, BlockPos ground, Vec3 click) {
        Set<UUID> before = new HashSet<>();
        for (DroneView view : PropwashApi.drones().dronesNear(level, click, 4.0, false)) {
            before.add(view.entity().getUUID());
        }
        BlockHitResult hit = new BlockHitResult(click, Direction.UP, ground, false);
        InteractionResult result;
        if (placer != null) {
            ItemStack held = placer.getItemInHand(InteractionHand.MAIN_HAND);
            placer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            result = stack.useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND, hit));
            placer.setItemInHand(InteractionHand.MAIN_HAND, held);
        } else {
            result = stack.getItem().useOn(new UseOnContext(level, null, InteractionHand.MAIN_HAND, stack, hit) {
            });
        }
        for (DroneView view : PropwashApi.drones().dronesNear(level, click, 4.0, false)) {
            if (!before.contains(view.entity().getUUID())) {
                return view.entity();
            }
        }
        throw new IllegalStateException("Drohne " + name + " nicht abgestellt bei " + ground.above().toShortString() + ": " + result);
    }

    public static DroneView view(Entity drone) {
        return PropwashApi.drones().view(drone).orElseThrow(() -> new IllegalStateException("keine Drohne: " + drone));
    }

    public static AccessoryInventory inventory(Entity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.FRONT)
                .orElseThrow(() -> new IllegalStateException("Dispenser ohne Inventar an " + drone));
    }

    public static List<ItemStack> tntRun() {
        return List.of(new ItemStack(Items.TNT, AmmoTable.TNT_LIMIT), new ItemStack(Items.TNT, 2));
    }

    public static void load(Entity drone, ItemStack... stacks) {
        AccessoryInventory inventory = inventory(drone);
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, i < stacks.length && stacks[i] != null ? stacks[i].copy() : ItemStack.EMPTY);
        }
    }

    public static Vec3 ahead(Entity drone, double metres) {
        DroneView view = view(drone);
        Vec3 flat = new Vec3(view.forward().x, 0.0, view.forward().z);
        Vec3 dir = flat.lengthSqr() > 1.0E-6 ? flat.normalize() : new Vec3(0.0, 0.0, -1.0);
        return view.center().add(dir.scale(metres));
    }

    public static Optional<DispenserLog.Entry> fire(Entity drone, ServerPlayer actor) {
        long before = DispenserLog.last(drone).map(DispenserLog.Entry::gameTime).orElse(-1L);
        int attempts = DispenserLog.counts(drone).attempts();
        boolean pressed = PropwashApi.accessories().triggerAction(drone, 1, true, actor);
        PropwashApi.accessories().triggerAction(drone, 1, false, actor);
        if (!pressed || DispenserLog.counts(drone).attempts() == attempts) {
            return Optional.empty();
        }
        return DispenserLog.last(drone).filter(entry -> entry.gameTime() >= before);
    }

    public static Vec3 muzzle(Entity drone, ServerPlayer actor) {
        load(drone);
        DispenserLog.Entry entry = fire(drone, actor).orElseThrow(() -> new IllegalStateException("leerer Dispenser reagiert nicht"));
        if (!"EMPTY".equals(entry.status())) {
            throw new IllegalStateException("leerer Dispenser meldet " + entry.status() + " statt EMPTY");
        }
        return entry.muzzle();
    }

    public static String counts(List<ItemStack> contents) {
        StringBuilder out = new StringBuilder();
        for (ItemStack stack : contents) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(stack.isEmpty() ? "-" : stack.getCount() + "×" + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        }
        return out.toString();
    }
}
