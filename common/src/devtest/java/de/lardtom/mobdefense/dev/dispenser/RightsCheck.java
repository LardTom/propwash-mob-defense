package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.dispenser.core.FireGate;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RightsCheck {
    public static final int SPACING = 3;
    public static final int OUTSIDE = 40;
    public static final int SPAWN_RADIUS = 16;
    private static final NameAndId ADMIN = new NameAndId(UUID.fromString("00000000-0000-4000-8000-00000000d7d7"), "mobdefense_d7_admin");

    private RightsCheck() {
    }

    private record Case(String label, ItemStack ammo) {
    }

    private static final List<Case> BLOCK_CASES = List.of(
            new Case("TNT", new ItemStack(Items.TNT)),
            new Case("Wassereimer", new ItemStack(Items.WATER_BUCKET)),
            new Case("Feuerzeug", new ItemStack(Items.FLINT_AND_STEEL)),
            new Case("Knochenmehl", new ItemStack(Items.BONE_MEAL, 4)),
            new Case("Kürbis", new ItemStack(Items.CARVED_PUMPKIN)));

    public static String run(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos spawn = level.getRespawnData().pos();
        boolean adminWasOp = server.getPlayerList().isOp(ADMIN);
        int radiusBefore = server instanceof DedicatedServer dedicated ? dedicated.spawnProtectionRadius() : SPAWN_RADIUS;
        Rules rules = new Rules(server).set(RuleDefaults.ALLOW_FLUID_BUCKETS, true);
        List<String> notes = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        List<Entity> drones = new ArrayList<>();
        try {
            if (!adminWasOp) {
                server.getPlayerList().op(ADMIN);
            }
            if (radiusBefore < SPAWN_RADIUS && server instanceof DedicatedServer dedicated) {
                dedicated.setSpawnProtectionRadius(SPAWN_RADIUS);
                notes.add("Spawnschutz für D7 vorübergehend " + SPAWN_RADIUS + " statt " + radiusBefore);
            }
            BlockPos outside = spawn.offset(OUTSIDE, 0, OUTSIDE);
            notes.add("Spawnschutz: " + round(server, level, spawn.offset(-6, 0, 2), guest(server, "md_d7_spawn"), GameType.SURVIVAL, BLOCK_CASES, true,
                    drones, problems));
            notes.add("Abenteuermodus: " + round(server, level, outside, guest(server, "md_d7_adventure"), GameType.ADVENTURE, BLOCK_CASES, true, drones,
                    problems));
            notes.add("Gegenprobe Überleben außerhalb: " + round(server, level, outside.offset(0, 0, 8), guest(server, "md_d7_survival"), GameType.SURVIVAL,
                    List.of(new Case("TNT", new ItemStack(Items.TNT)), new Case("Pfeil", new ItemStack(Items.ARROW, 2))), false, drones, problems));
        } finally {
            if (server instanceof DedicatedServer dedicated && dedicated.spawnProtectionRadius() != radiusBefore) {
                dedicated.setSpawnProtectionRadius(radiusBefore);
            }
            if (!adminWasOp) {
                server.getPlayerList().deop(ADMIN);
            }
            rules.restore();
            for (Entity drone : drones) {
                drone.discard();
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        return String.join(" | ", notes);
    }

    private static ServerPlayer guest(MinecraftServer server, String name) {
        ServerPlayer guest = Guests.create(server, name);
        if (server.getPlayerList().isOp(guest.nameAndId())) {
            throw new IllegalStateException("Gast " + name + " ist Operator");
        }
        return guest;
    }

    private static String round(MinecraftServer server, ServerLevel level, BlockPos origin, ServerPlayer guest, GameType mode, List<Case> cases,
                                boolean expectDenied, List<Entity> drones, List<String> problems) {
        List<String> out = new ArrayList<>();
        boolean protectedHere = server.isUnderSpawnProtection(level, origin, guest);
        if (expectDenied && mode == GameType.SURVIVAL && !protectedHere) {
            problems.add("Spawnschutz greift bei " + origin.toShortString() + " nicht (spawn-protection in server.properties, Operator vorhanden?)");
            return "ohne Spawnschutz";
        }
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            BlockPos column = origin.offset(i * SPACING, 0, 0);
            level.getChunk(column);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            guest.setGameMode(GameType.SURVIVAL);
            Entity drone = DispenserRig.onPillar(level, guest, DispenserRig.fiveInch(), "D7 " + c.label(), new BlockPos(column.getX(), surface, column.getZ()),
                    Direction.EAST, (pos, state) -> level.setBlockAndUpdate(pos, state));
            drones.add(drone);
            DispenserRig.load(drone, c.ammo());
            AABB box = drone.getBoundingBox().inflate(4.0);
            Map<BlockPos, BlockState> before = blocks(level, box);
            int tntBefore = level.getEntitiesOfClass(PrimedTnt.class, box.inflate(4.0)).size();
            guest.setGameMode(mode);
            DispenserLog.Entry entry = DispenserRig.fire(drone, guest).orElse(null);
            String status = entry == null ? "keine Reaktion" : entry.status();
            int changed = changed(level, before);
            List<PrimedTnt> tnt = level.getEntitiesOfClass(PrimedTnt.class, box.inflate(4.0));
            if (expectDenied) {
                if (!"DENIED".equals(status)) {
                    problems.add(c.label() + (protectedHere ? " im Spawnschutz" : " im Abenteuermodus") + ": " + status + " statt DENIED");
                }
                if (changed != 0 || tnt.size() != tntBefore) {
                    problems.add(c.label() + ": " + changed + " Blöcke verändert, " + (tnt.size() - tntBefore) + " TNT gezündet");
                }
                if (entry == null || entry.notice() != FireGate.Notice.NO_PERMISSION) {
                    problems.add(c.label() + ": OSD-Hinweis " + (entry == null ? "keiner" : entry.notice()) + " statt NO_PERMISSION");
                }
                if (!ItemStack.matches(DispenserRig.inventory(drone).get(0), c.ammo())) {
                    problems.add(c.label() + ": Munition verbraucht trotz DENIED");
                }
            } else if (!"PLACED".equals(status) && !"FIRED".equals(status)) {
                problems.add(c.label() + " mit Recht: " + status);
            }
            for (PrimedTnt primed : tnt) {
                primed.discard();
            }
            if (entry != null && entry.result() != null) {
                entry.result().entity().ifPresent(Entity::discard);
            }
            out.add(c.label() + " " + status + (changed == 0 ? "" : ", " + changed + " Blöcke"));
        }
        return (protectedHere ? "unter Spawnschutz, " : "ohne Spawnschutz, ") + guest.gameMode() + ": " + String.join(", ", out);
    }

    private static Map<BlockPos, BlockState> blocks(ServerLevel level, AABB box) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            out.put(pos.immutable(), level.getBlockState(pos));
        }
        return out;
    }

    private static int changed(ServerLevel level, Map<BlockPos, BlockState> before) {
        int changed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
            if (level.getBlockState(entry.getKey()) != entry.getValue()) {
                changed++;
            }
        }
        return changed;
    }
}
