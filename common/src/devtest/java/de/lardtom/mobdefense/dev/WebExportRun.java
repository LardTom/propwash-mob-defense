package de.lardtom.mobdefense.dev;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.export.WebExport;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;

public final class WebExportRun {
    public static final String PROPERTY = "mobdefense.webexport";
    private static final String TAG = "[Mob-Defense-Web-Export]";

    private WebExportRun() {
    }

    public static void init() {
        String target = System.getProperty(PROPERTY);
        if (target == null || target.isBlank()) {
            return;
        }
        MobDefense.onServerStarted(WebExportRun::onStarted);
    }

    private static void onStarted(MinecraftServer server) {
        String result;
        try {
            Path target = Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();
            long start = System.nanoTime();
            WebExport.Result export = WebExport.run(server, target);
            result = export.summary();
            MobDefense.LOG.info("{} {} nach {} in {} ms", TAG, result, target, (System.nanoTime() - start) / 1_000_000L);
        } catch (RuntimeException e) {
            MobDefense.LOG.error("{} abgebrochen", TAG, e);
            result = "FAIL Ausnahme im Web-Export: " + e;
        }
        DevResult.write(result);
        server.halt(false);
    }
}
