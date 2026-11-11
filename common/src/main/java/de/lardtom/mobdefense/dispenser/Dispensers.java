package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.payload.Payloads;
import de.lardtom.propwash.api.PropwashApi;

public final class Dispensers {
    private static boolean initialized;

    private Dispensers() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        DispenserState.register();
        PropwashApi.accessories().register(MobDefense.DISPENSER_MOUNT, DispenserBehavior.INSTANCE);
        Payloads.init();
        MobDefense.onServerStarted(server -> DispenserCadences.clear());
        MobDefense.LOG.info("{}: Dispenser-Halterung mit {} Slots angemeldet", MobDefense.MOD_NAME, DispenserBehavior.INSTANCE.slots());
    }

    public static boolean initialized() {
        return initialized;
    }
}
