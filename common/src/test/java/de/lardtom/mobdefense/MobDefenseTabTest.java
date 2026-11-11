package de.lardtom.mobdefense;

import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.TabSection;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobDefenseTabTest {
    @Test
    void accessoriesComeFirst() {
        assertEquals(List.of(TabSection.ACCESSORIES), MobDefenseTab.ORDER);
        List<TabSection> complete = TabSection.complete(MobDefenseTab.ORDER);
        assertEquals(TabSection.ACCESSORIES, complete.get(0));
        assertEquals(TabSection.values().length, complete.size());
    }

    @Test
    void iconIsTheDispenserMount() {
        assertEquals(MobDefense.id("dispenser_mount"), MobDefenseTab.ICON);
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources"));
        assertTrue(Files.isRegularFile(resources.resolve("data/mobdefense/propwash_parts/accessory/dispenser_mount.json")));
        assertTrue(Files.isRegularFile(resources.resolve("assets/mobdefense/items/dispenser_mount.json")));
    }

    @Test
    void nameKeyIsTheOneCreativeTabsUse() {
        assertEquals("itemGroup.mobdefense", MobDefenseTab.NAME_KEY);
        assertTrue(ExtensionTab.TITLE_KEY.startsWith("itemGroup.propwash"));
    }
}
