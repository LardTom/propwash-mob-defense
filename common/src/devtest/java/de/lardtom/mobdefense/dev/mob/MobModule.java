package de.lardtom.mobdefense.dev.mob;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.mob.client.MobSteps;

public final class MobModule implements DevModule {
    @Override
    public String name() {
        return "mob";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Tags reacts/* und Arten am Server", 5, MobChecks::tags);
        checks.add("Accessoren und Listener", 5, MobChecks::wiring);
        checks.add("Tags und Varianten der übrigen Arten", 5, MobChecksTwo::tags);
        checks.add("Haken der übrigen Arten in Vanilla", 5, MobChecksTwo::hooks);
        if (D6Run.enabled()) {
            checks.add("D6 Last mit 10 Drohnen und 50 Mobs", 20, D6Run::start);
        }
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        MobSteps.register(steps);
        de.lardtom.mobdefense.dev.mob.client.MobStepsTwo.register(steps);
        de.lardtom.mobdefense.dev.mob.client.UnarmedSteps.register(steps);
    }
}
