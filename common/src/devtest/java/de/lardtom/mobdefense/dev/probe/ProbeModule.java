package de.lardtom.mobdefense.dev.probe;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.propwash.api.PropwashApi;

public final class ProbeModule implements DevModule {
    @Override
    public String name() {
        return "probe";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Add-on am Server", 20, server -> {
            if (!MobDefense.initialized()) {
                throw new IllegalStateException("Mob Defense nicht gestartet");
            }
            return "Mob Defense läuft mit Propwash-API " + PropwashApi.API_VERSION + " nach 20 Ticks";
        });
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        ProbeSteps.register(steps);
    }
}
