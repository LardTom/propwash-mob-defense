package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class CovMdBTwoPilotsScene {
    public static final String ENV = "MOBDEFENSE_JOINTEST_COV_TWO_PILOTS";
    public static final String RESULT_FILE = "mobdefense-cov-two-pilots.txt";
    public static final int ZOMBIES = 10;
    public static final int ARM_TICKS = 600;
    public static final int REACT_TICKS = 600;
    public static final int REACT_PERIOD = 20;
    public static final int SHOW_TICKS = 40;
    public static final double APPROACH = 1.0;
    private static final String TAG = "[Mob-Defense-Zwei-Piloten]";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static final List<Entity> DRONES = new ArrayList<>();
    private static final List<ServerPlayer> PILOTS = new ArrayList<>();
    private static final List<Mob> MOBS = new ArrayList<>();
    private static final List<String> LEFT = new ArrayList<>();
    private static Stage stage = Stage.WAIT;
    private static int stageTicks;
    private static int armedAfter = -1;
    private static int reactedAfter = -1;
    private static int approachedAfter = -1;
    private static double startDistance = Double.NaN;
    private static double distance = Double.NaN;
    private static @Nullable Spread reaction;

    private enum Stage { WAIT, ARM, REACT, SHOWN, DONE }

    private CovMdBTwoPilotsScene() {
    }

    public record Spread(int first, int second, int free) {
        public boolean both() {
            return this.first > 0 && this.second > 0;
        }

        public String describe() {
            return this.first + " Zombies an Drohne A, " + this.second + " an Drohne B, " + this.free + " ohne Drohne";
        }
    }

    public static boolean enabled() {
        String value = System.getenv(ENV);
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true"));
    }

    public static BuildSpec build() {
        return DispenserRig.fiveInch().withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT);
    }

    public static void startFlight(ServerPlayer player, Entity drone) {
        player.setItemSlot(EquipmentSlot.HEAD, DispenserRig.item("propwash:goggles_creative", 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, DispenserRig.item("propwash:remote", 1));
        player.interactOn(drone, InteractionHand.MAIN_HAND, drone.position());
        player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
        Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
        if (pilot.isEmpty() || !pilot.get().equals(player.getUUID())) {
            throw new IllegalStateException(player.getGameProfile().name() + " fliegt die Drohne nicht (Pilot " + pilot.map(UUID::toString).orElse("keiner") + ")");
        }
    }

    public static void stopFlight(ServerPlayer player, Entity drone) {
        Optional<UUID> pilot = PropwashApi.drones().view(drone).flatMap(DroneView::pilot);
        if (pilot.isPresent() && pilot.get().equals(player.getUUID())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, DispenserRig.item("propwash:remote", 1));
            player.getItemInHand(InteractionHand.MAIN_HAND).use(player.level(), player, InteractionHand.MAIN_HAND);
        }
    }

    public static List<Mob> zombies(ServerLevel level, Entity first, Entity second, Consumer<Entity> track) {
        List<Mob> out = new ArrayList<>();
        Vec3 a = first.position();
        Vec3 b = second.position();
        for (int i = 0; i < ZOMBIES; i++) {
            double share = (i + 0.5) / ZOMBIES;
            Vec3 at = a.add(b.subtract(a).scale(share)).add(0.0, 0.0, 5.0 + (i % 2));
            Zombie zombie = MobScene.spawn(level, EntityTypes.ZOMBIE, new Vec3(at.x, Math.floor(a.y), at.z), 180.0F);
            track.accept(zombie);
            out.add(zombie);
        }
        return out;
    }

    public static Spread spread(ServerLevel level, Entity first, Entity second) {
        int a = 0;
        int b = 0;
        int free = 0;
        AABB box = first.getBoundingBox().minmax(second.getBoundingBox()).inflate(24.0);
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, box, Zombie::isAlive)) {
            Entity drone = DroneReactions.assigned(zombie).orElse(null);
            if (drone == first) {
                a++;
            } else if (drone == second) {
                b++;
            } else {
                free++;
            }
        }
        return new Spread(a, b, free);
    }

    public static String prepare(MinecraftServer server) {
        if (INSTALLED.compareAndSet(false, true)) {
            MobDefense.onServerTick(CovMdBTwoPilotsScene::tick);
        }
        return "wartet auf zwei Spieler (" + ENV + "), Ergebnis in " + RESULT_FILE;
    }

    private static void tick(MinecraftServer server) {
        switch (stage) {
            case WAIT -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.size() >= 2) {
                    setup(server, players.get(0), players.get(1));
                }
            }
            case ARM -> {
                stageTicks++;
                String gone = gone(server, "beim Scharfschalten");
                if (gone != null) {
                    finish(server, gone);
                } else if (armed()) {
                    armedAfter = stageTicks;
                    next(Stage.REACT);
                } else if (stageTicks >= ARM_TICKS) {
                    finish(server, "Drohnen nach " + ARM_TICKS + " Ticks nicht beide scharf: " + armedNote());
                }
            }
            case REACT -> {
                stageTicks++;
                String gone = gone(server, "während der Mob-Reaktion");
                if (gone != null) {
                    finish(server, gone);
                } else if (!armed()) {
                    finish(server, "Drohne während der Mob-Reaktion nicht mehr scharf: " + armedNote());
                } else if (stageTicks % REACT_PERIOD == 0) {
                    reaction = spread(server.overworld(), DRONES.get(0), DRONES.get(1));
                    distance = assignedDistance();
                    if (reaction.both() && reactedAfter < 0) {
                        reactedAfter = stageTicks;
                    }
                    if (reaction.both() && startDistance - distance >= APPROACH) {
                        approachedAfter = stageTicks;
                        finish(server, null);
                    } else if (stageTicks >= REACT_TICKS) {
                        finish(server, reaction.both() ? "Zombies nähern sich nach " + REACT_TICKS + " Ticks nicht um " + APPROACH + " m: " + approachNote()
                                : "Zombies verteilen sich nach " + REACT_TICKS + " Ticks nicht auf beide Drohnen: " + reaction.describe());
                    }
                }
            }
            case SHOWN -> {
                stageTicks++;
                departures(server);
                if (stageTicks >= SHOW_TICKS) {
                    for (int i = 0; i < PILOTS.size(); i++) {
                        if (connected(server, PILOTS.get(i))) {
                            stopFlight(PILOTS.get(i), DRONES.get(i));
                        }
                    }
                    MOBS.forEach(Entity::discard);
                    next(Stage.DONE);
                }
            }
            case DONE -> {
                stageTicks++;
                departures(server);
            }
        }
    }

    private static void next(Stage value) {
        stage = value;
        stageTicks = 0;
    }

    private static void setup(MinecraftServer server, ServerPlayer first, ServerPlayer second) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().globalPos().pos();
        try {
            new Rules(server).set(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT, 0);
            List<ServerPlayer> pilots = List.of(first, second);
            for (int i = 0; i < 2; i++) {
                int x = spawn.getX() + (i == 0 ? -6 : 6);
                int z = spawn.getZ() + 8;
                BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
                ServerPlayer pilot = pilots.get(i);
                pilot.setGameMode(GameType.CREATIVE);
                pilot.setYRot(-90.0F);
                Entity drone = DispenserRig.place(level, pilot, build(), "Zwei Piloten " + (i == 0 ? "A" : "B"), ground);
                DispenserRig.load(drone, new ItemStack(i == 0 ? Items.ARROW : Items.SNOWBALL, 16));
                Vec3 stand = drone.position().add(0.0, 0.0, -3.0);
                pilot.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), -90.0F, 10.0F, true);
                startFlight(pilot, drone);
                DRONES.add(drone);
                PILOTS.add(pilot);
            }
            MOBS.addAll(zombies(level, DRONES.get(0), DRONES.get(1), entity -> {
            }));
            startDistance = nearestDistance();
            next(Stage.ARM);
            MobDefense.LOG.info("{} Szene: Drohne A für {}, Drohne B für {}, {} Zombies", TAG, first.getGameProfile().name(), second.getGameProfile().name(),
                    MOBS.size());
        } catch (RuntimeException e) {
            next(Stage.DONE);
            write("FAIL Zwei Piloten: Aufbau " + e.getMessage());
        }
    }

    private static boolean connected(MinecraftServer server, ServerPlayer pilot) {
        return server.getPlayerList().getPlayer(pilot.getUUID()) == pilot;
    }

    private static @Nullable String gone(MinecraftServer server, String when) {
        List<String> names = new ArrayList<>();
        for (ServerPlayer pilot : PILOTS) {
            if (!connected(server, pilot)) {
                names.add(pilot.getGameProfile().name());
            }
        }
        return names.isEmpty() ? null : "Pilot " + String.join(" und ", names) + " " + when + " nach " + stageTicks + " Ticks getrennt, vor dem Ergebnis";
    }

    private static void departures(MinecraftServer server) {
        for (ServerPlayer pilot : PILOTS) {
            String name = pilot.getGameProfile().name();
            if (!LEFT.contains(name) && !connected(server, pilot)) {
                LEFT.add(name);
                append("getrennt nach dem Ergebnis: " + name + " nach " + (stage == Stage.SHOWN ? stageTicks : SHOW_TICKS + stageTicks) + " Ticks");
            }
        }
    }

    private static void finish(MinecraftServer server, @Nullable String problem) {
        ServerLevel level = server.overworld();
        List<String> problems = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (problem != null) {
            problems.add(problem);
        }
        notes.add(armedNote() + (armedAfter < 0 ? "" : ", beide scharf nach " + armedAfter + " Ticks"));
        Spread spread = reaction == null ? spread(level, DRONES.get(0), DRONES.get(1)) : reaction;
        notes.add((reactedAfter < 0 ? "ohne Mob-Reaktion: " : "Mob-Reaktion nach " + reactedAfter + " Ticks: ") + spread.describe());
        notes.add(approachNote());
        boolean present = gone(server, "") == null;
        notes.add(present ? "beide Piloten bis zum Ergebnis verbunden" : "nicht beide Piloten verbunden");
        for (int i = 0; i < 2 && present; i++) {
            Entity own = DRONES.get(i);
            Entity other = DRONES.get(1 - i);
            ServerPlayer pilot = PILOTS.get(i);
            int before = DispenserLog.counts(other).attempts();
            DispenserLog.Entry entry = DispenserRig.fire(own, pilot).orElse(null);
            int otherShots = DispenserLog.counts(other).attempts() - before;
            Entity shot = entry == null || entry.result() == null ? null : entry.result().entity().orElse(null);
            boolean owned = shot instanceof Projectile projectile && projectile.getOwner() == pilot;
            String label = (i == 0 ? "A" : "B") + " (" + pilot.getGameProfile().name() + ")";
            if (entry == null || !owned || otherShots != 0) {
                problems.add(label + ": " + (entry == null ? "kein Schuss" : entry.status()) + ", Geschoss " + shot + ", andere Drohne " + otherShots
                        + " Schüsse");
            } else {
                notes.add(label + ": " + shot.getType().toShortString() + " mit Besitzer " + pilot.getGameProfile().name() + ", andere Drohne 0 Schüsse");
            }
        }
        write((problems.isEmpty() ? "PASS Zwei Piloten: " : "FAIL Zwei Piloten: " + String.join("; ", problems) + " | ") + String.join(" | ", notes));
        for (ServerPlayer pilot : PILOTS) {
            if (!connected(server, pilot)) {
                LEFT.add(pilot.getGameProfile().name());
            }
        }
        next(Stage.SHOWN);
    }

    private static double nearestDistance() {
        double sum = 0.0;
        for (Mob mob : MOBS) {
            sum += Math.min(mob.distanceTo(DRONES.get(0)), mob.distanceTo(DRONES.get(1)));
        }
        return MOBS.isEmpty() ? Double.NaN : sum / MOBS.size();
    }

    private static double assignedDistance() {
        double sum = 0.0;
        int count = 0;
        for (Mob mob : MOBS) {
            Entity drone = mob.isAlive() ? DroneReactions.assigned(mob).orElse(null) : null;
            if (drone != null && DRONES.contains(drone)) {
                sum += mob.distanceTo(drone);
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static String approachNote() {
        String from = String.format(Locale.ROOT, "%.1f", startDistance);
        String to = Double.isNaN(distance) ? "-" : String.format(Locale.ROOT, "%.1f", distance);
        return (approachedAfter < 0 ? "Zombies nähern sich nicht: " : "Zombies nähern sich nach " + approachedAfter + " Ticks: ") + "mittlerer Abstand zur Drohne "
                + from + " → " + to + " m";
    }

    private static boolean armed() {
        for (Entity drone : DRONES) {
            if (!PropwashApi.drones().view(drone).map(DroneView::armed).orElse(false)) {
                return false;
            }
        }
        return true;
    }

    private static String armedNote() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < DRONES.size(); i++) {
            boolean armed = PropwashApi.drones().view(DRONES.get(i)).map(DroneView::armed).orElse(false);
            out.add((i == 0 ? "A" : "B") + (armed ? " scharf" : " nicht scharf"));
        }
        return String.join(", ", out);
    }

    private static void write(String line) {
        save(line, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void append(String line) {
        save(line, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
    }

    private static void save(String line, OpenOption... options) {
        MobDefense.LOG.info("{} {}", TAG, line);
        try {
            Files.writeString(Services.PLATFORM.gameDir().resolve(RESULT_FILE), line + System.lineSeparator(), StandardCharsets.UTF_8, options);
        } catch (IOException e) {
            MobDefense.LOG.error("{} {} nicht geschrieben", TAG, RESULT_FILE, e);
        }
    }
}
