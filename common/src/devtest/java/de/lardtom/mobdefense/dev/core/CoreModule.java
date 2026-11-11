package de.lardtom.mobdefense.dev.core;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.core.client.CoreSteps;

public final class CoreModule implements DevModule {
    @Override
    public String name() {
        return "core";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Teile, Items und Rezepte", 0, CoreChecks::parts);
        checks.add("Item-Tags", 0, CoreChecks::tags);
        checks.add("Welt-Regeln über B5", 0, CoreChecks::rules);
        checks.add("Kreativ-Reiter", 0, CoreChecks::tab);
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CoreSteps.register(steps);
    }
}
