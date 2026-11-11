package de.lardtom.mobdefense.dispenser;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import de.lardtom.mobdefense.dispenser.core.AmmoTable;
import de.lardtom.mobdefense.dispenser.core.FireGate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CovMdBFluidBucketTagTest {
    static final List<String> DESIGN = List.of("minecraft:water_bucket", "minecraft:lava_bucket", "minecraft:powder_snow_bucket", "minecraft:axolotl_bucket",
            "minecraft:cod_bucket", "minecraft:pufferfish_bucket", "minecraft:salmon_bucket", "minecraft:tadpole_bucket", "minecraft:tropical_fish_bucket");

    @BeforeAll
    static void bootstrap() {
        Vanilla.boot();
    }

    static Set<String> tag() throws IOException {
        Path file = Path.of(System.getProperty("mobdefense.resources", "src/main/resources")).toAbsolutePath().normalize()
                .resolve("data/mobdefense/tags/item/dispenser_fluid_buckets.json");
        JsonArray values = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values");
        Set<String> out = new TreeSet<>();
        for (JsonElement value : values) {
            if (value.isJsonPrimitive()) {
                out.add(value.getAsString());
            }
        }
        return out;
    }

    static List<Item> registeredBuckets() {
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.BUCKET) {
                continue;
            }
            boolean fluid = item instanceof BucketItem bucket && bucket.getContent() != Fluids.EMPTY;
            if (fluid || item instanceof MobBucketItem || item instanceof SolidBucketItem) {
                out.add(item);
            }
        }
        return out;
    }

    static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    @Test
    void designBucketsAreInTheTag() throws IOException {
        Set<String> tag = tag();
        for (String id : DESIGN) {
            assertTrue(tag.contains(id), id + " fehlt im Tag dispenser_fluid_buckets");
        }
    }

    @Test
    void everyRegisteredBucketIsLockedWithoutTheRule() {
        List<Item> buckets = registeredBuckets();
        assertTrue(buckets.size() >= DESIGN.size(), "nur " + buckets.size() + " Eimer im Register");
        for (Item item : buckets) {
            AmmoTable.Kind kind = DispenserAmmo.kind(new ItemStack(item));
            assertEquals(AmmoTable.Kind.FLUID_BUCKET, kind, id(item));
            AmmoTable.Kind[] slots = {kind, null, null, null};
            FireGate.Shot shot = FireGate.decide(100L, 0L, 0, slots, FireGate.Rules.DEFAULTS);
            assertEquals(FireGate.Outcome.BUCKET_LOCKED, shot.outcome(), id(item));
            assertEquals(FireGate.Notice.BUCKET_LOCKED, shot.notice(), id(item));
        }
    }

    @Disabled("cov-md-b-1")
    @Test
    void everyRegisteredBucketIsInTheTag() throws IOException {
        Set<String> tag = tag();
        List<String> missing = new ArrayList<>();
        for (Item item : registeredBuckets()) {
            if (!tag.contains(id(item))) {
                missing.add(id(item));
            }
        }
        assertTrue(missing.isEmpty(), "nicht im Tag dispenser_fluid_buckets: " + missing);
    }
}
