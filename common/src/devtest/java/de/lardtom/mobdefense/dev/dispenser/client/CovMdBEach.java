package de.lardtom.mobdefense.dev.dispenser.client;

import de.lardtom.mobdefense.dev.SelfTest.StepResult;
import de.lardtom.mobdefense.dev.client.Arena;
import de.lardtom.mobdefense.dev.client.Script;
import de.lardtom.mobdefense.dev.dispenser.CovMdBDrift;
import de.lardtom.propwash.api.drone.AutonomyResult;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class CovMdBEach {
    private static int keys;

    private CovMdBEach() {
    }

    @FunctionalInterface
    public interface Action {
        void run(MinecraftServer server, Map<String, Object> state, int index) throws Exception;
    }

    public static Script.Stage launch(Function<Map<String, Object>, Entity> drone, double metres, String key) {
        return (ctx, ticks) -> {
            Map<String, Object> state = ctx.state();
            if (state.get(key) instanceof CovMdBDrift) {
                return StepResult.pass();
            }
            if (ticks > 400) {
                return StepResult.fail("autonomer Flug startet nicht: " + state.get(key + ".last"));
            }
            if (ticks % 10 == 0) {
                ctx.onServer(server -> {
                    Entity target = drone.apply(state);
                    CovMdBDrift drift = new CovMdBDrift(target.getX(), target.getY() + metres, target.getZ());
                    AutonomyResult result = drift.begin(target, Arena.player(server), metres);
                    if (result.started()) {
                        state.put(key, drift);
                    } else {
                        state.put(key + ".last", result);
                    }
                });
            }
            return StepResult.pending();
        };
    }

    public static synchronized Script.Stage each(ToIntFunction<Map<String, Object>> count, int interval, Action action) {
        String key = "covmdb.each." + keys++;
        return (ctx, ticks) -> {
            Map<String, Object> state = ctx.state();
            int done = (int) state.getOrDefault(key, 0);
            Object pending = state.get(key + ".future");
            if (pending instanceof CompletableFuture<?> future) {
                if (!future.isDone()) {
                    return ticks > (done + 1) * (interval + 200) ? StepResult.fail("Server antwortet nicht") : StepResult.pending();
                }
                state.remove(key + ".future");
                if (future.isCompletedExceptionally()) {
                    Throwable cause = future.exceptionNow();
                    return StepResult.fail(cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
                done++;
                state.put(key, done);
                state.put(key + ".tick", ticks);
            }
            if (done >= count.applyAsInt(state)) {
                return StepResult.pass();
            }
            int last = (int) state.getOrDefault(key + ".tick", -interval);
            if (ticks - last < interval) {
                return StepResult.pending();
            }
            int index = done;
            state.put(key + ".future", ctx.onServer(server -> {
                try {
                    action.run(server, state, index);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
            return StepResult.pending();
        };
    }
}
