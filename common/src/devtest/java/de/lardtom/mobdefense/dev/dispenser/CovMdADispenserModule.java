package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdABarrelSteps;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdAFlightSteps;
import de.lardtom.mobdefense.dev.dispenser.client.CovMdAShotSteps;

public final class CovMdADispenserModule implements DevModule {
    @Override
    public String name() {
        return "covmda_dispenser";
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        CovMdAShotSteps.register(steps);
        CovMdABarrelSteps.register(steps);
        CovMdAFlightSteps.register(steps);
        CovMdAFlightSteps.registerOwnWindCharges(steps);
    }
}
