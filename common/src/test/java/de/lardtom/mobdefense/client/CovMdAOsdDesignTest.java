package de.lardtom.mobdefense.client;

import de.lardtom.mobdefense.client.core.AmmoCodes;
import de.lardtom.mobdefense.client.core.OsdLines;
import de.lardtom.propwash.api.client.osd.OsdElementSpec;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdAOsdDesignTest {
    record Row(String id, boolean enabled, int column, int row, String example) {
    }

    private static final Pattern TABLE_ROW = Pattern.compile("^\\| `(mobdefense:[a-z_]+)` \\| (an|aus)(?:, Spalte (\\d+), Zeile (\\d+))? \\| `([^`]+)`");

    private static Path design() {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        return resources.getParent().getParent().getParent().getParent().resolve("docs/design.md");
    }

    static Map<String, Row> designTable() throws IOException {
        List<String> lines = Files.readAllLines(design(), StandardCharsets.UTF_8);
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("### 2.5 OSD und HUD")) {
                start = i;
                break;
            }
        }
        assertTrue(start >= 0, "design.md §2.5 fehlt");
        Map<String, Row> rows = new LinkedHashMap<>();
        for (int i = start + 1; i < lines.size() && !lines.get(i).startsWith("### "); i++) {
            Matcher m = TABLE_ROW.matcher(lines.get(i));
            if (m.find()) {
                boolean on = m.group(2).equals("an");
                rows.put(m.group(1), new Row(m.group(1), on, on ? Integer.parseInt(m.group(3)) : -1, on ? Integer.parseInt(m.group(4)) : -1, m.group(5)));
            }
        }
        return rows;
    }

    @Test
    void fourElementsMatchTheDesignTable25() throws IOException {
        Map<String, Row> design = designTable();
        assertEquals(List.of("mobdefense:dispenser", "mobdefense:dispenser_slots", "mobdefense:booster", "mobdefense:spear"), List.copyOf(design.keySet()),
                "Tabelle in design.md §2.5");
        assertEquals(design.size(), MobDefenseOsd.specs().size());
        for (OsdElementSpec spec : MobDefenseOsd.specs()) {
            Row row = design.get(spec.id().toString());
            assertTrue(row != null, spec.id() + " steht nicht in design.md §2.5");
            assertEquals(row.enabled(), spec.enabledByDefault(), spec.id() + ": Standard an/aus");
            if (row.enabled()) {
                assertEquals(row.column(), spec.column(), spec.id() + ": Spalte");
                assertEquals(row.row(), spec.row(), spec.id() + ": Zeile");
            }
        }
    }

    @Test
    void dispenserOnAt1And12BoosterOnAt1And13SlotsAndSpearOff() {
        Map<Identifier, OsdElementSpec> specs = new LinkedHashMap<>();
        MobDefenseOsd.specs().forEach(spec -> specs.put(spec.id(), spec));
        OsdElementSpec dispenser = specs.get(MobDefenseOsd.DISPENSER);
        OsdElementSpec booster = specs.get(MobDefenseOsd.BOOSTER);
        assertTrue(dispenser.enabledByDefault() && dispenser.column() == 1 && dispenser.row() == 12, "Dispenser an, Spalte 1, Zeile 12");
        assertTrue(booster.enabledByDefault() && booster.column() == 1 && booster.row() == 13, "Booster an, Spalte 1, Zeile 13");
        assertTrue(!specs.get(MobDefenseOsd.DISPENSER_SLOTS).enabledByDefault(), "Dispenser-Slots aus");
        assertTrue(!specs.get(MobDefenseOsd.SPEAR).enabledByDefault(), "Speer aus");
    }

    @Test
    void designExampleOfTheSlotOverviewIsBuiltFromTheState() throws IOException {
        Row row = designTable().get("mobdefense:dispenser_slots");
        OsdState state = new OsdState(true, List.of(AmmoCodes.code("minecraft:arrow"), AmmoCodes.code("minecraft:splash_potion"),
                AmmoCodes.code("minecraft:wind_charge")), List.of(16, 1, 16), 0, false, false, false, 0L, false, false, 0, 0);
        assertEquals(row.example(), state.slotsText(), "Beispiel aus design.md §2.5");
        assertEquals("[ARW]SPL WND ---", state.slotsText());
    }

    @Test
    void theSelectedSlotStandsInBracketsWhereverItIs() {
        List<String> codes = List.of(AmmoCodes.code("minecraft:arrow"), AmmoCodes.code("minecraft:splash_potion"), AmmoCodes.code("minecraft:wind_charge"),
                AmmoCodes.code("minecraft:tnt"));
        List<String> expected = List.of("[ARW]SPL WND TNT", "ARW[SPL]WND TNT", "ARW SPL[WND]TNT", "ARW SPL WND[TNT]");
        for (int selected = 0; selected < expected.size(); selected++) {
            OsdState state = new OsdState(true, codes, List.of(16, 1, 16, 4), selected, false, false, false, 0L, false, false, 0, 0);
            String text = state.slotsText();
            assertEquals(expected.get(selected), text, "gewählter Slot " + (selected + 1));
            assertEquals(1, text.chars().filter(c -> c == '[').count());
            assertTrue(text.length() <= OsdLines.SLOTS_CHARS && OsdLines.printableAscii(text), text);
        }
        OsdState emptySelected = new OsdState(true, List.of("ARW", AmmoCodes.EMPTY, "WND"), List.of(16, 0, 16), 1, false, false, false, 0L, false,
                false, 0, 0);
        assertEquals("ARW[---]WND ---", emptySelected.slotsText(), "leerer gewählter Slot bleibt in Klammern");
    }
}
