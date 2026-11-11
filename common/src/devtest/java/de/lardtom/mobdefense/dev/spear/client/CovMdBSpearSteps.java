package de.lardtom.mobdefense.dev.spear.client;

import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.OsdReader;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.core.client.CovMdBWorkbench;
import de.lardtom.mobdefense.dev.spear.CovMdBSpearRun;
import de.lardtom.mobdefense.dev.spear.SpearChecks;
import de.lardtom.mobdefense.dev.spear.SpearDrive;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.spear.SpearStrikes;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CovMdBSpearSteps {
    public static final double LANE_HEIGHT = 0.9;
    public static final double TARGET_DISTANCE = 3.0;
    public static final double OVERSHOOT = 2.0;
    public static final int CONTACT_COOLDOWN = 10;
    public static final double PASS_SPEED = 15.0;
    public static final int MIN_PASSES = 2;
    public static final int MAX_PASSES = 3;
    private static final String DRONE = "covmdb.spear.drone";
    private static final String NOSE = "covmdb.spear.nose";
    private static final String START = "covmdb.spear.start";
    private static final String TARGET = "covmdb.spear.target";
    private static final String RUN = "covmdb.spear.run";
    private static final String NOTE = "covmdb.spear.note";
    private static final String PERCENTS = "covmdb.spear.percents";
    private static final String RESULTS = "covmdb.spear.results";
    public static final String FIRE_STEP = "Speer-Verbrennung – Verbrennung zündet den Zombie an, ohne Pilot im Anlauf und mit Pilot als Angreifer";
    private static final Pattern PERCENT = Pattern.compile("SPR\\s+(\\d+)%");
    private static final List<String> SOUNDS = new CopyOnWriteArrayList<>();
    private static final SoundEventListener LISTENER = (sound, events, range) -> SOUNDS.add(sound.getIdentifier().toString());
    private static boolean hudHidden;

    private CovMdBSpearSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        CovMdBWorkbench.register();
        steps.add("Speer-Sperre – mit etwa 5 m/s durch Zombie und Riesen geschoben, höchstens ein Treffer je 10 Ticks", 2400, Script.create()
                .server("Drohne mit Speer-Halterung", (server, state) -> rig(server, state, Arena.ground(-30, 40)))
                .append(drive("Zombie mit 5 m/s", 5.0, EntityTypes.ZOMBIE, () -> new ItemStack(Items.IRON_SPEAR)))
                .server("Zombie: ein Stich", (server, state) -> state.put(NOTE, cooldown(state, "Zombie, 5 m/s", 1, 1)))
                .then("Zombie", CovMdBSpearSteps::note)
                .append(drive("Riese mit 4,8 m/s", 4.8, EntityTypes.GIANT, () -> new ItemStack(Items.IRON_SPEAR)))
                .server("Riese: Stiche im Abstand von mindestens 10 Ticks", (server, state) -> state.put(NOTE, cooldown(state, "Riese, 4,8 m/s", 2, 3)))
                .then("Riese", CovMdBSpearSteps::note), CovMdBSpearSteps::cleanup);
        steps.add("Speer-Haltbarkeit – drei Treffer kosten 3, OSD-Element mobdefense:spear zeigt fallende SPR …%, Speer mit 1 Rest bricht mit Ton", 3000,
                Script.create()
                        .server("Drohne mit Goldspeer", (server, state) -> {
                            rig(server, state, Arena.ground(-30, 46));
                            spear(drone(state), new ItemStack(Items.GOLDEN_SPEAR));
                            state.put(PERCENTS, new ArrayList<Integer>());
                        })
                        .then("OSD vorher", CovMdBSpearSteps::readPercent)
                        .append(wearPass(1)).append(wearPass(2)).append(wearPass(3))
                        .server("Haltbarkeit −3", CovMdBSpearSteps::worn)
                        .then("OSD fällt", CovMdBSpearSteps::falling)
                        .run("Töne mithören", () -> {
                            SOUNDS.clear();
                            Minecraft.getInstance().getSoundManager().addListener(LISTENER);
                        })
                        .server("Speer mit 1 Rest-Haltbarkeit", (server, state) -> lastPoint(server, state))
                        .append(drive("letzter Treffer", PASS_SPEED, EntityTypes.ZOMBIE, null))
                        .sleep(10)
                        .server("Speer bricht", CovMdBSpearSteps::broken)
                        .then("Ton und leeres Fach", CovMdBSpearSteps::breakSound), CovMdBSpearSteps::cleanup);
        steps.add("Speer-Verzauberungen – Schärfe mehr Schaden als unverzaubert, Lunge ohne Wirkung", 3000, Script.create()
                .server("Drohne mit Speer-Halterung", (server, state) -> {
                    rig(server, state, Arena.ground(-30, 52));
                    state.put(RESULTS, new ArrayList<String[]>());
                })
                .append(enchantedPass("ohne", null, 0))
                .append(enchantedPass("Schärfe V", Enchantments.SHARPNESS, 5))
                .append(enchantedPass("Lunge III", Enchantments.LUNGE, 3))
                .server("Vergleich", CovMdBSpearSteps::enchantments)
                .then("Ergebnis", CovMdBSpearSteps::note), CovMdBSpearSteps::cleanup);
        steps.add("Speer-Rückstoß und Reiter – schneller Treffer stößt den Zombie in Flugrichtung, Hühnerjockey verliert den Reiter", 2400, Script.create()
                .server("Drohne mit Speer-Halterung", (server, state) -> rig(server, state, Arena.ground(-30, 58)))
                .append(drive("Zombie mit 15 m/s", PASS_SPEED, EntityTypes.ZOMBIE, () -> new ItemStack(Items.IRON_SPEAR)))
                .server("Rückstoß", CovMdBSpearSteps::knockback)
                .then("Rückstoß", CovMdBSpearSteps::note)
                .server("Hühnerjockey", CovMdBSpearSteps::jockey)
                .sleep(5)
                .server("Anlauf mit 15 m/s", (server, state) -> state.put(RUN, CovMdBSpearRun.start(drone(state), (Vec3) state.get(NOSE), PASS_SPEED,
                        TARGET_DISTANCE + 0.5 + OVERSHOOT)))
                .then("Anlauf fertig", CovMdBSpearSteps::finished)
                .sleep(5)
                .server("Reiter abgeworfen", CovMdBSpearSteps::dismounted)
                .then("Reiter", CovMdBSpearSteps::note), CovMdBSpearSteps::cleanup);
        steps.add("Speer im Vorbeiflug – Vorbeiflüge mit 15 m/s bis ein Zombie mit 20 Lebenspunkten fällt, Zahl im Bereich 2 bis 3", 4000, Script.create()
                .server("Drohne mit Eisenspeer, Zombie mit 20 Lebenspunkten", (server, state) -> {
                    rig(server, state, Arena.ground(-30, 64));
                    state.put("covmdb.spear.passes", 0);
                })
                .then("Vorbeiflüge bis zum Tod", CovMdBSpearSteps::passes)
                .then("Ergebnis", CovMdBSpearSteps::note), CovMdBSpearSteps::cleanup);
        steps.add("bildschirmfotos: Speer an der Drohne – Werkbank und Third Person, Eisenspeer unter dem Rahmen, Spitze vorn", 2400, Script.create()
                .server("Werkbank mit Speer-Halterung", (server, state) -> CovMdBWorkbench.open(server, Arena.ground(-6, 0).above(),
                        SpearChecks.build()))
                .then("Werkbank", (ctx, ticks) -> CovMdBWorkbench.shown(ctx, ticks, SpearChecks.build()))
                .append(Shots.photos("cov_spear_workbench"))
                .run("Werkbank zu", () -> Minecraft.getInstance().gui.setScreen(null))
                .server("Werkbank am Server zu", (server, state) -> Arena.player(server).closeContainer())
                .server("Drohne mit Eisenspeer", CovMdBSpearSteps::photoScene)
                .then("kurzer Anlauf", CovMdBSpearSteps::finished)
                .server("Drohne in Augenhöhe", CovMdBSpearSteps::photoLift)
                .sleep(20)
                .server("Blick von der Seite", (server, state) -> view(server, state, 0.0, 0.6, 0.05))
                .sleep(10)
                .check("Abstand der Kamera", CovMdBSpearSteps::cameraDistance)
                .append(Shots.photos("cov_spear_third_person_side", CovMdBSpearSteps::hideHud))
                .server("Blick schräg von vorn", (server, state) -> view(server, state, 0.45, 0.4, 0.12))
                .sleep(10)
                .append(Shots.photos("cov_spear_third_person_front", CovMdBSpearSteps::hideHud))
                .run("HUD zurück", CovMdBSpearSteps::restoreHud), CovMdBSpearSteps::cleanup);
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static Entity drone(Map<String, Object> state) {
        return (Entity) state.get(DRONE);
    }

    private static void rig(MinecraftServer server, Map<String, Object> state, BlockPos ground) {
        ServerLevel level = Arena.level(server);
        Entity drone = Arena.track(SpearChecks.placeDrone(level, ground, SpearChecks.build()));
        Vec3 nose = SpearChecks.nose(drone);
        Vec3 start = new Vec3(ground.getX() + 0.5, ground.getY() + 1.0 + LANE_HEIGHT, ground.getZ() + 0.5).subtract(nose.scale(2.0));
        state.put(DRONE, drone);
        state.put(NOSE, nose);
        state.put(START, start);
        reset(drone, start);
    }

    private static void reset(Entity drone, Vec3 start) {
        CovMdBSpearRun.stop();
        drone.setNoGravity(true);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.setPos(start.x, start.y, start.z);
        SpearStrikes.forget(drone);
    }

    private static void spear(Entity drone, ItemStack spear) {
        PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).orElseThrow(() -> new IllegalStateException("Speer-Halterung ohne Inventar"))
                .set(0, spear);
    }

    private static <T extends Mob> T target(ServerLevel level, EntityType<T> type, Vec3 at, Vec3 nose, boolean fireProof) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException("Ziel nicht erzeugt");
        }
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setSilent(true);
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        if (fireProof) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0, false, false));
        }
        float yaw = (float) Math.toDegrees(Math.atan2(nose.x, -nose.z));
        mob.snapTo(at.x, at.y, at.z, yaw, 0.0F);
        return Arena.spawn(level, mob);
    }

    private static Vec3 targetSpot(Map<String, Object> state) {
        Vec3 start = (Vec3) state.get(START);
        Vec3 nose = (Vec3) state.get(NOSE);
        return new Vec3(start.x, Math.floor(start.y - LANE_HEIGHT), start.z).add(nose.scale(TARGET_DISTANCE));
    }

    private static Script drive(String label, double speed, EntityType<? extends Mob> type, @Nullable Supplier<ItemStack> spear) {
        return Script.create()
                .server(label + ": vorbereiten", (server, state) -> {
                    Entity drone = drone(state);
                    reset(drone, (Vec3) state.get(START));
                    if (spear != null) {
                        spear(drone, spear.get());
                    }
                    Object old = state.remove(TARGET);
                    if (old instanceof Entity entity && entity.isAlive()) {
                        entity.discard();
                    }
                    state.put(TARGET, target(Arena.level(server), type, targetSpot(state), (Vec3) state.get(NOSE), true));
                })
                .sleep(5)
                .server(label + ": Anlauf", (server, state) -> {
                    LivingEntity target = (LivingEntity) state.get(TARGET);
                    double distance = TARGET_DISTANCE + target.getBbWidth() / 2.0 + OVERSHOOT;
                    state.put(RUN, CovMdBSpearRun.start(drone(state), (Vec3) state.get(NOSE), speed, distance));
                })
                .then(label + ": durch", CovMdBSpearSteps::finished);
    }

    private static StepResult finished(SelfTest.StepContext ctx, int ticks) {
        CovMdBSpearRun run = (CovMdBSpearRun) ctx.state().get(RUN);
        if (run != null && run.done()) {
            return StepResult.pass();
        }
        return ticks > 400 ? StepResult.fail("Anlauf endet nicht") : StepResult.pending();
    }

    private static String cooldown(Map<String, Object> state, String label, int min, int max) {
        CovMdBSpearRun run = (CovMdBSpearRun) state.get(RUN);
        LivingEntity target = (LivingEntity) state.get(TARGET);
        List<Integer> stabs = run.stabs();
        int shortest = Integer.MAX_VALUE;
        for (int i = 1; i < stabs.size(); i++) {
            shortest = Math.min(shortest, stabs.get(i) - stabs.get(i - 1));
        }
        String report = String.format(Locale.ROOT, "%s: %d Stiche bei Tick %s über %.2f Blöcke in %d Ticks, kürzester Abstand %s, Ziel verliert %.1f",
                label, stabs.size(), stabs, run.travelled(), run.ticks(), shortest == Integer.MAX_VALUE ? "-" : shortest + " Ticks",
                target.getMaxHealth() - target.getHealth());
        if (stabs.size() < min || stabs.size() > max || shortest < CONTACT_COOLDOWN) {
            throw new IllegalStateException(report);
        }
        return report;
    }

    private static Script wearPass(int number) {
        return drive("Treffer " + number, PASS_SPEED, EntityTypes.ZOMBIE, null)
                .sleep(10)
                .then("OSD nach Treffer " + number, CovMdBSpearSteps::readPercent);
    }

    private static @Nullable Entity clientDrone(SelfTest.StepContext ctx) {
        Entity drone = (Entity) ctx.state().get(DRONE);
        if (drone == null) {
            return null;
        }
        for (Entity entity : Arena.clientDrones(Minecraft.getInstance())) {
            if (entity.getUUID().equals(drone.getUUID())) {
                return entity;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static StepResult readPercent(SelfTest.StepContext ctx, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        Entity drone = clientDrone(ctx);
        if (drone == null || mc.level == null) {
            return ticks > 60 ? StepResult.fail("Drohne fehlt am Client") : StepResult.pending();
        }
        OsdReader.forget();
        OsdText text = MobDefenseOsd.spear(OsdReader.read(drone, mc.level.getGameTime()));
        OsdReader.forget();
        String shown = text == null ? "-" : text.text();
        Matcher matcher = PERCENT.matcher(shown);
        if (!matcher.find()) {
            return ticks > 60 ? StepResult.fail("OSD-Element mobdefense:spear zeigt „" + shown + "“") : StepResult.pending();
        }
        List<Integer> percents = (List<Integer>) ctx.state().get(PERCENTS);
        int percent = Integer.parseInt(matcher.group(1));
        if (!percents.isEmpty() && percent >= percents.getLast() && ticks < 40) {
            return StepResult.pending();
        }
        percents.add(percent);
        return StepResult.pass("OSD „" + shown + "“");
    }

    private static void worn(MinecraftServer server, Map<String, Object> state) {
        ItemStack spear = SpearDrive.spear(drone(state));
        if (!spear.is(Items.GOLDEN_SPEAR) || spear.getDamageValue() != 3) {
            throw new IllegalStateException("nach drei Treffern " + spear + " mit Abnutzung " + spear.getDamageValue() + " statt 3");
        }
        state.put(NOTE, "Goldspeer nach drei Treffern: Abnutzung 3 von " + spear.getMaxDamage());
    }

    @SuppressWarnings("unchecked")
    private static StepResult falling(SelfTest.StepContext ctx, int ticks) {
        List<Integer> percents = (List<Integer>) ctx.state().get(PERCENTS);
        for (int i = 1; i < percents.size(); i++) {
            if (percents.get(i) >= percents.get(i - 1)) {
                return StepResult.fail("SPR fällt nicht mit jedem Treffer: " + percents);
            }
        }
        return percents.size() == 4 ? StepResult.pass((String) ctx.state().remove(NOTE) + "; SPR " + percents + " %")
                : StepResult.fail("OSD-Werte " + percents);
    }

    private static void lastPoint(MinecraftServer server, Map<String, Object> state) {
        ItemStack spear = new ItemStack(Items.IRON_SPEAR);
        spear.setDamageValue(spear.getMaxDamage() - 1);
        spear(drone(state), spear);
        Vec3 nose = (Vec3) state.get(NOSE);
        Vec3 at = targetSpot(state).add(-nose.z * 3.0, 0.0, nose.x * 3.0);
        ServerPlayer player = Arena.player(server);
        player.teleportTo(Arena.level(server), at.x, at.y, at.z, Set.<Relative>of(), player.getYRot(), 0.0F, true);
    }

    private static void broken(MinecraftServer server, Map<String, Object> state) {
        CovMdBSpearRun run = (CovMdBSpearRun) state.get(RUN);
        ItemStack left = SpearDrive.spear(drone(state));
        LivingEntity target = (LivingEntity) state.get(TARGET);
        if (!run.broke() || !left.isEmpty() || target.getHealth() >= target.getMaxHealth()) {
            throw new IllegalStateException("Speer mit 1 Rest: gebrochen " + run.broke() + ", Fach " + left + ", Ziel " + target.getHealth());
        }
    }

    private static StepResult breakSound(SelfTest.StepContext ctx, int ticks) {
        List<String> heard = List.copyOf(SOUNDS);
        boolean broke = heard.contains("minecraft:entity.item.break");
        if (!broke && ticks < 20) {
            return StepResult.pending();
        }
        Minecraft.getInstance().getSoundManager().removeListener(LISTENER);
        if (!broke) {
            return heard.isEmpty() ? StepResult.fail("keine Töne am Client gehört (Sound-Engine aus?)")
                    : StepResult.fail("kein entity.item.break unter " + heard);
        }
        return StepResult.pass("Speer mit 1 Rest-Haltbarkeit bricht beim Treffer, Fach leer, Ton minecraft:entity.item.break am Client gehört (dazu "
                + heard.stream().filter(sound -> sound.contains("spear")).distinct().toList() + ")");
    }

    private static Script enchantedPass(String label, ResourceKey<Enchantment> enchantment, int level) {
        return Script.create()
                .server(label + ": Speer", (server, state) -> {
                    ItemStack spear = new ItemStack(Items.IRON_SPEAR);
                    if (enchantment != null) {
                        Holder<Enchantment> holder = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
                        spear.enchant(holder, level);
                    }
                    state.put("covmdb.spear.next", spear);
                })
                .append(Script.create()
                        .server(label + ": Zombie ohne Feuerschutz", (server, state) -> {
                            Entity drone = drone(state);
                            reset(drone, (Vec3) state.get(START));
                            spear(drone, (ItemStack) state.get("covmdb.spear.next"));
                            Object old = state.remove(TARGET);
                            if (old instanceof Entity entity) {
                                entity.discard();
                            }
                            state.put(TARGET, target(Arena.level(server), EntityTypes.ZOMBIE, targetSpot(state), (Vec3) state.get(NOSE), false));
                        })
                        .sleep(5)
                        .server(label + ": Anlauf", (server, state) -> {
                            LivingEntity target = (LivingEntity) state.get(TARGET);
                            state.put(RUN, CovMdBSpearRun.start(drone(state), (Vec3) state.get(NOSE), PASS_SPEED,
                                    TARGET_DISTANCE + target.getBbWidth() / 2.0 + OVERSHOOT));
                        })
                        .then(label + ": durch", CovMdBSpearSteps::finished)
                        .server(label + ": Messung", (server, state) -> {
                            LivingEntity target = (LivingEntity) state.get(TARGET);
                            @SuppressWarnings("unchecked")
                            List<String[]> results = (List<String[]>) state.get(RESULTS);
                            CovMdBSpearRun run = (CovMdBSpearRun) state.get(RUN);
                            results.add(new String[]{label, Float.toString(target.getMaxHealth() - target.getHealth()),
                                    Integer.toString(target.getRemainingFireTicks()), Integer.toString(run.stabs().size())});
                            target.clearFire();
                        }));
    }

    @SuppressWarnings("unchecked")
    private static void enchantments(MinecraftServer server, Map<String, Object> state) {
        List<String[]> results = (List<String[]>) state.get(RESULTS);
        float plain = Float.parseFloat(results.get(0)[1]);
        float sharp = Float.parseFloat(results.get(1)[1]);
        float lunge = Float.parseFloat(results.get(2)[1]);
        String report = report(results);
        List<String> problems = new ArrayList<>();
        if (!(plain > 0.0F) || !(sharp > plain + 0.5F)) {
            problems.add("Schärfe nicht stärker");
        }
        if (Math.abs(lunge - plain) > 0.01F) {
            problems.add("Lunge ändert den Schaden");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join(", ", problems) + ": " + report);
        }
        state.put(NOTE, report);
    }

    private static String report(List<String[]> results) {
        StringBuilder report = new StringBuilder();
        for (String[] result : results) {
            report.append(result[0]).append(": ").append(String.format(Locale.ROOT, "%.2f", Float.parseFloat(result[1]))).append(" Schaden, Feuer ")
                    .append(result[2]).append(" Ticks, ").append(result[3]).append(" Stich; ");
        }
        return report.toString().trim();
    }

    public static Script fireAspect() {
        return Script.create()
                .server("Drohne mit Speer-Halterung", (server, state) -> {
                    rig(server, state, Arena.ground(-30, 58));
                    state.put(RESULTS, new ArrayList<String[]>());
                })
                .append(enchantedPass("ohne", null, 0))
                .append(enchantedPass("Verbrennung II", Enchantments.FIRE_ASPECT, 2))
                .server("Verbrennung II mit Pilot als Angreifer", CovMdBSpearSteps::fireWithPilot)
                .server("Vergleich", CovMdBSpearSteps::fireResult)
                .then("Ergebnis", CovMdBSpearSteps::note);
    }

    @SuppressWarnings("unchecked")
    private static void fireWithPilot(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = drone(state);
        Vec3 nose = (Vec3) state.get(NOSE);
        ItemStack spear = new ItemStack(Items.IRON_SPEAR);
        spear.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_ASPECT), 2);
        Zombie zombie = target(level, EntityTypes.ZOMBIE, drone.position().add(nose.scale(1.0)), nose, false);
        SpearStrikes.Hit hit = SpearStrikes.stab(level, drone, Arena.player(server), spear, zombie, nose, nose.scale(PASS_SPEED));
        ((List<String[]>) state.get(RESULTS)).add(new String[]{"Verbrennung II mit Pilot (Stich über SpearStrikes.stab)", Float.toString(hit.dealt()),
                Integer.toString(zombie.getRemainingFireTicks()), hit.hurt() ? "1" : "0"});
    }

    @SuppressWarnings("unchecked")
    private static void fireResult(MinecraftServer server, Map<String, Object> state) {
        List<String[]> results = (List<String[]>) state.get(RESULTS);
        int plain = Integer.parseInt(results.get(0)[2]);
        int unpiloted = Integer.parseInt(results.get(1)[2]);
        int piloted = Integer.parseInt(results.get(2)[2]);
        String report = report(results);
        if (plain > 0 || unpiloted <= 0 || piloted <= 0) {
            throw new IllegalStateException("Verbrennung zündet nicht: " + report);
        }
        state.put(NOTE, report);
    }

    private static void knockback(MinecraftServer server, Map<String, Object> state) {
        Zombie zombie = (Zombie) state.get(TARGET);
        Vec3 nose = (Vec3) state.get(NOSE);
        double pushed = zombie.getDeltaMovement().dot(nose) * 20.0;
        CovMdBSpearRun run = (CovMdBSpearRun) state.get(RUN);
        if (run.stabs().isEmpty() || !(pushed > 0.5)) {
            throw new IllegalStateException("Zombie nicht in Flugrichtung gestoßen: " + pushed + " m/s, " + run.stabs().size() + " Stiche");
        }
        state.put(NOTE, String.format(Locale.ROOT, "15 m/s: Zombie bekommt %.2f m/s in Flugrichtung", pushed));
    }

    private static void jockey(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Entity drone = drone(state);
        Vec3 nose = (Vec3) state.get(NOSE);
        Object old = state.remove(TARGET);
        if (old instanceof Entity entity) {
            entity.discard();
        }
        Chicken chicken = target(level, EntityTypes.CHICKEN, targetSpot(state), nose, true);
        Zombie rider = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (rider == null) {
            throw new IllegalStateException("Reiter nicht erzeugt");
        }
        rider.setBaby(true);
        rider.setNoAi(true);
        rider.setPersistenceRequired();
        rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        rider.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0, false, false));
        rider.snapTo(chicken.getX(), chicken.getY() + 0.5, chicken.getZ(), chicken.getYRot(), 0.0F);
        Arena.spawn(level, rider);
        if (!rider.startRiding(chicken, true, false)) {
            throw new IllegalStateException("Reiter steigt nicht auf");
        }
        chicken.setChickenJockey(true);
        Vec3 start = (Vec3) state.get(START);
        double middle = (rider.getBoundingBox().minY + rider.getBoundingBox().maxY) / 2.0;
        Vec3 lane = new Vec3(start.x, middle, start.z);
        reset(drone, lane);
        spear(drone, new ItemStack(Items.IRON_SPEAR));
        state.put(TARGET, rider);
        state.put("covmdb.spear.chicken", chicken);
        state.put("covmdb.spear.box", String.format(Locale.ROOT, "Drohne y %.2f, Reiter %.2f…%.2f, Huhn %.2f…%.2f", lane.y, rider.getBoundingBox().minY,
                rider.getBoundingBox().maxY, chicken.getBoundingBox().minY, chicken.getBoundingBox().maxY));
    }

    private static void dismounted(MinecraftServer server, Map<String, Object> state) {
        Zombie rider = (Zombie) state.get(TARGET);
        Chicken chicken = (Chicken) state.get("covmdb.spear.chicken");
        CovMdBSpearRun run = (CovMdBSpearRun) state.get(RUN);
        if (rider.isPassenger() || run.stabs().isEmpty()) {
            throw new IllegalStateException("Reiter sitzt noch (" + run.stabs().size() + " Stiche, Reiter " + (rider.getMaxHealth() - rider.getHealth())
                    + " Schaden, Huhn " + chicken.getHealth() + ", " + state.get("covmdb.spear.box") + ")");
        }
        state.put(NOTE, String.format(Locale.ROOT, "Hühnerjockey mit 15 m/s: Reiter danach ohne Fahrzeug (%d Stiche, Reiter −%.1f, Huhn %s)", run.stabs().size(),
                rider.getMaxHealth() - rider.getHealth(), chicken.isAlive() ? "lebt" : "tot"));
    }

    private static StepResult passes(SelfTest.StepContext ctx, int ticks) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get("covmdb.spear.pending");
        if (pending instanceof CompletableFuture<?> future) {
            if (!future.isDone()) {
                return ticks > 3800 ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
            }
            state.remove("covmdb.spear.pending");
            if (future.isCompletedExceptionally()) {
                return StepResult.fail(String.valueOf(future.exceptionNow()));
            }
            state.put("covmdb.spear.since", ticks);
        }
        CovMdBSpearRun run = (CovMdBSpearRun) state.get(RUN);
        if (run != null && !run.done()) {
            return StepResult.pending();
        }
        String phase = (String) state.getOrDefault("covmdb.spear.phase", "prepare");
        int since = (int) state.getOrDefault("covmdb.spear.since", 0);
        if (ticks - since < 5) {
            return StepResult.pending();
        }
        Zombie zombie = (Zombie) state.get(TARGET);
        int passes = (int) state.get("covmdb.spear.passes");
        if (phase.equals("prepare") && zombie != null && !zombie.isAlive()) {
            state.put(NOTE, String.format(Locale.ROOT, "Zombie (20 Lebenspunkte, Rüstung %d) fällt nach %d Vorbeiflügen mit 15 m/s, Eisenspeer, Speerschaden %d %%; "
                    + "Bereich %d bis %d", zombie.getArmorValue(), passes, RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue(), MIN_PASSES, MAX_PASSES));
            if (passes < MIN_PASSES || passes > MAX_PASSES) {
                return StepResult.fail((String) state.get(NOTE));
            }
            return StepResult.pass();
        }
        if (passes >= 10) {
            return StepResult.fail("Zombie lebt nach 10 Vorbeiflügen: " + (zombie == null ? "-" : zombie.getHealth()));
        }
        if (phase.equals("prepare")) {
            state.put("covmdb.spear.phase", "launch");
            state.put("covmdb.spear.pending", ctx.onServer(server -> {
                Entity drone = drone(state);
                reset(drone, (Vec3) state.get(START));
                spear(drone, new ItemStack(Items.IRON_SPEAR));
                Zombie target = (Zombie) state.get(TARGET);
                Vec3 spot = targetSpot(state);
                if (target == null) {
                    target = target(Arena.level(server), EntityTypes.ZOMBIE, spot, (Vec3) state.get(NOSE), true);
                    state.put(TARGET, target);
                } else {
                    target.setDeltaMovement(Vec3.ZERO);
                    target.teleportTo(spot.x, spot.y, spot.z);
                    target.setInvulnerableTime(0);
                }
            }));
            return StepResult.pending();
        }
        state.put("covmdb.spear.phase", "prepare");
        state.put("covmdb.spear.passes", passes + 1);
        state.put("covmdb.spear.pending", ctx.onServer(server -> {
            Zombie target = (Zombie) state.get(TARGET);
            state.put(RUN, CovMdBSpearRun.start(drone(state), (Vec3) state.get(NOSE), PASS_SPEED, TARGET_DISTANCE + target.getBbWidth() / 2.0 + OVERSHOOT));
        }));
        return StepResult.pending();
    }

    private static void photoScene(MinecraftServer server, Map<String, Object> state) {
        rig(server, state, Arena.ground(-6, 5));
        Entity drone = drone(state);
        spear(drone, new ItemStack(Items.IRON_SPEAR));
        state.put(RUN, CovMdBSpearRun.start(drone, (Vec3) state.get(NOSE), 2.0, 1.0));
    }

    private static void photoLift(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(state);
        ServerPlayer player = Arena.player(server);
        CovMdBSpearRun.stop();
        Vec3 at = drone.position();
        double eye = Arena.ground().getY() + 1.0 + player.getEyeHeight();
        drone.setNoGravity(true);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.setPos(at.x, eye - 0.05, at.z);
        SpearStrikes.forget(drone);
    }

    private static void view(MinecraftServer server, Map<String, Object> state, double back, double side, double up) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(state);
        Vec3 nose = (Vec3) state.get(NOSE);
        Vec3 right = new Vec3(-nose.z, 0.0, nose.x);
        Vec3 center = drone.position();
        Vec3 eye = center.add(right.scale(side)).add(nose.scale(back)).add(0.0, up, 0.0);
        Vec3 feet = eye.subtract(0.0, player.getEyeHeight(), 0.0);
        Vec3 look = center.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(Arena.level(server), feet.x, feet.y, feet.z, Set.<Relative>of(), yaw, pitch, true);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static StepResult cameraDistance() {
        Minecraft mc = Minecraft.getInstance();
        for (Entity entity : Arena.clientDrones(mc)) {
            if (mc.player != null && PropwashApi.accessories().contents(entity, AccessorySlot.BOTTOM).stream().anyMatch(stack -> !stack.isEmpty())) {
                return StepResult.pass(String.format(Locale.ROOT, "Kamera %.2f m von der Drohne (Auge %.2f/%.2f/%.2f, Drohne %.2f/%.2f/%.2f)",
                        mc.player.getEyePosition().distanceTo(entity.position()), mc.player.getEyePosition().x, mc.player.getEyePosition().y,
                        mc.player.getEyePosition().z, entity.getX(), entity.getY(), entity.getZ()));
            }
        }
        return StepResult.fail("keine Drohne mit Speer am Client");
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
        Minecraft.getInstance().getSoundManager().removeListener(LISTENER);
        int since = (int) ctx.state().computeIfAbsent("covmdb.spear.cleanup", key -> ctx.tick());
        return Script.server(ctx, ctx.tick() - since, (server, state) -> {
            CovMdBSpearRun.stop();
            if (state.get(DRONE) instanceof Entity drone && !drone.isRemoved()) {
                PropwashApi.drones().autonomy().stop(drone);
            }
            ServerPlayer player = Arena.player(server);
            player.setGameMode(GameType.CREATIVE);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
    }
}
