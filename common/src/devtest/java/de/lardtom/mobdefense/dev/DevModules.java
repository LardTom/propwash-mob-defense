package de.lardtom.mobdefense.dev;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DevModules {
    private DevModules() {
    }

    public static List<DevModule> all() {
        List<DevModule> modules = new ArrayList<>();
        modules.add(new de.lardtom.mobdefense.dev.probe.ProbeModule());
        modules.add(new de.lardtom.mobdefense.dev.core.CoreModule());
        modules.add(new de.lardtom.mobdefense.dev.ui.UiModule());
        modules.add(new de.lardtom.mobdefense.dev.mob.MobModule());
        modules.add(new de.lardtom.mobdefense.dev.spear.SpearModule());
        modules.add(new de.lardtom.mobdefense.dev.dispenser.DispenserModule());
        modules.add(new de.lardtom.mobdefense.dev.booster.BoosterModule());
        modules.add(new de.lardtom.mobdefense.dev.dispenser.CovMdBDispenserModule());
        modules.add(new de.lardtom.mobdefense.dev.spear.CovMdBSpearModule());
        modules.add(new de.lardtom.mobdefense.dev.booster.CovMdBBoosterModule());
        modules.add(new de.lardtom.mobdefense.dev.mob.CovMdBMobModule());
        modules.add(new de.lardtom.mobdefense.dev.core.CovMdBCoreModule());
        modules.add(new de.lardtom.mobdefense.dev.mob.CovMdCMobModule());
        modules.add(new de.lardtom.mobdefense.dev.core.CovMdACoreModule());
        modules.add(new de.lardtom.mobdefense.dev.dispenser.CovMdADispenserModule());
        modules.add(new de.lardtom.mobdefense.dev.ui.CovMdAUiModule());
        modules.add(new de.lardtom.mobdefense.dev.dispenser.OwnMDispenserModule());
        Set<String> names = new HashSet<>();
        for (DevModule module : modules) {
            if (!names.add(module.name())) {
                throw new IllegalStateException("Modul doppelt angemeldet: " + module.name());
            }
        }
        return List.copyOf(modules);
    }
}
