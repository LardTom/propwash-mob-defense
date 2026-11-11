package de.lardtom.mobdefense.dispenser;

import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.DispenserBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmmoTableTest {
    record Design(String id, String code, double grams, int limit, AmmoTable.Kind kind) {
    }

    static final List<Design> DESIGN = List.of(
            new Design("minecraft:arrow", "ARW", 20.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:spectral_arrow", "SPC", 20.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:tipped_arrow", "TIP", 20.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:snowball", "SNW", 60.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:egg", "EGG", 60.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:splash_potion", "SPL", 350.0, 1, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:lingering_potion", "LNG", 350.0, 1, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:experience_bottle", "XPB", 300.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:wind_charge", "WND", 50.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:fire_charge", "FIR", 80.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:firework_rocket", "RKT", 60.0, 16, AmmoTable.Kind.PROJECTILE),
            new Design("minecraft:tnt", "TNT", 600.0, 4, AmmoTable.Kind.TNT),
            new Design("minecraft:bone_meal", "BON", 15.0, 16, AmmoTable.Kind.BLOCK_USE),
            new Design("minecraft:bucket", "BKT", 250.0, 1, AmmoTable.Kind.EMPTY_BUCKET),
            new Design("minecraft:water_bucket", "BKT", 1250.0, 1, AmmoTable.Kind.FLUID_BUCKET),
            new Design("minecraft:lava_bucket", "BKT", 1250.0, 1, AmmoTable.Kind.FLUID_BUCKET));

    @BeforeAll
    static void bootstrap() {
        Vanilla.boot();
    }

    private static ItemStack stack(String id) {
        Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow(() -> new AssertionError(id + " fehlt in 26.3"));
        return new ItemStack(item);
    }

    static boolean dispensable(Item item) {
        ItemStack stack = new ItemStack(item);
        return DispenserBlock.DISPENSER_REGISTRY.containsKey(item) || item instanceof ProjectileItem || item instanceof SpawnEggItem
                || stack.has(DataComponents.EQUIPPABLE);
    }

    @Test
    void designTableIsExact() {
        for (Design design : DESIGN) {
            AmmoTable.Ammo ammo = DispenserAmmo.ammo(stack(design.id()));
            assertEquals(design.code(), ammo.code(), design.id());
            assertEquals(design.grams(), ammo.grams(), 1.0E-9, design.id());
            assertEquals(design.kind(), ammo.kind(), design.id());
            assertEquals(design.limit(), DispenserAmmo.limit(stack(design.id())), design.id() + ": Stapelgrenze");
        }
        assertEquals(4 * 600.0, DispenserAmmo.grams(stack("minecraft:tnt").copyWithCount(4)), 1.0E-9, "vier TNT = 2,4 kg");
        assertEquals(4 * 350.0, DispenserAmmo.grams(List.of(stack("minecraft:splash_potion"), stack("minecraft:splash_potion"),
                stack("minecraft:lingering_potion"), stack("minecraft:splash_potion"))), 1.0E-9, "vier Tränke = 1,4 kg");
    }

    @Test
    void everyTableIdExistsInMinecraft() {
        for (String id : AmmoTable.ids()) {
            assertTrue(BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).isPresent(), id + " gibt es in 26.3 nicht");
        }
    }

    @Test
    void everyVanillaItemWithDispenserBehaviorHasCodeGramsAndLimit() {
        Map<String, String> seen = new TreeMap<>();
        List<String> problems = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!"minecraft".equals(id.getNamespace()) || !dispensable(item)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            AmmoTable.Ammo ammo = DispenserAmmo.ammo(stack);
            int limit = DispenserAmmo.limit(stack);
            if (!ammo.code().matches("[\\x20-\\x7E]{1,3}")) {
                problems.add(id + ": Kürzel " + ammo.code());
            }
            boolean ammunition = item instanceof ProjectileItem && DispenserBlock.DISPENSER_REGISTRY.containsKey(item)
                    || ammo.kind() == AmmoTable.Kind.TNT || ammo.kind() == AmmoTable.Kind.FLUID_BUCKET || ammo.kind() == AmmoTable.Kind.EMPTY_BUCKET;
            if (ammunition && !ammo.weighed()) {
                problems.add(id + ": Munition ohne eigenes Gewicht in AmmoTable");
            }
            if (ammo.weighed() != (AmmoTable.find(id.toString()).filter(AmmoTable.Ammo::weighed).isPresent())) {
                problems.add(id + ": Gewicht nicht aus der Tabelle");
            }
            if (limit < 1 || limit > AmmoTable.SLOT_LIMIT || limit > stack.getMaxStackSize()) {
                problems.add(id + ": Stapelgrenze " + limit);
            }
            seen.put(id.getPath(), ammo.code() + "/" + ammo.kind());
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(seen.size() > 100, "zu wenige Dispenser-Items gefunden: " + seen.size());
    }

    @Test
    void kindsDecideWhatTheRulesLock() {
        assertEquals(AmmoTable.Kind.FLUID_BUCKET, DispenserAmmo.kind(stack("minecraft:powder_snow_bucket")));
        assertEquals(AmmoTable.Kind.FLUID_BUCKET, DispenserAmmo.kind(stack("minecraft:axolotl_bucket")));
        assertEquals(AmmoTable.Kind.FLUID_BUCKET, DispenserAmmo.kind(stack("minecraft:sulfur_cube_bucket")));
        assertEquals(AmmoTable.Kind.ITEM, DispenserAmmo.kind(stack("minecraft:milk_bucket")));
        assertEquals(AmmoTable.Kind.BLOCK_USE, DispenserAmmo.kind(stack("minecraft:flint_and_steel")));
        assertEquals(AmmoTable.Kind.BLOCK_USE, DispenserAmmo.kind(stack("minecraft:carved_pumpkin")));
        assertEquals(AmmoTable.Kind.BLOCK_USE, DispenserAmmo.kind(stack("minecraft:red_shulker_box")));
        assertEquals(AmmoTable.Kind.BLOCK_USE, DispenserAmmo.kind(stack("minecraft:wither_skeleton_skull")));
        assertEquals(AmmoTable.Kind.ENTITY_USE, DispenserAmmo.kind(stack("minecraft:oak_boat")));
        assertEquals(AmmoTable.Kind.ENTITY_USE, DispenserAmmo.kind(stack("minecraft:minecart")));
        assertEquals(AmmoTable.Kind.ENTITY_USE, DispenserAmmo.kind(stack("minecraft:iron_chestplate")));
        assertEquals(AmmoTable.Kind.ENTITY_USE, DispenserAmmo.kind(stack("minecraft:zombie_spawn_egg")));
        assertEquals(AmmoTable.Kind.ENTITY_USE, DispenserAmmo.kind(stack("minecraft:armor_stand")));
        assertEquals(AmmoTable.Kind.ITEM, DispenserAmmo.kind(stack("minecraft:cobblestone")));
        assertEquals(AmmoTable.Kind.PROJECTILE, DispenserAmmo.kind(stack("minecraft:trident")));
        assertEquals("ITM", DispenserAmmo.code(stack("minecraft:cobblestone")));
    }

    @Test
    void slotLimits() {
        assertEquals(16, DispenserAmmo.limit(new ItemStack(Items.ARROW)));
        assertEquals(16, DispenserAmmo.limit(new ItemStack(Items.SNOWBALL)), "Schneeball stapelt in Vanilla nur bis 16");
        assertEquals(4, DispenserAmmo.limit(new ItemStack(Items.TNT)));
        assertEquals(1, DispenserAmmo.limit(new ItemStack(Items.SPLASH_POTION)));
        assertEquals(1, DispenserAmmo.limit(new ItemStack(Items.BUCKET)));
        assertEquals(1, DispenserAmmo.limit(new ItemStack(Items.IRON_SWORD)), "nicht stapelbar bleibt 1");
        assertEquals(16, DispenserAmmo.limit(new ItemStack(Items.ENDER_PEARL)));
    }

    @Test
    void codesAreShortAscii() {
        for (String code : AmmoTable.codes()) {
            assertTrue(code.matches("[A-Z]{3}"), code);
        }
        assertEquals(List.of("ARW", "SPC", "TIP", "SNW", "EGG", "SPL", "LNG", "XPB", "WND", "FIR", "RKT", "TNT", "BON", "BKT", "ITM"),
                AmmoTable.codes());
    }
}
