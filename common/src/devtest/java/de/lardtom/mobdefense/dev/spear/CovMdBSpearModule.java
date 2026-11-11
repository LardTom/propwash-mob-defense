package de.lardtom.mobdefense.dev.spear;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.spear.client.CovMdBSpearSteps;

public final class CovMdBSpearModule implements DevModule {
    @Override
    public String name() {
        return "covmdb_spear";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdBSpearSteps.register(steps);
    }
}
