package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MacroRun implements Runs.Run {
    public static final int PRESSES_PER_SECOND = 50;
    public static final int DURATION_TICKS = 40;
    public static final int AMMO = 16;

    private final Entity drone;
    private final Vec3 home;
    private final ServerPlayer actor;
    private final int cooldown;
    private final List<Long> shotTicks = new ArrayList<>();
    private int ticks;
    private int presses;
    private int accepted;
    private int lastDispensed;
    private boolean done;
    private @Nullable String failure;
    private String summary = "läuft";

    private MacroRun(Entity drone, ServerPlayer actor, int cooldown) {
        this.drone = drone;
        this.home = drone.position();
        this.actor = actor;
        this.cooldown = cooldown;
    }

    public static MacroRun start(MinecraftServer server, ServerLevel level, BlockPos origin, ServerPlayer actor) {
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin.getX(), origin.getZ());
        Entity drone = DispenserRig.onPillar(level, actor, DispenserRig.fiveInch(), "D5 Makro", new BlockPos(origin.getX(), surface, origin.getZ()),
                Direction.WEST, (pos, state) -> level.setBlockAndUpdate(pos, state));
        DispenserRig.load(drone, new ItemStack(Items.ARROW, AMMO));
        int cooldown = Integer.parseInt(new Rules(server).get(Rules.id(RuleDefaults.DISPENSER_COOLDOWN_TICKS)));
        return Runs.start(new MacroRun(drone, actor, cooldown));
    }

    @Override
    public void tick(MinecraftServer server) {
        if (this.done) {
            return;
        }
        if (this.ticks < DURATION_TICKS) {
            this.drone.setDeltaMovement(Vec3.ZERO);
            this.drone.setPos(this.home);
            int due = (this.ticks + 1) * PRESSES_PER_SECOND / 20 - this.ticks * PRESSES_PER_SECOND / 20;
            for (int i = 0; i < due; i++) {
                this.presses++;
                if (PropwashApi.accessories().triggerAction(this.drone, 1, true, this.actor)) {
                    this.accepted++;
                }
                PropwashApi.accessories().triggerAction(this.drone, 1, false, this.actor);
            }
            int dispensed = DispenserLog.counts(this.drone).dispensed();
            if (dispensed > this.lastDispensed) {
                for (int i = this.lastDispensed; i < dispensed; i++) {
                    this.shotTicks.add(this.drone.level().getGameTime());
                }
                this.lastDispensed = dispensed;
            }
            this.ticks++;
            return;
        }
        this.finish();
    }

    private void finish() {
        List<String> problems = new ArrayList<>();
        int shots = this.shotTicks.size();
        int allowed = DURATION_TICKS / Math.max(1, this.cooldown) + 1;
        for (int i = 1; i < shots; i++) {
            long gap = this.shotTicks.get(i) - this.shotTicks.get(i - 1);
            if (gap < Math.max(1, this.cooldown)) {
                problems.add("zwei Schüsse im Abstand " + gap + " Ticks bei Abklingzeit " + this.cooldown);
            }
        }
        if (shots > allowed) {
            problems.add(shots + " Schüsse in " + DURATION_TICKS + " Ticks, erlaubt " + allowed);
        }
        if (shots < allowed / 2) {
            problems.add("nur " + shots + " Schüsse, das Makro kommt kaum durch");
        }
        int left = DispenserRig.inventory(this.drone).get(0).getCount();
        if (left != AMMO - shots) {
            problems.add("Munition " + left + " statt " + (AMMO - shots));
        }
        if (this.actor.hasDisconnected() || this.drone.isRemoved()) {
            problems.add("Auslöser getrennt oder Drohne weg");
        }
        this.summary = String.format(Locale.ROOT, "Makro %d/s über %d Ticks: %d Drücke, %d von Propwash angenommen (Ratenbegrenzung), %d Schüsse "
                        + "(höchstens %d bei %d Ticks Abklingzeit), kleinster Abstand %s Ticks, %d Pfeile übrig, Auslöser verbunden",
                PRESSES_PER_SECOND, DURATION_TICKS, this.presses, this.accepted, shots, allowed, this.cooldown, minGap(), left);
        this.cleanup();
        this.done = true;
        this.failure = problems.isEmpty() ? null : String.join("; ", problems);
    }

    private String minGap() {
        long min = Long.MAX_VALUE;
        for (int i = 1; i < this.shotTicks.size(); i++) {
            min = Math.min(min, this.shotTicks.get(i) - this.shotTicks.get(i - 1));
        }
        return min == Long.MAX_VALUE ? "–" : Long.toString(min);
    }

    private void cleanup() {
        ServerLevel level = (ServerLevel) this.drone.level();
        for (Entity stray : level.getEntities((Entity) null, this.drone.getBoundingBox().inflate(64.0), entity -> entity instanceof Projectile)) {
            stray.discard();
        }
        this.drone.discard();
    }

    @Override
    public boolean done() {
        return this.done;
    }

    @Override
    public @Nullable String failure() {
        return this.failure;
    }

    @Override
    public String summary() {
        return this.summary;
    }

    @Override
    public void abort(MinecraftServer server, String reason) {
        try {
            this.cleanup();
        } finally {
            this.done = true;
            this.failure = reason;
        }
    }
}
