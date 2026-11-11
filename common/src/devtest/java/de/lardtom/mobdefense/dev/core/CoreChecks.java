package de.lardtom.mobdefense.dev.core;

import de.lardtom.mobdefense.MobDefense;
import de.lardtom.mobdefense.MobDefenseTab;
import de.lardtom.mobdefense.rules.MobDefenseRules;
import de.lardtom.mobdefense.rules.core.RuleDefaults;
import de.lardtom.mobdefense.rules.core.RuleSpec;
import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.build.BuildCheck;
import de.lardtom.propwash.api.part.AccessoryDefinition;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.worldrule.WorldRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CoreChecks {
    public static final TagKey<Item> FLUID_BUCKETS = TagKey.create(Registries.ITEM, MobDefense.id("dispenser_fluid_buckets"));
    public static final TagKey<Item> SPEAR_ACCEPTS = TagKey.create(Registries.ITEM, MobDefense.id("spear_mount_accepts"));
    public static final TagKey<Item> BOOSTER_ACCEPTS = TagKey.create(Registries.ITEM, MobDefense.id("booster_accepts"));
    public static final Map<Identifier, List<String>> GRIDS = grids();

    private CoreChecks() {
    }

    private static Map<Identifier, List<String>> grids() {
        Map<Identifier, List<String>> out = new LinkedHashMap<>();
        out.put(MobDefense.DISPENSER_MOUNT, List.of("air", "propwash:circuit_board", "air",
                "iron_nugget", "dispenser", "iron_nugget",
                "air", "propwash:copper_coil", "air"));
        out.put(MobDefense.SPEAR_MOUNT, List.of("iron_nugget", "air", "iron_nugget",
                "propwash:carbon_plate", "propwash:carbon_plate", "propwash:carbon_plate",
                "air", "air", "air"));
        out.put(MobDefense.ROCKET_BOOSTER, List.of("copper_nugget", "flint_and_steel", "copper_nugget",
                "air", "propwash:carbon_plate", "air",
                "air", "air", "air"));
        return out;
    }

    public static ItemStack item(String id) {
        Identifier key = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
        Item item = BuiltInRegistries.ITEM.getOptional(key).orElseThrow(() -> new IllegalStateException("Item " + key + " fehlt"));
        return new ItemStack(item);
    }

    public static ItemStack craft(MinecraftServer server, Identifier part) {
        List<ItemStack> grid = new ArrayList<>();
        for (String id : GRIDS.get(part)) {
            grid.add(id.equals("air") ? ItemStack.EMPTY : item(id));
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, server.overworld());
        if (recipe.isEmpty()) {
            throw new IllegalStateException("kein Rezept für " + part + " in der Werkbank");
        }
        if (!recipe.get().id().identifier().equals(part)) {
            throw new IllegalStateException("Muster für " + part + " trifft " + recipe.get().id().identifier());
        }
        return recipe.get().value().assemble(input);
    }

    public static String parts(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        for (Identifier part : MobDefense.PARTS) {
            Optional<AccessoryDefinition> definition = PropwashApi.parts().get(AccessoryDefinition.class, part);
            if (definition.isEmpty()) {
                problems.add(part + " nicht als Zubehör angemeldet");
                continue;
            }
            if (definition.get().slot() != CoreBuild.SLOTS.get(part)) {
                problems.add(part + " auf " + definition.get().slot() + " statt " + CoreBuild.SLOTS.get(part));
            }
            if (Math.abs(definition.get().basics().massGrams() - CoreBuild.GRAMS.get(part)) > 1.0e-6) {
                problems.add(part + " wiegt " + definition.get().basics().massGrams() + " g");
            }
            Optional<Item> item = PropwashApi.partItems().item(part);
            if (item.isEmpty() || !part.equals(BuiltInRegistries.ITEM.getKey(item.get()))) {
                problems.add(part + " ohne eigenes Item");
                continue;
            }
            ItemStack crafted = craft(server, part);
            if (!crafted.is(item.get()) || crafted.getCount() != 1) {
                problems.add(part + ": Rezept liefert " + crafted);
            }
        }
        BuildCheck check = PropwashApi.builds().check(CoreBuild.full());
        if (!check.buildable()) {
            problems.add("freestyle_5 mit allen drei Teilen nicht baubar: " + check);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        return "3 Zubehörteile mit Halterung, Masse, Item und Rezept (Werkbank-Muster ergibt je 1 Teil), freestyle_5 mit allen drei baubar";
    }

    public static String tags(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        List<String> spears = List.of("wooden_spear", "stone_spear", "copper_spear", "iron_spear", "golden_spear", "diamond_spear", "netherite_spear");
        for (String spear : spears) {
            expect(problems, spear, SPEAR_ACCEPTS, true);
        }
        for (String other : List.of("trident", "iron_sword", "stick")) {
            expect(problems, other, SPEAR_ACCEPTS, false);
        }
        expect(problems, "firework_rocket", BOOSTER_ACCEPTS, true);
        expect(problems, "firework_star", BOOSTER_ACCEPTS, false);
        for (String bucket : List.of("water_bucket", "lava_bucket", "powder_snow_bucket", "axolotl_bucket", "cod_bucket", "pufferfish_bucket",
                "salmon_bucket", "tadpole_bucket", "tropical_fish_bucket")) {
            expect(problems, bucket, FLUID_BUCKETS, true);
        }
        for (String other : List.of("bucket", "milk_bucket")) {
            expect(problems, other, FLUID_BUCKETS, false);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        return "spear_mount_accepts mit 7 Speeren ohne Dreizack, booster_accepts mit Feuerwerksrakete, dispenser_fluid_buckets mit 9 Eimern ohne leeren Eimer und Milch";
    }

    private static void expect(List<String> problems, String id, TagKey<Item> tag, boolean inside) {
        if (item(id).is(tag) != inside) {
            problems.add(id + (inside ? " fehlt in #" : " steht in #") + tag.location());
        }
    }

    public static String rules(MinecraftServer server) {
        List<String> problems = new ArrayList<>();
        List<Identifier> registered = new ArrayList<>();
        for (WorldRule<?> rule : PropwashApi.worldRules().registered()) {
            if (MobDefense.MOD_ID.equals(rule.id().getNamespace())) {
                registered.add(rule.id());
            }
        }
        List<Identifier> expected = new ArrayList<>();
        RuleDefaults.CORE.forEach(spec -> expected.add(MobDefense.id(spec.path())));
        if (!expected.equals(registered)) {
            problems.add("angemeldet " + registered + " statt " + expected);
        }
        for (RuleSpec spec : RuleDefaults.CORE) {
            Optional<WorldRule<?>> rule = MobDefenseRules.find(spec);
            if (rule.isEmpty()) {
                problems.add(spec.path() + " fehlt in MobDefenseRules");
                continue;
            }
            Object value = rule.get().get(server.overworld());
            Object standard = spec.flag() ? (Object) spec.defaultFlag() : (Object) spec.defaultValue();
            if (!standard.equals(value)) {
                problems.add(spec.path() + " ist " + value + " statt " + standard);
            }
            if (!rule.get().nameKey().equals("gamerule." + MobDefense.MOD_ID + "." + spec.path())) {
                problems.add(spec.path() + " mit Text " + rule.get().nameKey());
            }
        }
        if (PropwashApi.worldRules().find(MobDefense.id(RuleDefaults.SCULK_HEARS_DRONES.path())).isPresent()
                && MobDefenseRules.find(RuleDefaults.SCULK_HEARS_DRONES).isEmpty()) {
            problems.add("sculk_hears_drones an MobDefenseRules vorbei angemeldet");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join("; ", problems));
        }
        return registered.size() + " Welt-Regeln über PropwashApi.worldRules() in der Reihenfolge von RuleDefaults, Standardwerte in der Welt";
    }

    public static String tab(MinecraftServer server) {
        Optional<ExtensionTab> tab = MobDefenseTab.tab();
        if (tab.isEmpty() || !tab.get().registered()) {
            throw new IllegalStateException("Kreativ-Reiter von Mob Defense nicht angemeldet");
        }
        if (!tab.get().tabId().getPath().endsWith(MobDefense.MOD_ID) || !MobDefense.MOD_ID.equals(tab.get().namespace())) {
            throw new IllegalStateException("Kreativ-Reiter mit Id " + tab.get().tabId());
        }
        List<ItemStack> stacks = MobDefenseTab.partStacks();
        if (stacks.size() != MobDefense.PARTS.size()) {
            throw new IllegalStateException("Propwashs Tab bekommt " + stacks.size() + " statt 3 Teile");
        }
        return "Reiter " + tab.get().tabId() + " mit Symbol " + BuiltInRegistries.ITEM.getKey(tab.get().iconStack().getItem()) + ", 3 Teile für Propwashs Tab";
    }
}
