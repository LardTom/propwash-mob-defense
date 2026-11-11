package de.lardtom.mobdefense.net;

import de.lardtom.mobdefense.MobDefense;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoticesWorldChangeTest {
    @Test
    void newWorldWithAnEarlierTickLetsEveryWarningThrough() {
        UUID pilot = UUID.randomUUID();
        for (int i = 0; i < Notices.PER_SECOND - Notices.THREAT_RESERVE; i++) {
            assertTrue(Notices.admit(pilot, Notice.TNT_LOCKED, 812));
        }
        assertTrue(Notices.admit(pilot, Notice.THREAT, 812));
        long[] ticks = {158, 178, 198};
        for (long tick : ticks) {
            assertTrue(Notices.admit(pilot, Notice.THREAT, tick), "BEDROHUNG in Tick " + tick + " der neuen Welt");
        }
        assertTrue(Notices.admit(pilot, Notice.NO_AMMO, 288), "KEINE MUNITION in Tick 288 der neuen Welt");
        assertTrue(Notices.admit(pilot, Notice.BARREL_BLOCKED, 313), "LAUF BLOCKIERT in Tick 313 der neuen Welt");
        assertTrue(Notices.admit(pilot, Notice.HIT, 340), "TREFFER in Tick 340 der neuen Welt");
    }

    @Test
    void serverStartForgetsTheLastWorld() {
        Notices.install((player, payload) -> {
        });
        UUID pilot = UUID.randomUUID();
        for (int i = 0; i < Notices.PER_SECOND - Notices.THREAT_RESERVE; i++) {
            assertTrue(Notices.admit(pilot, Notice.NO_AMMO, 30));
        }
        assertFalse(Notices.admit(pilot, Notice.NO_AMMO, 31));
        MobDefense.serverStarted(null);
        for (int i = 0; i < Notices.PER_SECOND - Notices.THREAT_RESERVE; i++) {
            assertTrue(Notices.admit(pilot, Notice.NO_AMMO, 35), "Hinweis " + (i + 1) + " in Tick 35 der neuen Welt verworfen");
        }
        assertFalse(Notices.admit(pilot, Notice.NO_AMMO, 36));
        assertTrue(Notices.admit(pilot, Notice.THREAT, 36));
    }
}
