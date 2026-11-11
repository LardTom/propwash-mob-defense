package de.lardtom.mobdefense.client.core;

import java.util.List;
import java.util.Locale;

public final class OsdLines {
    public static final int DISPENSER_CHARS = 14;
    public static final int SLOTS_CHARS = 16;
    public static final int BOOSTER_CHARS = 7;
    public static final int SPEAR_CHARS = 8;
    public static final String DISPENSER_EMPTY = "DSP --";
    public static final String BOOSTER_READY = "RKT RDY";
    public static final String BOOSTER_EMPTY = "RKT ---";
    public static final String SPEAR_EMPTY = "SPR ---";
    public static final String DISPENSER_PREVIEW = "DSP 2 ARW 12";
    public static final String SLOTS_PREVIEW = "[ARW]SPL WND ---";
    public static final String BOOSTER_PREVIEW = "RKT RDY";
    public static final String SPEAR_PREVIEW = "SPR 87%";
    private static final int MAX_COUNT = 999;
    private static final double MAX_SECONDS = 9.9;

    private OsdLines() {
    }

    public static String dispenser(int slot, String code, int count, boolean coolingDown) {
        if (slot < 0 || count <= 0 || code == null || code.isBlank() || AmmoCodes.EMPTY.equals(code)) {
            return DISPENSER_EMPTY;
        }
        return "DSP " + (slot + 1) + " " + code + " " + Math.min(count, MAX_COUNT) + (coolingDown ? "*" : "");
    }

    public static String slots(List<String> codes, int selected) {
        StringBuilder out = new StringBuilder();
        boolean bracketBefore = false;
        for (int i = 0; i < codes.size(); i++) {
            String code = codes.get(i) == null || codes.get(i).isBlank() ? AmmoCodes.EMPTY : codes.get(i);
            boolean chosen = i == selected;
            if (i > 0 && !chosen && !bracketBefore) {
                out.append(' ');
            }
            out.append(chosen ? "[" + code + "]" : code);
            bracketBefore = chosen;
        }
        return out.toString();
    }

    public static String boosterBurning(double secondsLeft) {
        double seconds = Math.max(0.0, Math.min(MAX_SECONDS, secondsLeft));
        double tenths = Math.max(0.0, Math.ceil(seconds * 10.0 - 1.0E-6));
        return String.format(Locale.ROOT, "RKT %.1f", tenths / 10.0);
    }

    public static String booster(boolean loaded) {
        return loaded ? BOOSTER_READY : BOOSTER_EMPTY;
    }

    public static String spear(int maxDamage, int damage) {
        if (maxDamage <= 0) {
            return "SPR 100%";
        }
        int left = Math.max(0, maxDamage - Math.max(0, damage));
        int percent = (int) Math.round(100.0 * left / maxDamage);
        if (percent == 0 && left > 0) {
            percent = 1;
        }
        return "SPR " + percent + "%";
    }

    public static boolean printableAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                return false;
            }
        }
        return true;
    }
}
