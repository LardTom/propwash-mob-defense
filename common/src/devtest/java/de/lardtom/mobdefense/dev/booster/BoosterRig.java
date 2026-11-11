package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.BoostState;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.booster.Boosters;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.drone.AutonomyOptions;
import de.lardtom.propwash.api.drone.AutonomyResult;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.DroneDamage;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.AccessorySlot;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class BoosterRig {
    public static final Identifier FLIGHT = MobDefense.id("selftest_booster");
    private static final List<Hit> HITS = new ArrayList<>();
    private static boolean listening;

    public record Hit(UUID drone, float amount, boolean fireworks, String cause) {
    }

    private BoosterRig() {
    }

    public static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    public static BuildSpec build() {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300")).withAccessory(AccessorySlot.TOP, MobDefense.ROCKET_BOOSTER);
    }

    public static BuildSpec buildWithSpear() {
        return build().withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT);
    }

    public static Entity place(ServerLevel level, @Nullable ServerPlayer placer, BuildSpec spec, String name, BlockPos ground) {
        ItemStack stack = PropwashApi.builds().createDrone(DroneData.of(spec).withName(name));
        Vec3 click = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5);
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

    public static AccessoryInventory booster(Entity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.TOP)
                .orElseThrow(() -> new IllegalStateException("Raketen-Booster ohne Inventar"));
    }

    public static ItemStack rocket(int flight, int stars) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        List<FireworkExplosion> explosions = new ArrayList<>();
        for (int i = 0; i < stars; i++) {
            explosions.add(new FireworkExplosion(FireworkExplosion.Shape.values()[i % FireworkExplosion.Shape.values().length],
                    IntList.of(0xE02020 + i * 0x1010), IntList.of(0xF0F0F0), i % 2 == 0, i % 2 == 1));
        }
        rocket.set(DataComponents.FIREWORKS, new Fireworks(flight, explosions));
        return rocket;
    }

    public static void load(Entity drone, ItemStack rocket) {
        booster(drone).set(BoosterBehavior.SLOT, rocket.copyWithCount(1));
    }

    public static boolean ignite(Entity drone, ServerPlayer actor) {
        boolean pressed = PropwashApi.accessories().triggerAction(drone, BoosterBehavior.IGNITE, true, actor);
        PropwashApi.accessories().triggerAction(drone, BoosterBehavior.IGNITE, false, actor);
        return pressed && Boosters.burning(drone);
    }

    public static BoostFlight hover(Entity drone, ServerPlayer owner, double metres) {
        BoostFlight flight = new BoostFlight(drone.getX(), drone.getY() + metres, drone.getZ());
        AutonomyOptions options = AutonomyOptions.of(FLIGHT).label("MD BOOST").maxDuration(Duration.ofMinutes(5)).takeoff(Math.min(30.0, metres));
        AutonomyResult result = PropwashApi.drones().autonomy().start(drone, owner, flight, options);
        if (!result.started()) {
            throw new IllegalStateException("Schwebeflug startet nicht: " + result);
        }
        return flight;
    }

    public static String describe(BoostState state, long now) {
        return "ends_at=" + state.endsAt() + " (" + (state.endsAt() - now) + " Ticks), flight=" + state.flight() + ", stars=" + state.stars()
                + ", thrust_n=" + state.thrustNewtons();
    }

    public static synchronized void listen() {
        if (listening) {
            return;
        }
        listening = true;
        PropwashEvents.DRONE_DAMAGED.register(BoosterRig::damaged);
    }

    private static synchronized void damaged(DroneDamage damage) {
        Entity cause = damage.source().getEntity();
        HITS.add(new Hit(damage.drone().getUUID(), damage.amount(), damage.source().is(DamageTypes.FIREWORKS),
                cause == null ? "-" : BuiltInRegistries.ENTITY_TYPE.getKey(cause.getType()).toString()));
    }

    public static synchronized List<Hit> hits(UUID drone) {
        return HITS.stream().filter(hit -> hit.drone().equals(drone)).toList();
    }

    public static synchronized void clearHits() {
        HITS.clear();
    }
}
