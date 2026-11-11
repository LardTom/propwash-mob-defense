package de.lardtom.mobdefense.dev.ui;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.ui.client.UiSteps;

public final class UiModule implements DevModule {
    @Override
    public String name() {
        return "ui";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Fortschritte geladen", 0, UiChecks::advancements);
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        UiSteps.register(steps);
    }
}
