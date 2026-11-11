package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

public final class PvpMatrix implements Runs.Run {
    public static final float HEALTH = 10.0F;
    public static final int SETTLE_TICKS = 16;
    public static final int LANE_SPACING = 12;
    public static final double TARGET_DISTANCE = 2.5;

    public enum Payload {
        ARROW("Pfeil", true),
        HARMING("Schadenstrank", true),
        POISON("Gifttrank", true),
        HEALING("Heiltrank", false);

        final String label;
        final boolean harmful;

        Payload(String label, boolean harmful) {
            this.label = label;
            this.harmful = harmful;
        }

        ItemStack stack() {
            return switch (this) {
                case ARROW -> new ItemStack(Items.ARROW, 4);
                case HARMING -> potion(Potions.HARMING);
                case POISON -> potion(Potions.POISON);
                case HEALING -> potion(Potions.HEALING);
            };
        }

        private static ItemStack potion(Holder<Potion> potion) {
            return PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        }
    }

    public record Combo(boolean rule, boolean pvp) {
        boolean harms() {
            return this.rule && this.pvp;
        }

        String label() {
            return "payloads_hit_players " + (this.rule ? "an" : "aus") + " × PvP " + (this.pvp ? "an" : "aus");
        }
    }

    public static final List<Combo> COMBOS = List.of(new Combo(true, true), new Combo(true, false), new Combo(false, true), new Combo(false, false));

    private static final class Lane {
        private final Payload payload;
        private final BlockPos pillar;
        private final ServerPlayer target;
        private Entity drone;

        private Lane(Payload payload, BlockPos pillar, Entity drone, ServerPlayer target) {
            this.payload = payload;
            this.pillar = pillar;
            this.drone = drone;
            this.target = target;
        }

        Payload payload() {
            return this.payload;
        }

        Entity drone() {
            return this.drone;
        }

        ServerPlayer target() {
            return this.target;
        }
    }

    private final List<Combo> combos;
    private final List<Lane> lanes = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final Rules rules;
    private final ServerPlayer shooter;
    private final List<Entity> placedEntities = new ArrayList<>();
    private int combo = -1;
    private int ticks;
    private boolean done;
    private @Nullable String failure;

    private PvpMatrix(MinecraftServer server, ServerPlayer shooter, List<Combo> combos) {
        this.rules = new Rules(server);
        this.shooter = shooter;
        this.combos = List.copyOf(combos);
    }

    public static PvpMatrix start(MinecraftServer server, ServerLevel level, BlockPos origin, ServerPlayer shooter, List<Payload> payloads,
                                  List<Combo> combos, BiConsumer<BlockPos, BlockState> builder) {
        PvpMatrix run = new PvpMatrix(server, shooter, combos);
        for (int i = 0; i < payloads.size(); i++) {
            BlockPos column = origin.offset(0, 0, i * LANE_SPACING);
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            BlockPos pillar = new BlockPos(column.getX(), surface, column.getZ());
            Entity drone = DispenserRig.onPillar(level, shooter, DispenserRig.fiveInch(), "D3 " + payloads.get(i).label, pillar, Direction.EAST, builder);
            run.placedEntities.add(drone);
            Vec3 stand = DispenserRig.ahead(drone, TARGET_DISTANCE);
            Vec3 feet = new Vec3(stand.x, surface, stand.z);
            ServerPlayer target = Guests.place(server, "md_d3_" + payloads.get(i).name().toLowerCase(Locale.ROOT), level, feet, 0.0F);
            run.lanes.add(new Lane(payloads.get(i), pillar, drone, target));
        }
        return Runs.start(run);
    }

    @Override
    public void tick(MinecraftServer server) {
        if (this.done) {
            return;
        }
        if (this.combo < 0 || this.ticks >= SETTLE_TICKS) {
            if (this.combo >= 0) {
                this.evaluate(this.combos.get(this.combo));
            }
            this.combo++;
            this.ticks = 0;
            if (this.combo >= this.combos.size()) {
                this.finish(server);
                return;
            }
            this.fire(this.combos.get(this.combo));
            return;
        }
        this.ticks++;
    }

    private void fire(Combo combo) {
        this.rules.set(RuleDefaults.PAYLOADS_HIT_PLAYERS, combo.rule()).set(Rules.PVP, combo.pvp());
        for (Lane lane : this.lanes) {
            ServerLevel level = (ServerLevel) lane.target().level();
            lane.drone.discard();
            lane.drone = DispenserRig.onPillar(level, this.shooter, DispenserRig.fiveInch(), "D3 " + lane.payload().label, lane.pillar,
                    Direction.EAST, (pos, state) -> {
                    });
            this.placedEntities.add(lane.drone);
            Guests.ready(lane.target(), HEALTH);
            DispenserRig.load(lane.drone(), lane.payload().stack());
            DispenserLog.Entry entry = DispenserRig.fire(lane.drone(), this.shooter).orElse(null);
            if (entry == null || entry.result() == null || !entry.result().success()) {
                this.problems.add(combo.label() + ", " + lane.payload().label + ": nicht abgefeuert (" + (entry == null ? "keine Reaktion" : entry.status()) + ")");
            } else {
                entry.result().entity().ifPresent(this.placedEntities::add);
            }
        }
    }

    private void evaluate(Combo combo) {
        List<String> row = new ArrayList<>();
        for (Lane lane : this.lanes) {
            ServerPlayer target = lane.target();
            float health = target.getHealth();
            boolean poisoned = target.hasEffect(MobEffects.POISON);
            String seen;
            boolean ok;
            switch (lane.payload()) {
                case HEALING -> {
                    ok = health > HEALTH;
                    seen = String.format(Locale.ROOT, "%.0f→%.0f", HEALTH, health);
                }
                case POISON -> {
                    ok = poisoned == combo.harms() && health <= HEALTH;
                    seen = poisoned ? "vergiftet" : "nicht vergiftet";
                }
                default -> {
                    ok = combo.harms() ? health < HEALTH : health >= HEALTH;
                    seen = String.format(Locale.ROOT, "%.0f→%.0f", HEALTH, health);
                }
            }
            row.add(lane.payload().label + " " + seen + (ok ? "" : " (FALSCH)"));
            if (!ok) {
                this.problems.add(combo.label() + ", " + lane.payload().label + ": " + seen + ", erwartet "
                        + (lane.payload() == Payload.HEALING ? "geheilt" : combo.harms() ? "getroffen" : "kein Schaden"));
            }
        }
        this.notes.add(combo.label() + ": " + String.join(", ", row));
    }

    private void finish(MinecraftServer server) {
        this.rules.restore();
        for (Lane lane : this.lanes) {
            lane.target().removeAllEffects();
        }
        this.cleanup();
        this.done = true;
        if (!this.problems.isEmpty()) {
            this.failure = String.join("; ", this.problems);
        }
    }

    private void cleanup() {
        for (Entity entity : this.placedEntities) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
        for (Lane lane : this.lanes) {
            ServerLevel level = (ServerLevel) lane.target().level();
            AABB around = new AABB(lane.pillar).inflate(10.0);
            for (Entity stray : level.getEntities((Entity) null, around, entity -> entity instanceof Projectile
                    || entity instanceof AreaEffectCloud)) {
                stray.discard();
            }
        }
        Guests.removeAll();
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
        return this.notes.isEmpty() ? "noch keine Kombination ausgewertet" : String.join(" | ", this.notes);
    }

    @Override
    public void abort(MinecraftServer server, String reason) {
        try {
            this.rules.restore();
            this.cleanup();
        } finally {
            this.done = true;
            this.failure = reason;
        }
    }
}
