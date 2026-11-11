package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.dispenser.client.OwnMSteps;
import de.lardtom.mobdefense.dev.dispenser.client.TimedTriggerSteps;

public final class OwnMDispenserModule implements DevModule {
    @Override
    public String name() {
        return "ownm_dispenser";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        OwnMSteps.register(steps);
        TimedTriggerSteps.register(steps);
    }
}
