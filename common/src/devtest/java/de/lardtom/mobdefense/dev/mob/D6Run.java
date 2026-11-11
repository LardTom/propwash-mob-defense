package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.mob.DroneReactions;
import de.lardtom.mobdefense.platform.Services;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class D6Run {
    public static final String PROPERTY = "mobdefense.d6";
    public static final String RESULT_FILE = "mobdefense-d6-result.txt";
    public static final String STOP_FILE = "jointest.stop";
    public static final int DRONES = 10;
    public static final int MOBS = 50;
    public static final double LIMIT_MS = 2.0;
    public static final int PLACE_TICK = 40;
    public static final int SETTLE_TICKS = 200;
    public static final int PHASE_TICKS = 300;
    public static final int SKIP_TICKS = 60;
    public static final int PAIRS = 3;
    private static final String TAG = "[Mob-Defense-D6]";
    private static final Map<EntityType<? extends Mob>, Integer> MIX = mix();

    private static D6Run running;

    private final MinecraftServer server;
    private final ServerLevel level;
    private final List<Entity> drones = new ArrayList<>();
    private final List<Mob> mobs = new ArrayList<>();
    private final List<double[]> phases = new ArrayList<>();
    private final List<Boolean> phaseOn = new ArrayList<>();
    private final List<Double> samples = new ArrayList<>();
    private int tick;
    private int phase = -1;
    private int phaseTick;
    private int maxAssigned;
    private long reactionNanos;
    private long reactionTicks;
    private int maxEvaluations;
    private long mobHits;
    private boolean done;

    private D6Run(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
    }

    private static Map<EntityType<? extends Mob>, Integer> mix() {
        Map<EntityType<? extends Mob>, Integer> out = new LinkedHashMap<>();
        out.put(EntityTypes.ZOMBIE, 10);
        out.put(EntityTypes.HUSK, 5);
        out.put(EntityTypes.SKELETON, 10);
        out.put(EntityTypes.STRAY, 5);
        out.put(EntityTypes.VINDICATOR, 5);
        out.put(EntityTypes.PILLAGER, 5);
        out.put(EntityTypes.HOGLIN, 5);
        out.put(EntityTypes.PHANTOM, 5);
        return out;
    }

    public static boolean enabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static String start(MinecraftServer server) {
        if (running != null) {
            return "D6 läuft schon";
        }
        if (!server.isDedicatedServer()) {
            throw new IllegalStateException("D6 nur am dedizierten Server");
        }
        D6Run run = new D6Run(server);
        running = run;
        try {
            run.prepare();
        } catch (RuntimeException e) {
            run.abort(e);
            throw e;
        }
        MobDefense.onServerTick(s -> {
            if (running == run) {
                try {
                    run.tick();
                } catch (RuntimeException e) {
                    run.abort(e);
                }
            }
        });
        return "D6 gestartet, Drohnen und Mobs folgen nach " + PLACE_TICK + " Ticks";
    }

    private void prepare() {
        int total = MIX.values().stream().mapToInt(Integer::intValue).sum();
        if (total != MOBS) {
            throw new IllegalStateException("Mob-Mischung hat " + total + " statt " + MOBS);
        }
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                this.level.setChunkForced(cx, cz, true);
            }
        }
        command("time set 18000");
        this.server.getGameRules().set(GameRules.ADVANCE_TIME, false, this.server);
        this.server.getGameRules().set(GameRules.SPAWN_MOBS, false, this.server);
        this.server.getGameRules().set(GameRules.MOB_GRIEFING, false, this.server);
        MobScene.rule(this.server, MobDefense.id(RuleDefaults.MOB_DAMAGE_TO_DRONES_PERCENT.path()), "0");
        MobDefense.LOG.info("{} Chunks um 8,8 dauerhaft geladen, Nacht, Zeit steht, keine Spawns, mobGriefing aus, Mob-Schaden 0 %", TAG);
    }

    private void abort(RuntimeException error) {
        if (this.done) {
            return;
        }
        this.done = true;
        DroneReactions.setActiveFilter(null);
        MobDefense.LOG.error("{} abgebrochen", TAG, error);
        write("FAIL D6: abgebrochen: " + (error.getMessage() == null ? error.toString() : error.getMessage()));
    }

    private void write(String result) {
        Path dir = Services.PLATFORM.gameDir();
        try {
            Files.writeString(dir.resolve(RESULT_FILE), result + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve(STOP_FILE), "D6 fertig" + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            MobDefense.LOG.error("{} Ergebnis nicht geschrieben", TAG, e);
        }
    }

    private void place() {
        int y = this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8);
        for (int i = 0; i < DRONES; i++) {
            double angle = Math.PI * 2.0 * i / DRONES;
            BlockPos ground = new BlockPos((int) Math.round(8 + Math.cos(angle) * 12), y - 1, (int) Math.round(8 + Math.sin(angle) * 12));
            this.drones.add(MobScene.placeDrone(this.level, ground, "D6 " + (i + 1)));
        }
        int index = 0;
        for (Map.Entry<EntityType<? extends Mob>, Integer> entry : MIX.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                double angle = Math.PI * 2.0 * index / MOBS + 0.37;
                double radius = 20.0 + (index % 5) * 3.0;
                double height = entry.getKey() == EntityTypes.PHANTOM ? 18.0 : 0.0;
                Vec3 at = new Vec3(8.5 + Math.cos(angle) * radius, y + height, 8.5 + Math.sin(angle) * radius);
                Mob mob = MobScene.spawn(this.level, entry.getKey(), at, (float) Math.toDegrees(angle));
                if (mob instanceof Hoglin hoglin) {
                    hoglin.setImmuneToZombification(true);
                }
                this.mobs.add(mob);
                index++;
            }
        }
        DroneReactions.setActiveFilter(view -> true);
        MobDefense.LOG.info("{} {} Drohnen und {} Mobs um 8,{},8, geparkte Drohnen zählen als aktiv", TAG, this.drones.size(), this.mobs.size(), y);
    }

    private void command(String command) {
        this.server.getCommands().performPrefixedCommand(this.server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private void react(boolean on) {
        MobScene.rule(this.server, MobDefense.id(RuleDefaults.MOBS_REACT_TO_DRONES.path()), Boolean.toString(on));
    }

    private void tick() {
        if (this.done) {
            return;
        }
        this.tick++;
        if (this.tick == PLACE_TICK) {
            place();
            react(true);
        }
        if (this.tick <= PLACE_TICK + SETTLE_TICKS) {
            return;
        }
        if (this.phase < 0 || this.phaseTick >= PHASE_TICKS) {
            if (this.phase >= 0) {
                closePhase();
            }
            this.phase++;
            if (this.phase >= PAIRS * 2) {
                finish();
                return;
            }
            this.phaseTick = 0;
            this.samples.clear();
            boolean on = this.phase % 2 == 1;
            react(on);
            if (on) {
                DroneReactions.resetStats();
            }
        }
        this.phaseTick++;
        if (this.phaseTick > SKIP_TICKS) {
            long[] times = this.server.getTickTimesNanos();
            long last = times[Math.floorMod(this.server.getTickCount() - 1, times.length)];
            this.samples.add(last / 1.0E6);
            if (this.phase % 2 == 1) {
                this.maxAssigned = Math.max(this.maxAssigned, DroneReactions.assignments(this.level));
            }
        }
    }

    private void closePhase() {
        double[] values = this.samples.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double median = values.length == 0 ? Double.NaN : values[values.length / 2];
        double mean = Arrays.stream(values).average().orElse(Double.NaN);
        boolean on = this.phase % 2 == 1;
        this.phases.add(new double[]{median, mean});
        this.phaseOn.add(on);
        if (on) {
            DroneReactions.Stats stats = DroneReactions.stats();
            this.reactionNanos += stats.nanos();
            this.reactionTicks += stats.ticks();
            this.maxEvaluations = Math.max(this.maxEvaluations, stats.maxEvaluations());
            this.mobHits += stats.mobHits();
        }
        MobDefense.LOG.info("{} Phase {} ({}): Median {} ms, Mittel {} ms, {} Werte", TAG, this.phase + 1, on ? "Reaktion an" : "Reaktion aus",
                format(median), format(mean), values.length);
    }

    private void finish() {
        this.done = true;
        react(true);
        DroneReactions.setActiveFilter(null);
        double offMedian = average(false, 0);
        double onMedian = average(true, 0);
        double offMean = average(false, 1);
        double onMean = average(true, 1);
        double delta = onMedian - offMedian;
        double own = this.reactionTicks == 0 ? 0.0 : this.reactionNanos / 1.0E6 / this.reactionTicks;
        long alive = this.mobs.stream().filter(Entity::isAlive).count();
        List<String> problems = new ArrayList<>();
        if (!(delta <= LIMIT_MS)) {
            problems.add("MSPT steigt um " + format(delta) + " ms (Grenze " + format(LIMIT_MS) + ")");
        }
        if (this.maxAssigned < 20) {
            problems.add("nur " + this.maxAssigned + " Mobs gleichzeitig zugeordnet, Last zu klein");
        }
        if (this.mobHits == 0) {
            problems.add("kein Mob hat eine Drohne erreicht, die KI lief nicht");
        }
        if (this.maxEvaluations > 64) {
            problems.add("Budget überschritten: " + this.maxEvaluations + " Auswertungen in einem Tick");
        }
        String summary = String.format(Locale.ROOT,
                "%d Drohnen, %d Mobs (%d leben), MSPT Median aus %s ms / an %s ms (Δ %s ms, Grenze %s), Mittel aus %s ms / an %s ms, "
                        + "Reaktions-Takt %s ms je Tick, höchstens %d Auswertungen je Tick, höchstens %d Mobs zugeordnet, %d Treffer an Drohnen, %d Phasen à %d Ticks",
                this.drones.size(), this.mobs.size(), alive, format(offMedian), format(onMedian), format(delta), format(LIMIT_MS), format(offMean),
                format(onMean), format(own), this.maxEvaluations, this.maxAssigned, this.mobHits, this.phases.size(), PHASE_TICKS);
        String result = (problems.isEmpty() ? "PASS D6: " : "FAIL D6: " + String.join("; ", problems) + " | ") + summary;
        MobDefense.LOG.info("{} {}", TAG, result);
        write(result);
    }

    private double average(boolean on, int index) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < this.phases.size(); i++) {
            if (this.phaseOn.get(i) == on && Double.isFinite(this.phases.get(i)[index])) {
                sum += this.phases.get(i)[index];
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
