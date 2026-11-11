package de.lardtom.mobdefense.dev.dispenser;

import de.lardtom.mobdefense.dev.DevModule;
import de.lardtom.mobdefense.dev.SelfTest;
import de.lardtom.mobdefense.dev.ServerChecks;
import de.lardtom.mobdefense.dev.dispenser.client.DispenserSteps;

public final class DispenserModule implements DevModule {
    @Override
    public String name() {
        return "dispenser";
    }

    @Override
    public void serverChecks(ServerChecks checks) {
        checks.add("Dispenser-Verhalten, Aktionen und Munition", 0, DispenserChecks::behavior);
        checks.add("Chunks für D3, D5, D7", DispenserChecks.PREPARE_TICK, DispenserChecks::prepare);
        checks.add("D7 Rechte – Spawnschutz und Abenteuermodus", DispenserChecks.RIGHTS_TICK, DispenserChecks::rights);
        checks.add("D3 Start – Spieler × PvP mit Pfeil, Schadens-, Gift- und Heiltrank", DispenserChecks.MATRIX_TICK, DispenserChecks::startMatrix);
        checks.add("D5 Start – Makro 50/s", DispenserChecks.MACRO_TICK, DispenserChecks::startMacro);
        checks.add("D3 Spieler × PvP", DispenserChecks.RESULT_TICK, DispenserChecks::matrixResult);
        checks.add("D5 Makro 50/s", DispenserChecks.RESULT_TICK + 1, DispenserChecks::macroResult);
    }

    @Override
    public void clientSteps(SelfTest.Steps steps) {
        DispenserSteps.register(steps);
    }
}
