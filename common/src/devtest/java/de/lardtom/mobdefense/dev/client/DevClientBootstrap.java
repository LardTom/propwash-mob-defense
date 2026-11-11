package de.lardtom.mobdefense.dev.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.MobDefenseClient;
import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.DevModules;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.join.JoinProbe;

public final class DevClientBootstrap {
    private DevClientBootstrap() {
    }

    public static void init() {
        if (SelfTest.enabled()) {
            for (DevModule module : DevModules.all()) {
                module.clientSteps(SelfTest.steps(module.name()));
            }
            CleanupStep.register(SelfTest.baseSteps());
            SelfTestRunner.install();
        } else if (SelfTest.smokeEnabled() && JoinProbe.requested()) {
            MobDefense.LOG.info("[Mob-Defense-Beitrittstest] Client mit Mob Defense");
            JoinProbe.install(true);
            MobDefenseClient.onClientTick(JoinProbe::tick);
        } else if (SelfTest.smokeEnabled()) {
            SelfTestRunner.installSmoke();
        }
        de.lardtom.mobdefense.dev.booster.client.D4Spectator.init();
        de.lardtom.mobdefense.dev.dispenser.client.CovMdBTwoPilotsClient.init();
    }
}
