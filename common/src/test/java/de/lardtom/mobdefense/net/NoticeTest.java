package de.lardtom.mobdefense.net;

import de.lardtom.propwash.api.accessory.DispenseResult;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NoticeTest {
    @Test
    void dispenseResultsMapToWarnings() {
        assertEquals(Optional.of(Notice.NO_AMMO), Notice.of(DispenseResult.Status.EMPTY));
        assertEquals(Optional.of(Notice.BARREL_BLOCKED), Notice.of(DispenseResult.Status.BLOCKED));
        assertEquals(Optional.of(Notice.NO_PERMISSION), Notice.of(DispenseResult.Status.DENIED));
        assertEquals(Optional.empty(), Notice.of(DispenseResult.Status.FIRED));
        assertEquals(Optional.empty(), Notice.of(DispenseResult.Status.REFUSED));
    }

    @Test
    void indexRoundTripAndLimits() {
        for (Notice notice : Notice.values()) {
            assertEquals(notice, Notice.byIndex(notice.ordinal()));
        }
        assertEquals(Notice.NO_AMMO, Notice.byIndex(99));
        assertEquals(Notice.MAX_TICKS, new NoticePayload(1, Notice.THREAT, 10_000).ticks());
        assertEquals(1, new NoticePayload(1, Notice.THREAT, -5).ticks());
        assertEquals(40, Notice.DEFAULT_TICKS);
        assertEquals(4, Notices.PER_SECOND);
        assertEquals(20, Notices.WINDOW_TICKS);
    }
}
