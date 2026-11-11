package de.lardtom.mobdefense.spear.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

public final class SpearMass {
    public static final Map<String, Double> VANILLA = grams();

    private SpearMass() {
    }

    private static Map<String, Double> grams() {
        Map<String, Double> out = new LinkedHashMap<>();
        out.put("minecraft:wooden_spear", 180.0);
        out.put("minecraft:stone_spear", 260.0);
        out.put("minecraft:copper_spear", 280.0);
        out.put("minecraft:iron_spear", 300.0);
        out.put("minecraft:golden_spear", 340.0);
        out.put("minecraft:diamond_spear", 280.0);
        out.put("minecraft:netherite_spear", 360.0);
        return Map.copyOf(out);
    }

    public static OptionalDouble grams(String itemId) {
        Double grams = itemId == null ? null : VANILLA.get(itemId);
        return grams == null ? OptionalDouble.empty() : OptionalDouble.of(grams);
    }
}
