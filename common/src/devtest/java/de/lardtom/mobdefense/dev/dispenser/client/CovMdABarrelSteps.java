package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.propwash.api.client.hud.FlightTelemetry;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CovMdABarrelSteps {
    public static final double SAME_MUZZLE = 0.001;
    private static final String COUNT = "covmda.barrel.count";
    private static final String PILLAR = "covmda.barrel.pillar";
    private static final String MUZZLE = "covmda.barrel.muzzle";
    private static final String WALL = "covmda.barrel.wall";

    private CovMdABarrelSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Lauf blockiert und leer – Mündung in der Wand: BLOCKED, Warnung BARREL BLOCKED/LAUF BLOCKIERT, Ton dispenser.fail, Slot unverändert; "
                + "leer: NO AMMO, DSP --, Ton dispenser.fail", 3000, Script.create()
                .server("5″ auf dem Sockel, Nase über der Kante", CovMdABarrelSteps::pillar)
                .append(Pilot.fly())
                .sleep(20)
                .server("Mündung aus Propwash (leerer Schuss)", CovMdABarrelSteps::probe)
                .then("Mündung vor dem Sockel", (ctx, ticks) -> StepResult.pass(String.valueOf(ctx.state().get(WALL))))
                .sleep(20)
                .run("Töne mitschreiben", () -> {
                    CovMdASounds.listen();
                    CovMdASounds.clear();
                })
                .server("Wand an die Mündung, feuern", (server, state) -> {
                    wall(server, state);
                    fire(server, state, "BLOCKED");
                })
                .then("Warnung Englisch", (ctx, ticks) -> warning(ctx, ticks, Notice.BARREL_BLOCKED, "BARREL BLOCKED"))
                .run("Sprache Deutsch", () -> Shots.language(Minecraft.getInstance(), Shots.GERMAN))
                .then("Warnung Deutsch", (ctx, ticks) -> warning(ctx, ticks, Notice.BARREL_BLOCKED, "LAUF BLOCKIERT"))
                .run("Sprache zurück", () -> Shots.restoreLanguage(Minecraft.getInstance()))
                .then("Ton dispenser.fail", (ctx, ticks) -> fail(ctx, ticks, 1))
                .append(Shots.photos("covmda_barrel_blocked"))
                .sleep(45)
                .server("Dispenser leeren, feuern", (server, state) -> {
                    DispenserRig.load(Pilot.serverDrone(server, state));
                    fire(server, state, "EMPTY");
                })
                .then("Warnung NO AMMO", (ctx, ticks) -> warning(ctx, ticks, Notice.NO_AMMO, "NO AMMO"))
                .then("Anzeige DSP --", CovMdABarrelSteps::emptyLine)
                .then("zweiter Ton dispenser.fail", (ctx, ticks) -> fail(ctx, ticks, 2)), CovMdABarrelSteps::cleanup);
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .run("Tasten los", () -> Pilot.land(Minecraft.getInstance()))
            .server("Flug beenden", (server, state) -> Pilot.release(server));

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }

    private static void pillar(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos pillar = Arena.ground(4, -10).above();
        Entity drone = Arena.track(DispenserRig.onPillar(level, player, DispenserRig.fiveInch(), "CovMdA Wand", pillar, Direction.NORTH,
                (pos, block) -> Arena.place(level, pos, block)));
        state.put(Pilot.DRONE, drone.getUUID());
        state.put(PILLAR, pillar);
        player.setItemSlot(EquipmentSlot.HEAD, Arena.stack(Pilot.GOGGLES));
        Vec3 stand = Vec3.atBottomCenterOf(pillar.south(3));
        player.teleportTo(level, stand.x, stand.y, stand.z, java.util.Set.of(), 180.0F, 10.0F, true);
    }

    private static void probe(MinecraftServer server, Map<String, Object> state) {
        Entity drone = Pilot.serverDrone(server, state);
        BlockPos pillar = (BlockPos) state.get(PILLAR);
        Vec3 muzzle = DispenserRig.muzzle(drone, Arena.player(server));
        BlockPos wall = BlockPos.containing(muzzle);
        String seen = String.format(Locale.ROOT, "Mündung (%.4f; %.4f; %.4f), %+.1f cm über der Kante des Sockels, Wand %s", muzzle.x, muzzle.y, muzzle.z,
                beyondEdge(muzzle, pillar, Direction.NORTH) * 100.0, wall.toShortString());
        if (!wall.equals(pillar.north().above())) {
            throw new IllegalStateException(seen + ": Mündung nicht im Block vor dem Sockel (" + pillar.north().above().toShortString() + ")");
        }
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 8));
        state.put(MUZZLE, muzzle);
        state.put(WALL, seen);
    }

    private static void wall(MinecraftServer server, Map<String, Object> state) {
        BlockPos wall = BlockPos.containing((Vec3) state.get(MUZZLE));
        ServerLevel level = Arena.level(server);
        Arena.place(level, wall, Blocks.STONE.defaultBlockState());
        Arena.place(level, wall.above(), Blocks.STONE.defaultBlockState());
    }

    static double beyondEdge(Vec3 point, BlockPos pillar, Direction facing) {
        double edgeX = pillar.getX() + 0.5 + facing.getStepX() * 0.5;
        double edgeZ = pillar.getZ() + 0.5 + facing.getStepZ() * 0.5;
        return (point.x - edgeX) * facing.getStepX() + (point.z - edgeZ) * facing.getStepZ();
    }

    private static void fire(MinecraftServer server, Map<String, Object> state, String expected) {
        Entity drone = Pilot.serverDrone(server, state);
        int before = count(drone);
        DispenserLog.Entry entry = DispenserRig.fire(drone, Arena.player(server)).orElseThrow(() -> new IllegalStateException("keine Reaktion"));
        if (!expected.equals(entry.status())) {
            throw new IllegalStateException(entry.status() + " statt " + expected + String.format(Locale.ROOT, " (Mündung %s)", entry.muzzle())
                    + muzzleNote(state, entry));
        }
        if (state.get(MUZZLE) instanceof Vec3 probed && "BLOCKED".equals(expected) && entry.muzzle().distanceTo(probed) > SAME_MUZZLE) {
            throw new IllegalStateException(String.format(Locale.ROOT, "Mündung beim Schuss %s statt %s (%.1f mm daneben)", entry.muzzle(), probed,
                    entry.muzzle().distanceTo(probed) * 1000.0));
        }
        int after = count(drone);
        if (after != before) {
            throw new IllegalStateException("Slot " + before + " → " + after + ", Munition verloren");
        }
        state.put(COUNT, after);
    }

    private static String muzzleNote(Map<String, Object> state, DispenserLog.Entry entry) {
        if (!(state.get(MUZZLE) instanceof Vec3 probed)) {
            return "";
        }
        BlockPos wall = BlockPos.containing(probed);
        return String.format(Locale.ROOT, "; vorher %s, Mündung beim Schuss %.1f mm daneben, %s Steinblock %s", state.get(WALL),
                entry.muzzle().distanceTo(probed) * 1000.0, wall.equals(BlockPos.containing(entry.muzzle())) ? "im" : "außerhalb vom", wall.toShortString());
    }

    private static int count(Entity drone) {
        int count = 0;
        for (ItemStack stack : DispenserRig.inventory(drone).contents()) {
            count += stack.getCount();
        }
        return count;
    }

    static StepResult warning(SelfTest.StepContext ctx, int ticks, Notice notice, String text) {
        Entity drone = Pilot.drone().orElse(null);
        if (drone == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        OsdText shown = MobDefenseOsd.warningText(notice, new Context(drone, true, false, drone.level().getGameTime()));
        if (shown == null || !text.equals(shown.text())) {
            return ticks > 60 ? StepResult.fail("Warnung " + (shown == null ? "fehlt" : "„" + shown.text() + "“") + " statt „" + text + "“")
                    : StepResult.pending();
        }
        if (!text.equals(I18n.get(notice.translationKey())) || !OsdLines.printableAscii(text)) {
            return StepResult.fail("Sprachdatei liefert „" + I18n.get(notice.translationKey()) + "“");
        }
        return StepResult.pass("OSD-Warnung „" + text + "“ (" + shown.tone() + "), Slot " + ctx.state().get(COUNT) + " Pfeile");
    }

    private static StepResult fail(SelfTest.StepContext ctx, int ticks, int expected) {
        List<String> heard = CovMdASounds.dispenser();
        long fails = heard.stream().filter("fail"::equals).count();
        if (fails < expected) {
            return ticks > 60 ? StepResult.fail("dispenser.fail fehlt: " + heard) : StepResult.pending();
        }
        if (heard.contains("launch") || heard.contains("dispense")) {
            return StepResult.fail("Schuss-Ton trotz Fehlschlag: " + heard);
        }
        return StepResult.pass("am Client: " + heard);
    }

    private static StepResult emptyLine(SelfTest.StepContext ctx, int ticks) {
        Entity drone = Pilot.drone().orElse(null);
        if (drone == null) {
            return StepResult.fail("Drohne nicht am Client");
        }
        Context context = new Context(drone, true, false, drone.level().getGameTime());
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            if (spec.id().equals(MobDefenseOsd.DISPENSER)) {
                OsdText text = spec.content().text(context);
                if (text != null && OsdLines.DISPENSER_EMPTY.equals(text.text())) {
                    return StepResult.pass("mobdefense:dispenser zeigt „" + text.text() + "“ (" + text.tone() + ")");
                }
                return ticks > 60 ? StepResult.fail("mobdefense:dispenser zeigt " + (text == null ? "nichts" : "„" + text.text() + "“")) : StepResult.pending();
            }
        }
        return StepResult.fail("mobdefense:dispenser nicht angemeldet");
    }

    record Context(@Nullable Entity entity, boolean fpv, boolean lineOfSight, long gameTime) implements OsdContext {
        @Override
        public Optional<Entity> drone() {
            return Optional.ofNullable(this.entity);
        }

        @Override
        public FlightTelemetry telemetry() {
            Vec3 pos = this.entity == null ? Vec3.ZERO : this.entity.position();
            return new FlightTelemetry(true, "ACRO", Optional.empty(), pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 16.8, 4.2, 1.0, false, 0.0, 0.0,
                    0.0, false, 0, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        @Override
        public boolean preview() {
            return false;
        }

        @Override
        public float partialTick() {
            return 0.0F;
        }
    }
}
