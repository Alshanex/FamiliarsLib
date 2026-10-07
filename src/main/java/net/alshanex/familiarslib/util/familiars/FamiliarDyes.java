package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Shrunk dyes: dyes put through the shrinking station. They color a familiar's eyes instead of its clothes.
 */
public final class FamiliarDyes {
    public static final TagKey<Item> SHRINKABLE_DYES = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shrinkable_dyes"));

    public static boolean isShrunkDye(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ComponentRegistry.SHRUNK_DYE.get());
    }

    public static boolean canBeShrunk(ItemStack stack) {
        return !stack.isEmpty() && stack.is(SHRINKABLE_DYES) && !isShrunkDye(stack);
    }

    public static ItemStack shrink(ItemStack stack) {
        ItemStack result = stack.copyWithCount(1);
        result.set(ComponentRegistry.SHRUNK_DYE.get(), Unit.INSTANCE);
        return result;
    }

    private FamiliarDyes() {}
}