package de.lardtom.mobdefense.dev.dispenser.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.client.Shots;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.UnderFrame;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.MountPose;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.build.DroneData;
import de.lardtom.propwash.api.build.DronePreset;
import de.lardtom.propwash.api.drone.DroneView;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.part.AccessorySlot;
import de.lardtom.propwash.api.part.AnchorSpec;
import de.lardtom.propwash.api.part.FrameDefinition;
import de.lardtom.propwash.api.part.PartDefinition;
import de.lardtom.propwash.api.part.PartKind;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UnderFrameSteps {
    public static final String NAME = "Unter dem Rahmen – Dispenser an jedem Rahmen mit Halterung vorn (Propwash, Just More Parts, Camera Drones) eingerückt, "
            + "am Rahmen, Schuss aus der Mündung, Seitenbild";
    static final double POSE_TOLERANCE_MM = 0.5;
    static final double MUZZLE_TOLERANCE_M = 0.02;
    static final double MAX_SHOT_DEGREES = DispenserSteps.MAX_SINGLE_DEGREES;
    static final int SYNC_TICKS = 20;
    static final double MIN_DISTANCE = 0.35;
    static final double CAMERA_ABOVE_STAND = 0.05;
    static final int CLIENT_TICKS = 200;
    private static final String CASES = "underframe.cases";
    private static final String INDEX = "underframe.index";
    private static final String PHASE = "underframe.phase";
    private static final String SINCE = "underframe.since";
    private static final String DRONE = "underframe.drone";
    private static final String SERVER_LINE = "underframe.server";
    private static final String SERVER_MISS = "underframe.serverMiss";
    private static final String LINES = "underframe.lines";
    private static final String MISSES = "underframe.misses";
    private static boolean hudHidden;

    private UnderFrameSteps() {
    }

    record Case(FrameDefinition frame, boolean spear) {
        String label() {
            return this.frame.id() + (this.spear ? " mit Speer-Halterung" : "");
        }

        String photo() {
            return "dispenser_under_" + this.frame.id().getNamespace() + "_" + this.frame.id().getPath() + (this.spear ? "_spear" : "");
        }
    }

    public static void register(SelfTest.Steps steps) {
        steps.add(NAME, 6000, UnderFrameSteps::tick, Script.create().run("Anzeige zurück", UnderFrameSteps::restoreHud));
    }

    static AccessoryDefinition dispenser() {
        return PropwashApi.parts().get(AccessoryDefinition.class, MobDefense.DISPENSER_MOUNT)
                .orElseThrow(() -> new IllegalStateException(MobDefense.DISPENSER_MOUNT + " nicht angemeldet"));
    }

    static List<Case> cases() {
        List<FrameDefinition> frames = new ArrayList<>();
        for (PartDefinition definition : PropwashApi.parts().all(PartKind.FRAME)) {
            if (definition instanceof FrameDefinition frame && frame.accessoryMounts().contains(AccessorySlot.FRONT)) {
                frames.add(frame);
            }
        }
        frames.sort(Comparator.comparing((FrameDefinition frame) -> order(frame.id().getNamespace())).thenComparing(frame -> frame.id().toString()));
        List<Case> out = new ArrayList<>();
        for (FrameDefinition frame : frames) {
            out.add(new Case(frame, false));
            if (frame.id().equals(DispenserRig.propwash("freestyle_5"))) {
                out.add(new Case(frame, true));
            }
        }
        return out;
    }

    private static int order(String namespace) {
        return switch (namespace) {
            case PropwashApi.MOD_ID -> 0;
            case "justmoreparts" -> 1;
            case "camdrones" -> 2;
            default -> 3;
        };
    }

    static BuildSpec spec(Case c) {
        BuildSpec base = null;
        for (DronePreset preset : PropwashApi.builds().presets()) {
            BuildSpec build = preset.data().build();
            if (build.part(PartKind.FRAME).equals(c.frame().id())) {
                base = build;
                break;
            }
        }
        boolean preset = base != null;
        if (base == null) {
            base = DispenserRig.fiveInch().withPart(PartKind.FRAME, c.frame().id());
        }
        BuildSpec spec = BuildSpec.of(base.part(PartKind.FRAME), base.part(PartKind.STACK), base.part(PartKind.MOTOR), base.part(PartKind.PROP),
                base.part(PartKind.VIDEO), base.part(PartKind.BATTERY)).withAccessory(AccessorySlot.FRONT, MobDefense.DISPENSER_MOUNT);
        spec = c.spear() ? spec.withAccessory(AccessorySlot.BOTTOM, MobDefense.SPEAR_MOUNT) : spec;
        return preset ? spec : buildable(spec);
    }

    static BuildSpec buildable(BuildSpec start) {
        BuildSpec best = start;
        int problems = problems(best);
        for (int pass = 0; pass < 2 && problems > 0; pass++) {
            for (PartKind kind : List.of(PartKind.STACK, PartKind.MOTOR, PartKind.VIDEO, PartKind.BATTERY)) {
                for (PartDefinition part : PropwashApi.parts().all(kind)) {
                    BuildSpec trial = best.withPart(kind, part.id());
                    if (kind == PartKind.MOTOR) {
                        for (PartDefinition prop : PropwashApi.parts().all(PartKind.PROP)) {
                            BuildSpec paired = trial.withPart(PartKind.PROP, prop.id());
                            int count = problems(paired);
                            if (count < problems) {
                                best = paired;
                                problems = count;
                            }
                        }
                        continue;
                    }
                    int count = problems(trial);
                    if (count < problems) {
                        best = trial;
                        problems = count;
                    }
                }
            }
        }
        return best;
    }

    private static int problems(BuildSpec spec) {
        return PropwashApi.builds().check(spec).problems().size();
    }

    static UnderFrame.Anchor front(FrameDefinition frame) {
        AnchorSpec spec = frame.geometry().accessoryAnchors().get(AccessorySlot.FRONT);
        if (spec == null) {
            return new UnderFrame.Anchor(0.0, 0.0, -0.3 * frame.wheelbaseMm(), 0.0, 0.0);
        }
        return new UnderFrame.Anchor(spec.x(), spec.y(), spec.z(), spec.tiltDeg(), spec.lengthMm());
    }

    @SuppressWarnings("unchecked")
    static StepResult tick(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        List<Case> cases = (List<Case>) state.computeIfAbsent(CASES, key -> cases());
        List<String> lines = (List<String>) state.computeIfAbsent(LINES, key -> new ArrayList<String>());
        List<String> misses = (List<String>) state.computeIfAbsent(MISSES, key -> new ArrayList<String>());
        if (cases.isEmpty()) {
            return StepResult.fail("kein Rahmen mit Halterung vorn");
        }
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= cases.size()) {
            return summary(ctx, cases, lines, misses);
        }
        Case c = cases.get(index);
        int phase = (int) state.getOrDefault(PHASE, 0);
        int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
        int ticks = ctx.tick() - since;
        StepResult result = switch (phase) {
            case 0 -> Script.server(ctx, ticks, (server, s) -> place(server, s, c));
            case 1 -> synced(ctx, ticks);
            case 2 -> measure(ctx, c, lines, misses);
            case 3 -> Shots.shot(ctx, ticks, c.photo(), UnderFrameSteps::hideHud);
            case 4 -> Script.server(ctx, ticks, UnderFrameSteps::remove);
            default -> StepResult.fail("unbekannte Stufe " + phase);
        };
        if (!result.done()) {
            return result;
        }
        if (result.failed()) {
            return StepResult.fail(c.label() + ": " + result.message());
        }
        if (phase < 4) {
            state.put(PHASE, phase + 1);
        } else {
            state.put(PHASE, 0);
            state.put(INDEX, index + 1);
        }
        state.put(SINCE, ctx.tick() + 1);
        return StepResult.pending();
    }

    private static StepResult summary(SelfTest.StepContext ctx, List<Case> cases, List<String> lines, List<String> misses) {
        restoreHud();
        int jmp = 0;
        int cam = 0;
        int core = 0;
        for (Case c : cases) {
            if (c.spear()) {
                continue;
            }
            switch (c.frame().id().getNamespace()) {
                case PropwashApi.MOD_ID -> core++;
                case "justmoreparts" -> jmp++;
                case "camdrones" -> cam++;
                default -> {
                }
            }
        }
        String count = String.format(Locale.ROOT, "%d Propwash-, %d Just-More-Parts- und %d Camera-Drones-Rahmen%s", core, jmp, cam,
                jmp == 0 || cam == 0 ? " (Add-ons nur mit ihrem Jar im Run-Ordner mods/)" : "");
        if (core == 0) {
            return StepResult.fail("kein Propwash-Rahmen mit Halterung vorn");
        }
        if (!misses.isEmpty()) {
            return StepResult.fail(count + ", nicht unter dem Rahmen: " + String.join("; ", misses));
        }
        return StepResult.pass(count + ", alle unter dem Rahmen");
    }

    private static void place(MinecraftServer server, Map<String, Object> state, Case c) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        AccessoryDefinition dispenser = dispenser();
        BlockPos ground = Arena.ground(0, -8);
        BlockPos stand = ground.above();
        Arena.place(level, stand, Blocks.STONE.defaultBlockState());
        player.setGameMode(GameType.CREATIVE);
        player.setYRot(180.0F);
        player.setYHeadRot(180.0F);
        ItemStack item = PropwashApi.builds().createDrone(DroneData.of(spec(c)));
        Entity drone = Arena.track(DispenserRig.place(level, player, item, c.label(), stand, Vec3.atCenterOf(stand).add(0.0, 0.5, 0.0)));
        state.put(DRONE, drone.getUUID());
        if (c.spear()) {
            PropwashApi.accessories().inventory(drone, AccessorySlot.BOTTOM).ifPresent(inventory -> inventory.set(0, new ItemStack(Items.IRON_SPEAR)));
        }
        DispenserRig.load(drone, new ItemStack(Items.ARROW, 1));
        DispenserLog.Entry entry = DispenserRig.fire(drone, player).orElseThrow(() -> new IllegalStateException("Dispenser reagiert nicht"));
        List<String> problems = new ArrayList<>();
        MountPose mount = entry.mount();
        Vec3 forward = mount.forward();
        double ahead = mount.front().subtract(mount.anchor()).dot(forward) * 1000.0;
        double behind = mount.anchor().subtract(mount.center()).dot(forward) * 1000.0;
        double below = mount.anchor().subtract(mount.center()).dot(mount.up()) * 1000.0;
        double maxAhead = (1.0 - UnderFrame.RECESS_SHARE) * dispenser.depthMm();
        if (ahead < -POSE_TOLERANCE_MM || ahead > maxAhead + POSE_TOLERANCE_MM) {
            problems.add(String.format(Locale.ROOT, "Mündung %.1f mm vor dem Anker (erlaubt 0 bis %.1f mm)", ahead, maxAhead));
        }
        double wantBehind = dispenser.recessMm() - dispenser.depthMm() / 2.0;
        double wantBelow = dispenser.heightMm() / 2.0 + dispenser.dropMm();
        if (Math.abs(behind - wantBehind) > POSE_TOLERANCE_MM || Math.abs(below - wantBelow) > POSE_TOLERANCE_MM) {
            problems.add(String.format(Locale.ROOT, "Mitte %.1f mm hinter und %.1f mm unter dem Anker statt %.1f und %.1f mm", behind, below, wantBehind,
                    wantBelow));
        }
        String shot;
        if (entry.result() == null || !entry.result().success() || entry.result().entity().isEmpty()) {
            shot = "kein Pfeil (" + entry.status() + ")";
            problems.add(shot);
        } else {
            Entity arrow = entry.result().entity().get();
            double lowest = mount.center().y - dispenser.heightMm() / 2000.0;
            double lift = Math.max(0.0, drone.getBoundingBox().minY - lowest);
            double off = arrow.position().distanceTo(entry.muzzle().add(0.0, lift, 0.0));
            Vec3 launch = entry.launch();
            double degrees = launch.lengthSqr() < 1.0E-9 ? 180.0 : Math.toDegrees(Math.acos(Math.clamp(launch.normalize().dot(forward), -1.0, 1.0)));
            shot = String.format(Locale.ROOT, "Pfeil %.4f m neben der Mündung (im Stand %.1f mm angehoben), %.1f° neben vorn", off, lift * 1000.0, degrees);
            if (off > MUZZLE_TOLERANCE_M) {
                problems.add(String.format(Locale.ROOT, "Pfeil startet %.3f m neben der Mündung", off));
            }
            if (degrees > MAX_SHOT_DEGREES) {
                problems.add(String.format(Locale.ROOT, "Pfeil fliegt %.1f° neben vorn", degrees));
            }
            arrow.discard();
        }
        state.put(SERVER_LINE, String.format(Locale.ROOT, "Mündung %.1f mm vor dem Anker, Mitte %.1f mm dahinter und %.1f mm darunter, %s", ahead, behind,
                below, shot));
        state.put(SERVER_MISS, String.join(", ", problems));
        look(player, level, drone, c.frame().wheelbaseMm(), stand.getY() + 1.0);
    }

    private static void look(ServerPlayer player, ServerLevel level, Entity drone, int wheelbaseMm, double standTop) {
        DroneView view = DispenserRig.view(drone);
        Vec3 forward = new Vec3(view.forward().x, 0.0, view.forward().z);
        forward = forward.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, -1.0) : forward.normalize();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        double distance = Math.max(MIN_DISTANCE, wheelbaseMm / 1000.0 * 1.3);
        Vec3 target = view.center().add(0.0, -0.03, 0.0);
        Vec3 eye = target.add(side.scale(distance)).add(0.0, distance * 0.08, 0.0);
        eye = new Vec3(eye.x, Math.max(eye.y, standTop + CAMERA_ABOVE_STAND), eye.z);
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(target.y - eye.y, Math.hypot(dx, dz)));
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, Set.<Relative>of(), yaw, pitch, true);
    }

    private static StepResult synced(SelfTest.StepContext ctx, int ticks) {
        Object id = ctx.state().get(DRONE);
        Minecraft mc = Minecraft.getInstance();
        boolean seen = false;
        for (Entity entity : Arena.clientDrones(mc)) {
            seen |= entity.getUUID().equals(id);
        }
        if (seen && ticks >= SYNC_TICKS) {
            return StepResult.pass();
        }
        return ticks > CLIENT_TICKS ? StepResult.fail("Drohne kam nicht beim Client an") : StepResult.pending();
    }

    private static StepResult measure(SelfTest.StepContext ctx, Case c, List<String> lines, List<String> misses) {
        AccessoryDefinition dispenser = dispenser();
        String serverLine = String.valueOf(ctx.state().get(SERVER_LINE));
        String serverMiss = String.valueOf(ctx.state().getOrDefault(SERVER_MISS, ""));
        String line;
        boolean ok = serverMiss.isEmpty();
        if (c.spear()) {
            line = c.label() + ": " + serverLine;
        } else {
            UnderFrame.Fit fit;
            try {
                fit = fit(c.frame(), dispenser);
            } catch (IOException | RuntimeException e) {
                return StepResult.fail("Rahmenmodell nicht lesbar: " + e.getMessage());
            }
            ok &= fit.underFrame(dispenser.depthMm());
            line = c.label() + ": " + fit.describe() + "; " + serverLine;
        }
        lines.add(line);
        ctx.log((ok ? "unter dem Rahmen: " : "NICHT UNTER DEM RAHMEN: ") + line + (serverMiss.isEmpty() ? "" : " – " + serverMiss));
        if (!ok) {
            misses.add(line + (serverMiss.isEmpty() ? "" : " – " + serverMiss));
        }
        return StepResult.pass();
    }

    static UnderFrame.Fit fit(FrameDefinition frame, AccessoryDefinition dispenser) throws IOException {
        Identifier id = frame.id();
        JsonObject item = json(Identifier.fromNamespaceAndPath(id.getNamespace(), "items/drone_render/frame/" + id.getPath() + ".json")).getAsJsonObject("model");
        if (!"minecraft:model".equals(item.get("type").getAsString())) {
            throw new IOException(id + ": Rahmenmodell vom Typ " + item.get("type").getAsString());
        }
        Identifier model = Identifier.parse(item.get("model").getAsString());
        List<UnderFrame.Part> body = UnderFrame.parts(json(Identifier.fromNamespaceAndPath(model.getNamespace(), "models/" + model.getPath() + ".json")),
                UnderFrame.transform(item));
        Identifier own = MobDefense.DISPENSER_MOUNT;
        List<UnderFrame.Part> housing = UnderFrame.parts(json(Identifier.fromNamespaceAndPath(own.getNamespace(), "models/part/accessory/" + own.getPath() + ".json")));
        return UnderFrame.measure(body, front(frame), housing, dispenser.widthMm(), dispenser.heightMm(), dispenser.depthMm(), dispenser.recessMm(),
                dispenser.dropMm());
    }

    private static JsonObject json(Identifier id) throws IOException {
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void remove(MinecraftServer server, Map<String, Object> state) {
        Arena.player(server).setGameMode(GameType.CREATIVE);
        Object id = state.remove(DRONE);
        if (id instanceof UUID uuid) {
            Entity drone = Arena.level(server).getEntity(uuid);
            if (drone != null) {
                drone.discard();
            }
        }
    }

    private static void hideHud() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
            hudHidden = true;
        }
    }

    static void restoreHud() {
        Minecraft mc = Minecraft.getInstance();
        if (hudHidden && mc.gui.hud.isHidden()) {
            mc.gui.hud.toggle();
        }
        hudHidden = false;
    }
}
