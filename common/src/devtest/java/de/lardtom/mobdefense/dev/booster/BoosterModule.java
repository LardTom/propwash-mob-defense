package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.booster.client.BoosterSteps;

public final class BoosterModule implements DevModule {
    @Override
    public String name() {
        return "booster";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Raketen-Booster angemeldet", 0, server -> BoosterChecks.registered());
        if (D4Scene.enabled()) {
            checks.add("D4 Zuschauer: Bereich laden", 10, D4Scene::area);
            checks.add("D4 Zuschauer: fremde Drohne mit Rakete und Speer", 40, D4Scene::start);
        }
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        BoosterSteps.register(steps);
    }
}
