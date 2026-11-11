package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.booster.client.CovMdBBoosterSteps;

public final class CovMdBBoosterModule implements DevModule {
    @Override
    public String name() {
        return "covmdb_booster";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdBBoosterSteps.register(steps);
    }
}
