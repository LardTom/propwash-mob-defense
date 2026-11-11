package de.lardtom.mobdefense.spear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.client.MobDefenseOsd;
import de.lardtom.mobdefense.net.Notice;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.spear.core.SpearDamage;
import de.lardtom.propwash.api.client.osd.OsdTone;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.KineticWeapon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnmSpearTest {
    static final double BRISK_SPEED = 10.0;
    static final float VILLAGER_HEALTH = 20.0F;
    private static boolean ready;

    @BeforeAll
    static synchronized void boot() {
        if (ready) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.Provider lookup = VanillaRegistries.createWorldLookup();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(DataComponentInitializers.PendingComponents::apply);
        ready = true;
    }

    private static KineticWeapon netherite() {
        KineticWeapon kinetic = new ItemStack(Items.NETHERITE_SPEAR).get(DataComponents.KINETIC_WEAPON);
        assertNotNull(kinetic, "Netheritspeer ohne Kinetik-Komponente");
        return kinetic;
    }

    @Test
    void netheriteSpearAtTenMetresPerSecondDealsVanillaDamageByDefault() {
        KineticWeapon kinetic = netherite();
        SpearDamage.Outcome outcome = SpearDamage.evaluate(Spears.stats(kinetic), BRISK_SPEED, BRISK_SPEED,
                RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue());
        assertTrue(outcome.damage());
        assertEquals(SpearDamage.vanilla(BRISK_SPEED, kinetic.damageMultiplier()), outcome.amount(), 0.0F);
        assertEquals(13.0F, outcome.amount(), 0.0F);
    }

    @Test
    void villagerFallsAfterTwoBriskContactsWithTheNetheriteSpear() {
        KineticWeapon kinetic = netherite();
        float perContact = SpearDamage.evaluate(Spears.stats(kinetic), BRISK_SPEED, BRISK_SPEED,
                RuleDefaults.SPEAR_DAMAGE_PERCENT.defaultValue()).amount();
        int contacts = (int) Math.ceil(VILLAGER_HEALTH / perContact);
        assertEquals(2, contacts, perContact + " Schaden je Kontakt");
    }

    @Test
    void hitNoticeIsAppendedAndShownAsAccent() {
        Notice hit = Notice.valueOf("HIT");
        assertEquals(Notice.values().length - 1, hit.ordinal());
        assertEquals(5, Notice.THREAT.ordinal());
        assertEquals("mobdefense:warning/hit", hit.id().toString());
        assertEquals("mobdefense.osd.warning.hit", hit.translationKey());
        assertEquals(OsdTone.ACCENT, MobDefenseOsd.tone(hit));
    }

    @Test
    void hitTextIsShortAsciiInBothLanguages() throws IOException {
        assertEquals("HIT", text("en_us", "mobdefense.osd.warning.hit"));
        assertEquals("TREFFER", text("de_de", "mobdefense.osd.warning.hit"));
    }

    private static String text(String language, String key) throws IOException {
        Path resources = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize();
        Path file = resources.getParent().getParent().getParent().getParent().resolve("common/src/lang/ui").resolve(language + ".json");
        JsonObject object = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        String value = object.has(key) ? object.get(key).getAsString() : "";
        assertTrue(value.chars().allMatch(c -> c >= 0x20 && c < 0x7F), language + ": " + value);
        return value;
    }
}
