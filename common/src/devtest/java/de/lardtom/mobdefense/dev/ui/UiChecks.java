package de.lardtom.mobdefense.dev.ui;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.net.Notices;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

public final class UiChecks {
    public static final List<String> ADVANCEMENTS = List.of("root", "air_support", "lance_flight", "afterburner", "recoil");

    private UiChecks() {
    }

    public static String advancements(MinecraftServer server) {
        List<String> missing = new ArrayList<>();
        for (String id : ADVANCEMENTS) {
            AdvancementHolder holder = server.getAdvancements().get(MobDefense.id(id));
            if (holder == null) {
                missing.add(id);
            } else if (!id.equals("root") && holder.value().parent().map(parent -> !parent.equals(MobDefense.id("root"))).orElse(true)) {
                missing.add(id + " (ohne Elternteil root)");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Fortschritte fehlen: " + missing);
        }
        if (!Notices.installed()) {
            throw new IllegalStateException("Versand der OSD-Hinweise im Loader nicht angemeldet");
        }
        return ADVANCEMENTS.size() + " Fortschritte geladen (root und vier unter root), Versand der OSD-Hinweise angemeldet";
    }
}
