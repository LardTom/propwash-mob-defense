package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.client.core.AmmoCodes;
import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.mobdefense.dispenser.core.SlotCycle;

import java.util.ArrayList;
import java.util.List;

public record OsdState(boolean dispenser, List<String> codes, List<Integer> counts, int selected, boolean coolingDown, boolean booster,
                       boolean rocket, long burnEndsAt, boolean spearMount, boolean spear, int spearMax, int spearDamage) {
    public static final OsdState NONE = new OsdState(false, List.of(), List.of(), 0, false, false, false, 0L, false, false, 0, 0);

    public OsdState {
        codes = pad(codes, AmmoCodes.EMPTY);
        counts = pad(counts, 0);
    }

    private static <T> List<T> pad(List<T> values, T empty) {
        List<T> out = new ArrayList<>(SlotCycle.SLOTS);
        for (int i = 0; i < SlotCycle.SLOTS; i++) {
            T value = values != null && i < values.size() ? values.get(i) : null;
            out.add(value == null ? empty : value);
        }
        return List.copyOf(out);
    }

    public int firing() {
        boolean[] filled = new boolean[SlotCycle.SLOTS];
        for (int i = 0; i < SlotCycle.SLOTS; i++) {
            filled[i] = this.counts.get(i) > 0;
        }
        return SlotCycle.firing(this.selected, filled);
    }

    public String dispenserText() {
        int slot = this.firing();
        return slot < 0 ? OsdLines.DISPENSER_EMPTY : OsdLines.dispenser(slot, this.codes.get(slot), this.counts.get(slot), this.coolingDown);
    }

    public String slotsText() {
        return OsdLines.slots(this.codes, Math.floorMod(this.selected, SlotCycle.SLOTS));
    }

    public boolean burning(double now) {
        return this.burnEndsAt > now;
    }

    public String boosterText(double now) {
        if (this.burning(now)) {
            return OsdLines.boosterBurning((this.burnEndsAt - now) / 20.0);
        }
        return OsdLines.booster(this.rocket);
    }

    public String spearText() {
        return this.spear ? OsdLines.spear(this.spearMax, this.spearDamage) : OsdLines.SPEAR_EMPTY;
    }
}
