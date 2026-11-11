package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.MobDefense;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Runs {
    private static final List<Run> ACTIVE = new ArrayList<>();
    private static boolean hooked;

    public interface Run {
        void tick(MinecraftServer server) throws Exception;

        boolean done();

        @Nullable String failure();

        String summary();

        void abort(MinecraftServer server, String reason);
    }

    private Runs() {
    }

    public static synchronized <T extends Run> T start(T run) {
        if (!hooked) {
            hooked = true;
            MobDefense.onServerTick(Runs::tick);
        }
        ACTIVE.add(run);
        return run;
    }

    private static void tick(MinecraftServer server) {
        List<Run> runs;
        synchronized (Runs.class) {
            if (ACTIVE.isEmpty()) {
                return;
            }
            runs = new ArrayList<>(ACTIVE);
        }
        for (Run run : runs) {
            try {
                run.tick(server);
            } catch (Exception e) {
                MobDefense.LOG.error("[Mob-Defense-Dispenser] {} abgebrochen", run.getClass().getSimpleName(), e);
                run.abort(server, e.getMessage() == null ? e.toString() : e.getMessage());
            }
            if (run.done()) {
                synchronized (Runs.class) {
                    ACTIVE.remove(run);
                }
            }
        }
    }

    public static String result(@Nullable Run run, String name) {
        if (run == null) {
            throw new IllegalStateException(name + " lief nicht");
        }
        if (!run.done()) {
            throw new IllegalStateException(name + " nicht fertig: " + run.summary());
        }
        if (run.failure() != null) {
            throw new IllegalStateException(name + ": " + run.failure());
        }
        return run.summary();
    }
}
