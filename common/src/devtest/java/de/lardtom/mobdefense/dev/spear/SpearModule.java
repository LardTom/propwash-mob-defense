package de.lardtom.mobdefense.dev.spear;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.spear.client.SpearFeedbackSteps;
import de.lardtom.mobdefense.dev.spear.client.SpearRamSteps;
import de.lardtom.mobdefense.dev.spear.client.SpearSteps;

public final class SpearModule implements DevModule {
    @Override
    public String name() {
        return "spear";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Speer-Halterung nimmt nur Speere", 0, server -> SpearChecks.accepts());
        checks.add("D3 Speer: Bereich laden", 10, SpearChecks::d3Area);
        checks.add("D3 Speer: payloads_hit_players × PvP", 60, SpearChecks::d3);
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        SpearSteps.register(steps);
        SpearFeedbackSteps.register(steps);
        SpearRamSteps.register(steps);
    }
}
