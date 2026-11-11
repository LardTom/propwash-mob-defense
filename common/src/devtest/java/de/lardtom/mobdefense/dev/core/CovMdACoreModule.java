package de.lardtom.mobdefense.dev.core;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.core.client.CovMdACoreSteps;

public final class CovMdACoreModule implements DevModule {
    @Override
    public String name() {
        return "covmda_core";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdACoreSteps.register(steps);
    }
}
