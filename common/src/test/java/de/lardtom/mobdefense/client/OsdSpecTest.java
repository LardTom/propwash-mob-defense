package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.propwash.api.client.osd.OsdAlign;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import de.lardtom.propwash.api.client.osd.OsdGroup;
import de.lardtom.propwash.api.client.osd.OsdTone;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsdSpecTest {
    private static Map<Identifier, OsdElementSpec> specs() {
        Map<Identifier, OsdElementSpec> out = new LinkedHashMap<>();
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            out.put(spec.id(), spec);
        }
        return out;
    }

    @Test
    void fourElementsAsInTheDesignTable() {
        Map<Identifier, OsdElementSpec> specs = specs();
        assertEquals(List.of(MobDefenseOsd.DISPENSER, MobDefenseOsd.DISPENSER_SLOTS, MobDefenseOsd.BOOSTER, MobDefenseOsd.SPEAR), List.copyOf(specs.keySet()));
        OsdElementSpec dispenser = specs.get(MobDefenseOsd.DISPENSER);
        assertTrue(dispenser.enabledByDefault());
        assertEquals(1, dispenser.column());
        assertEquals(12, dispenser.row());
        OsdElementSpec booster = specs.get(MobDefenseOsd.BOOSTER);
        assertTrue(booster.enabledByDefault());
        assertEquals(1, booster.column());
        assertEquals(13, booster.row());
        assertTrue(!specs.get(MobDefenseOsd.DISPENSER_SLOTS).enabledByDefault());
        assertTrue(!specs.get(MobDefenseOsd.SPEAR).enabledByDefault());
    }

    @Test
    void allInTheAddonGroupInsideTheGridWithOwnNames() {
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            assertEquals(OsdGroup.ADDON, spec.group(), spec.id().toString());
            assertEquals(OsdAlign.LEFT, spec.align(), spec.id().toString());
            assertEquals("mobdefense", spec.id().getNamespace());
            assertEquals("mobdefense.osd." + spec.id().getPath(), spec.nameKey());
            assertTrue(spec.column() < OsdElementSpec.COLUMNS && spec.row() < OsdElementSpec.ROWS);
            assertTrue(spec.column() + spec.maxChars() <= OsdElementSpec.COLUMNS, spec.id() + " ragt aus dem Raster");
        }
        Set<String> rows = MobDefenseOsd.specs().stream().map(spec -> spec.column() + "/" + spec.row()).collect(Collectors.toSet());
        assertEquals(4, rows.size(), "zwei Elemente auf demselben Platz");
    }

    @Test
    void previewsAreTheDesignExamples() {
        assertEquals("DSP 2 ARW 12", OsdLines.DISPENSER_PREVIEW);
        assertEquals("[ARW]SPL WND ---", OsdLines.SLOTS_PREVIEW);
        assertEquals("RKT RDY", OsdLines.BOOSTER_PREVIEW);
        assertEquals("SPR 87%", OsdLines.SPEAR_PREVIEW);
        assertTrue(OsdLines.DISPENSER_PREVIEW.length() <= OsdLines.DISPENSER_CHARS);
        assertTrue(OsdLines.SLOTS_PREVIEW.length() <= OsdLines.SLOTS_CHARS);
    }

    @Test
    void warningsHaveOwnIdsAndTones() {
        assertEquals(7, Notice.values().length);
        for (Notice notice : Notice.values()) {
            assertEquals("mobdefense", notice.id().getNamespace());
            assertTrue(notice.id().getPath().startsWith("warning/"));
            assertEquals("mobdefense.osd.warning." + notice.path(), notice.translationKey());
        }
        assertEquals(OsdTone.WARNING, MobDefenseOsd.tone(Notice.THREAT));
        assertEquals(OsdTone.CAUTION, MobDefenseOsd.tone(Notice.NO_AMMO));
        assertEquals(OsdTone.ACCENT, MobDefenseOsd.tone(Notice.valueOf("HIT")));
    }
}
