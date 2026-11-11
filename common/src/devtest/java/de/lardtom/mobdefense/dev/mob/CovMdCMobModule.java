package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.mob.client.CovMdCFairnessSteps;
import de.lardtom.mobdefense.dev.mob.client.CovMdCKindSteps;
import de.lardtom.mobdefense.dev.mob.client.CovMdCRuleSteps;

public final class CovMdCMobModule implements DevModule {
    @Override
    public String name() {
        return "covmdc_mob";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        if (CovMdCPackCheck.enabled()) {
            checks.add("cov-md-c Datenpaket ignores_drones am dedizierten Server", 5, CovMdCPackCheck::start);
        }
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdCRuleSteps.register(steps);
        CovMdCKindSteps.register(steps);
        CovMdCFairnessSteps.register(steps);
    }
}
