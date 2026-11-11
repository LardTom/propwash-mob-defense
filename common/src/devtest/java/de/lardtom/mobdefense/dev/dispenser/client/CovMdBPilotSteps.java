package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.OsdReader;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.CovMdBDrift;
import de.lardtom.mobdefense.dev.dispenser.CovMdBLanes;
import de.lardtom.mobdefense.dev.dispenser.CovMdBTwoPilotsScene;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.Guests;
import de.lardtom.mobdefense.dev.dispenser.Rules;
import de.lardtom.mobdefense.dev.mob.MobScene;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CovMdBPilotSteps {
    public static final double TARGET_DISTANCE = 2.5;
    public static final float TARGET_HEALTH = 3.0F;
    public static final float SPEAR_TARGET_HEALTH = 5.0F;
    public static final int MAX_ARROWS = 5;
    private static final String NOTE = "covmdb.pilot.note";
    private static final String GUEST = "covmdb.pilot.guest";
    private static final String ARROWS = "covmdb.pilot.arrows";
    private static final String SECOND = "covmdb.pilot.second";
    private static final String DRIFT = "covmdb.pilot.drift";
    private static final String FLYING = "covmdb.pilot.flying";

    private CovMdBPilotSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("Pilot als Verursacher – Testspieler mit Pfeil und Speer besiegt, DamageSource#getEntity ist der Pilot, die Todesnachricht nennt ihn",
                3000, Script.create()
                        .server("Drohne mit Dispenser und Speer auf dem Sockel", CovMdBPilotSteps::scene)
                        .append(Pilot.fly())
                        .server("payloads_hit_players und PvP an, Testspieler vor der Mündung", CovMdBPilotSteps::target)
                        .then("Pfeile bis der Testspieler fällt", CovMdBPilotSteps::arrows)
                        .server("Pfeil: Verursacher und Todesnachricht", (server, state) -> state.put(NOTE, arrowKill(server, state)))
                        .then("Pfeil", CovMdBPilotSteps::note)
                        .server("Speer: Treffer mit 15 m/s am zweiten Testspieler", (server, state) -> state.put(NOTE, spearKill(server, state)))
                        .then("Speer", CovMdBPilotSteps::note), CovMdBPilotSteps::cleanup);
        steps.add("Zwei Piloten (simuliert) – Aktion nur an der eigenen Drohne, OSD je Pilot, Zombies verteilen sich auf beide Drohnen", 3600,
                Script.create()
                        .server("zwei Drohnen, die zweite fliegt ein Testspieler", CovMdBPilotSteps::twoScene)
                        .append(Pilot.fly())
                        .then("Drohne A am Boden scharf schalten", Pilot::arm)
                        .server("Drohne B fliegt für den zweiten Spieler", CovMdBPilotSteps::secondFlying)
                        .then("Drohne B", CovMdBPilotSteps::note)
                        .then("beide Drohnen fliegen, B schwebt ruhig", CovMdBPilotSteps::bothFlying)
                        .server("je fünf Zombies vor jeder Drohne", CovMdBPilotSteps::zombiesAtEach)
                        .then("Zombies verteilen sich", CovMdBPilotSteps::spread)
                        .server("Zähler vorher", CovMdBPilotSteps::countsBefore)
                        .append(Pilot.press(Pilot.ACTION_1, 2))
                        .server("Aktion 1 am Client: nur die eigene Drohne", (server, state) -> state.put(NOTE, ownOnly(server, state)))
                        .then("eigene Aktion", CovMdBPilotSteps::note)
                        .sleep(6)
                        .server("Aktion 1 des zweiten Piloten", (server, state) -> state.put(NOTE, secondOnly(server, state)))
                        .then("Aktion des zweiten Piloten", CovMdBPilotSteps::note)
                        .then("OSD je Pilot", CovMdBPilotSteps::osd), CovMdBPilotSteps::cleanup);
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static void scene(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        BlockPos ground = Arena.ground(-12, -24);
        Entity drone = CovMdBLanes.pillarDrone(server, ground, 1, DispenserRig.fiveInch().withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT),
                "CovMdB Pilot");
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 16));
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(Pilot.GOGGLES));
        Vec3 stand = drone.position().add(-2.0, 0.0, 2.0);
        player.teleportTo(Arena.level(server), stand.x, stand.y, stand.z, Set.<Relative>of(), 0.0F, 0.0F, true);
        state.put(Pilot.DRONE, drone.getUUID());
    }

    private static ServerPlayer pilot(MinecraftServer server, Entity drone) {
        UUID id = DispenserRig.view(drone).pilot().orElseThrow(() -> new IllegalStateException("Drohne ohne Pilot"));
        ServerPlayer pilot = server.getPlayerList().getPlayer(id);
        if (pilot == null) {
            throw new IllegalStateException("Pilot " + id + " nicht am Server");
        }
        return pilot;
    }

    private static ServerPlayer guest(MinecraftServer server, Entity drone, String name, double distance, float health) {
        Vec3 at = DispenserRig.ahead(drone, distance);
        ServerPlayer guest = Guests.place(server, name, Arena.level(server), new Vec3(at.x, Arena.ground().getY() + 1.0, at.z),
                CovMdBLanes.FACING.getOpposite().toYRot());
        Guests.ready(guest, health);
        return guest;
    }

    private static void target(MinecraftServer server, Map<String, Object> state) {
        new Rules(server).set(RuleDefaults.PAYLOADS_HIT_PLAYERS, true).set(Rules.PVP, true);
        Entity drone = Pilot.serverDrone(server, state);
        pilot(server, drone);
        state.put(GUEST, guest(server, drone, "md_cov_ziel", TARGET_DISTANCE, TARGET_HEALTH));
        state.put(ARROWS, 0);
    }

    private static StepResult arrows(SelfTest.StepContext ctx, int ticks) {
        if (ticks % 8 != 0) {
            return StepResult.pending();
        }
        Map<String, Object> state = ctx.state();
        ServerPlayer guest = (ServerPlayer) state.get(GUEST);
        if (guest.isDeadOrDying()) {
            return StepResult.pass("Testspieler nach " + state.get(ARROWS) + " Pfeilen besiegt");
        }
        int fired = (int) state.get(ARROWS);
        if (fired >= MAX_ARROWS) {
            return StepResult.fail("Testspieler lebt nach " + fired + " Pfeilen (" + guest.getHealth() + ")");
        }
        state.put(ARROWS, fired + 1);
        ctx.onServer(server -> DispenserRig.fire(Pilot.serverDrone(server, state), Arena.player(server)));
        return StepResult.pending();
    }

    private static String deathMessage(ServerPlayer victim, DamageSource source) {
        String tracker = victim.getCombatTracker().getDeathMessage().getString();
        String own = source.getLocalizedDeathMessage(victim).getString();
        return tracker.equals(own) ? own : own + " (Kampf-Protokoll: " + tracker + ")";
    }

    private static String arrowKill(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        ServerPlayer pilot = pilot(server, drone);
        ServerPlayer guest = (ServerPlayer) state.get(GUEST);
        DamageSource source = guest.getLastDamageSource();
        if (source == null || source.getEntity() != pilot || !(source.getDirectEntity() instanceof AbstractArrow)) {
            throw new IllegalStateException("Schadensquelle " + describe(source) + " statt Pfeil vom Piloten " + pilot.getGameProfile().name());
        }
        String message = deathMessage(guest, source);
        if (!message.contains(pilot.getGameProfile().name())) {
            throw new IllegalStateException("Todesnachricht nennt den Piloten nicht: " + message);
        }
        return "Pfeil: Verursacher " + pilot.getGameProfile().name() + " (Pilot), direkt " + describe(source) + ", Todesnachricht „" + message + "“";
    }

    private static String spearKill(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        ServerPlayer pilot = pilot(server, drone);
        ServerLevel level = Arena.level(server);
        ServerPlayer guest = guest(server, drone, "md_cov_ziel_speer", 1.0, SPEAR_TARGET_HEALTH);
        DroneView view = DispenserRig.view(drone);
        Vec3 nose = new Vec3(view.forward().x, 0.0, view.forward().z).normalize();
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
        ItemStack spear = PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().get(0);
        SpearStrikes.Hit hit = SpearStrikes.stab(level, drone, pilot, spear, guest, nose, nose.scale(15.0));
        DamageSource source = guest.getLastDamageSource();
        if (!guest.isDeadOrDying() || source == null || source.getEntity() != pilot || source.getDirectEntity() != drone) {
            throw new IllegalStateException("Speer: " + hit + ", Testspieler " + guest.getHealth() + ", Quelle " + describe(source));
        }
        String message = deathMessage(guest, source);
        if (!message.contains(pilot.getGameProfile().name())) {
            throw new IllegalStateException("Todesnachricht nennt den Piloten nicht: " + message);
        }
        return String.format(Locale.ROOT, "Speer: %.1f Schaden, Verursacher %s (Pilot), direkt die Drohne, Schadensart %s, Todesnachricht „%s“", hit.dealt(),
                pilot.getGameProfile().name(), source.typeHolder().getRegisteredName(), message);
    }

    private static String describe(@Nullable DamageSource source) {
        if (source == null) {
            return "keine";
        }
        return source.typeHolder().getRegisteredName() + ", Verursacher " + (source.getEntity() == null ? "keiner" : source.getEntity().getName().getString())
                + ", direkt " + (source.getDirectEntity() == null ? "keiner" : source.getDirectEntity().getType().toShortString());
    }

    private static Entity[] twoDrones(MinecraftServer server, Map<String, Object> state) {
        return new Entity[]{Pilot.serverDrone(server, state), Arena.level(server).getEntity((UUID) state.get(SECOND))};
    }

    private static void twoScene(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        new Rules(server).set(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT, 0);
        float yaw = CovMdBLanes.FACING.toYRot();
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        Entity own = Arena.track(DispenserRig.place(level, player, CovMdBTwoPilotsScene.build(), "CovMdB Pilot A", Arena.ground(-6, -44)));
        Entity other = Arena.track(DispenserRig.place(level, player, CovMdBTwoPilotsScene.build(), "CovMdB Pilot B", Arena.ground(6, -44)));
        DispenserRig.load(own, new ItemStack(Items.ARROW, 16));
        DispenserRig.load(other, new ItemStack(Items.SNOWBALL, 16));
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(Pilot.GOGGLES));
        Vec3 stand = own.position().add(0.0, 0.0, 3.0);
        player.teleportTo(level, stand.x, stand.y, stand.z, Set.<Relative>of(), yaw, 10.0F, true);
        ServerPlayer guest = Guests.place(server, "md_cov_pilot_b", level, other.position().add(0.0, 0.0, 3.0), yaw);
        guest.setGameMode(GameType.CREATIVE);
        CovMdBTwoPilotsScene.startFlight(guest, other);
        state.put(Pilot.DRONE, own.getUUID());
        state.put(SECOND, other.getUUID());
        state.put(GUEST, guest);
    }

    private static void secondFlying(MinecraftServer server, Map<String, Object> state) {
        Entity[] drones = twoDrones(server, state);
        ServerPlayer guest = (ServerPlayer) state.get(GUEST);
        DroneView view = DispenserRig.view(drones[1]);
        boolean piloted = view.pilot().filter(id -> id.equals(guest.getUUID())).isPresent();
        if (view.flying() && piloted) {
            state.put(NOTE, "Drohne B: Pilotensitzung des Testspielers md_cov_pilot_b läuft");
            return;
        }
        CovMdBTwoPilotsScene.stopFlight(guest, drones[1]);
        state.put(DRIFT, CovMdBDrift.start(drones[1], Arena.player(server), 2.0));
        state.put(NOTE, "Drohne B: Pilotensitzung des Testspielers ohne Client " + (piloted ? "fliegt nicht" : "endet sofort") + ", deshalb autonomer "
                + "Schwebeflug mit dem Spieler des Clients als Besitzer (md_cov_pilot_b steht in keiner Spielerliste, als Besitzer endet der Flug "
                + "nach dem Start mit OWNER_OFFLINE), Aktionen an Drohne B löst md_cov_pilot_b aus");
    }

    private static StepResult bothFlying(SelfTest.StepContext ctx, int ticks) {
        Object seen = ctx.state().get(FLYING);
        boolean steady = !(ctx.state().get(DRIFT) instanceof CovMdBDrift drift) || drift.settled();
        if (seen instanceof String text && text.startsWith("A fliegt, B fliegt;") && steady) {
            return StepResult.pass(text + ", nach " + ticks + " Ticks");
        }
        if (ticks > 400) {
            return StepResult.fail("Drohnen fliegen nicht beide: " + seen + (steady ? "" : ", B schwebt nicht ruhig"));
        }
        ctx.onServer(server -> ctx.state().put(FLYING, flying(twoDrones(server, ctx.state()))));
        return StepResult.pending();
    }

    private static String flying(Entity[] drones) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < drones.length; i++) {
            out.append(i == 0 ? "" : ", ").append(i == 0 ? "A" : "B").append(DispenserRig.view(drones[i]).flying() ? " fliegt" : " fliegt nicht");
        }
        out.append(';');
        for (int i = 0; i < drones.length; i++) {
            DroneView view = DispenserRig.view(drones[i]);
            out.append(String.format(Locale.ROOT, " %s bei (%.1f; %.1f; %.1f)%s%s", i == 0 ? "A" : "B", view.center().x, view.center().y, view.center().z,
                    view.pilot().isPresent() ? " mit Pilot" : "", view.autopilot() ? " autonom" : ""));
        }
        return out.toString();
    }

    private static void zombiesAtEach(MinecraftServer server, Map<String, Object> state) {
        Entity[] drones = twoDrones(server, state);
        ServerLevel level = Arena.level(server);
        double y = Arena.ground().getY() + 1.0;
        int each = CovMdBTwoPilotsScene.ZOMBIES / drones.length;
        for (Entity drone : drones) {
            for (int i = 0; i < each; i++) {
                double dx = i - (each - 1) / 2.0;
                Arena.track(MobScene.spawn(level, EntityTypes.ZOMBIE, new Vec3(drone.getX() + dx, y, drone.getZ() + 5.0 + (i % 2)), 180.0F));
            }
        }
    }

    private static StepResult spread(SelfTest.StepContext ctx, int ticks) {
        if (ticks % 20 != 0) {
            return StepResult.pending();
        }
        Object last = ctx.state().get("covmdb.pilot.spread");
        if (last instanceof CovMdBTwoPilotsScene.Spread spread && spread.both()) {
            return StepResult.pass(spread.describe());
        }
        if (ticks > 600) {
            return StepResult.fail("Zombies verteilen sich nicht: " + (last instanceof CovMdBTwoPilotsScene.Spread spread ? spread.describe() : last) + "; "
                    + ctx.state().get(FLYING));
        }
        ctx.onServer(server -> {
            Entity[] drones = twoDrones(server, ctx.state());
            ctx.state().put("covmdb.pilot.spread", CovMdBTwoPilotsScene.spread(Arena.level(server), drones[0], drones[1]));
            ctx.state().put(FLYING, flying(drones));
        });
        return StepResult.pending();
    }

    private static void countsBefore(MinecraftServer server, Map<String, Object> state) {
        Entity[] drones = twoDrones(server, state);
        state.put("covmdb.pilot.a", DispenserLog.counts(drones[0]).attempts());
        state.put("covmdb.pilot.b", DispenserLog.counts(drones[1]).attempts());
    }

    private static String ownOnly(MinecraftServer server, Map<String, Object> state) {
        Entity[] drones = twoDrones(server, state);
        int a = DispenserLog.counts(drones[0]).attempts() - (int) state.get("covmdb.pilot.a");
        int b = DispenserLog.counts(drones[1]).attempts() - (int) state.get("covmdb.pilot.b");
        DispenserLog.Entry entry = DispenserLog.last(drones[0]).orElse(null);
        Entity shot = entry == null || entry.result() == null ? null : entry.result().entity().orElse(null);
        ServerPlayer pilot = Arena.player(server);
        if (a < 1 || b != 0 || !(shot instanceof AbstractArrow arrow) || arrow.getOwner() != pilot) {
            throw new IllegalStateException("Aktion 1 am Client: Drohne A " + a + " Schüsse, Drohne B " + b + ", Geschoss " + shot);
        }
        state.put("covmdb.pilot.a", DispenserLog.counts(drones[0]).attempts());
        return "Taste Aktion 1 des Clients: Drohne A " + a + " Pfeil(e), Besitzer " + pilot.getGameProfile().name() + ", Drohne B 0 Schüsse";
    }

    private static String secondOnly(MinecraftServer server, Map<String, Object> state) {
        Entity[] drones = twoDrones(server, state);
        ServerPlayer guest = (ServerPlayer) state.get(GUEST);
        Optional<DispenserLog.Entry> entry = DispenserRig.fire(drones[1], guest);
        int a = DispenserLog.counts(drones[0]).attempts() - (int) state.get("covmdb.pilot.a");
        Entity shot = entry.map(DispenserLog.Entry::result).flatMap(result -> result.entity()).orElse(null);
        if (entry.isEmpty() || a != 0 || shot == null || !(shot instanceof Projectile projectile)
                || projectile.getOwner() != guest) {
            throw new IllegalStateException("Aktion 1 des zweiten Piloten: " + entry.map(DispenserLog.Entry::status).orElse("kein Schuss") + ", Drohne A " + a
                    + ", Geschoss " + shot);
        }
        return "Aktion 1 des zweiten Piloten: Drohne B " + shot.getType().toShortString() + ", Besitzer md_cov_pilot_b, Drohne A 0 Schüsse";
    }

    private static StepResult osd(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Entity> own = PropwashClientApi.input().drone();
        if (own.isEmpty() || mc.level == null) {
            return ticks > 60 ? StepResult.fail("keine eigene Drohne am Client") : StepResult.pending();
        }
        UUID second = (UUID) ctx.state().get(SECOND);
        Entity other = null;
        for (Entity entity : Arena.clientDrones(mc)) {
            if (entity.getUUID().equals(second)) {
                other = entity;
            }
        }
        if (other == null) {
            return ticks > 60 ? StepResult.fail("zweite Drohne fehlt am Client") : StepResult.pending();
        }
        long now = mc.level.getGameTime();
        OsdText mine = MobDefenseOsd.dispenser(OsdReader.read(own.get(), now));
        OsdText theirs = MobDefenseOsd.dispenser(OsdReader.read(other, now + 1));
        String a = mine == null ? "-" : mine.text();
        String b = theirs == null ? "-" : theirs.text();
        OsdReader.forget();
        if (!a.contains("ARW") || a.contains("SNW") || !b.contains("SNW")) {
            return ticks > 100 ? StepResult.fail("OSD eigene Drohne „" + a + "“, zweite Drohne „" + b + "“") : StepResult.pending();
        }
        return StepResult.pass("OSD des Clients „" + a + "“ (eigene Pfeile), die zweite Drohne würde „" + b + "“ zeigen");
    }

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        int since = (int) ctx.state().computeIfAbsent("covmdb.pilot.cleanup", key -> ctx.tick());
        if (ctx.tick() == since) {
            Pilot.land(Minecraft.getInstance());
        }
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            Pilot.release(server);
            Object guest = state.get(GUEST);
            Object second = state.get(SECOND);
            if (guest instanceof ServerPlayer player && second instanceof UUID id) {
                Entity drone = Arena.level(server).getEntity(id);
                if (drone != null) {
                    CovMdBTwoPilotsScene.stopFlight(player, drone);
                    PropwashApi.drones().autonomy().stop(drone);
                }
            }
            Guests.removeAll();
        });
    }
}
