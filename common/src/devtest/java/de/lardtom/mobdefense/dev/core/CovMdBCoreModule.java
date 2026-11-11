package de.lardtom.mobdefense.dev.core;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.core.client.CovMdBCoreSteps;

public final class CovMdBCoreModule implements DevModule {
    @Override
    public String name() {
        return "covmdb_core";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdBCoreSteps.register(steps);
    }
}
