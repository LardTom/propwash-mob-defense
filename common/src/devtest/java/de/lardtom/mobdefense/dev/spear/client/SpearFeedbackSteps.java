package de.lardtom.mobdefense.dev.spear.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.OsdNotices;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.client.HoverAssist;
import de.lardtom.mobdefense.dev.dispenser.client.Pilot;
import de.lardtom.mobdefense.dev.spear.SpearChecks;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.spear.SpearFeedback;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SpearFeedbackSteps {
    public static final double BRISK_SPEED = 10.0;
    public static final double PILOT_DISTANCE = 30.0;
    public static final double TARGET_AHEAD = 1.4;
    public static final double HOVER_METRES = 1.2;
    public static final double BROADCAST_RANGE = 16.0;
    public static final int WAIT_TICKS = 20;
    public static final int STAB_GAP_TICKS = 25;
    public static final double MIN_AHEAD = 0.05;
    public static final String PHOTO = "spear_fpv_hit";
    private static final String HIT_PATH = "hit";
    private static final String DRONE_ID = "spear.feedback.drone";
    private static final String VILLAGER = "spear.feedback.villager";
    private static final String NOSE = "spear.feedback.nose";
    private static final String NOTES = "spear.feedback.notes";
    private static final String MISSING = "spear.feedback.missing";
    private static final List<Heard> SOUNDS = new CopyOnWriteArrayList<>();
    private static final SoundEventListener LISTENER = (sound, events, range) -> SOUNDS.add(new Heard(sound.getIdentifier().toString(), sound.getX(),
            sound.getY(), sound.getZ()));

    private SpearFeedbackSteps() {
    }

    private record Heard(String id, double x, double y, double z) {
    }

    public static void register(SelfTest.Steps steps) {
        HoverAssist.register();
        steps.add("S8 Treffer-Rückmeldung – 7″ mit Netheritspeer im FPV, Pilot 30 Blöcke entfernt: Speer-Ton, Partikel und OSD TREFFER beim Piloten, "
                + "zwei zügige Stiche mit 10 m/s besiegen einen Dorfbewohner, Speerspitze liegt im Sichtfeld der Kamera", 3000, Script.create()
                .server("7″ mit Netheritspeer", SpearFeedbackSteps::rig)
                .append(Pilot.fly())
                .append(Pilot.takeOff(HOVER_METRES))
                .server("Pilot 30 Blöcke zurück, Dorfbewohner vor der Nase", SpearFeedbackSteps::stage)
                .run("Töne mithören", SpearFeedbackSteps::listen)
                .sleep(10)
                .server("Stich 1 mit 10 m/s", (server, state) -> stab(server, state, 1))
                .server("Partikel beim Piloten", SpearFeedbackSteps::particles)
                .then("OSD TREFFER", SpearFeedbackSteps::notice)
                .then("Speer-Ton beim Piloten", SpearFeedbackSteps::sound)
                .then("Foto im FPV", (ctx, ticks) -> Shots.shot(ctx, ticks, PHOTO, () -> {
                }))
                .sleep(STAB_GAP_TICKS)
                .server("Stich 2 mit 10 m/s", (server, state) -> stab(server, state, 2))
                .server("Dorfbewohner besiegt", SpearFeedbackSteps::defeated)
                .server("Speerspitze im Sichtfeld der Kamera", SpearFeedbackSteps::tipInView)
                .then("Ergebnis", SpearFeedbackSteps::result), SpearFeedbackSteps::cleanup);
    }

    static BuildSpec sevenInch() {
        return BuildSpec.of(DispenserRig.propwash("long_range_7"), DispenserRig.propwash("stack_30"), DispenserRig.propwash("m2807_1300"),
                DispenserRig.propwash("p7_2b"), DispenserRig.propwash("digital_hd"), DispenserRig.propwash("b6s_4000_lion"))
                .withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT);
    }

    private static void rig(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.spawn(server, state, sevenInch(), "S8 Treffer 7", Arena.ground(0, -6), 180.0F);
        inventory(drone).set(0, new ItemStack(Items.NETHERITE_SPEAR));
        state.put(DRONE_ID, drone.getId());
    }

    private static AccessoryInventory inventory(Entity drone) {
        return PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM)
                .orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"));
    }

    private static void stage(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Vec3 nose = SpearChecks.nose(drone);
        state.put(NOSE, nose);
        double feet = Arena.ground().getY() + 1.0;
        Vec3 back = drone.position().subtract(nose.scale(PILOT_DISTANCE));
        float toward = (float) Math.toDegrees(Math.atan2(-nose.x, nose.z));
        player.teleportTo(level, back.x, feet, back.z, Set.<Relative>of(), toward, 10.0F, true);
        player.setDeltaMovement(Vec3.ZERO);
        Mob villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
        if (villager == null) {
            throw new IllegalStateException("Dorfbewohner nicht erzeugt");
        }
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.setPersistenceRequired();
        Vec3 at = drone.position().add(nose.scale(TARGET_AHEAD));
        villager.snapTo(at.x, Math.max(feet, drone.getY() - villager.getEyeHeight()), at.z, toward + 180.0F, 0.0F);
        Arena.spawn(level, villager);
        state.put(VILLAGER, villager);
        note(state, String.format(Locale.ROOT, "Pilot %.1f Blöcke vom Dorfbewohner (%.0f Leben)", player.position().distanceTo(villager.position()),
                villager.getHealth()));
    }

    private static void listen() {
        SOUNDS.clear();
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().addListener(LISTENER);
        if (Shots.enabled()) {
            Shots.language(mc, Shots.GERMAN);
        }
    }

    private static void stab(MinecraftServer server, Map<String, Object> state, int number) {
        Entity drone = Pilot.serverDrone(server, state);
        Mob villager = (Mob) state.get(VILLAGER);
        Vec3 nose = (Vec3) state.get(NOSE);
        ServerLevel level = Arena.level(server);
        AccessoryInventory inventory = inventory(drone);
        ItemStack spear = inventory.get(0);
        float before = villager.getHealth();
        SpearStrikes.Hit hit = SpearStrikes.stab(level, drone, Arena.player(server), spear, villager, nose, nose.scale(BRISK_SPEED));
        inventory.set(0, spear);
        note(state, String.format(Locale.ROOT, "Stich %d mit %.0f m/s: −%.1f Leben (%s), Dorfbewohner hat %.1f", number, BRISK_SPEED,
                before - villager.getHealth(), hit.skip(), Math.max(0.0F, villager.getHealth())));
    }

    private static void particles(MinecraftServer server, Map<String, Object> state) {
        Mob villager = (Mob) state.get(VILLAGER);
        Optional<SpearFeedback.Shown> shown = SpearFeedback.last().filter(entry -> entry.target() == villager.getId());
        if (shown.isEmpty() || !shown.get().pilotParticles()) {
            missing(state, "keine Treffer-Partikel beim Piloten (" + shown.map(Object::toString).orElse("keine Rückmeldung") + ")");
            return;
        }
        note(state, String.format(Locale.ROOT, "Partikel beim Piloten: %d Krit, %d Schadensanzeige, an %d Spieler; Ton direkt an den Piloten: %s",
                SpearFeedback.CRIT_PARTICLES, shown.get().indicators(), shown.get().viewers(), shown.get().pilotSound()));
    }

    private static void tipInView(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        Optional<Vec3> tip = SpearStrikes.lastTip(drone);
        Optional<CameraPose> camera = PropwashApi.drones().cameraPose(drone);
        if (tip.isEmpty() || camera.isEmpty()) {
            missing(state, "Speerspitze oder Kamera fehlt (Spitze " + tip.isPresent() + ", Kamera " + camera.isPresent() + ")");
            return;
        }
        Vec3 look = camera.get().look().normalize();
        Vec3 up = camera.get().up().normalize();
        Vec3 right = look.cross(up).normalize();
        Vec3 offset = tip.get().subtract(camera.get().eye());
        double ahead = offset.dot(look);
        double vertical = Math.toDegrees(Math.atan2(offset.dot(up), ahead));
        double horizontal = Math.toDegrees(Math.atan2(offset.dot(right), ahead));
        double half = camera.get().verticalFovDegrees() / 2.0;
        String where = String.format(Locale.ROOT, "Speerspitze %.0f mm vor der Linse, %.1f° %s und %.1f° %s der Bildmitte (Sichtfeld senkrecht %.0f°)",
                ahead * 1000.0, Math.abs(vertical), vertical < 0.0 ? "unter" : "über", Math.abs(horizontal), horizontal < 0.0 ? "links" : "rechts",
                camera.get().verticalFovDegrees());
        if (ahead < MIN_AHEAD || Math.abs(vertical) >= half || Math.abs(horizontal) >= half) {
            missing(state, where + ": außerhalb des Sichtfelds der Kamera");
        } else {
            note(state, where + ": im Sichtfeld der Kamera (nur die Lage; ob der Speer gezeichnet wird, prüft „S8 Speer im FPV-Bild“)");
        }
    }

    private static void defeated(MinecraftServer server, Map<String, Object> state) {
        Mob villager = (Mob) state.get(VILLAGER);
        if (villager.isAlive() && !villager.isDeadOrDying()) {
            missing(state, String.format(Locale.ROOT, "Dorfbewohner lebt nach 2 Stichen mit Netheritspeer und %.0f m/s (%.1f Leben)", BRISK_SPEED,
                    villager.getHealth()));
        } else {
            note(state, "Dorfbewohner nach 2 Stichen besiegt");
        }
    }

    private static StepResult notice(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        int drone = (int) ctx.state().get(DRONE_ID);
        Optional<Notice> hit = Arrays.stream(Notice.values()).filter(notice -> notice.path().equals(HIT_PATH)).findFirst();
        boolean active = mc.level != null && hit.isPresent() && OsdNotices.active(hit.get(), drone, mc.level.getGameTime());
        if (!active && ticks < WAIT_TICKS) {
            return StepResult.pending();
        }
        if (!active) {
            missing(ctx.state(), "kein OSD-Hinweis TREFFER beim Piloten (" + (hit.isPresent() ? "Hinweis angemeldet" : "kein Hinweis „hit“")
                    + ", Hinweis-Pakete am Client " + OsdNotices.received() + ")");
            return StepResult.pass();
        }
        String key = hit.get().translationKey();
        String text = I18n.get(key);
        if (text.equals(key) || !MobDefenseOsd.registered().contains(hit.get().id())) {
            missing(ctx.state(), "OSD-Hinweis " + hit.get().id() + " ohne Text oder nicht angemeldet („" + text + "“)");
        } else {
            note(ctx.state(), "OSD zeigt „" + text + "“ (" + MobDefenseOsd.tone(hit.get()) + ")");
        }
        return StepResult.pass();
    }

    private static StepResult sound(SelfTest.StepContext ctx, int ticks) {
        KineticWeapon kinetic = new ItemStack(Items.NETHERITE_SPEAR).get(DataComponents.KINETIC_WEAPON);
        String expected = kinetic == null ? "" : kinetic.hitSound().map(holder -> holder.value().location().toString()).orElse("");
        Optional<Heard> heard = SOUNDS.stream().filter(entry -> entry.id().equals(expected)).findFirst();
        if (heard.isEmpty() && ticks < WAIT_TICKS) {
            return StepResult.pending();
        }
        Minecraft mc = Minecraft.getInstance();
        if (heard.isEmpty()) {
            List<String> ids = SOUNDS.stream().map(Heard::id).distinct().toList();
            missing(ctx.state(), "kein Speer-Ton " + expected + " beim Piloten (gehört: " + (ids.isEmpty() ? "nichts" : ids) + ")");
            return StepResult.pass();
        }
        double distance = mc.player == null ? 0.0 : Math.sqrt(mc.player.distanceToSqr(heard.get().x(), heard.get().y(), heard.get().z()));
        if (distance <= BROADCAST_RANGE) {
            missing(ctx.state(), String.format(Locale.ROOT, "Speer-Ton nur %.1f Blöcke vom Spieler, Prüfung braucht mehr als %.0f", distance,
                    BROADCAST_RANGE));
        } else {
            note(ctx.state(), String.format(Locale.ROOT, "Speer-Ton %s beim Piloten, %.1f Blöcke vom Spieler entfernt", expected, distance));
        }
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    private static void note(Map<String, Object> state, String note) {
        ((List<String>) state.computeIfAbsent(NOTES, key -> new CopyOnWriteArrayList<String>())).add(note);
    }

    @SuppressWarnings("unchecked")
    private static void missing(Map<String, Object> state, String problem) {
        ((List<String>) state.computeIfAbsent(MISSING, key -> new CopyOnWriteArrayList<String>())).add(problem);
    }

    @SuppressWarnings("unchecked")
    private static StepResult result(SelfTest.StepContext ctx, int ticks) {
        List<String> notes = new ArrayList<>((List<String>) ctx.state().getOrDefault(NOTES, List.of()));
        List<String> problems = (List<String>) ctx.state().getOrDefault(MISSING, List.of());
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems) + " | " + String.join("; ", notes));
        }
        return StepResult.pass(String.join("; ", notes));
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("Mithören aus, Sprache zurück, Flughilfe aus", () -> {
                Minecraft mc = Minecraft.getInstance();
                mc.getSoundManager().removeListener(LISTENER);
                SOUNDS.clear();
                Shots.restoreLanguage(mc);
                Pilot.land(mc);
            })
            .server("Flug beenden", (server, state) -> Pilot.release(server));

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }
}
