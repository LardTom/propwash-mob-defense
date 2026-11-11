package de.lardtom.mobdefense;

import de.lardtom.propwash.api.PropwashApi;
import de.lardtom.propwash.api.registry.ExtensionTab;
import de.lardtom.propwash.api.registry.TabSection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MobDefenseTab {
    public static final Identifier ICON = MobDefense.DISPENSER_MOUNT;
    public static final List<TabSection> ORDER = List.of(TabSection.ACCESSORIES);
    public static final String NAME_KEY = "itemGroup." + MobDefense.MOD_ID;

    private static ExtensionTab tab;

    private MobDefenseTab() {
    }

    public static synchronized ExtensionTab init() {
        if (tab == null) {
            tab = PropwashApi.extensionTab(MobDefense.MOD_ID).icon(ICON).order(ORDER.toArray(TabSection[]::new));
            PropwashApi.creativeTab().add(MobDefenseTab::partStacks);
        }
        return tab;
    }

    public static synchronized Optional<ExtensionTab> tab() {
        return Optional.ofNullable(tab);
    }

    public static List<ItemStack> partStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Identifier part : MobDefense.PARTS) {
            ItemStack stack = PropwashApi.partItems().stack(part);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }
}
