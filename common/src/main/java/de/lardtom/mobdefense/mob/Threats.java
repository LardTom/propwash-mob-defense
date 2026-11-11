package de.lardtom.mobdefense.mob;

import de.lardtom.mobdefense.mob.core.ThreatPulse;
import de.lardtom.mobdefense.net.Notices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

final class Threats {
    private static final Map<Entity, Long> LAST = new WeakHashMap<>();

    private Threats() {
    }

    static synchronized void publish(ServerLevel level, long now, Collection<DroneState> threatened) {
        for (DroneState drone : threatened) {
            Optional<UUID> id = drone.view().pilot();
            if (id.isEmpty()) {
                continue;
            }
            ServerPlayer pilot = level.getServer().getPlayerList().getPlayer(id.get());
            if (pilot == null) {
                continue;
            }
            Entity entity = drone.entity();
            Long last = LAST.get(entity);
            if (!ThreatPulse.due(now, last == null ? ThreatPulse.NEVER : last)) {
                continue;
            }
            boolean sent = Notices.threat(pilot, entity, ThreatPulse.SHOW_TICKS);
            LAST.put(entity, ThreatPulse.mark(now, sent));
        }
    }
}
