package de.lardtom.mobdefense.dev.booster.client;

import de.lardtom.mobdefense.booster.BoostState;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.booster.Boosters;
import de.lardtom.mobdefense.booster.core.BoostProfile;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.booster.BoostFlight;
import de.lardtom.mobdefense.dev.booster.BoosterRig;
import de.lardtom.mobdefense.dev.booster.Follow;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class BoosterSteps {
    public static final int SERVICE_ACCESSORY_BUTTON = 3000;
    public static final double HOVER_METRES = 4.0;
    public static final double LOW_HOVER_METRES = 3.0;
    public static final double MIN_ACCELERATION = 0.85;
    public static final double MAX_ACCELERATION = 1.10;
    public static final double MIN_DELTA_V = 0.70;
    public static final double MAX_DELTA_V = 1.05;
    public static final double DAMAGE_TOLERANCE = 1.5;
    public static final double PILOT_ZOMBIE_SIDE = 3.0;
    public static final double CAMERA_BEHIND = 1.4;
    public static final double CAMERA_SIDE = 0.8;
    public static final double CAMERA_UP = 0.35;
    public static final double CAMERA_LOOK_AHEAD = 0.3;
    public static final double PHOTO_TRAVEL = 1.2;
    public static final double PHOTO_SIDE = 1.3;
    public static final double PHOTO_UP = 0.25;
    public static final double PHOTO_CENTER_BACK = 0.25;
    private static final String DRONE = "booster.drone";
    private static final String FLIGHT = "booster.flight";
    private static final String MASS = "booster.mass";
    private static final String ZOMBIE = "booster.zombie";
    private static final String HEALTH = "booster.health";
    private static final String NOTE = "booster.note";
    private static final String REMOTE = "propwash:remote";
    private static final String GOGGLES = "propwash:goggles_creative";
    private static boolean hudHidden;
    private static volatile @Nullable BoostFlight photoFlight;
    private static volatile @Nullable UUID photoDrone;

    private BoosterSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("S9 Booster – zweite Rakete abgelehnt, Zündung mit 2 g entlang der Nase, Brenndauer nach Flugdauer, Slot leer", 1600, Script.create()
                .server("Drohne mit Raketen-Booster", (server, state) -> place(server, state, Arena.ground(0, 6), "S9 Booster"))
                .server("Wartung: zweite Rakete abgelehnt", BoosterSteps::service)
                .then("Wartung", BoosterSteps::note)
                .sleep(5)
                .server("Schwebeflug", (server, state) -> state.put(FLIGHT, BoosterRig.hover(drone(server, state), Arena.player(server), HOVER_METRES)))
                .then("schwebt ruhig", BoosterSteps::settled)
                .server("Zündung über Zubehör-Aktion 3", (server, state) -> state.put(NOTE, ignite(server, state, 1, 0)))
                .then("Zündung", BoosterSteps::note)
                .then("Brennschluss", BoosterSteps::burnedOut)
                .sleep(4)
                .server("Tempo, Brenndauer, Slot", (server, state) -> state.put(NOTE, measured(server, state)))
                .then("Messung", BoosterSteps::note)
                .server("Landen", (server, state) -> PropwashApi.drones().autonomy().stop(drone(server, state))), BoosterSteps::cleanup);
        steps.add("S9 Booster – Funken am Heck (Foto), Rakete mit Sternen explodiert am Ende (Drohne und Zombie getroffen)", 2400, Script.create()
                .server("Drohne mit Rakete ohne Sterne im Schwebeflug", BoosterSteps::photoScene)
                .then("schwebt ruhig", BoosterSteps::settled)
                .append(photoBurn(Shots.ENGLISH, "booster_burn"))
                .server("Drohne hält an, neue Rakete ohne Sterne", BoosterSteps::stopAndReload)
                .then("schwebt wieder ruhig", BoosterSteps::settled)
                .append(photoBurn(Shots.GERMAN, "booster_burn_de"))
                .run("Sprache und HUD zurück", () -> {
                    Shots.restoreLanguage(Minecraft.getInstance());
                    restoreHud();
                })
                .sleep(10)
                .server("Rakete mit Sternen im Flug nachladen, Zombie folgt, Zündung", BoosterSteps::igniteStars)
                .then("Zündung mit Sternen", BoosterSteps::note)
                .then("Explosion", BoosterSteps::burnedOut)
                .sleep(6)
                .server("Schaden durch die Sterne", (server, state) -> state.put(NOTE, blasted(server, state)))
                .then("Schaden", BoosterSteps::note), BoosterSteps::cleanup);
        steps.add("S9 Booster – Pilotenwechsel beendet den Booster sofort (kein Schub, keine Explosion, Rakete verbraucht)", 1600, Script.create()
                .server("Pilot mit Drohne, Sternen-Rakete und Zombie", BoosterSteps::pilotScene)
                .sleep(10)
                .server("Funke binden", BoosterSteps::bind)
                .sleep(10)
                .server("Flug starten", BoosterSteps::startFlight)
                .await("FPV-Ansicht am Client", 200, () -> PropwashClientApi.input().flying())
                .sleep(10)
                .server("Zündung über Zubehör-Aktion 3", (server, state) -> state.put(NOTE, ignite(server, state, 3, 1)))
                .then("Zündung", BoosterSteps::note)
                .sleep(6)
                .server("Pilot steigt aus (PILOT_CHANGED)", (server, state) -> state.put(NOTE, release(server, state)))
                .then("Pilotenwechsel", BoosterSteps::note)
                .await("FPV endet am Client", 200, () -> !PropwashClientApi.input().flying())
                .sleep(BoostProfile.burnTicks(3) + 10)
                .server("keine Explosion nach der Brenndauer", (server, state) -> state.put(NOTE, quiet(server, state)))
                .then("ohne Explosion", BoosterSteps::note), BoosterSteps::cleanup);
    }

    private static void place(MinecraftServer server, Map<String, Object> state, BlockPos ground, String name) {
        BoosterRig.listen();
        ServerPlayer player = Arena.player(server);
        Entity drone = Arena.track(BoosterRig.place(Arena.level(server), player, BoosterRig.build(), name, ground));
        state.put(DRONE, drone.getUUID());
        Boosters.forget(drone.getUUID());
    }

    private static Entity drone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(DRONE);
        Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt am Server");
        }
        return drone;
    }

    private static UUID droneId(Map<String, Object> state) {
        return (UUID) state.get(DRONE);
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static void service(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(server, state);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
        player.setShiftKeyDown(false);
        if (!player.containerMenu.clickMenuButton(player, SERVICE_ACCESSORY_BUTTON)) {
            player.closeContainer();
            throw new IllegalStateException("Wartung öffnet das Zubehör nicht (" + player.containerMenu.getClass().getSimpleName() + ")");
        }
        AbstractContainerMenu menu = player.containerMenu;
        try {
            Slot booster = menu.slots.getFirst();
            if (booster.container == player.getInventory()) {
                throw new IllegalStateException("Zubehör-Menü ohne Slot des Boosters");
            }
            ItemStack two = BoosterRig.rocket(1, 0).copyWithCount(2);
            if (booster.getMaxStackSize(two) != 1 || !booster.mayPlace(two)) {
                throw new IllegalStateException("Slot nimmt Raketen nicht einzeln: höchstens " + booster.getMaxStackSize(two));
            }
            if (booster.mayPlace(new ItemStack(Items.ARROW)) || booster.mayPlace(new ItemStack(Items.TRIDENT))) {
                throw new IllegalStateException("Slot nimmt Pfeil oder Dreizack");
            }
            player.getInventory().setItem(0, two);
            player.getInventory().setItem(1, new ItemStack(Items.ARROW, 4));
            int rockets = inventorySlot(menu, player, 0);
            int arrows = inventorySlot(menu, player, 1);
            menu.quickMoveStack(player, rockets);
            int firstIn = booster.getItem().getCount();
            int firstLeft = player.getInventory().getItem(0).getCount();
            menu.quickMoveStack(player, rockets);
            int secondIn = booster.getItem().getCount();
            int secondLeft = player.getInventory().getItem(0).getCount();
            menu.quickMoveStack(player, arrows);
            boolean arrowIn = booster.getItem().is(Items.ARROW);
            if (firstIn != 1 || firstLeft != 1 || secondIn != 1 || secondLeft != 1 || arrowIn) {
                throw new IllegalStateException(String.format(Locale.ROOT, "erwartet 1 Rakete im Booster und 1 im Inventar: nach dem ersten Umschichten %d/%d, "
                        + "nach dem zweiten %d/%d, Pfeil im Slot %s", firstIn, firstLeft, secondIn, secondLeft, arrowIn));
            }
            state.put(NOTE, "Wartung: 2 Raketen umgeschichtet → 1 im Booster, die zweite bleibt im Inventar (abgelehnt), Pfeil und Dreizack abgelehnt");
        } finally {
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.getInventory().setItem(1, ItemStack.EMPTY);
            player.closeContainer();
        }
        ItemStack loaded = BoosterRig.booster(drone).get(BoosterBehavior.SLOT);
        if (!loaded.is(Items.FIREWORK_ROCKET) || loaded.getCount() != 1) {
            throw new IllegalStateException("Booster hält nach der Wartung " + loaded);
        }
    }

    private static int inventorySlot(AbstractContainerMenu menu, ServerPlayer player, int index) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == player.getInventory() && slot.getContainerSlot() == index) {
                return i;
            }
        }
        throw new IllegalStateException("Inventar-Slot " + index + " fehlt im Zubehör-Menü");
    }

    private static StepResult settled(SelfTest.StepContext ctx, int ticks) {
        BoostFlight flight = (BoostFlight) ctx.state().get(FLIGHT);
        BoostFlight.Snapshot last = flight == null ? null : flight.last();
        if (last != null && ticks > 40 && last.armed() && Math.abs(last.vy()) < 0.3 && last.horizontalSpeed() < 0.4
                && Math.abs(last.y() - flight.targetY()) < 0.6) {
            return StepResult.pass(String.format(Locale.ROOT, "schwebt bei y %.2f (Ziel %.2f)", last.y(), flight.targetY()));
        }
        if (ticks > 500) {
            return StepResult.fail("schwebt nicht ruhig: " + (last == null ? "keine Flugdaten" : String.format(Locale.ROOT,
                    "y %.2f (Ziel %.2f), vy %.2f, vh %.2f, scharf %s", last.y(), flight.targetY(), last.vy(), last.horizontalSpeed(), last.armed())));
        }
        return StepResult.pending();
    }

    private static String ignite(MinecraftServer server, Map<String, Object> state, int flightDuration, int stars) {
        Entity drone = drone(server, state);
        ServerPlayer player = Arena.player(server);
        if (!BoosterRig.booster(drone).get(BoosterBehavior.SLOT).is(Items.FIREWORK_ROCKET)) {
            BoosterRig.load(drone, BoosterRig.rocket(flightDuration, stars));
        }
        DroneView view = BoosterRig.view(drone);
        Object flight = state.get(FLIGHT);
        if (flight instanceof BoostFlight boost) {
            boost.level();
            boost.record(view.forward().x, view.forward().z);
        }
        long now = Arena.level(server).getGameTime();
        double mass = view.massKg();
        state.put(MASS, mass);
        if (!BoosterRig.ignite(drone, player)) {
            throw new IllegalStateException("Booster zündet nicht (fliegt " + view.flying() + ")");
        }
        BoostState data = BoostState.of(drone);
        long ticks = data.endsAt() - now;
        double thrust = BoostProfile.thrustNewtons(mass);
        if (ticks != BoostProfile.burnTicks(flightDuration) || data.flight() != flightDuration || data.stars() != stars
                || Math.abs(data.thrustNewtons() - thrust) > 0.01) {
            throw new IllegalStateException("Drohnen-Daten passen nicht: " + BoosterRig.describe(data, now) + ", erwartet "
                    + BoostProfile.burnTicks(flightDuration) + " Ticks und " + String.format(Locale.ROOT, "%.2f N", thrust));
        }
        if (!BoosterRig.booster(drone).get(BoosterBehavior.SLOT).is(Items.FIREWORK_ROCKET)) {
            throw new IllegalStateException("Rakete verschwindet beim Zünden statt in der Wiege zu brennen");
        }
        if (BoosterRig.ignite(drone, player) && BoostState.of(drone).endsAt() != data.endsAt()) {
            throw new IllegalStateException("zweite Zündung während des Brennens");
        }
        return String.format(Locale.ROOT, "gezündet: Masse %.3f kg, Schub %.2f N (= m × 19,6), %d Ticks (%.2f s), Flugdauer %d, Sterne %d", mass,
                data.thrustNewtons(), ticks, ticks / 20.0, data.flight(), data.stars());
    }

    private static StepResult burnedOut(SelfTest.StepContext ctx, int ticks) {
        Object id = ctx.state().get(DRONE);
        if (id instanceof UUID uuid && Boosters.lastEnding(uuid).isPresent()) {
            return StepResult.pass();
        }
        return ticks > 200 ? StepResult.fail("Booster brennt nicht aus") : StepResult.pending();
    }

    private static String measured(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        BoostFlight flight = (BoostFlight) state.get(FLIGHT);
        flight.stopRecording();
        List<BoostFlight.Sample> samples = flight.samples();
        Boosters.Ending ending = Boosters.lastEnding(droneId(state)).orElseThrow(() -> new IllegalStateException("kein Ende des Boosters"));
        if (ending.reason() != Boosters.Reason.BURNED_OUT || ending.pushes() != BoostProfile.burnTicks(1) || ending.refused() != 0) {
            throw new IllegalStateException("Ende " + ending.reason() + " nach " + ending.pushes() + " Schüben (" + ending.refused() + " abgelehnt), erwartet "
                    + BoostProfile.burnTicks(1));
        }
        if (!BoosterRig.booster(drone).get(BoosterBehavior.SLOT).isEmpty()) {
            throw new IllegalStateException("Slot nach dem Brennen nicht leer: " + BoosterRig.booster(drone).get(BoosterBehavior.SLOT));
        }
        if (BoostState.of(drone).active()) {
            throw new IllegalStateException("Drohnen-Daten nach dem Brennen nicht zurückgesetzt");
        }
        if (samples.size() < 20) {
            throw new IllegalStateException("zu wenige Messpunkte: " + samples.size());
        }
        double mass = (double) state.get(MASS);
        double t0 = samples.getFirst().time();
        double v0 = samples.getFirst().along();
        double half = BoostFlight.speedAt(samples, t0 + 0.5);
        double acceleration = (half - v0) / 0.5;
        double burn = BoostProfile.burnSeconds(1);
        double peak = BoostFlight.maxAlong(samples, t0, t0 + burn + 0.3);
        double gained = peak - v0;
        double ideal = BoostProfile.deltaV(mass, 1);
        double target = BoostProfile.acceleration(mass);
        double minY = samples.stream().mapToDouble(BoostFlight.Sample::y).min().orElse(Double.NaN);
        double maxY = samples.stream().mapToDouble(BoostFlight.Sample::y).max().orElse(Double.NaN);
        String report = String.format(Locale.ROOT, "Tempo entlang der Nase %.2f → %.2f m/s (+%.2f, Rechnung %.2f ohne Luftwiderstand, %.0f %%), "
                        + "Beschleunigung in den ersten 0,5 s %.2f m/s² (Rechnung %.2f, %.0f %%), Höhe %.2f…%.2f m, Masse im Flugmodell %.3f kg, %d Schübe, Slot leer",
                v0, peak, gained, ideal, 100.0 * gained / ideal, acceleration, target, 100.0 * acceleration / target, minY, maxY,
                samples.getFirst().massKg(), ending.pushes());
        if (acceleration < MIN_ACCELERATION * target || acceleration > MAX_ACCELERATION * target) {
            throw new IllegalStateException("Beschleunigung außerhalb " + MIN_ACCELERATION + "…" + MAX_ACCELERATION + " × 2 g: " + report);
        }
        if (gained < MIN_DELTA_V * ideal || gained > MAX_DELTA_V * ideal) {
            throw new IllegalStateException("Tempo-Zuwachs außerhalb " + MIN_DELTA_V + "…" + MAX_DELTA_V + " × Rechnung: " + report);
        }
        return report;
    }

    private static void photoScene(MinecraftServer server, Map<String, Object> state) {
        place(server, state, Arena.ground(-4, 8), "S9 Foto");
        Entity drone = drone(server, state);
        BoosterRig.load(drone, BoosterRig.rocket(3, 0));
        BoostFlight flight = BoosterRig.hover(drone, Arena.player(server), LOW_HOVER_METRES);
        state.put(FLIGHT, flight);
        photoFlight = flight;
        photoDrone = drone.getUUID();
    }

    private static Script photoBurn(String language, String photo) {
        return Script.create()
                .server("Kamera hinter der Drohne", BoosterSteps::chaseCamera)
                .sleep(10)
                .run("Sprache " + language, () -> Shots.language(Minecraft.getInstance(), language))
                .then("Bildschirmfoto " + photo + " 8 Ticks nach der Zündung", (ctx, ticks) -> Shots.shot(ctx, ticks, photo, BoosterSteps::photoIgnite))
                .server("Zündung ohne Sterne", (server, state) -> state.put(NOTE, photoIgnited(server, state)))
                .then("Zündung", BoosterSteps::note)
                .then("Brennschluss", BoosterSteps::burnedOut)
                .server("verpufft ohne Explosion", (server, state) -> state.put(NOTE, fizzled(state)))
                .then("verpufft", BoosterSteps::note);
    }

    private static void photoIgnite() {
        hideHud();
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        UUID id = photoDrone;
        if (server == null || id == null) {
            return;
        }
        server.execute(() -> {
            Entity drone = Arena.level(server).getEntity(id);
            if (drone != null && !drone.isRemoved() && !Boosters.burning(drone)) {
                sideCamera(server, drone);
                igniteLevel(server, drone);
            }
        });
    }

    private static void sideCamera(MinecraftServer server, Entity drone) {
        ServerPlayer player = Arena.player(server);
        DroneView view = BoosterRig.view(drone);
        Vec3 forward = new Vec3(view.forward().x, 0.0, view.forward().z);
        forward = forward.lengthSqr() > 1.0E-9 ? forward.normalize() : new Vec3(0.0, 0.0, -1.0);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 at = view.center().add(forward.scale(PHOTO_TRAVEL));
        Vec3 eye = at.add(right.scale(PHOTO_SIDE)).add(0.0, PHOTO_UP, 0.0);
        Vec3 look = at.subtract(forward.scale(PHOTO_CENTER_BACK)).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Vec3 feet = eye.subtract(0.0, player.getEyeHeight(), 0.0);
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void igniteLevel(MinecraftServer server, Entity drone) {
        BoostFlight flight = photoFlight;
        if (flight != null && drone.getUUID().equals(photoDrone)) {
            flight.level();
        }
        if (!BoosterRig.ignite(drone, Arena.player(server))) {
            throw new IllegalStateException("Booster zündet nicht");
        }
    }

    private static String photoIgnited(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        boolean shot = Boosters.burning(drone);
        if (!shot) {
            igniteLevel(server, drone);
        }
        BoostState data = BoostState.of(drone);
        long now = Arena.level(server).getGameTime();
        if (!data.active() || data.flight() != 3 || data.stars() != 0 || data.endsAt() - now > BoostProfile.burnTicks(3)) {
            throw new IllegalStateException("Drohnen-Daten passen nicht: " + BoosterRig.describe(data, now));
        }
        return (shot ? "beim Foto gezündet" : "gezündet") + ", noch " + (data.endsAt() - now) + " von " + BoostProfile.burnTicks(3) + " Ticks";
    }

    private static String fizzled(Map<String, Object> state) {
        Boosters.Ending ending = Boosters.lastEnding(droneId(state)).orElseThrow(() -> new IllegalStateException("kein Ende des Boosters"));
        if (ending.reason() != Boosters.Reason.BURNED_OUT || ending.pushes() != BoostProfile.burnTicks(3) || ending.blast().exploded()) {
            throw new IllegalStateException("Ende " + ending);
        }
        Boosters.forget(droneId(state));
        return "verpufft nach " + ending.pushes() + " Schüben ohne Explosion";
    }

    private static void stopAndReload(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        ((BoostFlight) state.get(FLIGHT)).hold(drone.getX(), drone.getZ());
        BoosterRig.load(drone, BoosterRig.rocket(3, 0));
    }

    private static void chaseCamera(MinecraftServer server, Map<String, Object> state) {
        chaseCamera(server, drone(server, state), CAMERA_LOOK_AHEAD);
    }

    private static void chaseCamera(MinecraftServer server, Entity drone, double lookAhead) {
        ServerPlayer player = Arena.player(server);
        DroneView view = BoosterRig.view(drone);
        Vec3 forward = new Vec3(view.forward().x, 0.0, view.forward().z);
        forward = forward.lengthSqr() > 1.0E-9 ? forward.normalize() : new Vec3(0.0, 0.0, -1.0);
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 at = view.center();
        Vec3 eye = at.subtract(forward.scale(CAMERA_BEHIND)).add(right.scale(CAMERA_SIDE)).add(0.0, CAMERA_UP, 0.0);
        Vec3 look = at.add(forward.scale(lookAhead)).subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        Vec3 feet = eye.subtract(0.0, player.getEyeHeight(), 0.0);
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void igniteStars(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        ServerLevel level = Arena.level(server);
        BoosterRig.load(drone, BoosterRig.rocket(3, 1));
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setPersistenceRequired();
        zombie.setSilent(true);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0, false, false));
        zombie.snapTo(drone.getX(), drone.getY() - Follow.TARGET_DOWN, drone.getZ(), 0.0F, 0.0F);
        Arena.spawn(level, zombie);
        state.put(ZOMBIE, zombie);
        state.put(HEALTH, zombie.getHealth());
        Follow.start(drone, zombie);
        state.put(NOTE, ignite(server, state, 3, 1));
    }

    private static String blasted(MinecraftServer server, Map<String, Object> state) {
        Follow.stop();
        UUID id = droneId(state);
        Boosters.Ending ending = Boosters.lastEnding(id).orElseThrow(() -> new IllegalStateException("kein Ende des Boosters"));
        Boosters.Blast blast = ending.blast();
        if (ending.reason() != Boosters.Reason.BURNED_OUT || !blast.exploded() || blast.damage() != BoostProfile.blastDamage(1)) {
            throw new IllegalStateException("keine Explosion: " + ending);
        }
        List<BoosterRig.Hit> hits = BoosterRig.hits(id);
        Optional<BoosterRig.Hit> fireworks = hits.stream().filter(BoosterRig.Hit::fireworks).findFirst();
        if (fireworks.isEmpty() || Math.abs(fireworks.get().amount() - BoostProfile.blastDamage(1)) > 0.01F || !blast.droneHurt()) {
            throw new IllegalStateException("Drohne nicht mit " + BoostProfile.blastDamage(1) + " Feuerwerks-Schaden getroffen: " + hits);
        }
        Zombie zombie = (Zombie) state.get(ZOMBIE);
        float before = (float) state.get(HEALTH);
        float lost = before - zombie.getHealth();
        double distance = Follow.lastDistance();
        float expected = BoostProfile.blastDamageAt(1, distance);
        if (blast.hurt() < 1 || !(lost > 0.0F) || Math.abs(lost - expected) > DAMAGE_TOLERANCE) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Zombie verliert %.2f statt etwa %.2f (Abstand %.2f m, %d getroffen)", lost, expected,
                    distance, blast.hurt()));
        }
        Entity drone = Arena.level(server).getEntity(id);
        boolean broken = drone == null || drone.isRemoved();
        return String.format(Locale.ROOT, "Sterne explodieren nach %d Schüben: Drohne %.0f Feuerwerks-Schaden (%s), Zombie in %.2f m −%.2f (Rechnung %.2f)",
                ending.pushes(), fireworks.get().amount(), broken ? "zerbricht, Propwash bricht ab 6" : "bleibt", distance, lost, expected);
    }

    private static void pilotScene(MinecraftServer server, Map<String, Object> state) {
        BoosterRig.listen();
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos ground = Arena.ground(3, 4);
        Vec3 stand = Vec3.atBottomCenterOf(ground.above()).add(0.0, 0.0, 3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), 180.0F, 15.0F, true);
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(GOGGLES));
        place(server, state, ground, "S9 Pilot");
        Entity drone = drone(server, state);
        BoosterRig.load(drone, BoosterRig.rocket(3, 1));
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setSilent(true);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0, false, false));
        Vec3 forward = BoosterRig.view(drone).forward();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        side = side.lengthSqr() > 1.0E-9 ? side.normalize().scale(PILOT_ZOMBIE_SIDE) : new Vec3(PILOT_ZOMBIE_SIDE, 0.0, 0.0);
        zombie.snapTo(drone.getX() + side.x, drone.getY(), drone.getZ() + side.z, 0.0F, 0.0F);
        Arena.spawn(level, zombie);
        state.put(ZOMBIE, zombie);
        state.put(HEALTH, zombie.getHealth());
    }

    private static void bind(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(server, state);
        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
    }

    private static void startFlight(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(server, state);
        player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
        Optional<UUID> pilot = BoosterRig.view(drone).pilot();
        if (pilot.isEmpty() || !pilot.get().equals(player.getUUID())) {
            throw new IllegalStateException("Flug nicht gestartet (Pilot " + pilot.map(UUID::toString).orElse("keiner") + ")");
        }
    }

    private static String release(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(server, state);
        if (!Boosters.burning(drone)) {
            throw new IllegalStateException("Booster brennt vor dem Aussteigen nicht");
        }
        long now = Arena.level(server).getGameTime();
        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
        player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
        if (BoosterRig.view(drone).pilot().isPresent()) {
            throw new IllegalStateException("Pilot bleibt nach dem Aussteigen");
        }
        if (Boosters.burning(drone) || BoostState.of(drone).active()) {
            throw new IllegalStateException("Booster brennt nach dem Pilotenwechsel weiter: " + BoosterRig.describe(BoostState.of(drone), now));
        }
        Boosters.Ending ending = Boosters.lastEnding(drone.getUUID()).orElseThrow(() -> new IllegalStateException("kein Ende des Boosters"));
        if (ending.reason() != Boosters.Reason.PILOT_CHANGED || ending.blast().exploded()) {
            throw new IllegalStateException("Ende " + ending);
        }
        if (!BoosterRig.booster(drone).get(BoosterBehavior.SLOT).isEmpty()) {
            throw new IllegalStateException("Rakete bleibt nach dem Abbruch im Slot");
        }
        return "PILOT_CHANGED beendet den Booster sofort nach " + ending.pushes() + " von " + BoostProfile.burnTicks(3) + " Schüben, Rakete verbraucht";
    }

    private static String quiet(MinecraftServer server, Map<String, Object> state) {
        UUID id = droneId(state);
        Boosters.Ending ending = Boosters.lastEnding(id).orElseThrow(() -> new IllegalStateException("kein Ende des Boosters"));
        if (ending.reason() != Boosters.Reason.PILOT_CHANGED) {
            throw new IllegalStateException("Ende überschrieben: " + ending);
        }
        if (BoosterRig.hits(id).stream().anyMatch(BoosterRig.Hit::fireworks)) {
            throw new IllegalStateException("Drohne nach dem Abbruch von Feuerwerk getroffen");
        }
        Zombie zombie = (Zombie) state.get(ZOMBIE);
        float before = (float) state.get(HEALTH);
        if (zombie.getHealth() < before) {
            throw new IllegalStateException("Zombie nach dem Abbruch getroffen: " + zombie.getHealth() + " statt " + before);
        }
        Entity drone = Arena.level(server).getEntity(id);
        if (drone == null || drone.isRemoved()) {
            throw new IllegalStateException("Drohne nach dem Abbruch zerstört");
        }
        return "nach der vollen Brenndauer keine Explosion: Drohne heil, Zombie " + zombie.getHealth() + "/" + before;
    }

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hudHidden = true;
        }
    }

    private static void restoreHud() {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hudHidden = false;
    }

    private static final SelfTest.ClientStep CLEANUP_SCRIPT = Script.create()
            .run("HUD zurück", BoosterSteps::restoreHud)
            .server("Folgen aus, Flug beenden", (server, state) -> {
                Follow.stop();
                ServerPlayer player = Arena.player(server);
                for (Entity drone : Arena.drones(player.level())) {
                    PropwashApi.drones().autonomy().stop(drone);
                    Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
                    if (pilot.isPresent() && pilot.get().equals(player.getUUID())) {
                        player.setItemInHand(InteractionHand.MAIN_HAND, Arena.stack(REMOTE));
                        player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
                    }
                }
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            });

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP_SCRIPT.tick(ctx);
    }
}
