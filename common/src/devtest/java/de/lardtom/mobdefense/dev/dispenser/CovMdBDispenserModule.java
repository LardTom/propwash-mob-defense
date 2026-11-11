package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdBBlockSteps;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdBPilotSteps;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdBWindSteps;

public final class CovMdBDispenserModule implements DevModule {
    @Override
    public String name() {
        return "covmdb_dispenser";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        if (CovMdBTwoPilotsScene.enabled()) {
            checks.add("Zwei Piloten: Szene vorbereiten", 10, CovMdBTwoPilotsScene::prepare);
        }
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdBWindSteps.register(steps);
        CovMdBBlockSteps.register(steps);
        CovMdBPilotSteps.register(steps);
    }
}
