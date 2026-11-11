package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.propwash.api.client.PropwashClientApi;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdContent;
import de.lardtom.propwash.api.client.osd.OsdContext;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdText;
import de.lardtom.propwash.api.client.osd.OsdTone;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MobDefenseOsd {
    public static final Identifier DISPENSER = MobDefense.id("dispenser");
    public static final Identifier DISPENSER_SLOTS = MobDefense.id("dispenser_slots");
    public static final Identifier BOOSTER = MobDefense.id("booster");
    public static final Identifier SPEAR = MobDefense.id("spear");
    public static final int SPEAR_LOW_PERCENT = 20;
    private static final Map<Identifier, Integer> PREVIEWS = new LinkedHashMap<>();
    private static final List<OsdElementSpec> SPECS = List.of(
            spec(DISPENSER, 1, 12, true, OsdLines.DISPENSER_CHARS, OsdLines.DISPENSER_PREVIEW, (state, now) -> dispenser(state)),
            spec(DISPENSER_SLOTS, 1, 11, false, OsdLines.SLOTS_CHARS, OsdLines.SLOTS_PREVIEW, (state, now) -> slots(state)),
            spec(BOOSTER, 1, 13, true, OsdLines.BOOSTER_CHARS, OsdLines.BOOSTER_PREVIEW, MobDefenseOsd::booster),
            spec(SPEAR, 1, 14, false, OsdLines.SPEAR_CHARS, OsdLines.SPEAR_PREVIEW, (state, now) -> spear(state)));
    private static final List<Identifier> REGISTERED = new ArrayList<>();
    private static boolean registered;

    @FunctionalInterface
    public interface Line {
        @Nullable OsdText text(OsdState state, double now);
    }

    private MobDefenseOsd() {
    }

    public static List<OsdElementSpec> specs() {
        return SPECS;
    }

    public static synchronized List<Identifier> registered() {
        return List.copyOf(REGISTERED);
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        for (OsdElementSpec spec : SPECS) {
            try {
                PropwashClientApi.registerOsdElement(spec);
                REGISTERED.add(spec.id());
            } catch (IllegalArgumentException e) {
                MobDefense.LOG.warn("{}: OSD-Element {} nicht angemeldet: {}", MobDefense.MOD_NAME, spec.id(), e.getMessage());
            }
        }
        for (Notice notice : Notice.values()) {
            try {
                PropwashClientApi.registerOsdWarning(notice.id(), context -> warningText(notice, context));
                REGISTERED.add(notice.id());
            } catch (IllegalArgumentException e) {
                MobDefense.LOG.warn("{}: OSD-Warnung {} nicht angemeldet: {}", MobDefense.MOD_NAME, notice.id(), e.getMessage());
            }
        }
    }

    public static synchronized int previews(Identifier id) {
        return PREVIEWS.getOrDefault(id, 0);
    }

    public static synchronized void resetPreviews() {
        PREVIEWS.clear();
    }

    private static synchronized void previewed(Identifier id) {
        PREVIEWS.merge(id, 1, Integer::sum);
    }

    private static OsdElementSpec spec(Identifier id, int column, int row, boolean enabled, int maxChars, String preview, Line text) {
        return OsdElementSpec.builder(id)
                .position(column, row)
                .align(OsdAlign.LEFT)
                .group(OsdGroup.ADDON)
                .enabledByDefault(enabled)
                .maxChars(maxChars)
                .content(content(id, preview, text))
                .build();
    }

    private static OsdContent content(Identifier id, String preview, Line text) {
        return context -> {
            if (context.preview()) {
                previewed(id);
                return OsdText.of(preview);
            }
            Entity drone = context.drone().orElse(null);
            return drone == null ? null : text.text(OsdReader.read(drone, context.gameTime()), context.gameTime() + context.partialTick());
        };
    }

    public static @Nullable OsdText dispenser(OsdState state) {
        if (!state.dispenser()) {
            return null;
        }
        String text = state.dispenserText();
        return OsdText.of(text, OsdLines.DISPENSER_EMPTY.equals(text) ? OsdTone.CAUTION : OsdTone.NORMAL);
    }

    public static @Nullable OsdText slots(OsdState state) {
        return state.dispenser() ? OsdText.of(state.slotsText(), OsdTone.NORMAL) : null;
    }

    public static @Nullable OsdText booster(OsdState state, double now) {
        if (!state.booster()) {
            return null;
        }
        if (state.burning(now)) {
            return OsdText.of(state.boosterText(now), OsdTone.ACCENT);
        }
        return OsdText.of(state.boosterText(now), state.rocket() ? OsdTone.NORMAL : OsdTone.CAUTION);
    }

    public static @Nullable OsdText spear(OsdState state) {
        if (!state.spearMount()) {
            return null;
        }
        boolean low = !state.spear() || state.spearMax() > 0 && (state.spearMax() - state.spearDamage()) * 100 < SPEAR_LOW_PERCENT * state.spearMax();
        return OsdText.of(state.spearText(), low ? OsdTone.CAUTION : OsdTone.NORMAL);
    }

    public static OsdTone tone(Notice notice) {
        return switch (notice) {
            case THREAT -> OsdTone.WARNING;
            case HIT -> OsdTone.ACCENT;
            default -> OsdTone.CAUTION;
        };
    }

    public static @Nullable OsdText warningText(Notice notice, OsdContext context) {
        Entity drone = context.drone().orElse(null);
        if (drone == null || !OsdNotices.active(notice, drone.getId(), context.gameTime())) {
            return null;
        }
        return OsdText.of(I18n.get(notice.translationKey()), tone(notice));
    }
}
