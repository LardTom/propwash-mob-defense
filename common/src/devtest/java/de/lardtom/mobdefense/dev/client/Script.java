package de.lardtom.mobdefense.dev.client;

import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class Script implements SelfTest.ClientStep {
    private static final String INDEX = "script.index";
    private static final String SINCE = "script.since";
    private static final String FUTURE = "script.future";
    private static final String NOTES = "script.notes";
    private static final int SERVER_TICKS = 600;

    private final List<Named> stages = new ArrayList<>();

    private Script() {
    }

    public static Script create() {
        return new Script();
    }

    @FunctionalInterface
    public interface Stage {
        StepResult tick(SelfTest.StepContext ctx, int ticks);
    }

    @FunctionalInterface
    public interface ServerAction {
        void run(MinecraftServer server, Map<String, Object> state) throws Exception;
    }

    private record Named(String label, Stage stage) {
    }

    public Script then(String label, Stage stage) {
        this.stages.add(new Named(label, stage));
        return this;
    }

    public Script run(String label, Runnable action) {
        return this.then(label, (ctx, ticks) -> {
            action.run();
            return StepResult.pass();
        });
    }

    public Script check(String label, Supplier<StepResult> check) {
        return this.then(label, (ctx, ticks) -> check.get());
    }

    public Script server(String label, ServerAction action) {
        return this.then(label, (ctx, ticks) -> server(ctx, ticks, action));
    }

    public Script await(String label, int maxTicks, BooleanSupplier condition) {
        return this.then(label, (ctx, ticks) -> {
            if (condition.getAsBoolean()) {
                return StepResult.pass();
            }
            return ticks >= maxTicks ? StepResult.fail("nicht erreicht nach " + maxTicks + " Ticks") : StepResult.pending();
        });
    }

    public Script sleep(int ticks) {
        return this.then("warten", (ctx, elapsed) -> elapsed >= ticks ? StepResult.pass() : StepResult.pending());
    }

    public Script append(Script other) {
        this.stages.addAll(other.stages);
        return this;
    }

    public static StepResult server(SelfTest.StepContext ctx, int ticks, ServerAction action) {
        Map<String, Object> state = ctx.state();
        Object pending = state.get(FUTURE);
        if (pending == null) {
            state.put(FUTURE, ctx.onServer(server -> {
                try {
                    action.run(server, state);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }));
            return StepResult.pending();
        }
        CompletableFuture<?> future = (CompletableFuture<?>) pending;
        if (!future.isDone()) {
            return ticks >= SERVER_TICKS ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
        }
        state.remove(FUTURE);
        if (future.isCompletedExceptionally()) {
            Throwable cause = future.exceptionNow();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            return StepResult.fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }
        return StepResult.pass();
    }

    @SuppressWarnings("unchecked")
    public static void note(SelfTest.StepContext ctx, String note) {
        ((List<String>) ctx.state().computeIfAbsent(NOTES, key -> new ArrayList<String>())).add(note);
        ctx.log(note);
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepResult tick(SelfTest.StepContext ctx) {
        Map<String, Object> state = ctx.state();
        int index = (int) state.getOrDefault(INDEX, 0);
        if (index >= this.stages.size()) {
            return this.finish(state);
        }
        int since = (int) state.computeIfAbsent(SINCE, key -> ctx.tick());
        Named named = this.stages.get(index);
        StepResult result = named.stage().tick(ctx, ctx.tick() - since);
        if (!result.done()) {
            return result;
        }
        if (result.failed()) {
            return StepResult.fail(named.label() + ": " + result.message());
        }
        if (!result.message().isEmpty()) {
            ((List<String>) state.computeIfAbsent(NOTES, key -> new ArrayList<String>())).add(result.message());
            ctx.log(named.label() + ": " + result.message());
        }
        state.put(INDEX, index + 1);
        state.put(SINCE, ctx.tick() + 1);
        return index + 1 >= this.stages.size() ? this.finish(state) : StepResult.pending();
    }

    @SuppressWarnings("unchecked")
    private StepResult finish(Map<String, Object> state) {
        List<String> notes = (List<String>) state.getOrDefault(NOTES, List.of());
        return StepResult.pass(String.join("; ", notes));
    }
}
