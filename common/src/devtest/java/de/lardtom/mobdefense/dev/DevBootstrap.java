package de.lardtom.mobdefense.dev;

public final class DevBootstrap {
    private DevBootstrap() {
    }

    public static void init() {
        ServerSmoke.init();
        WebExportRun.init();
        de.lardtom.mobdefense.dev.core.D8Run.init();
    }
}
