package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.mob.client.CovMdBMobInteractionSteps;
import de.lardtom.mobdefense.dev.mob.client.CovMdBMobSteps;

public final class CovMdBMobModule implements DevModule {
    @Override
    public String name() {
        return "covmdb_mob";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdBMobSteps.register(steps);
        CovMdBMobInteractionSteps.register(steps);
    }
}
