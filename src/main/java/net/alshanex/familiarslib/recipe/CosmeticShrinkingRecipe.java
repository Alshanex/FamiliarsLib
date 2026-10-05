package net.alshanex.familiarslib.recipe;

import com.mojang.serialization.MapCodec;
import net.alshanex.familiarslib.registry.FRecipeRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmetics;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shrinks any item in the {@code familiarslib:familiar_hats} or {@code familiarslib:familiar_weapons}
 * tags into a familiar cosmetic. The result is the same item (enchantments, name and all) with
 * the {@code familiarslib:familiar_cosmetic} component.
 */
public class CosmeticShrinkingRecipe extends ShrinkingRecipe {

    public static final CosmeticShrinkingRecipe INSTANCE = new CosmeticShrinkingRecipe();

    @Override
    public Ingredient getInputIngredient() {
        return Ingredient.EMPTY;
    }

    @Override
    public int priority() {
        return 50;
    }

    @Override
    public boolean matches(Input input, Level level) {
        ItemStack stack = input.item();
        return FamiliarCosmetics.canBecomeCosmetic(stack) && !FamiliarCosmetics.isShrunkCosmetic(stack);
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return FamiliarCosmetics.shrink(input.item());
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY; // dynamic
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FRecipeRegistry.COSMETIC_SHRINKING_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<CosmeticShrinkingRecipe> {
        public static final MapCodec<CosmeticShrinkingRecipe> CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, CosmeticShrinkingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public MapCodec<CosmeticShrinkingRecipe> codec() { return CODEC; }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CosmeticShrinkingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
