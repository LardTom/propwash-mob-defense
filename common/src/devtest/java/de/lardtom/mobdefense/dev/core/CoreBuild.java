package de.lardtom.mobdefense.dev.core;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildSpec;
import de.lardtom.propwash.api.part.AccessorySlot;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CoreBuild {
    public static final Map<Identifier, AccessorySlot> SLOTS = slots();
    public static final Map<Identifier, Double> GRAMS = grams();
    public static final double ACCESSORY_GRAMS = 150.0 + 40.0 + 28.0;
    public static final double ACCESSORY_WATTS = 1.2 + 0.0 + 0.2;

    private CoreBuild() {
    }

    private static Map<Identifier, AccessorySlot> slots() {
        Map<Identifier, AccessorySlot> out = new LinkedHashMap<>();
        out.put(MobDefense.DISPENSER_MOUNT, AccessorySlot.FRONT);
        out.put(MobDefense.SPEAR_MOUNT, AccessorySlot.BOTTOM);
        out.put(MobDefense.ROCKET_BOOSTER, AccessorySlot.TOP);
        return Map.copyOf(out);
    }

    private static Map<Identifier, Double> grams() {
        Map<Identifier, Double> out = new LinkedHashMap<>();
        out.put(MobDefense.DISPENSER_MOUNT, 150.0);
        out.put(MobDefense.SPEAR_MOUNT, 40.0);
        out.put(MobDefense.ROCKET_BOOSTER, 28.0);
        return Map.copyOf(out);
    }

    public static Identifier propwash(String path) {
        return Identifier.fromNamespaceAndPath(PropwashApi.MOD_ID, path);
    }

    public static BuildSpec base() {
        return BuildSpec.of(propwash("freestyle_5"), propwash("stack_30"), propwash("m2207_1750"), propwash("p51_3b"), propwash("digital_hd"),
                propwash("b6s_1300"));
    }

    public static BuildSpec full() {
        BuildSpec spec = base();
        for (Identifier part : MobDefense.PARTS) {
            spec = spec.withAccessory(SLOTS.get(part), part);
        }
        return spec;
    }

    public static Map<AccessorySlot, Identifier> mounts() {
        Map<AccessorySlot, Identifier> out = new LinkedHashMap<>();
        for (Identifier part : MobDefense.PARTS) {
            out.put(SLOTS.get(part), part);
        }
        return out;
    }
}
