package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.FireCadence;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DispenserCadences {
    private static final Map<UUID, FireCadence.Setting> SETTINGS = new HashMap<>();

    private DispenserCadences() {
    }

    public static void accept(ServerPlayer player, CadencePayload payload) {
        set(player.getUUID(), payload.setting());
    }

    public static synchronized void set(UUID player, FireCadence.Setting setting) {
        if (FireCadence.Setting.DEFAULT.equals(setting)) {
            SETTINGS.remove(player);
        } else {
            SETTINGS.put(player, setting);
        }
    }

    public static synchronized FireCadence.Setting of(UUID player) {
        return SETTINGS.getOrDefault(player, FireCadence.Setting.DEFAULT);
    }

    public static FireCadence.Setting of(Optional<ServerPlayer> pilot) {
        return pilot.map(player -> of(player.getUUID())).orElse(FireCadence.Setting.DEFAULT);
    }

    public static synchronized void clear() {
        SETTINGS.clear();
    }
}
