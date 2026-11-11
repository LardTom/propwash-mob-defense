package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.CovMdAWatch;
import de.lardtom.mobdefense.dev.dispenser.DispenserRig;
import de.lardtom.mobdefense.dev.dispenser.Rules;
import de.lardtom.mobdefense.dispenser.DispenserBehavior;
import de.lardtom.mobdefense.dispenser.DispenserCadences;
import de.lardtom.mobdefense.dispenser.DispenserLog;
import de.lardtom.mobdefense.dispenser.core.FireCadence;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.propwash.api.PropwashApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class TimedTriggerSteps {
    public static final int EVERY = 2;
    public static final int TRIGGER_TICKS = 48;
    public static final int RULE_LOW = 1;
    public static final int RULE_HIGH = 8;
    public static final int SET_DELAY = 10;
    public static final int SET_INTERVAL = 6;
    public static final int ARROWS = 64;
    private static final String DRONE = "takt.drone";
    private static final String START = "takt.start";
    private static final String TRIGGERS = "takt.triggers";
    private static final String DONE = "takt.done";
    private static final String PROBLEMS = "takt.problems";
    private static final String RULES = "takt.rules";
    private static final String SETTING = "takt.setting";
    private static final String RESULT = "takt.result";

    private TimedTriggerSteps() {
    }

    public static void register(SelfTest.Steps steps) {
        steps.add("Auslöser im Takt wie der Flugplaner – Zubehör-Aktion 1 alle " + EVERY + " Ticks über triggerAction an einer Drohne ohne Pilot, "
                + "Welt-Regel dispenser_cooldown_ticks " + RULE_LOW + ": Abstand nie unter der festen Mindestzeit " + FireCadence.MIN_TICKS
                + " Ticks und nie unter dem eingestellten Abstand " + SET_INTERVAL + " Ticks, Welt-Regel " + RULE_HIGH + " darüber", 1500, script(),
                TimedTriggerSteps::cleanup);
    }

    private static Script script() {
        return Script.create()
                .server("Drohne ohne Pilot auf der Säule, " + ARROWS + " Pfeile, Welt-Regel dispenser_cooldown_ticks " + RULE_LOW, TimedTriggerSteps::setUp)
                .append(phase("Standard-Abstand " + FireCadence.Setting.DEFAULT.intervalTicks() + " Ticks, Welt-Regel " + RULE_LOW, RULE_LOW,
                        FireCadence.Setting.DEFAULT, FireCadence.MIN_TICKS))
                .append(phase("eingestellt " + SET_DELAY + "/" + SET_INTERVAL + " Ticks, Welt-Regel " + RULE_LOW, RULE_LOW,
                        new FireCadence.Setting(SET_DELAY, SET_INTERVAL), SET_INTERVAL))
                .append(phase("eingestellt " + SET_DELAY + "/" + SET_INTERVAL + " Ticks, Welt-Regel " + RULE_HIGH, RULE_HIGH,
                        new FireCadence.Setting(SET_DELAY, SET_INTERVAL), RULE_HIGH))
                .server("Welt-Regel und Schussfolge zurück", TimedTriggerSteps::restore)
                .then("Abstand nie unter der Einstellung", TimedTriggerSteps::verdict);
    }

    private static Script phase(String label, int rule, FireCadence.Setting setting, int gap) {
        return Script.create()
                .server(label + ": Takt starten", (server, state) -> start(server, state, rule, setting))
                .then(label + ": alle " + (TRIGGER_TICKS / EVERY) + " Auslöser gesendet", TimedTriggerSteps::sent)
                .sleep(2 * RULE_HIGH)
                .server(label + ": Pfeile", (server, state) -> state.put(RESULT + "." + label, check(server, state, label, gap)))
                .then(label, (ctx, ticks) -> StepResult.pass((String) ctx.state().get(RESULT + "." + label)));
    }

    private static void setUp(MinecraftServer server, Map<String, Object> state) {
        ServerPlayer player = Arena.player(server);
        ServerLevel level = player.level();
        BlockPos pillar = Arena.ground(8, -6).above();
        Entity drone = Arena.track(DispenserRig.onPillar(level, player, DispenserRig.fiveInch(), "OWNC5B Takt", pillar, Direction.NORTH,
                (pos, block) -> Arena.place(level, pos, block)));
        DispenserRig.load(drone, new ItemStack(Items.ARROW, ARROWS));
        state.put(DRONE, drone.getUUID());
        state.put(SETTING, DispenserCadences.of(player.getUUID()));
        state.put(PROBLEMS, new ArrayList<String>());
        Rules rules = new Rules(server);
        rules.set(RuleDefaults.DISPENSER_COOLDOWN_TICKS, RULE_LOW);
        state.put(RULES, rules);
    }

    private static void start(MinecraftServer server, Map<String, Object> state, int rule, FireCadence.Setting setting) {
        ServerPlayer player = Arena.player(server);
        Entity drone = drone(server, state);
        ((Rules) state.get(RULES)).set(RuleDefaults.DISPENSER_COOLDOWN_TICKS, rule);
        DispenserCadences.set(player.getUUID(), setting);
        AtomicInteger triggers = new AtomicInteger();
        AtomicInteger done = new AtomicInteger();
        state.put(TRIGGERS, triggers);
        state.put(DONE, done);
        state.put(START, drone.level().getGameTime());
        for (int at = 0; at < TRIGGER_TICKS; at += EVERY) {
            CovMdAWatch.later(1 + at, () -> {
                if (PropwashApi.accessories().triggerAction(drone, DispenserBehavior.FIRE, true, player)) {
                    triggers.incrementAndGet();
                }
                PropwashApi.accessories().triggerAction(drone, DispenserBehavior.FIRE, false, player);
                done.incrementAndGet();
            });
        }
    }

    private static StepResult sent(SelfTest.StepContext ctx, int ticks) {
        if (ctx.state().get(DONE) instanceof AtomicInteger done && done.get() >= TRIGGER_TICKS / EVERY) {
            return StepResult.pass();
        }
        return ticks > TRIGGER_TICKS + 200 ? StepResult.fail("Auslöser nicht gesendet") : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private static StepResult verdict(SelfTest.StepContext ctx, int ticks) {
        List<String> problems = (List<String>) ctx.state().get(PROBLEMS);
        return problems == null || problems.isEmpty() ? StepResult.pass() : StepResult.fail(String.join("; ", problems));
    }

    @SuppressWarnings("unchecked")
    private static String check(MinecraftServer server, Map<String, Object> state, String label, int gap) {
        Entity drone = drone(server, state);
        long start = (long) state.get(START);
        int triggers = ((AtomicInteger) state.get(TRIGGERS)).get();
        List<Long> times = new ArrayList<>();
        for (DispenserLog.Entry entry : DispenserLog.history(drone)) {
            if (entry.gameTime() >= start && entry.result() != null && entry.result().success()) {
                if (!(entry.result().entity().orElse(null) instanceof AbstractArrow)) {
                    throw new IllegalStateException(label + ": " + CovMdAWatch.type(entry.result().entity().orElse(null)) + " statt Pfeil");
                }
                times.add(entry.gameTime());
            }
        }
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < times.size(); i++) {
            gaps.add(times.get(i) - times.get(i - 1));
        }
        int expected = (TRIGGER_TICKS - EVERY) / gap + 1;
        String text = label + ": " + times.size() + " Pfeile bei " + triggers + " Auslösern im Abstand " + gaps + " Ticks";
        String problem = null;
        if (triggers != TRIGGER_TICKS / EVERY) {
            problem = (TRIGGER_TICKS / EVERY) + " Auslöser erwartet";
        } else if (gaps.stream().anyMatch(seen -> seen < gap)) {
            problem = "Abstand " + gaps.stream().mapToLong(Long::longValue).min().orElse(0) + " Ticks unter " + gap + " Ticks";
        } else if (times.size() != expected || gaps.stream().anyMatch(seen -> seen != gap)) {
            problem = "erwartet " + expected + " Pfeile im Abstand " + gap + " Ticks";
        }
        if (problem != null) {
            ((List<String>) state.get(PROBLEMS)).add(text + " – " + problem);
            return text + " – FEHLER: " + problem;
        }
        return text;
    }

    private static void restore(MinecraftServer server, Map<String, Object> state) {
        if (state.remove(RULES) instanceof Rules rules) {
            rules.restore();
        }
        if (state.remove(SETTING) instanceof FireCadence.Setting setting) {
            DispenserCadences.set(Arena.player(server).getUUID(), setting);
        }
    }

    private static Entity drone(MinecraftServer server, Map<String, Object> state) {
        Object id = state.get(DRONE);
        Entity drone = id instanceof UUID uuid ? Arena.level(server).getEntity(uuid) : null;
        if (drone == null) {
            throw new IllegalStateException("Drohne fehlt am Server");
        }
        return drone;
    }

    private static final SelfTest.ClientStep CLEANUP = Script.create()
            .server("Welt-Regel und Schussfolge zurück, Geschosse weg", (server, state) -> {
                restore(server, state);
                CovMdAShotSteps.sweep(server);
            });

    private static StepResult cleanup(SelfTest.StepContext ctx) {
        return CLEANUP.tick(ctx);
    }
}
