package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.booster.BoosterRig;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.CovMdAWatch;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.payload.Payloads;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.TuneSpec;
import de.lardtom.propwash.api.drone.CameraPose;
import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public final class CovMdAShotSteps {
    public static final double MAX_DEGREES = 10.0;
    public static final double UPTILT = 30.0;
    public static final int TNT_FUSE = 80;
    public static final double LIFT = 0.6;
    private static final String DRONE = "covmda.shot.drone";
    private static final String TARGET = "covmda.shot.target";
    private static final String HEALTH = "covmda.shot.health";
    private static final String WATCH = "covmda.shot.watch";
    private static final String NOTES = "covmda.shot.notes";
    private static final String MARK = "covmda.shot.mark";

    private CovMdAShotSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Alle Pfeilarten – normaler Pfeil, Spektralpfeil und Trankpfeil der Langsamkeit treffen einen Zombie: Schaden, Leuchten, Trankeffekt",
                1600, Script.create()
                        .server("Drohne auf dem Sockel, Zombie 3 m davor auf einem Block", (server, state) -> {
                            range(server, state, null, 0.0);
                            raisedTarget(server, state, EntityTypes.ZOMBIE, 3.0);
                        })
                        .server("Pfeile laden", (server, state) -> DispenserRig.load(drone(server, state), new ItemStack(Items.ARROW),
                                new ItemStack(Items.SPECTRAL_ARROW), PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.SLOWNESS)))
                        .append(arrow("normaler Pfeil", entity -> entity instanceof Arrow, target -> true))
                        .append(arrow("Spektralpfeil", entity -> entity instanceof SpectralArrow, target -> target.hasEffect(MobEffects.GLOWING)))
                        .append(arrow("Trankpfeil Langsamkeit", entity -> entity instanceof Arrow, target -> target.hasEffect(MobEffects.SLOWNESS)))
                        .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("Schneeball, Ei, Erfahrungsfläschchen – je ein Schuss nach vorn, Schneeball stößt den Zombie, Fläschchen gibt Erfahrungskugeln",
                1600, Script.create()
                        .server("Drohne auf dem Sockel, Zombie 2,5 m davor auf einem Block", (server, state) -> {
                            range(server, state, null, 0.0);
                            raisedTarget(server, state, EntityTypes.ZOMBIE, 2.5);
                        })
                        .server("Schneeball", CovMdAShotSteps::snowball)
                        .sleep(20)
                        .server("zweiter Schneeball von einer frischen Drohne, falls der erste vorbeiflog", (server, state) -> {
                            if (!snowballHit(server, state)) {
                                note(state, "erster Schneeball am Zombie vorbei");
                                drone(server, state).discard();
                                range(server, state, null, 0.0);
                                snowball(server, state);
                            }
                        })
                        .sleep(20)
                        .server("Treffer merken", (server, state) -> snowballHit(server, state))
                        .server("Schneeball stößt den Zombie", CovMdAShotSteps::snowballPushed)
                        .server("Zombie weg, Ei", (server, state) -> {
                            removeTarget(server, state);
                            shoot(server, state, new ItemStack(Items.EGG), ThrownEgg.class, true);
                        })
                        .sleep(30)
                        .server("Ei geflogen", (server, state) -> flown(server, state, "Ei", 2.0))
                        .server("Spieler weit hinter die Drohne, Erfahrungsfläschchen", (server, state) -> {
                            ServerPlayer player = Arena.player(server);
                            Entity drone = drone(server, state);
                            Vec3 far = DispenserRig.ahead(drone, -20.0);
                            player.teleportTo(player.level(), far.x, Arena.ground().getY() + 1.0, far.z, Set.of(), 180.0F, 0.0F, true);
                            shoot(server, state, new ItemStack(Items.EXPERIENCE_BOTTLE), ThrownExperienceBottle.class, true);
                        })
                        .sleep(25)
                        .server("Erfahrungskugeln", CovMdAShotSteps::orbs)
                        .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("Feuerkugel – kleine Feuerkugel nach vorn, am Einschlag auf Gras entsteht Feuer (drone_griefing an)", 1200, Script.create()
                .server("Drohne auf dem Sockel, Grasblock 4 m davor", CovMdAShotSteps::grassTarget)
                .server("Feuerkugel", (server, state) -> shoot(server, state, new ItemStack(Items.FIRE_CHARGE), SmallFireball.class, true))
                .sleep(30)
                .server("Feuer am Einschlag", CovMdAShotSteps::fire)
                .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("Feuerwerksrakete als Geschoss – fliegt nach vorn und explodiert, mit Stern Schaden am Zombie", 1600, Script.create()
                .server("Drohne auf dem Sockel", (server, state) -> range(server, state, null, 0.0))
                .server("Rakete ohne Stern", (server, state) -> shoot(server, state, BoosterRig.rocket(1, 0), FireworkRocketEntity.class, true))
                .then("Rakete fliegt und explodiert", (ctx, ticks) -> rocketGone(ctx, ticks))
                .server("Zombie 4 m davor, Rakete mit Stern", (server, state) -> {
                    target(server, state, EntityTypes.ZOMBIE, 4.0);
                    shoot(server, state, BoosterRig.rocket(1, 1), FireworkRocketEntity.class, true);
                })
                .sleep(30)
                .server("Zombie getroffen", (server, state) -> hurt(server, state, "Rakete mit Stern"))
                .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("Item ohne Verhalten – Bruchstein und Enderperle fliegen als Gegenstand nach vorn, keine geworfene Perle", 1200, Script.create()
                .server("Drohne auf dem Sockel", (server, state) -> range(server, state, null, 0.0))
                .server("Bruchstein", (server, state) -> item(server, state, new ItemStack(Items.COBBLESTONE)))
                .sleep(6)
                .server("Enderperle", (server, state) -> item(server, state, new ItemStack(Items.ENDER_PEARL)))
                .sleep(20)
                .server("keine geworfene Perle", CovMdAShotSteps::noPearl)
                .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("Wurftrank des Schadens – trifft ein Schwein, das Schwein verliert Leben", 1200, Script.create()
                .server("Drohne auf dem Sockel, Schwein 3 m davor auf einem Block", (server, state) -> {
                    range(server, state, null, 0.0);
                    raisedTarget(server, state, EntityTypes.PIG, 3.0);
                })
                .server("Schadenstrank", (server, state) -> shoot(server, state, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING),
                        Projectile.class, true))
                .sleep(30)
                .server("Schwein verletzt", (server, state) -> hurt(server, state, "Schadenstrank"))
                .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("TNT-Abwurf – an der Mündung gezündet, Zündzeit 80 Ticks wie Vanilla", 1200, Script.create()
                .server("Drohne auf dem Sockel", (server, state) -> range(server, state, null, 0.0))
                .server("TNT", CovMdAShotSteps::tntFuse)
                .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
        steps.add("Kamera-Uptilt 30° – Pfeil fliegt entlang MountPose#forward, nicht entlang der Kamera, Einschlag unter der Bildmitte", 1200, Script.create()
                .server("Drohne mit 30° Uptilt auf dem Sockel", CovMdAShotSteps::uptiltRange)
                .server("Pfeil", CovMdAShotSteps::uptiltShot)
                .then("Ergebnis", CovMdAShotSteps::notes), CovMdAShotSteps::cleanup);
    }

    static final SelfTest.ClientStep CLEANUP = Script.create().server("Geschosse und Kugeln weg", (server, state) -> sweep(server));

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }

    public static void sweep(MinecraftServer server) {
        ServerLevel level = Arena.level(server);
        AABB box = Arena.box(level);
        List<Entity> leftovers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Projectile || entity instanceof PrimedTnt || (entity instanceof ExperienceOrb || entity instanceof Chicken)
                    && box.contains(entity.position())) {
                leftovers.add(entity);
            }
        }
        leftovers.forEach(Entity::discard);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(Arena.spawn()).offset(-24, -3, -40), BlockPos.containing(Arena.spawn()).offset(24, 8, 24))) {
            if (level.getBlockState(pos).is(Blocks.FIRE)) {
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            }
        }
        CovMdAWatch.clear();
    }

    static Entity drone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(DRONE);
        Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt am Server");
        }
        return drone;
    }

    public static Entity range(MinecraftServer server, Map<String, Object> state, EntityType<? extends Mob> type, double distance) {
        return range(server, state, type, distance, DispenserRig.fiveInch(), "CovMdA Geschosse");
    }

    static Entity range(MinecraftServer server, Map<String, Object> state, EntityType<? extends Mob> type, double distance, BuildSpec spec,
                        String name) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos pillar = Arena.ground(0, -6).above();
        Entity drone = Arena.track(DispenserRig.onPillar(level, player, spec, name, pillar, Direction.NORTH, (pos, block) -> Arena.place(level, pos, block)));
        drone.setNoGravity(true);
        drone.setDeltaMovement(Vec3.ZERO);
        drone.teleportTo(drone.getX(), drone.getY() + LIFT, drone.getZ());
        state.put(DRONE, drone.getUUID());
        Vec3 behind = drone.position().add(5.0, 1.0, 5.0);
        player.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), 135.0F, 15.0F, true);
        if (type != null) {
            target(server, state, type, distance);
        }
        return drone;
    }

    static Mob target(MinecraftServer server, Map<String, Object> state, EntityType<? extends Mob> type, double distance) {
        ServerLevel level = Arena.level(server);
        Entity drone = drone(server, state);
        Vec3 at = DispenserRig.ahead(drone, distance);
        Mob mob = spawn(level, type, new Vec3(at.x, Arena.ground().getY() + 1.0, at.z));
        state.put(TARGET, mob.getUUID());
        state.put(HEALTH, mob.getHealth());
        return mob;
    }

    static Mob raisedTarget(MinecraftServer server, Map<String, Object> state, EntityType<? extends Mob> type, double distance) {
        ServerLevel level = Arena.level(server);
        Entity drone = drone(server, state);
        Vec3 at = DispenserRig.ahead(drone, distance);
        BlockPos block = BlockPos.containing(at.x, Arena.ground().getY() + 1.0, at.z);
        Arena.place(level, block, Blocks.STONE.defaultBlockState());
        Mob mob = spawn(level, type, new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5));
        state.put(TARGET, mob.getUUID());
        state.put(HEALTH, mob.getHealth());
        return mob;
    }

    public static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 at) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) {
            throw new IllegalStateException(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " nicht erzeugt");
        }
        mob.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        if (mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
        return Arena.spawn(level, mob);
    }

    static LivingEntity targetEntity(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(TARGET);
        Entity entity = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalStateException("Ziel fehlt");
        }
        return living;
    }

    static void removeTarget(MinecraftServer server, Map<String, Object> state) {
        Object id = state.remove(TARGET);
        Entity entity = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (entity != null) {
            entity.discard();
        }
    }

    @SuppressWarnings("unchecked")
    static void note(Map<String, Object> state, String note) {
        ((List<String>) state.computeIfAbsent(NOTES, key -> new ArrayList<String>())).add(note);
    }

    @SuppressWarnings("unchecked")
    static StepResult notes(SelfTest.StepContext ctx, int ticks) {
        List<String> notes = (List<String>) ctx.state().getOrDefault(NOTES, List.of());
        return StepResult.pass(String.join("; ", notes));
    }

    public static DispenserLog.Entry shoot(MinecraftServer server, Map<String, Object> state, ItemStack ammo, Class<? extends Entity> kind, boolean watch) {
        Entity drone = drone(server, state);
        DispenserRig.load(drone, ammo);
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        Entity shot = entry.result() == null ? null : entry.result().entity().orElse(null);
        String name = BuiltInRegistries.ITEM.getKey(ammo.getItem()).getPath();
        if (entry.result() == null || !entry.result().success() || shot == null || !kind.isInstance(shot)) {
            throw new IllegalStateException(name + ": " + entry.status() + " " + CovMdAWatch.type(shot) + " statt " + kind.getSimpleName());
        }
        Vec3 own = entry.launch().subtract(entry.droneVelocity().scale(1.0 / 20.0));
        double angle = degrees(own, entry.forward());
        if (!(shot instanceof ItemEntity) && angle > MAX_DEGREES) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s: %s %.1f° neben forward", name, CovMdAWatch.type(shot), angle));
        }
        if (!Payloads.is(shot)) {
            throw new IllegalStateException(name + ": " + CovMdAWatch.type(shot) + " nicht als Payload markiert");
        }
        if (watch) {
            state.put(WATCH, CovMdAWatch.watch(shot, entry.forward(), 100));
        }
        state.put(MARK, shot);
        note(state, String.format(Locale.ROOT, "%s → %s mit %.1f m/s, %.1f° von forward", name, CovMdAWatch.type(shot), entry.launch().length() * 20.0,
                angle));
        return entry;
    }

    static double degrees(Vec3 a, Vec3 b) {
        if (a.lengthSqr() < 1.0E-12 || b.lengthSqr() < 1.0E-12) {
            return 180.0;
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, a.normalize().dot(b.normalize())))));
    }

    private static Script arrow(String label, Predicate<Entity> kind, Predicate<LivingEntity> effect) {
        return Script.create()
                .server(label, (server, state) -> {
                    LivingEntity target = targetEntity(server, state);
                    target.setHealth(target.getMaxHealth());
                    target.removeAllEffects();
                    target.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
                    target.setInvulnerableTime(0);
                    state.put(HEALTH, target.getHealth());
                    Entity drone = drone(server, state);
                    int before = DispenserLog.counts(drone).dispensed();
                    DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
                    Entity shot = entry.result() == null ? null : entry.result().entity().orElse(null);
                    if (DispenserLog.counts(drone).dispensed() != before + 1 || shot == null || !kind.test(shot)) {
                        throw new IllegalStateException(label + ": " + entry.status() + " " + CovMdAWatch.type(shot));
                    }
                    Arena.track(shot);
                    state.put(MARK, shot);
                })
                .sleep(25)
                .server(label + " trifft", (server, state) -> {
                    LivingEntity target = targetEntity(server, state);
                    float before = (float) state.get(HEALTH);
                    if (!(target.getHealth() < before)) {
                        throw new IllegalStateException(label + ": Zombie unverletzt (" + target.getHealth() + " von " + before + ")");
                    }
                    if (!effect.test(target)) {
                        throw new IllegalStateException(label + ": Wirkung fehlt, Effekte " + target.getActiveEffects());
                    }
                    List<String> effects = new ArrayList<>();
                    for (MobEffectInstance instance : target.getActiveEffects()) {
                        if (!instance.getEffect().equals(MobEffects.FIRE_RESISTANCE)) {
                            effects.add(instance.getEffect().getRegisteredName().replace("minecraft:", ""));
                        }
                    }
                    note(state, String.format(Locale.ROOT, "%s trifft: %.0f → %.0f Leben%s", label, before, target.getHealth(),
                            effects.isEmpty() ? "" : ", Effekte " + effects));
                    Object shot = state.remove(MARK);
                    if (shot instanceof Entity entity) {
                        entity.discard();
                    }
                });
    }

    private static void snowball(MinecraftServer server, Map<String, Object> state) {
        LivingEntity target = targetEntity(server, state);
        state.put(WATCH + ".target", CovMdAWatch.watch(target, DispenserRig.view(drone(server, state)).forward(), 40));
        shoot(server, state, new ItemStack(Items.SNOWBALL), Snowball.class, true);
    }

    private static boolean snowballHit(MinecraftServer server, Map<String, Object> state) {
        LivingEntity target = targetEntity(server, state);
        boolean hit = state.get(TARGET + ".snowball") != null
                || target.getLastDamageSource() != null && target.getLastDamageSource().getDirectEntity() instanceof Snowball;
        if (hit) {
            state.put(TARGET + ".snowball", Boolean.TRUE);
        }
        return hit;
    }

    private static void snowballPushed(MinecraftServer server, Map<String, Object> state) {
        CovMdAWatch.Watch watch = (CovMdAWatch.Watch) state.get(WATCH);
        LivingEntity target = targetEntity(server, state);
        CovMdAWatch.Watch push = (CovMdAWatch.Watch) state.get(WATCH + ".target");
        double speed = push == null ? 0.0 : push.maxSpeedAlong();
        if (watch == null || watch.removedAt() < 0) {
            throw new IllegalStateException("Schneeball fliegt weiter statt zu treffen");
        }
        if (!snowballHit(server, state)) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Schneeball trifft den Zombie nicht (%s): Schneeball endet nach %d Ticks bei %s, "
                    + "Zombie bei %s", target.getLastDamageSource(), watch == null ? -1 : watch.removedAt(), watch == null ? "-" : watch.last(), target.position()));
        }
        if (!(speed > 0.05)) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Zombie nicht gestoßen: %.3f Blöcke je Tick", speed));
        }
        note(state, String.format(Locale.ROOT, "Schneeball trifft nach %d Ticks, Stoß %.2f m/s nach vorn, Leben %.0f", watch.removedAt(), speed * 20.0,
                target.getHealth()));
    }

    private static void flown(MinecraftServer server, Map<String, Object> state, String label, double metres) {
        CovMdAWatch.Watch watch = (CovMdAWatch.Watch) state.get(WATCH);
        if (watch == null || watch.maxAlong() < metres) {
            throw new IllegalStateException(label + " nur " + (watch == null ? "-" : String.format(Locale.ROOT, "%.1f m nach vorn, entfernt nach %d Ticks bei %s "
                    + "(Start %s)", watch.maxAlong(), watch.removedAt(), watch.last(), watch.start())));
        }
        note(state, String.format(Locale.ROOT, "%s %.1f m nach vorn, zerplatzt nach %d Ticks", label, watch.maxAlong(), watch.removedAt()));
    }

    private static void orbs(MinecraftServer server, Map<String, Object> state) {
        CovMdAWatch.Watch watch = (CovMdAWatch.Watch) state.get(WATCH);
        if (watch == null || watch.removedAt() < 0) {
            throw new IllegalStateException("Erfahrungsfläschchen zerplatzt nicht");
        }
        ServerLevel level = Arena.level(server);
        List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, new AABB(watch.last(), watch.last()).inflate(6.0));
        int points = 0;
        for (ExperienceOrb orb : orbs) {
            points += orb.getValue();
        }
        if (orbs.isEmpty()) {
            throw new IllegalStateException("keine Erfahrungskugeln am Einschlag " + watch.last());
        }
        note(state, String.format(Locale.ROOT, "Erfahrungsfläschchen %.1f m nach vorn, zerplatzt: %d Kugeln mit %d Punkten", watch.maxAlong(), orbs.size(),
                points));
        for (ExperienceOrb orb : orbs) {
            orb.discard();
        }
    }

    private static void grassTarget(MinecraftServer server, Map<String, Object> state) {
        Entity drone = range(server, state, null, 0.0);
        ServerLevel level = Arena.level(server);
        BlockPos front = BlockPos.containing(DispenserRig.ahead(drone, 4.5));
        BlockPos block = new BlockPos(front.getX(), Arena.ground().getY() + 2, front.getZ());
        for (BlockPos pos : BlockPos.betweenClosed(block.offset(-2, -1, -1), block.offset(2, 2, 3))) {
            Arena.place(level, pos.immutable(), Blocks.AIR.defaultBlockState());
        }
        Arena.place(level, block, Blocks.GRASS_BLOCK.defaultBlockState());
        Arena.place(level, block.relative(Direction.SOUTH).below(), Blocks.GRASS_BLOCK.defaultBlockState());
        state.put(TARGET + ".block", block);
    }

    private static void fire(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos block = (BlockPos) state.get(TARGET + ".block");
        CovMdAWatch.Watch watch = (CovMdAWatch.Watch) state.get(WATCH);
        List<BlockPos> fire = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(block.offset(-3, -2, -3), block.offset(3, 3, 6))) {
            if (level.getBlockState(pos).is(Blocks.FIRE)) {
                fire.add(pos.immutable());
            }
        }
        if (watch == null || watch.removedAt() < 0) {
            throw new IllegalStateException("Feuerkugel schlägt nicht ein");
        }
        if (fire.isEmpty()) {
            throw new IllegalStateException("kein Feuer am Einschlag bei " + block.toShortString() + " (Feuerkugel endet bei " + watch.last() + ")");
        }
        note(state, String.format(Locale.ROOT, "Feuerkugel schlägt nach %d Ticks ein, Feuer bei %s vor dem Grasblock %s", watch.removedAt(),
                fire.getFirst().toShortString(), block.toShortString()));
        for (BlockPos pos : fire) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    private static StepResult rocketGone(SelfTest.StepContext ctx, int ticks) {
        CovMdAWatch.Watch watch = (CovMdAWatch.Watch) ctx.state().get(WATCH);
        if (watch == null) {
            return StepResult.fail("Rakete nicht beobachtet");
        }
        if (watch.removedAt() < 0) {
            return ticks > 120 ? StepResult.fail("Rakete explodiert nicht") : StepResult.pending();
        }
        if (watch.maxAlong() < 3.0) {
            return StepResult.fail(String.format(Locale.ROOT, "Rakete nur %.1f m nach vorn", watch.maxAlong()));
        }
        return StepResult.pass(String.format(Locale.ROOT, "Rakete %.1f m nach vorn geflogen, nach %d Ticks explodiert", watch.maxAlong(), watch.removedAt()));
    }

    private static void hurt(MinecraftServer server, Map<String, Object> state, String label) {
        LivingEntity target = targetEntity(server, state);
        float before = (float) state.get(HEALTH);
        if (!(target.getHealth() < before) && target.isAlive()) {
            throw new IllegalStateException(label + ": " + CovMdAWatch.type(target) + " unverletzt (" + target.getHealth() + ")");
        }
        note(state, String.format(Locale.ROOT, "%s: %s %.0f → %.0f Leben (%s)", label, CovMdAWatch.type(target), before, target.isAlive() ? target.getHealth() : 0.0F,
                target.getLastDamageSource() == null ? "-" : target.getLastDamageSource().typeHolder().getRegisteredName()));
    }

    private static void item(MinecraftServer server, Map<String, Object> state, ItemStack stack) {
        Entity drone = drone(server, state);
        ServerLevel level = Arena.level(server);
        DispenserRig.load(drone, stack);
        Set<UUID> before = new HashSet<>();
        level.getEntitiesOfClass(ItemEntity.class, Arena.box(level)).forEach(item -> before.add(item.getUUID()));
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (entry.result() == null || !entry.result().success()) {
            throw new IllegalStateException(name + ": " + entry.status());
        }
        ItemEntity item = null;
        for (ItemEntity candidate : level.getEntitiesOfClass(ItemEntity.class, new AABB(entry.muzzle(), entry.muzzle()).inflate(3.0))) {
            if (!before.contains(candidate.getUUID()) && candidate.getItem().is(stack.getItem())) {
                item = candidate;
            }
        }
        if (item == null) {
            throw new IllegalStateException(name + ": " + entry.status() + ", kein Gegenstand vor der Mündung");
        }
        double along = item.getDeltaMovement().dot(entry.forward().normalize());
        if (!(along > 0.1)) {
            throw new IllegalStateException(String.format(Locale.ROOT, "%s fliegt nicht nach vorn: %.3f Blöcke je Tick", name, along));
        }
        Arena.track(item);
        note(state, String.format(Locale.ROOT, "%s → Gegenstand mit %.1f m/s nach vorn (Ergebnis %s)", name, along * 20.0, entry.status()));
    }

    private static void noPearl(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        List<ThrownEnderpearl> pearls = level.getEntitiesOfClass(ThrownEnderpearl.class, Arena.box(level));
        if (!pearls.isEmpty()) {
            throw new IllegalStateException(pearls.size() + " geworfene Enderperlen");
        }
        note(state, "keine geworfene Enderperle");
    }

    private static void tntFuse(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        DispenserRig.load(drone, new ItemStack(Items.TNT));
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        Entity shot = entry.result() == null ? null : entry.result().entity().orElse(null);
        if (!(shot instanceof PrimedTnt tnt)) {
            throw new IllegalStateException("TNT: " + entry.status() + " " + CovMdAWatch.type(shot));
        }
        int fuse = tnt.getFuse();
        double fromMuzzle = tnt.position().distanceTo(entry.muzzle());
        tnt.discard();
        if (fuse != TNT_FUSE) {
            throw new IllegalStateException("Zündzeit " + fuse + " statt " + TNT_FUSE + " Ticks");
        }
        if (fromMuzzle > 1.0) {
            throw new IllegalStateException(String.format(Locale.ROOT, "TNT %.2f m von der Mündung", fromMuzzle));
        }
        note(state, String.format(Locale.ROOT, "TNT gezündet %.2f m von der Mündung, Zündzeit %d Ticks (%.1f s)", fromMuzzle, fuse, fuse / 20.0));
    }

    private static void uptiltRange(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos pillar = Arena.ground(0, -6).above();
        Arena.place(level, pillar, Blocks.STONE.defaultBlockState());
        player.setYRot(Direction.NORTH.toYRot());
        player.setYHeadRot(Direction.NORTH.toYRot());
        DroneData data = DroneData.of(DispenserRig.fiveInch()).withTune(new TuneSpec(Map.of(TuneSpec.CAMERA_UPTILT, (float) UPTILT))).withName("CovMdA Uptilt");
        ItemStack stack = PropwashApi.builds().createDrone(data);
        Vec3 click = new Vec3(pillar.getX() + 0.5, pillar.getY() + 1.0, pillar.getZ() + 0.5);
        Set<UUID> before = new HashSet<>();
        PropwashApi.drones().dronesNear(level, click, 4.0, false).forEach(view -> before.add(view.entity().getUUID()));
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(click, Direction.UP, pillar, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        Entity drone = null;
        for (DroneView view : PropwashApi.drones().dronesNear(level, click, 4.0, false)) {
            if (!before.contains(view.entity().getUUID())) {
                drone = Arena.track(view.entity());
            }
        }
        if (drone == null) {
            throw new IllegalStateException("Drohne mit Uptilt nicht abgestellt");
        }
        state.put(DRONE, drone.getUUID());
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 4));
    }

    private static void uptiltShot(MinecraftServer server, Map<String, Object> state) {
        Entity drone = drone(server, state);
        CameraPose camera = PropwashApi.drones().cameraPose(drone).orElseThrow(() -> new IllegalStateException("cameraPose fehlt (API-Befund)"));
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        Entity shot = entry.result() == null ? null : entry.result().entity().orElse(null);
        if (!(shot instanceof AbstractArrow)) {
            throw new IllegalStateException("Pfeil: " + entry.status());
        }
        Arena.track(shot);
        Vec3 own = entry.launch().subtract(entry.droneVelocity().scale(1.0 / 20.0)).normalize();
        double toMount = degrees(own, entry.forward());
        double toCamera = degrees(own, camera.look());
        double cameraTilt = degrees(camera.look(), entry.forward());
        double vertical = Math.toDegrees(Math.atan2(own.dot(camera.up().normalize()), own.dot(camera.look().normalize())));
        String text = String.format(Locale.ROOT, "Pfeil %.1f° von MountPose#forward, %.1f° von der Kamera-Achse; Kamera %.1f° über der Nase; Einschlag %.1f° %s der Bildmitte",
                toMount, toCamera, cameraTilt, Math.abs(vertical), vertical < 0.0 ? "unter" : "über");
        if (toMount > MAX_DEGREES || Math.abs(cameraTilt - UPTILT) > 3.0 || toCamera < UPTILT - MAX_DEGREES || vertical > -(UPTILT - MAX_DEGREES)) {
            throw new IllegalStateException(text);
        }
        note(state, text);
    }
}
