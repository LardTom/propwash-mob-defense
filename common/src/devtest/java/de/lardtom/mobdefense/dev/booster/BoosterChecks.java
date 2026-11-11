package de.lardtom.mobdefense.dev.booster;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.booster.BoostState;
import de.lardtom.mobdefense.booster.BoosterBehavior;
import de.lardtom.mobdefense.booster.Boosters;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.accessory.AccessoryBehavior;
import de.lardtom.propwash.api.drone.DroneDataKey;
import de.lardtom.propwash.api.event.PropwashEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BoosterChecks {
    private BoosterChecks() {
    }

    public static String registered() {
        List<String> problems = new ArrayList<>();
        Optional<AccessoryBehavior> behavior = PropwashApi.accessories().behavior(MobDefense.ROCKET_BOOSTER);
        if (behavior.isEmpty() || behavior.get() != BoosterBehavior.INSTANCE) {
            problems.add("kein Verhalten für " + MobDefense.ROCKET_BOOSTER);
        } else if (!behavior.get().actionNameKey(BoosterBehavior.IGNITE).equals(Optional.of(BoosterBehavior.IGNITE_KEY))) {
            problems.add("Aktion 3 ohne Namen");
        }
        Optional<DroneDataKey<?>> key = PropwashApi.droneData().find(MobDefense.id(BoostState.PATH));
        if (key.isEmpty() || key.get().sync() != DroneDataKey.Sync.TRACKING || !BoostState.registered()) {
            problems.add("Drohnen-Daten mobdefense:booster fehlen oder sind nicht TRACKING");
        }
        List<String> accepted = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (Boosters.accepts(new ItemStack(item))) {
                accepted.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        if (!accepted.equals(List.of("minecraft:firework_rocket")) || Boosters.accepts(new ItemStack(Items.ARROW))) {
            problems.add("Tag booster_accepts nimmt " + accepted);
        }
        if (!PropwashEvents.PILOT_CHANGED.hasListeners()) {
            problems.add("kein Listener auf PILOT_CHANGED");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        return "Verhalten, Aktion 3 „" + BoosterBehavior.IGNITE_KEY + "“, Drohnen-Daten mobdefense:booster (TRACKING), nimmt nur " + accepted
                + ", PILOT_CHANGED angemeldet";
    }
}
