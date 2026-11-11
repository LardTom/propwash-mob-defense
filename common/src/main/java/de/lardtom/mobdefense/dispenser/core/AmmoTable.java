package de.lardtom.mobdefense.dispenser.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AmmoTable {
    public static final int SLOT_LIMIT = 16;
    public static final int TNT_LIMIT = 4;
    public static final int SINGLE = 1;
    public static final String OTHER = "ITM";
    public static final double BUCKET_GRAMS = 250.0;
    public static final double FULL_BUCKET_GRAMS = 1250.0;
    private static final Pattern CODE = Pattern.compile("[A-Z]{3}");

    public enum Kind {
        PROJECTILE,
        TNT,
        FLUID_BUCKET,
        EMPTY_BUCKET,
        BLOCK_USE,
        ENTITY_USE,
        ITEM;

        public boolean changesBlocks() {
            return this == FLUID_BUCKET || this == EMPTY_BUCKET || this == BLOCK_USE;
        }
    }

    public record Ammo(String code, double grams, int limit, Kind kind) {
        public Ammo {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(kind, "kind");
            if (!CODE.matcher(code).matches()) {
                throw new IllegalArgumentException("Kürzel muss aus drei Großbuchstaben bestehen: " + code);
            }
            if (!(grams >= 0.0) || !Double.isFinite(grams)) {
                throw new IllegalArgumentException(code + ": Gramm ungültig: " + grams);
            }
            if (limit < 1 || limit > SLOT_LIMIT) {
                throw new IllegalArgumentException(code + ": Stapelgrenze außerhalb von 1 bis " + SLOT_LIMIT + ": " + limit);
            }
        }

        public boolean weighed() {
            return this.grams > 0.0;
        }
    }

    private static final Map<String, Ammo> BY_ID = table();

    private AmmoTable() {
    }

    private static Map<String, Ammo> table() {
        Map<String, Ammo> out = new LinkedHashMap<>();
        put(out, new Ammo("ARW", 20.0, SLOT_LIMIT, Kind.PROJECTILE), "arrow");
        put(out, new Ammo("SPC", 20.0, SLOT_LIMIT, Kind.PROJECTILE), "spectral_arrow");
        put(out, new Ammo("TIP", 20.0, SLOT_LIMIT, Kind.PROJECTILE), "tipped_arrow");
        put(out, new Ammo("SNW", 60.0, SLOT_LIMIT, Kind.PROJECTILE), "snowball");
        put(out, new Ammo("EGG", 60.0, SLOT_LIMIT, Kind.PROJECTILE), "egg", "blue_egg", "brown_egg");
        put(out, new Ammo("SPL", 350.0, SINGLE, Kind.PROJECTILE), "splash_potion");
        put(out, new Ammo("LNG", 350.0, SINGLE, Kind.PROJECTILE), "lingering_potion");
        put(out, new Ammo("XPB", 300.0, SLOT_LIMIT, Kind.PROJECTILE), "experience_bottle");
        put(out, new Ammo("WND", 50.0, SLOT_LIMIT, Kind.PROJECTILE), "wind_charge");
        put(out, new Ammo("FIR", 80.0, SLOT_LIMIT, Kind.PROJECTILE), "fire_charge");
        put(out, new Ammo("RKT", 60.0, SLOT_LIMIT, Kind.PROJECTILE), "firework_rocket");
        put(out, new Ammo("TNT", 600.0, TNT_LIMIT, Kind.TNT), "tnt");
        put(out, new Ammo("BON", 15.0, SLOT_LIMIT, Kind.BLOCK_USE), "bone_meal");
        put(out, new Ammo("BKT", BUCKET_GRAMS, SINGLE, Kind.EMPTY_BUCKET), "bucket");
        put(out, new Ammo("BKT", FULL_BUCKET_GRAMS, SINGLE, Kind.FLUID_BUCKET), "water_bucket", "lava_bucket", "powder_snow_bucket",
                "axolotl_bucket", "cod_bucket", "pufferfish_bucket", "salmon_bucket", "tadpole_bucket", "tropical_fish_bucket", "sulfur_cube_bucket");
        put(out, new Ammo("BKT", FULL_BUCKET_GRAMS, SINGLE, Kind.ITEM), "milk_bucket");
        put(out, new Ammo(OTHER, 0.0, SLOT_LIMIT, Kind.BLOCK_USE), "flint_and_steel", "shears", "glowstone", "honeycomb", "brush",
                "glass_bottle", "potion", "carved_pumpkin", "wither_skeleton_skull", "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
                "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box", "gray_shulker_box",
                "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box", "brown_shulker_box",
                "green_shulker_box", "red_shulker_box", "black_shulker_box");
        return Collections.unmodifiableMap(out);
    }

    private static void put(Map<String, Ammo> out, Ammo ammo, String... paths) {
        for (String path : paths) {
            if (out.put("minecraft:" + path, ammo) != null) {
                throw new IllegalStateException("doppelt: " + path);
            }
        }
    }

    public static Optional<Ammo> find(String itemId) {
        return Optional.ofNullable(BY_ID.get(itemId));
    }

    public static Ammo resolve(String itemId, Kind fallback) {
        Ammo known = BY_ID.get(itemId);
        if (known != null) {
            return known;
        }
        Kind kind = Objects.requireNonNull(fallback, "fallback");
        return switch (kind) {
            case TNT -> new Ammo("TNT", 0.0, TNT_LIMIT, kind);
            case FLUID_BUCKET, EMPTY_BUCKET -> new Ammo("BKT", 0.0, SINGLE, kind);
            default -> new Ammo(OTHER, 0.0, SLOT_LIMIT, kind);
        };
    }

    public static int limit(Ammo ammo, int itemMaxStack) {
        return Math.max(1, Math.min(ammo.limit(), itemMaxStack));
    }

    public static double grams(Ammo ammo, int count, double fallbackGramsEach) {
        double each = ammo.weighed() ? ammo.grams() : Math.max(0.0, fallbackGramsEach);
        return each * Math.max(0, count);
    }

    public static List<String> ids() {
        return List.copyOf(BY_ID.keySet());
    }

    public static List<String> codes() {
        return BY_ID.values().stream().map(Ammo::code).distinct().toList();
    }
}
