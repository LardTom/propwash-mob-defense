package de.lardtom.mobdefense.mob;

import de.lardtom.propwash.api.drone.DroneView;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnarmedDroneTest {
    private static final Optional<UUID> PILOT = Optional.of(UUID.fromString("00000000-0000-0000-0000-0000000f1b41"));

    @AfterEach
    void defaultFilter() {
        DroneReactions.setActiveFilter(null);
    }

    @Test
    void connectedButUnarmedDroneOnTheGroundIsLeftAlone() {
        assertFalse(DroneReactions.active(drone(PILOT, true, false, false, true)), "Pilot verbunden, nicht scharf, am Boden");
    }

    @Test
    void disarmedDroneInTheAirIsLeftAlone() {
        assertFalse(DroneReactions.active(drone(PILOT, true, false, false, false)), "Pilot verbunden, entschärft in der Luft");
    }

    @Test
    void parkedDroneIsLeftAlone() {
        assertFalse(DroneReactions.active(drone(Optional.empty(), false, false, false, true)), "abgestellt");
    }

    @Test
    void armedDroneDrawsReactions() {
        assertTrue(DroneReactions.active(drone(PILOT, true, true, false, false)), "scharf in der Luft");
        assertTrue(DroneReactions.active(drone(PILOT, true, true, false, true)), "scharf am Boden");
    }

    @Test
    void autopilotDrawsReactions() {
        assertTrue(DroneReactions.active(drone(Optional.empty(), true, false, true, false)), "autonom ohne Pilot");
        assertTrue(DroneReactions.active(drone(PILOT, true, true, true, false)), "Heimflug");
    }

    @Test
    void filterForSelfTestsStillOverridesTheDefault() {
        DroneReactions.setActiveFilter(view -> true);
        assertTrue(DroneReactions.active(drone(Optional.empty(), false, false, false, true)));
        DroneReactions.setActiveFilter(null);
        assertFalse(DroneReactions.active(drone(Optional.empty(), false, false, false, true)));
    }

    private static DroneState drone(Optional<UUID> pilot, boolean flying, boolean armed, boolean autopilot, boolean landed) {
        DroneView view = new DroneView(null, pilot, flying, armed, autopilot, Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 0.0, 1.0), new Vec3(0.0, 1.0, 0.0),
                0.6, Map.of());
        return new DroneState(null, view, Vec3.ZERO, 0.0, landed ? 0.0 : 5.0, landed);
    }
}
