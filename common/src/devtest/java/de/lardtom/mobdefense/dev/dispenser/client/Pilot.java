package de.lardtom.mobdefense.dev.dispenser.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.flight.FlightState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class Pilot {
    public static final String REMOTE = "propwash:remote";
    public static final String GOGGLES = "propwash:goggles_creative";
    public static final String DRONE = "dispenser.drone";
    public static final String ARM = "key.propwash.drone_arm";
    public static final String ACTION_1 = "key.propwash.accessory_action_1";
    public static final String ACTION_2 = "key.propwash.accessory_action_2";
    public static final String STEADY_COMPASS_DEGREES = "0";
    private static final CompassProperty STEADY_COMPASS = new CompassProperty(STEADY_COMPASS_DEGREES);
    private static final List<KeyMapping> HELD = new ArrayList<>();
    private static double startY;

    private Pilot() {
    }

    public static Entity spawn(MinecraftServer server, Map<String, Object> state, BuildSpec build, String name, BlockPos ground, float yaw) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, 3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), yaw, 10.0F, true);
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(GOGGLES));
        Entity drone = Arena.track(DispenserRig.place(level, player, build, name, ground));
        state.put(DRONE, drone.getUUID());
        return drone;
    }

    public static Entity serverDrone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(DRONE);
        Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt am Server");
        }
        return drone;
    }

    public static void bind(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = serverDrone(server, state);
        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
    }

    public static void start(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = serverDrone(server, state);
        ItemStack remote = player.getItemInHand(InteractionHand.MAIN_HAND);
        remote.use(player.level(), player, InteractionHand.MAIN_HAND);
        Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
        if (pilot.isEmpty() || !pilot.get().equals(player.getUUID())) {
            throw new IllegalStateException("Flug nicht gestartet (Pilot " + pilot.map(UUID::toString).orElse("keiner") + ")");
        }
    }

    public static void release(MinecraftServer server) {
        ServerPlayer player = Arena.player(server);
        for (Entity drone : Arena.drones(player.level())) {
            Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
            if (pilot.isPresent() && pilot.get().equals(player.getUUID())) {
                player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
                player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
            }
        }
    }

    public static Script fly() {
        return Script.create()
                .sleep(10)
                .server("Funke binden", Pilot::bind)
                .sleep(10)
                .run("Kompassfehler " + STEADY_COMPASS_DEGREES + "° für den neuen Flug", STEADY_COMPASS::set)
                .server("Flug starten", Pilot::start)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying() && PropwashClientApi.input().drone().isPresent())
                .run("Test-Eigenschaft wie vor dem Flug", STEADY_COMPASS::restore)
                .sleep(10);
    }

    public static Script takeOff(double metres) {
        return Script.create()
                .run("Höhe halten", () -> drone().ifPresent(d -> {
                    startY = d.getY();
                    HoverAssist.hover(d.getY() + metres);
                }))
                .then("scharf schalten", Pilot::arm)
                .then("Höhe erreicht", (ctx, ticks) -> settled(ticks, metres));
    }

    public static Optional<Entity> drone() {
        return PropwashClientApi.input().drone();
    }

    public static boolean armed() {
        return drone().flatMap(d -> PropwashApi.drones().view(d)).map(DroneView::armed).orElse(false);
    }

    public static StepResult arm(SelfTest.StepContext ctx, int ticks) {
        if (armed()) {
            return StepResult.pass();
        }
        if (ticks % 40 == 0) {
            click(ARM);
        }
        return ticks > 240 ? StepResult.fail("Drohne lässt sich nicht scharf schalten") : StepResult.pending();
    }

    private static StepResult settled(int ticks, double metres) {
        FlightState state = HoverAssist.last();
        double height = state == null ? 0.0 : state.y() - startY;
        if (state != null && ticks > 40 && armed() && Math.abs(state.vy()) < 0.25 && Math.hypot(state.vx(), state.vz()) < 0.4 && height > metres * 0.6) {
            return StepResult.pass(String.format(Locale.ROOT, "schwebt %.1f m über dem Start", height));
        }
        return ticks > 400 ? StepResult.fail("schwebt nicht ruhig: " + (state == null ? "keine Flugdaten" : String.format(Locale.ROOT,
                "Höhe %.1f m, vy %.2f, vh %.2f", height, state.vy(), Math.hypot(state.vx(), state.vz())))) : StepResult.pending();
    }

    public static void hold(String name, boolean down) {
        KeyMapping key = KeyMapping.get(name);
        if (key == null) {
            throw new IllegalStateException("Taste " + name + " fehlt");
        }
        key.setDown(down);
        if (down && !HELD.contains(key)) {
            HELD.add(key);
        } else if (!down) {
            HELD.remove(key);
        }
    }

    public static void click(String name) {
        KeyMapping key = KeyMapping.get(name);
        if (key == null) {
            throw new IllegalStateException("Taste " + name + " fehlt");
        }
        KeyMapping.click(InputConstants.getKey(key.saveString()));
    }

    public static Script press(String name, int ticks) {
        return Script.create()
                .run("Taste " + name, () -> hold(name, true))
                .sleep(ticks)
                .run("Taste " + name + " los", () -> hold(name, false))
                .sleep(4);
    }

    public static void releaseKeys() {
        for (KeyMapping key : List.copyOf(HELD)) {
            key.setDown(false);
        }
        HELD.clear();
    }

    public static void land(Minecraft mc) {
        HoverAssist.off();
        releaseKeys();
        STEADY_COMPASS.restore();
    }
}
