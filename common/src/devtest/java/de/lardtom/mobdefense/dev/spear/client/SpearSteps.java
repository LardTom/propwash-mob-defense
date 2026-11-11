package de.lardtom.mobdefense.dev.spear.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.spear.SpearChecks;
import de.lardtom.mobdefense.dev.spear.SpearDrive;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.mobdefense.spear.core.SpearDamage;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryInventory;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpearSteps {
    public static final double LANE_SIDE = 4.0;
    public static final double LANE_HEIGHT = 0.9;
    public static final double ZOMBIE_DISTANCE = 3.0;
    public static final double OVERSHOOT = 1.5;
    public static final double PHOTO_DISTANCE = 0.5;
    private static final String DRONE = "spear.drone";
    private static final String NOSE = "spear.nose";
    private static final String START = "spear.start";
    private static final String ZOMBIE = "spear.zombie";
    private static final String RUN = "spear.run";
    private static final String ACCEPTS = "spear.accepts";
    private static final String NOTE = "spear.note";
    private static final String CLEANUP = "spear.cleanup.since";
    private static final int WORN = -1;
    private static final int FIRE_ASPECT_LEVEL = 2;
    private static boolean hudHidden;

    private SpearSteps() {
    }

    private enum Expect {
        DAMAGE,
        NONE,
        BREAK,
        FIRE,
        UNLIT;

        private boolean husk() {
            return this == FIRE || this == UNLIT;
        }
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("S8 Speer – nur Speere (Dreizack abgelehnt), 15 m/s gegen Zombie Schaden wie berechnet ±1, unter 4,6 m/s kein Schaden, "
                + "Haltbarkeit, Foto an der Drohne", 3000, Script.create()
                .server("Halterung nimmt nur Speere", (server, state) -> state.put(ACCEPTS, SpearChecks.accepts()))
                .then("Nur Speere", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(ACCEPTS)))
                .server("Drohne mit Speer-Halterung", SpearSteps::rig)
                .append(pass(15.0, 0, Expect.DAMAGE))
                .append(pass(5.0, 0, Expect.DAMAGE))
                .append(pass(4.0, 0, Expect.NONE))
                .append(pass(15.0, WORN, Expect.BREAK))
                .server("Drohne für das Foto", SpearSteps::photoScene)
                .sleep(20)
                .check("Speer am Client sichtbar", SpearSteps::clientSees)
                .append(Shots.photos("spear_drone", SpearSteps::hideHud))
                .run("HUD zurück", SpearSteps::restoreHud), SpearSteps::cleanup);
        steps.add("S8 Speer-Verbrennung – Eisenspeer mit Verbrennung II zündet einen Wüstenzombie an, ohne Pilot im Anlauf mit 15 m/s und mit Pilot "
                + "als Angreifer, ohne Verbrennung kein Feuer, Schadensquelle bleibt", 2400, Script.create()
                .server("Drohne mit Speer-Halterung", SpearSteps::rig)
                .append(pass(15.0, 0, Expect.UNLIT))
                .append(pass(15.0, 0, Expect.FIRE))
                .server("Verbrennung II mit Pilot", (server, state) -> state.put(NOTE, fireWithPilot(server, state)))
                .then("Verbrennung II mit Pilot", (ctx, ticks) -> StepResult.pass((String) ctx.state().get(NOTE))), SpearSteps::cleanup);
    }

    private static Script pass(double speed, int wear, Expect expect) {
        String label = String.format(Locale.ROOT, "%.0f m/s", speed);
        return Script.create()
                .server("Anlauf " + label + " vorbereiten", (server, state) -> prepare(server, state, wear, expect == Expect.FIRE))
                .sleep(5)
                .server("Anlauf " + label, (server, state) -> launch(server, state, speed, expect.husk()))
                .then("Anlauf " + label + " fertig", (ctx, ticks) -> {
                    SpearDrive.Run run = (SpearDrive.Run) ctx.state().get(RUN);
                    if (run != null && run.done()) {
                        return StepResult.pass();
                    }
                    return ticks > 400 ? StepResult.fail("Anlauf endet nicht") : StepResult.pending();
                })
                .sleep(5)
                .server("Ergebnis " + label, (server, state) -> state.put(NOTE, result(server, state, speed, wear, expect)))
                .then("Ergebnis " + label, (ctx, ticks) -> StepResult.pass((String) ctx.state().get(NOTE)));
    }

    private static void rig(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Entity drone = Arena.track(SpearChecks.placeDrone(level, Arena.ground(0, 6), SpearChecks.build()));
        Vec3 nose = SpearChecks.nose(drone);
        Vec3 right = new Vec3(-nose.z, 0.0, nose.x);
        BlockPos ground = Arena.ground();
        Vec3 start = new Vec3(Arena.spawn().x, ground.getY() + 1.0 + LANE_HEIGHT, Arena.spawn().z).add(right.scale(LANE_SIDE)).subtract(nose.scale(2.0));
        state.put(DRONE, drone);
        state.put(NOSE, nose);
        state.put(START, start);
    }

    private static void prepare(MinecraftServer server, Map<String, Object> state, int wear, boolean fire) {
        Entity drone = (Entity) state.get(DRONE);
        Vec3 start = (Vec3) state.get(START);
        Object old = state.remove(ZOMBIE);
        if (old instanceof Entity entity) {
            entity.discard();
        }
        SpearDrive.stop();
        drone.setNoGravity(true);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.setPos(start.x, start.y, start.z);
        SpearStrikes.forget(drone);
        ItemStack spear = fire ? fireSpear(server) : new ItemStack(Items.IRON_SPEAR);
        spear.setDamageValue(wear == WORN ? spear.getMaxDamage() - 1 : wear);
        AccessoryInventory inventory = PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM)
                .orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"));
        inventory.set(0, spear);
    }

    private static void launch(MinecraftServer server, Map<String, Object> state, double speed, boolean husk) {
        Entity drone = (Entity) state.get(DRONE);
        Vec3 nose = (Vec3) state.get(NOSE);
        ServerLevel level = Arena.level(server);
        SpearStrikes.forget(drone);
        Vec3 at = new Vec3(drone.getX(), Arena.ground().getY() + 1.0, drone.getZ()).add(nose.scale(ZOMBIE_DISTANCE));
        Zombie zombie = zombie(level, husk ? EntityTypes.HUSK : EntityTypes.ZOMBIE, at, nose);
        state.put(ZOMBIE, zombie);
        state.put(RUN, SpearDrive.start(drone, zombie, nose, speed, ZOMBIE_DISTANCE + OVERSHOOT));
    }

    private static Zombie zombie(ServerLevel level, EntityType<? extends Zombie> type, Vec3 at, Vec3 nose) {
        Zombie zombie = type.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException(type.toShortString() + " nicht erzeugt");
        }
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0, false, false));
        float yaw = (float) Math.toDegrees(Math.atan2(nose.x, -nose.z));
        zombie.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        Arena.spawn(level, zombie);
        zombie.clearFire();
        return zombie;
    }

    private static ItemStack fireSpear(MinecraftServer server) {
        ItemStack spear = new ItemStack(Items.IRON_SPEAR);
        spear.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT), FIRE_ASPECT_LEVEL);
        return spear;
    }

    private static String fireWithPilot(MinecraftServer server, Map<String, Object> state) {
        Entity drone = (Entity) state.get(DRONE);
        Vec3 nose = (Vec3) state.get(NOSE);
        Object old = state.remove(ZOMBIE);
        if (old instanceof Entity entity) {
            entity.discard();
        }
        SpearDrive.stop();
        ServerLevel level = Arena.level(server);
        ServerPlayer pilot = Arena.player(server);
        Vec3 at = new Vec3(drone.getX(), Arena.ground().getY() + 1.0, drone.getZ()).add(nose.scale(ZOMBIE_DISTANCE));
        Zombie zombie = zombie(level, EntityTypes.HUSK, at, nose);
        state.put(ZOMBIE, zombie);
        ItemStack spear = fireSpear(server);
        SpearStrikes.Hit hit = SpearStrikes.stab(level, drone, pilot, spear, zombie, nose, nose.scale(SpearChecks.TEST_SPEED));
        float lost = zombie.getMaxHealth() - zombie.getHealth();
        int fire = zombie.getRemainingFireTicks();
        DamageSource source = zombie.getLastDamageSource();
        String label = "Verbrennung II mit Pilot, 15,0 m/s";
        if (!hit.hurt() || Math.abs(lost - SpearChecks.EXPECTED_IRON_15) > 1.0F) {
            throw new IllegalStateException(label + ": Wüstenzombie verliert " + SpearChecks.format(lost) + " statt "
                    + SpearChecks.format(SpearChecks.EXPECTED_IRON_15) + " ±1 (" + hit + ")");
        }
        if (source == null || !source.is(DamageTypes.SPEAR) || source.getDirectEntity() != drone || source.getEntity() != pilot) {
            throw new IllegalStateException(label + ": Schadensquelle " + (source == null ? "keine" : source.typeHolder().getRegisteredName()
                    + " direkt " + source.getDirectEntity() + " Verursacher " + source.getEntity()) + " statt minecraft:spear von der Drohne mit Pilot");
        }
        if (fire <= 0) {
            throw new IllegalStateException(label + ": Wüstenzombie brennt nicht (Feuer " + fire + " Ticks, Schaden " + SpearChecks.format(lost) + ")");
        }
        return label + ": Wüstenzombie verliert " + SpearChecks.format(lost) + " und brennt " + fire + " Ticks, Verursacher Pilot, direkte Ursache Drohne";
    }

    private static String result(MinecraftServer server, Map<String, Object> state, double speed, int wear, Expect expect) {
        Entity drone = (Entity) state.get(DRONE);
        Zombie zombie = (Zombie) state.get(ZOMBIE);
        Vec3 nose = (Vec3) state.get(NOSE);
        SpearDrive.Run run = (SpearDrive.Run) state.get(RUN);
        String label = String.format(Locale.ROOT, "%.1f m/s", speed);
        float lost = zombie.getMaxHealth() - zombie.getHealth();
        double pushed = zombie.getDeltaMovement().dot(nose) * 20.0;
        ItemStack spear = SpearDrive.spear(drone);
        if (expect == Expect.NONE) {
            if (run.travelled() < ZOMBIE_DISTANCE + 0.5) {
                throw new IllegalStateException(label + ": Drohne nur " + SpearChecks.format(run.travelled()) + " Blöcke weit, Zombie nicht erreicht");
            }
            if (lost > 0.0F || zombie.hurtTime > 0 || Math.abs(pushed) > 0.5 || spear.getDamageValue() != wear) {
                throw new IllegalStateException(label + ": Schaden " + SpearChecks.format(lost) + ", Stoß " + SpearChecks.format(pushed)
                        + ", Haltbarkeit −" + (spear.getDamageValue() - wear) + " statt nichts");
            }
            return label + ": durch den Zombie geflogen (" + SpearChecks.format(run.travelled()) + " Blöcke), kein Schaden, kein Stoß, Speer unverändert";
        }
        float expected = SpearDamage.scaled(speed, SpearChecks.IRON_MULTIPLIER, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
        if (!run.hit() || Math.abs(lost - expected) > 1.0F || lost <= 0.0F) {
            throw new IllegalStateException(label + ": Zombie verliert " + SpearChecks.format(lost) + " statt " + SpearChecks.format(expected)
                    + " ±1 (Treffer " + run.hit() + ", " + run.ticks() + " Ticks, " + SpearChecks.format(run.travelled()) + " Blöcke)");
        }
        DamageSource source = zombie.getLastDamageSource();
        if (source == null || !source.is(DamageTypes.SPEAR) || source.getDirectEntity() != drone || source.getEntity() != null) {
            throw new IllegalStateException(label + ": Schadensquelle " + (source == null ? "keine" : source.typeHolder().getRegisteredName()
                    + " direkt " + source.getDirectEntity() + " Verursacher " + source.getEntity()) + " statt minecraft:spear von der Drohne ohne Pilot");
        }
        boolean knockback = speed >= 5.1;
        if (knockback != pushed > 0.5) {
            throw new IllegalStateException(label + ": Zombie mit " + SpearChecks.format(pushed) + " m/s entlang der Nase gestoßen, Rückstoß erwartet: "
                    + knockback);
        }
        String push = knockback ? ", Rückstoß " + SpearChecks.format(pushed) + " m/s entlang der Nase" : ", kein Rückstoß (unter 5,1 m/s)";
        if (expect.husk()) {
            boolean lit = expect == Expect.FIRE;
            String spearLabel = label + (lit ? " mit Verbrennung II" : " ohne Verbrennung");
            int fire = zombie.getRemainingFireTicks();
            if (lit != fire > 0) {
                throw new IllegalStateException(spearLabel + ": Wüstenzombie brennt " + fire + " Ticks, Feuer erwartet: " + lit + " (Schaden "
                        + SpearChecks.format(lost) + ")");
            }
            if (spear.getDamageValue() != wear + 1) {
                throw new IllegalStateException(spearLabel + ": Haltbarkeit −" + (spear.getDamageValue() - wear) + " statt −1");
            }
            return spearLabel + " ohne Pilot: Wüstenzombie verliert " + SpearChecks.format(lost) + " (Formel " + SpearChecks.format(expected) + ") und "
                    + (lit ? "brennt " + fire + " Ticks" : "brennt nicht") + push + ", Schadensart minecraft:spear, Haltbarkeit −1";
        }
        if (expect == Expect.BREAK) {
            if (!spear.isEmpty()) {
                throw new IllegalStateException(label + ": abgenutzter Speer bleibt (" + spear.getDamageValue() + "/" + spear.getMaxDamage() + ")");
            }
            return label + " mit abgenutztem Speer: " + SpearChecks.format(lost) + " Schaden, Speer zerbricht und verschwindet aus dem Slot";
        }
        if (spear.getDamageValue() != wear + 1) {
            throw new IllegalStateException(label + ": Haltbarkeit −" + (spear.getDamageValue() - wear) + " statt −1");
        }
        return label + ": Zombie verliert " + SpearChecks.format(lost) + " (Formel " + SpearChecks.format(expected) + ", Rüstung 2)" + push
                + ", Schadensart minecraft:spear, Haltbarkeit −1";
    }

    private static void photoScene(MinecraftServer server, Map<String, Object> state) {
        Entity drone = (Entity) state.get(DRONE);
        Vec3 nose = (Vec3) state.get(NOSE);
        Object old = state.remove(ZOMBIE);
        if (old instanceof Entity entity) {
            entity.discard();
        }
        SpearDrive.stop();
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Vec3 right = new Vec3(-nose.z, 0.0, nose.x);
        double eye = Arena.ground().getY() + 1.0 + player.getEyeHeight();
        Vec3 center = new Vec3(Arena.spawn().x, eye, Arena.spawn().z).add(right.scale(LANE_SIDE));
        drone.setNoGravity(true);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.setPos(center.x, center.y - 0.05, center.z);
        SpearStrikes.forget(drone);
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow().set(0, new ItemStack(Items.IRON_SPEAR));
        Vec3 feet = center.subtract(right.scale(PHOTO_DISTANCE)).add(nose.scale(0.03)).subtract(0.0, player.getEyeHeight(), 0.0);
        Vec3 look = center.subtract(feet.add(0.0, player.getEyeHeight(), 0.0));
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
        state.put(NOTE, String.format(Locale.ROOT, "Foto: Drohne bei %.1f/%.1f/%.1f, Spieler seitlich in %.2f m", center.x, center.y, center.z,
                PHOTO_DISTANCE));
    }

    private static StepResult clientSees() {
        Minecraft mc = Minecraft.getInstance();
        for (Entity entity : Arena.clientDrones(mc)) {
            if (!PropwashApi.accessories().contents(entity, AccessorySlot.BOTTOM).stream().anyMatch(stack -> stack.is(Items.IRON_SPEAR))) {
                continue;
            }
            return StepResult.pass("Eisenspeer am Client in der Speer-Halterung, Abstand " + SpearChecks.format(entity.distanceTo(mc.player)) + " m");
        }
        return StepResult.fail("keine Drohne mit Eisenspeer am Client");
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

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        restoreHud();
        int since = (int) ctx.state().computeIfAbsent(CLEANUP, key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, state) -> SpearDrive.stop());
    }
}
