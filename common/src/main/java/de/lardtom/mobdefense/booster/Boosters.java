package de.lardtom.mobdefense.booster;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.booster.core.BoosterPose;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryAction;
import de.lardtom.propwash.api.accessory.AccessoryContext;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.drone.DronePhysics;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.event.PropwashEvents;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class Boosters {
    public static final TagKey<Item> ACCEPTS = TagKey.create(Registries.ITEM, MobDefense.id("booster_accepts"));
    public static final Identifier AFTERBURNER = MobDefense.id("afterburner");
    private static final int SWEEP_TICKS = 20;
    private static final int STALE_TICKS = 40;
    private static final int ENDING_TICKS = 6000;
    private static final Map<UUID, Burn> BURNS = new HashMap<>();
    private static final Map<UUID, Ending> ENDINGS = new HashMap<>();
    private static boolean initialized;

    public enum Reason {
        BURNED_OUT,
        PILOT_CHANGED,
        REMOVED,
        LOST
    }

    public record Ending(UUID drone, Reason reason, long gameTime, int flight, int stars, int pushes, int refused, Blast blast) {
    }

    public record Blast(boolean exploded, float damage, boolean droneHurt, int hurt) {
        public static final Blast NONE = new Blast(false, 0.0F, false, 0);
    }

    static final class Burn {
        final UUID drone;
        final ItemStack rocket;
        final long startedAt;
        final long endsAt;
        final double thrust;
        final int flight;
        final int stars;
        final Vec3 arm;
        final @Nullable UUID actor;
        long lastPush = Long.MIN_VALUE;
        int pushes;
        int refused;

        Burn(UUID drone, ItemStack rocket, long startedAt, long endsAt, double thrust, int flight, int stars, Vec3 arm, @Nullable UUID actor) {
            this.drone = drone;
            this.rocket = rocket;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
            this.thrust = thrust;
            this.flight = flight;
            this.stars = stars;
            this.arm = arm;
            this.actor = actor;
        }
    }

    private Boosters() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        BoostState.register();
        PropwashApi.accessories().register(MobDefense.ROCKET_BOOSTER, BoosterBehavior.INSTANCE);
        PropwashEvents.PILOT_CHANGED.register((drone, previous, current) -> {
            if (!previous.equals(current)) {
                stop(drone, Reason.PILOT_CHANGED);
            }
        });
        MobDefense.onServerStarted(server -> clear());
        MobDefense.onServerTick(Boosters::sweep);
        MobDefense.LOG.info("{}: Raketen-Booster angemeldet (2 g, höchstens {} N, Brenndauer {}/{}/{} Ticks)", MobDefense.MOD_NAME,
                (int) BoostProfile.MAX_NEWTONS, BoostProfile.burnTicks(1), BoostProfile.burnTicks(2), BoostProfile.burnTicks(3));
    }

    public static boolean initialized() {
        return initialized;
    }

    public static boolean accepts(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ACCEPTS);
    }

    public static int flight(ItemStack rocket) {
        Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);
        return BoostProfile.flight(fireworks == null ? 0 : fireworks.flightDuration());
    }

    public static int stars(ItemStack rocket) {
        Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);
        return fireworks == null ? 0 : fireworks.explosions().size();
    }

    public static synchronized boolean burning(Entity drone) {
        return BURNS.containsKey(drone.getUUID());
    }

    public static synchronized int active() {
        return BURNS.size();
    }

    public static synchronized Optional<Ending> lastEnding(UUID drone) {
        return Optional.ofNullable(ENDINGS.get(drone));
    }

    public static synchronized void forget(UUID drone) {
        ENDINGS.remove(drone);
    }

    public static boolean ignite(AccessoryAction action) {
        Entity drone = action.drone();
        ServerLevel level = action.level();
        if (!action.flying() || action.inventory().size() <= BoosterBehavior.SLOT) {
            return false;
        }
        ItemStack rocket = action.inventory().get(BoosterBehavior.SLOT);
        if (!accepts(rocket)) {
            return false;
        }
        double mass = PropwashApi.drones().view(drone).map(DroneView::massKg).orElse(0.0);
        double thrust = BoostProfile.thrustNewtons(mass);
        if (!(thrust > 0.0)) {
            return false;
        }
        long now = level.getGameTime();
        int flight = flight(rocket);
        int stars = stars(rocket);
        ServerPlayer actor = action.player();
        Burn burn = new Burn(drone.getUUID(), rocket.copyWithCount(1), now, now + BoostProfile.burnTicks(flight), thrust, flight, stars,
                arm(action), actor.getUUID());
        synchronized (Boosters.class) {
            if (BURNS.containsKey(burn.drone)) {
                return false;
            }
            BURNS.put(burn.drone, burn);
            ENDINGS.remove(burn.drone);
        }
        BoostState.set(drone, new BoostState(burn.endsAt, flight, stars, (float) thrust));
        BoosterEffects.launch(level, drone);
        push(action, burn, now);
        award(actor);
        return true;
    }

    static void tick(AccessoryContext context) {
        Entity drone = context.drone();
        Burn burn;
        synchronized (Boosters.class) {
            burn = BURNS.get(drone.getUUID());
        }
        if (burn == null) {
            if (BoostState.of(drone).active()) {
                AccessoryInventory inventory = context.inventory();
                if (inventory.size() > BoosterBehavior.SLOT && accepts(inventory.get(BoosterBehavior.SLOT))) {
                    inventory.take(BoosterBehavior.SLOT, 1);
                }
                BoostState.set(drone, BoostState.IDLE);
                record(new Ending(drone.getUUID(), Reason.LOST, context.level().getGameTime(), 0, 0, 0, 0, Blast.NONE));
            }
            return;
        }
        if (!holds(context.inventory(), burn.rocket)) {
            stop(drone, Reason.REMOVED);
            return;
        }
        long now = context.level().getGameTime();
        if (now >= burn.endsAt) {
            finish(context, burn);
            return;
        }
        if (burn.lastPush < now) {
            push(context, burn, now);
        }
        BoosterEffects.sparks(context.level(), tail(context.mount()), context.mount().forward());
    }

    private static void push(AccessoryContext context, Burn burn, long now) {
        boolean applied = context.physics().push(new Vec3(0.0, 0.0, -burn.thrust), true, burn.arm, BoostProfile.TICK_SECONDS);
        burn.lastPush = now;
        burn.pushes++;
        if (!applied) {
            burn.refused++;
        }
    }

    private static void finish(AccessoryContext context, Burn burn) {
        synchronized (Boosters.class) {
            if (BURNS.get(burn.drone) != burn) {
                return;
            }
            BURNS.remove(burn.drone);
        }
        Entity drone = context.drone();
        ServerLevel level = context.level();
        spend(context.inventory(), burn.rocket);
        BoostState.set(drone, BoostState.IDLE);
        MountPose mount = context.mount();
        Vec3 middle = mount.at(0.0, BoosterPose.axisUpMetres(), BoosterPose.middleForwardMetres());
        Blast blast;
        if (burn.stars > 0) {
            ServerPlayer attacker = context.pilot().orElseGet(() -> player(level.getServer(), burn.actor));
            blast = BoosterBlast.explode(level, drone, middle, burn.rocket, burn.stars, attacker);
        } else {
            BoosterEffects.burnOut(level, tail(mount));
            blast = Blast.NONE;
        }
        record(new Ending(burn.drone, Reason.BURNED_OUT, level.getGameTime(), burn.flight, burn.stars, burn.pushes, burn.refused, blast));
    }

    public static void stop(Entity drone, Reason reason) {
        Burn burn;
        synchronized (Boosters.class) {
            burn = BURNS.remove(drone.getUUID());
        }
        if (burn == null || !(drone.level() instanceof ServerLevel level)) {
            return;
        }
        if (reason != Reason.REMOVED) {
            PropwashApi.accessories().inventory(drone, AccessorySlot.TOP).ifPresent(inventory -> spend(inventory, burn.rocket));
        }
        BoostState.set(drone, BoostState.IDLE);
        BoosterEffects.extinguish(level, drone);
        record(new Ending(burn.drone, reason, level.getGameTime(), burn.flight, burn.stars, burn.pushes, burn.refused, Blast.NONE));
    }

    private static void spend(AccessoryInventory inventory, ItemStack rocket) {
        if (holds(inventory, rocket)) {
            inventory.take(BoosterBehavior.SLOT, 1);
        }
    }

    private static boolean holds(AccessoryInventory inventory, ItemStack rocket) {
        return inventory.size() > BoosterBehavior.SLOT && ItemStack.isSameItemSameComponents(inventory.get(BoosterBehavior.SLOT), rocket);
    }

    static Vec3 tail(MountPose mount) {
        return mount.at(0.0, BoosterPose.axisUpMetres(), BoosterPose.tailForwardMetres());
    }

    static Vec3 arm(AccessoryContext context) {
        Optional<DroneView> view = PropwashApi.drones().view(context.drone());
        if (view.isEmpty() || view.get().forward().lengthSqr() < 1.0E-12) {
            return Vec3.ZERO;
        }
        Vec3 forward = view.get().forward().normalize();
        double behind = -tail(context.mount()).subtract(view.get().center()).dot(forward);
        return new Vec3(0.0, 0.0, Math.max(-DronePhysics.MAX_ARM, Math.min(DronePhysics.MAX_ARM, behind)));
    }

    private static @Nullable ServerPlayer player(MinecraftServer server, @Nullable UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static void award(ServerPlayer player) {
        try {
            AdvancementHolder holder = player.level().getServer().getAdvancements().get(AFTERBURNER);
            if (holder != null) {
                player.getAdvancements().award(holder, "done");
            }
        } catch (RuntimeException e) {
            MobDefense.LOG.debug("{}: Fortschritt {} nicht vergeben", MobDefense.MOD_NAME, AFTERBURNER, e);
        }
    }

    private static synchronized void record(Ending ending) {
        ENDINGS.put(ending.drone(), ending);
    }

    private static void sweep(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (now % SWEEP_TICKS != 0) {
            return;
        }
        synchronized (Boosters.class) {
            BURNS.values().removeIf(burn -> now > burn.endsAt + STALE_TICKS);
            ENDINGS.values().removeIf(ending -> now - ending.gameTime() > ENDING_TICKS);
        }
    }

    private static synchronized void clear() {
        BURNS.clear();
        ENDINGS.clear();
    }
}
