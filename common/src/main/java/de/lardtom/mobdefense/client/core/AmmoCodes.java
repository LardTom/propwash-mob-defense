package de.lardtom.mobdefense.client.core;

import java.util.List;
import java.util.Map;

public final class AmmoCodes {
    public static final String EMPTY = "---";
    public static final String OTHER = "ITM";
    public static final List<String> ALL = List.of("ARW", "SPC", "TIP", "SNW", "EGG", "SPL", "LNG", "XPB", "WND", "FIR", "RKT", "TNT", "BKT", "BON",
            OTHER);
    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("arrow", "ARW"),
            Map.entry("spectral_arrow", "SPC"),
            Map.entry("tipped_arrow", "TIP"),
            Map.entry("snowball", "SNW"),
            Map.entry("egg", "EGG"),
            Map.entry("blue_egg", "EGG"),
            Map.entry("brown_egg", "EGG"),
            Map.entry("splash_potion", "SPL"),
            Map.entry("lingering_potion", "LNG"),
            Map.entry("experience_bottle", "XPB"),
            Map.entry("wind_charge", "WND"),
            Map.entry("fire_charge", "FIR"),
            Map.entry("firework_rocket", "RKT"),
            Map.entry("tnt", "TNT"),
            Map.entry("bucket", "BKT"),
            Map.entry("bone_meal", "BON"));

    private AmmoCodes() {
    }

    public static String code(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return EMPTY;
        }
        int colon = itemId.indexOf(':');
        String path = colon < 0 ? itemId : itemId.substring(colon + 1);
        String exact = EXACT.get(path);
        if (exact != null) {
            return exact;
        }
        if (path.endsWith("_arrow")) {
            return "ARW";
        }
        if (path.endsWith("_bucket")) {
            return "BKT";
        }
        return OTHER;
    }
}
