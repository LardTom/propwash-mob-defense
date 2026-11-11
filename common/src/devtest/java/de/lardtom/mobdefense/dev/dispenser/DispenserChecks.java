package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dispenser.DispenserAmmo;
import de.lardtom.mobdefense.dispenser.DispenserBehavior;
import de.lardtom.mobdefense.dispenser.DispenserState;
import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.build.DroneData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.DispenserBlock;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class DispenserChecks {
    public static final int PREPARE_TICK = 1;
    public static final int RIGHTS_TICK = 10;
    public static final int MATRIX_TICK = 12;
    public static final int MACRO_TICK = 14;
    public static final int RESULT_TICK = 88;
    public static final int MATRIX_OFFSET = 48;
    public static final int MACRO_OFFSET = -48;
    private static @Nullable PvpMatrix matrix;
    private static @Nullable MacroRun macro;

    private DispenserChecks() {
    }

    public static String behavior(MinecraftServer server) {
        AccessoryBehavior behavior = PropwashApi.accessories().behavior(MobDefense.DISPENSER_MOUNT)
                .orElseThrow(() -> new IllegalStateException("kein Verhalten für " + MobDefense.DISPENSER_MOUNT));
        if (behavior != DispenserBehavior.INSTANCE || behavior.slots() != SlotCycle.SLOTS) {
            throw new IllegalStateException("Verhalten " + behavior + " mit " + behavior.slots() + " Slots");
        }
        if (!behavior.actionNameKey(1).equals(Optional.of(DispenserBehavior.FIRE_KEY))
                || !behavior.actionNameKey(2).equals(Optional.of(DispenserBehavior.NEXT_SLOT_KEY))
                || behavior.actionNameKey(3).isPresent() || behavior.actionNameKey(4).isPresent()) {
            throw new IllegalStateException("actionNameKey liefert " + behavior.actionNameKey(1) + ", " + behavior.actionNameKey(2) + ", "
                    + behavior.actionNameKey(3) + ", " + behavior.actionNameKey(4));
        }
        if (!DispenserState.registered()) {
            throw new IllegalStateException("Drohnen-Daten mobdefense:dispenser nicht angemeldet");
        }
        if (PropwashApi.droneData().find(MobDefense.id(DispenserState.PATH)).isEmpty()) {
            throw new IllegalStateException("Drohnen-Daten " + MobDefense.id(DispenserState.PATH) + " fehlen bei Propwash");
        }
        int items = 0;
        double lightest = Double.MAX_VALUE;
        List<String> problems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            ItemStack stack = new ItemStack(item);
            if (!"minecraft".equals(id.getNamespace()) || !(DispenserBlock.DISPENSER_REGISTRY.containsKey(item) || item instanceof ProjectileItem
                    || item instanceof SpawnEggItem || stack.has(DataComponents.EQUIPPABLE))) {
                continue;
            }
            items++;
            double grams = DispenserAmmo.grams(stack);
            lightest = Math.min(lightest, grams);
            if (!(grams > 0.0) || !behavior.accepts(0, stack) || behavior.maxStackSize(0, stack) < 1) {
                problems.add(id + ": " + grams + " g, Grenze " + behavior.maxStackSize(0, stack));
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(problems.size() + " Items ohne Gewicht oder Grenze: "
                    + String.join(", ", problems.subList(0, Math.min(8, problems.size()))));
        }
        if (behavior.accepts(0, PropwashApi.builds().createDrone(DroneData.of(DispenserRig.fiveInch())))) {
            throw new IllegalStateException("Drohne passt in den Dispenser");
        }
        double full = behavior.payloadGrams(List.of(new ItemStack(Items.TNT, AmmoTable.TNT_LIMIT))).orElse(-1.0);
        return String.format(Locale.ROOT, "Verhalten mit 4 Slots, Aktion 1 „%s“, Aktion 2 „%s“, Drohnen-Daten angemeldet; %d Vanilla-Items mit "
                + "Dispenser-Verhalten alle mit Gewicht (leichtestes %.1f g) und Stapelgrenze; vier TNT %.0f g; Drohnen abgelehnt",
                DispenserBehavior.FIRE_KEY, DispenserBehavior.NEXT_SLOT_KEY, items, lightest, full);
    }

    public static String prepare(MinecraftServer server) {
        BlockPos spawn = server.overworld().getRespawnData().pos();
        int chunks = forceChunks(server, spawn.offset(-8, 0, -8), RightsCheck.OUTSIDE + 24)
                + forceChunks(server, spawn.offset(MATRIX_OFFSET, 0, 0), PvpMatrix.LANE_SPACING * PvpMatrix.Payload.values().length)
                + forceChunks(server, spawn.offset(MACRO_OFFSET, 0, 0), 8);
        return chunks + " Chunks dauerhaft geladen für D3, D5 und D7";
    }

    public static String rights(MinecraftServer server) {
        return RightsCheck.run(server);
    }

    public static String startMatrix(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos origin = level.getRespawnData().pos().offset(MATRIX_OFFSET, 0, 0);
        matrix = PvpMatrix.start(server, level, origin, Guests.create(server, "md_d3_shooter"), List.of(PvpMatrix.Payload.values()), PvpMatrix.COMBOS,
                (pos, state) -> level.setBlockAndUpdate(pos, state));
        return "gestartet bei " + origin.toShortString() + ", " + PvpMatrix.Payload.values().length + " Bahnen, " + PvpMatrix.COMBOS.size() + " Kombinationen";
    }

    public static String startMacro(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockPos origin = level.getRespawnData().pos().offset(MACRO_OFFSET, 0, 0);
        macro = MacroRun.start(server, level, origin, Guests.create(server, "md_d5_macro"));
        return "gestartet bei " + origin.toShortString();
    }

    public static String matrixResult(MinecraftServer server) {
        return Runs.result(matrix, "D3");
    }

    public static String macroResult(MinecraftServer server) {
        return Runs.result(macro, "D5");
    }

    static int forceChunks(MinecraftServer server, BlockPos origin, int reach) {
        ServerLevel level = server.overworld();
        ChunkPos from = ChunkPos.containing(origin.offset(-8, 0, -8));
        ChunkPos to = ChunkPos.containing(origin.offset(reach + 8, 0, reach + 8));
        int count = 0;
        for (int x = from.x(); x <= to.x(); x++) {
            for (int z = from.z(); z <= to.z(); z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
                count++;
            }
        }
        return count;
    }
}
