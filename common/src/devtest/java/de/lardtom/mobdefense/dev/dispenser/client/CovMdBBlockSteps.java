package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.CovMdBLanes;
import de.lardtom.mobdefense.dev.dispenser.CovMdBLanes.Lane;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.Rules;
import de.lardtom.mobdefense.dispenser.DispenserAmmo;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.mobdefense.dispenser.core.FireGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public final class CovMdBBlockSteps {
    private static final String LANES = "covmdb.block.lanes";
    private static final String NOTE = "covmdb.block.note";
    private static final String WATER = "covmdb.block.water";
    private static final String TNT = "covmdb.block.tnt";
    private static final String DRONES = "covmdb.block.drones";
    private static final String NOTES = "covmdb.block.notes";
    private static final String PROBLEMS = "covmdb.block.problems";
    private static final int INTERVAL = 3;
    private static final int ROW_Z = 18;
    private static final int LANE_SPACING = 4;
    private static final int PILLAR = 2;

    private CovMdBBlockSteps() {
    }

    @FunctionalInterface
    private interface Prep {
        void run(ServerLevel level, Lane lane);
    }

    @FunctionalInterface
    private interface Check {
        String run(ServerLevel level, Lane lane, DispenserLog.Entry entry, ItemStack after);
    }

    private record Case(String label, Supplier<ItemStack> item, Prep prep, Check check) {
    }

    public static void register(SelfTest.Steps steps) {
        List<Case> cases = cases();
        steps.add("Block-Verhalten wie Vanilla – Schere, Feuerzeug, Rüstung, Kürbis und Schädel, Shulkerkiste, Glowstone, Wachs, Pinsel, Boot und Lore",
                2400, Script.create()
                        .server("Bahnen mit Drohnen", (server, state) -> state.put(DRONES, drones(server, cases.size(), ROW_Z)))
                        .then("Probe-Schuss je Bahn für den Mündungsblock", CovMdBEach.each(state -> cases.size(), INTERVAL, CovMdBBlockSteps::probe))
                        .sleep(6)
                        .then("Ziele aufbauen und feuern", CovMdBEach.each(state -> cases.size(), INTERVAL, (server, state, i) -> fireCase(server, state,
                                cases.get(i), i)))
                        .then("Ergebnis", CovMdBBlockSteps::summary));
        steps.add("Eimer gesperrt – jeder Flüssigkeits- und Tier-Eimer aus dem Register, kein Block, Eimer bleibt im Slot", 1600, Script.create()
                .then("je Eimer eine Drohne feuern", CovMdBEach.each(state -> buckets().size(), INTERVAL, CovMdBBlockSteps::bucket))
                .then("Ergebnis", CovMdBBlockSteps::bucketSummary));
        steps.add("Leerer Eimer schöpft – Wasserquelle weg, Wassereimer im Slot, zweiter Schuss gesperrt", 1200, Script.create()
                .server("Drohne vor einer Wasserquelle", (server, state) -> state.put(DRONES, drones(server, 1, ROW_Z + 8)))
                .then("Probe-Schuss", CovMdBEach.each(state -> 1, INTERVAL, CovMdBBlockSteps::probe))
                .sleep(6)
                .server("leerer Eimer schöpft", CovMdBBlockSteps::scoop)
                .then("Schöpfen", CovMdBBlockSteps::note)
                .sleep(6)
                .server("zweiter Schuss mit Wassereimer", CovMdBBlockSteps::secondShot)
                .then("Zweiter Schuss", CovMdBBlockSteps::note));
        steps.add("TNT mit tnt_explodes false – nach 80 Ticks keine Explosion, Blöcke und Zombie unverändert", 1200, Script.create()
                .server("Drohne auf hohem Sockel, Zombie darunter, tnt_explodes false", CovMdBBlockSteps::tntScene)
                .server("TNT abwerfen", CovMdBBlockSteps::tntFire)
                .then("Wurf", CovMdBBlockSteps::note)
                .sleep(100)
                .server("nach 100 Ticks", CovMdBBlockSteps::tntAfter)
                .then("Ergebnis", CovMdBBlockSteps::note));
    }

    private static StepResult note(SelfTest.StepContext ctx, int ticks) {
        Object note = ctx.state().remove(NOTE);
        return note == null ? StepResult.pass() : StepResult.pass(note.toString());
    }

    private static List<Entity> drones(MinecraftServer server, int count, int rowZ) {
        List<Entity> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BlockPos ground = Arena.ground(-24 + i * LANE_SPACING, rowZ);
            out.add(CovMdBLanes.pillarDrone(server, ground, PILLAR, DispenserRig.fiveInch(), "CovMdB " + i));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void probe(MinecraftServer server, Map<String, Object> state, int index) {
        List<Entity> drones = (List<Entity>) state.get(DRONES);
        List<Lane> lanes = (List<Lane>) state.computeIfAbsent(LANES, key -> new ArrayList<Lane>());
        Entity drone = drones.get(index);
        BlockPos pillar = BlockPos.containing(drone.getX(), drone.getY() - 0.5, drone.getZ());
        lanes.add(CovMdBLanes.probe(server, drone, pillar));
    }

    @SuppressWarnings("unchecked")
    private static List<Lane> lanes(Map<String, Object> state) {
        return (List<Lane>) state.get(LANES);
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Map<String, Object> state, String key) {
        return (List<String>) state.computeIfAbsent(key, name -> new ArrayList<String>());
    }

    private static void fireCase(MinecraftServer server, Map<String, Object> state, Case test, int index) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Lane lane = lanes(state).get(index);
        try {
            Arena.place(level, lane.target(), level.getBlockState(lane.target()));
            test.prep().run(level, lane);
            DispenserRig.load(lane.drone(), test.item().get());
            DispenserLog.Entry entry = DispenserRig.fire(lane.drone(), player).orElseThrow(() -> new IllegalStateException("kein Schuss"));
            list(state, NOTES).add(test.label() + ": " + entry.status() + ", " + test.check().run(level, lane, entry, lane.slot()));
        } catch (RuntimeException e) {
            list(state, PROBLEMS).add(test.label() + ": " + e.getMessage());
        }
    }

    private static StepResult summary(SelfTest.StepContext ctx, int ticks) {
        List<String> notes = list(ctx.state(), NOTES);
        List<String> problems = list(ctx.state(), PROBLEMS);
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems) + (notes.isEmpty() ? "" : " | in Ordnung: " + String.join("; ", notes)));
        }
        return StepResult.pass(String.join("; ", notes));
    }

    private static void used(DispenserLog.Entry entry) {
        if (!"USED".equals(entry.status())) {
            throw new IllegalStateException(entry.status() + " statt USED");
        }
    }

    private static void floor(ServerLevel level, Lane lane) {
        Arena.place(level, lane.target().below(), Blocks.STONE.defaultBlockState());
    }

    private static <T extends Entity> T entity(ServerLevel level, EntityType<T> type, Lane lane) {
        T entity = type.create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            throw new IllegalStateException(BuiltInRegistries.ENTITY_TYPE.getKey(type) + " nicht erzeugt");
        }
        Vec3 at = CovMdBLanes.center(lane.target());
        entity.snapTo(at.x, at.y, at.z, CovMdBLanes.FACING.getOpposite().toYRot(), 0.0F);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        return Arena.spawn(level, entity);
    }

    private static <T extends Entity> List<T> near(ServerLevel level, Class<T> type, BlockPos pos, double radius) {
        List<T> found = level.getEntitiesOfClass(type, new AABB(pos).inflate(radius), entity -> true);
        found.forEach(Arena::track);
        return found;
    }

    private static List<ItemEntity> drops(ServerLevel level, BlockPos pos, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3.0), drop -> drop.getItem().is(item));
    }

    private static String damage(ItemStack after, Item item, int expected) {
        if (!after.is(item) || after.getDamageValue() != expected) {
            throw new IllegalStateException("im Slot " + after + " mit Abnutzung " + after.getDamageValue() + " statt " + expected);
        }
        return id(item) + " im Slot, Haltbarkeit −" + expected;
    }

    private static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getPath();
    }

    private static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        out.add(new Case("Schere", () -> new ItemStack(Items.SHEARS), (level, lane) -> {
            floor(level, lane);
            entity(level, EntityTypes.SHEEP, lane);
        }, (level, lane, entry, after) -> {
            used(entry);
            Sheep sheep = near(level, Sheep.class, lane.target(), 1.0).stream().findFirst().orElseThrow(() -> new IllegalStateException("Schaf fehlt"));
            if (!sheep.isSheared()) {
                throw new IllegalStateException("Schaf nicht geschoren");
            }
            List<ItemEntity> wool = level.getEntitiesOfClass(ItemEntity.class, new AABB(lane.target()).inflate(3.0), drop -> drop.getItem().is(ItemTags.WOOL));
            if (wool.isEmpty()) {
                throw new IllegalStateException("keine Wolle gefallen");
            }
            return "Schaf geschoren, " + wool.stream().mapToInt(drop -> drop.getItem().getCount()).sum() + " Wolle gefallen, " + damage(after, Items.SHEARS, 1);
        }));
        out.add(new Case("Feuerzeug vor TNT", () -> new ItemStack(Items.FLINT_AND_STEEL), (level, lane) -> Arena.place(level, lane.target(),
                Blocks.TNT.defaultBlockState()), (level, lane, entry, after) -> {
            used(entry);
            List<PrimedTnt> primed = level.getEntitiesOfClass(PrimedTnt.class, new AABB(lane.target()).inflate(2.0), tnt -> true);
            boolean gone = level.getBlockState(lane.target()).isAir();
            primed.forEach(Entity::discard);
            if (primed.isEmpty() || !gone) {
                throw new IllegalStateException(primed.size() + " PrimedTnt, TNT-Block " + (gone ? "weg" : "noch da"));
            }
            return "TNT-Block gezündet (PrimedTnt, danach entschärft), " + damage(after, Items.FLINT_AND_STEEL, 1);
        }));
        out.add(new Case("Feuerzeug vor Holz", () -> new ItemStack(Items.FLINT_AND_STEEL), (level, lane) -> {
            Arena.place(level, lane.target().below(), Blocks.OAK_PLANKS.defaultBlockState());
            Arena.place(level, lane.target().relative(CovMdBLanes.FACING), Blocks.OAK_PLANKS.defaultBlockState());
        }, (level, lane, entry, after) -> {
            used(entry);
            BlockState fire = level.getBlockState(lane.target());
            level.setBlockAndUpdate(lane.target(), Blocks.AIR.defaultBlockState());
            if (!fire.is(BlockTags.FIRE)) {
                throw new IllegalStateException("vor dem Holz steht " + fire + " statt Feuer");
            }
            return "Feuer auf dem Holz (danach gelöscht), " + damage(after, Items.FLINT_AND_STEEL, 1);
        }));
        out.add(new Case("Rüstung anlegen", () -> new ItemStack(Items.IRON_HELMET), (level, lane) -> {
            floor(level, lane);
            entity(level, EntityTypes.ARMOR_STAND, lane).setNoGravity(true);
        }, (level, lane, entry, after) -> {
            used(entry);
            ArmorStand stand = near(level, ArmorStand.class, lane.target(), 1.0).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("Rüstungsständer fehlt"));
            if (!stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) || !after.isEmpty()) {
                throw new IllegalStateException("Kopf " + stand.getItemBySlot(EquipmentSlot.HEAD) + ", Slot " + after);
            }
            return "Rüstungsständer trägt den Eisenhelm im Kopf-Slot, Slot leer";
        }));
        out.add(new Case("Kürbis auf zwei Schneeblöcke", () -> new ItemStack(Items.CARVED_PUMPKIN), (level, lane) -> {
            Arena.place(level, lane.target().below(), Blocks.SNOW_BLOCK.defaultBlockState());
            Arena.place(level, lane.target().below(2), Blocks.SNOW_BLOCK.defaultBlockState());
        }, (level, lane, entry, after) -> {
            used(entry);
            List<SnowGolem> golems = near(level, SnowGolem.class, lane.target(), 3.0);
            if (golems.isEmpty()) {
                throw new IllegalStateException("kein Schneegolem, am Ziel " + level.getBlockState(lane.target()));
            }
            return "Kürbis gesetzt, Schneegolem entsteht, Schneeblöcke verbraucht " + level.getBlockState(lane.target().below()).isAir();
        }));
        out.add(new Case("Schädel auf Seelensand", () -> new ItemStack(Items.WITHER_SKELETON_SKULL), (level, lane) -> {
            Direction side = CovMdBLanes.FACING.getClockWise();
            BlockPos row = lane.target().below();
            for (BlockPos pos : List.of(row, row.relative(side), row.relative(side.getOpposite()), row.below())) {
                Arena.place(level, pos, Blocks.SOUL_SAND.defaultBlockState());
            }
            Arena.place(level, row.below().relative(side), Blocks.AIR.defaultBlockState());
            Arena.place(level, row.below().relative(side.getOpposite()), Blocks.AIR.defaultBlockState());
        }, (level, lane, entry, after) -> {
            used(entry);
            BlockState skull = level.getBlockState(lane.target());
            if (!skull.is(Blocks.WITHER_SKELETON_SKULL) && !skull.is(Blocks.WITHER_SKELETON_WALL_SKULL)) {
                throw new IllegalStateException("am Ziel " + skull + " statt Schädel");
            }
            return "Schädel als Block gesetzt (Seelensand-T wie bei Vanilla), Slot " + (after.isEmpty() ? "leer" : after);
        }));
        out.add(new Case("Schädel auf freien Platz", () -> new ItemStack(Items.WITHER_SKELETON_SKULL), CovMdBBlockSteps::floor,
                (level, lane, entry, after) -> {
                    BlockState at = level.getBlockState(lane.target());
                    if (!"BLOCKED".equals(entry.status()) || !at.isAir() || !after.is(Items.WITHER_SKELETON_SKULL)) {
                        throw new IllegalStateException("Vanilla setzt hier nichts: Status " + entry.status() + ", Ziel " + at + ", Slot " + after);
                    }
                    return "wie Vanilla nicht gesetzt (Vanilla setzt Witherskelettschädel nur auf ein Seelensand-T), Schädel bleibt im Slot";
                }));
        out.add(new Case("Shulkerkiste", () -> new ItemStack(Items.SHULKER_BOX), (level, lane) -> {
        }, (level, lane, entry, after) -> {
            used(entry);
            BlockState box = level.getBlockState(lane.target());
            if (!box.is(BlockTags.SHULKER_BOXES)) {
                throw new IllegalStateException("am Ziel " + box + " statt Shulkerkiste");
            }
            return "Shulkerkiste gesetzt, Slot " + (after.isEmpty() ? "leer" : after);
        }));
        out.add(new Case("Glowstone in den Seelenanker", () -> new ItemStack(Items.GLOWSTONE), (level, lane) -> Arena.place(level, lane.target(),
                Blocks.RESPAWN_ANCHOR.defaultBlockState()), (level, lane, entry, after) -> {
            used(entry);
            BlockState anchor = level.getBlockState(lane.target());
            if (!anchor.is(Blocks.RESPAWN_ANCHOR) || anchor.getValue(RespawnAnchorBlock.CHARGE) != 1) {
                throw new IllegalStateException("Anker " + anchor);
            }
            return "Seelenanker-Ladung 0 → 1";
        }));
        out.add(new Case("Wachs auf Kupfer", () -> new ItemStack(Items.HONEYCOMB), (level, lane) -> Arena.place(level, lane.target(),
                CovMdBLanes.block("minecraft:copper_block")), (level, lane, entry, after) -> {
            used(entry);
            BlockState copper = level.getBlockState(lane.target());
            if (!copper.is(CovMdBLanes.block("minecraft:waxed_copper_block").getBlock())) {
                throw new IllegalStateException("am Ziel " + copper + " statt gewachstem Kupfer");
            }
            return "Kupferblock gewachst";
        }));
        out.add(new Case("Pinsel am Gürteltier", () -> new ItemStack(Items.BRUSH), (level, lane) -> {
            floor(level, lane);
            entity(level, EntityTypes.ARMADILLO, lane);
        }, (level, lane, entry, after) -> {
            used(entry);
            List<ItemEntity> scutes = drops(level, lane.target(), Items.ARMADILLO_SCUTE);
            if (scutes.isEmpty()) {
                throw new IllegalStateException("keine Hornschuppe, Gürteltier " + near(level, Armadillo.class, lane.target(), 1.0).size());
            }
            return "Gürteltier lässt eine Hornschuppe fallen, " + damage(after, Items.BRUSH, 16);
        }));
        out.add(new Case("Boot über Wasser", () -> new ItemStack(Items.OAK_BOAT), (level, lane) -> {
            Arena.place(level, lane.target().below(), Blocks.STONE.defaultBlockState());
            Arena.place(level, lane.target(), Blocks.WATER.defaultBlockState());
        }, (level, lane, entry, after) -> {
            used(entry);
            List<AbstractBoat> boats = near(level, AbstractBoat.class, lane.target(), 3.0);
            level.setBlockAndUpdate(lane.target(), Blocks.AIR.defaultBlockState());
            if (boats.isEmpty()) {
                throw new IllegalStateException("kein Boot");
            }
            return "Boot-Entity über dem Wasser bei " + BlockPos.containing(boats.getFirst().position()).toShortString();
        }));
        out.add(new Case("Lore auf der Schiene", () -> new ItemStack(Items.MINECART), (level, lane) -> {
            floor(level, lane);
            Arena.place(level, lane.target(), Blocks.RAIL.defaultBlockState());
        }, (level, lane, entry, after) -> {
            used(entry);
            List<AbstractMinecart> carts = near(level, AbstractMinecart.class, lane.target(), 2.0);
            if (carts.isEmpty()) {
                throw new IllegalStateException("keine Lore");
            }
            return "Lore auf der Schiene bei " + BlockPos.containing(carts.getFirst().position()).toShortString();
        }));
        return out;
    }

    public static List<Item> buckets() {
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.BUCKET) {
                continue;
            }
            boolean fluid = item instanceof BucketItem bucket && bucket.getContent() != Fluids.EMPTY;
            if (fluid || item instanceof MobBucketItem || item instanceof SolidBucketItem) {
                out.add(item);
            }
        }
        return out;
    }

    private static int blocks(ServerLevel level, BlockPos from, BlockPos to) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            count += level.getBlockState(pos).isAir() ? 0 : 1;
        }
        return count;
    }

    private static void bucket(MinecraftServer server, Map<String, Object> state, int index) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Item item = buckets().get(index);
        BlockPos ground = Arena.ground(-24 + index * 5, ROW_Z + 16);
        Entity drone = CovMdBLanes.pillarDrone(server, ground, PILLAR, DispenserRig.fiveInch(), "CovMdB Eimer " + index);
        BlockPos top = ground.above(PILLAR);
        BlockPos from = top.offset(1, -PILLAR, -2);
        BlockPos to = top.offset(4, 2, 2);
        int before = blocks(level, from, to);
        AABB box = new AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to.offset(1, 1, 1)));
        int living = level.getEntitiesOfClass(LivingEntity.class, box, entity -> !(entity instanceof Player)).size();
        ItemStack stack = new ItemStack(item);
        if (!stack.is(DispenserAmmo.FLUID_BUCKETS)) {
            list(state, "covmdb.block.untagged").add(id(item));
        }
        DispenserRig.load(drone, stack);
        DispenserLog.Entry entry = DispenserRig.fire(drone, player).orElse(null);
        ItemStack after = DispenserRig.inventory(drone).get(0);
        int now = blocks(level, from, to);
        int livingNow = level.getEntitiesOfClass(LivingEntity.class, box, entity -> !(entity instanceof Player)).size();
        String status = entry == null ? "kein Eintrag" : entry.status();
        boolean locked = "BUCKET_LOCKED".equals(status) || "REFUSED".equals(status);
        boolean notice = entry != null && entry.notice() == FireGate.Notice.BUCKET_LOCKED;
        AmmoTable.Kind kind = DispenserAmmo.kind(stack);
        if (!locked || !notice || now != before || livingNow != living || !after.is(item) || after.getCount() != 1) {
            list(state, PROBLEMS).add(String.format(Locale.ROOT, "%s (%s): %s, Hinweis %s, Blöcke %d → %d, Lebewesen %d → %d, Slot %s", id(item), kind, status,
                    entry == null ? "-" : entry.notice(), before, now, living, livingNow, after));
        } else {
            list(state, NOTES).add(id(item) + " " + status);
        }
        drone.discard();
    }

    private static StepResult bucketSummary(SelfTest.StepContext ctx, int ticks) {
        List<String> untagged = list(ctx.state(), "covmdb.block.untagged");
        String tag = untagged.isEmpty() ? "alle im Tag mobdefense:dispenser_fluid_buckets" : "nicht im Tag mobdefense:dispenser_fluid_buckets (gesperrt über die "
                + "Munitionstabelle): " + untagged;
        List<String> problems = list(ctx.state(), PROBLEMS);
        List<String> notes = list(ctx.state(), NOTES);
        if (!problems.isEmpty()) {
            return StepResult.fail(String.join("; ", problems) + " | " + tag);
        }
        return StepResult.pass(notes.size() + " Eimer gesperrt (kein Block, keine Flüssigkeit, kein Tier, Eimer bleibt, Warnung BUCKET_LOCKED): "
                + String.join(", ", notes) + "; " + tag);
    }

    private static void scoop(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Lane lane = lanes(state).getFirst();
        BlockPos target = lane.target();
        Direction side = CovMdBLanes.FACING.getClockWise();
        for (BlockPos pos : List.of(target.below(), target.relative(side), target.relative(side.getOpposite()), target.relative(CovMdBLanes.FACING))) {
            Arena.place(level, pos, Blocks.GLASS.defaultBlockState());
        }
        Arena.place(level, target, Blocks.WATER.defaultBlockState());
        DispenserRig.load(lane.drone(), new ItemStack(Items.BUCKET));
        DispenserLog.Entry entry = DispenserRig.fire(lane.drone(), player).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        ItemStack after = lane.slot();
        boolean source = level.getFluidState(target).is(Fluids.WATER);
        if (!"USED".equals(entry.status()) || source || !after.is(Items.WATER_BUCKET)) {
            throw new IllegalStateException("Schöpfen: " + entry.status() + ", Quelle " + (source ? "noch da" : "weg") + ", Slot " + after);
        }
        state.put(WATER, target);
        state.put(NOTE, "leerer Eimer: USED, Wasserquelle bei " + target.toShortString() + " weg, im Slot jetzt " + id(after.getItem()));
    }

    private static void secondShot(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        Lane lane = lanes(state).getFirst();
        BlockPos target = (BlockPos) state.get(WATER);
        DispenserLog.Entry entry = DispenserRig.fire(lane.drone(), player).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        ItemStack after = lane.slot();
        int water = 0;
        for (BlockPos pos : BlockPos.betweenClosed(target.offset(-2, -2, -2), target.offset(2, 2, 2))) {
            water += level.getFluidState(pos).isEmpty() ? 0 : 1;
        }
        boolean locked = "BUCKET_LOCKED".equals(entry.status()) || "REFUSED".equals(entry.status());
        if (!locked || water != 0 || !after.is(Items.WATER_BUCKET)) {
            throw new IllegalStateException("zweiter Schuss: " + entry.status() + ", " + water + " Wasserblöcke, Slot " + after);
        }
        state.put(NOTE, "zweiter Schuss " + entry.status() + " (Hinweis " + entry.notice() + "), kein Wasser gesetzt, Wassereimer bleibt im Slot");
    }

    private static void tntScene(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        BlockPos ground = Arena.ground(-24, ROW_Z + 26);
        Entity drone = CovMdBLanes.pillarDrone(server, ground, 6, DispenserRig.fiveInch(), "CovMdB TNT");
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) {
            throw new IllegalStateException("Zombie nicht erzeugt");
        }
        Vec3 at = DispenserRig.ahead(drone, 2.5);
        zombie.snapTo(at.x, ground.getY() + 1.0, at.z, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        Arena.spawn(level, zombie);
        new Rules(server).set(Rules.TNT_EXPLODES, false);
        Map<String, Object> tnt = new HashMap<>();
        tnt.put("drone", drone);
        tnt.put("zombie", zombie);
        tnt.put("health", zombie.getHealth());
        tnt.put("from", ground.offset(-4, -3, -6));
        tnt.put("to", ground.offset(10, 8, 6));
        tnt.put("blocks", blocks(level, ground.offset(-4, -3, -6), ground.offset(10, 8, 6)));
        state.put(TNT, tnt);
    }

    @SuppressWarnings("unchecked")
    private static void tntFire(MinecraftServer server, Map<String, Object> state) {
        Map<String, Object> tnt = (Map<String, Object>) state.get(TNT);
        Entity drone = (Entity) tnt.get("drone");
        DispenserRig.load(drone, new ItemStack(Items.TNT, 1));
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("kein Schuss"));
        tnt.put("status", entry.status());
        state.put(NOTE, "tnt_explodes false: Wurf " + entry.status() + " (Hinweis " + entry.notice() + "), Slot danach " + DispenserRig.inventory(drone).get(0));
    }

    @SuppressWarnings("unchecked")
    private static void tntAfter(MinecraftServer server, Map<String, Object> state) {
        ServerLevel level = Arena.level(server);
        Map<String, Object> tnt = (Map<String, Object>) state.get(TNT);
        Zombie zombie = (Zombie) tnt.get("zombie");
        float health = (float) tnt.get("health");
        int before = (int) tnt.get("blocks");
        int now = blocks(level, (BlockPos) tnt.get("from"), (BlockPos) tnt.get("to"));
        BlockPos from = (BlockPos) tnt.get("from");
        BlockPos to = (BlockPos) tnt.get("to");
        int primed = level.getEntitiesOfClass(PrimedTnt.class, new AABB(Vec3.atLowerCornerOf(from), Vec3.atLowerCornerOf(to)).inflate(8.0), e -> true).size();
        if (now != before || !zombie.isAlive() || zombie.getHealth() < health || primed != 0) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Blöcke %d → %d, Zombie %.1f → %.1f, %d PrimedTnt", before, now, health,
                    zombie.getHealth(), primed));
        }
        state.put(NOTE, String.format(Locale.ROOT, "nach 100 Ticks keine Explosion: %d Blöcke unverändert, Zombie %.0f/%.0f, kein PrimedTnt (Wurf %s, wie "
                + "Vanilla: der Dispenser wirft bei tnt_explodes false nichts)", now, zombie.getHealth(), zombie.getMaxHealth(), tnt.get("status")));
    }
}
