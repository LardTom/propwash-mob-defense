package de.lardtom.mobdefense.dev.ui;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ui.client.CovMdAUiSteps;

public final class CovMdAUiModule implements DevModule {
    @Override
    public String name() {
        return "covmda_ui";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdAUiSteps.register(steps);
        CovMdAUiSteps.registerLineOfSight(steps);
        CovMdAUiSteps.registerThreat(steps);
        CovMdAUiSteps.registerThreatUnderFire(steps);
        CovMdAUiSteps.registerLanceFlight(steps);
    }
}
