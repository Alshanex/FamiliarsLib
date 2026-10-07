package net.alshanex.familiarslib.recipe;

import com.mojang.serialization.MapCodec;
import net.alshanex.familiarslib.registry.FRecipeRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarDyes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shrinks any item in the {@code familiarslib:shrinkable_dyes} tag into a shrunk dye: the same item, marked with
 * the {@code familiarslib:shrunk_dye} component. Shrunk dyes color a familiar's eyes instead of its clothes.
 */
public class DyeShrinkingRecipe extends ShrinkingRecipe {

    public static final DyeShrinkingRecipe INSTANCE = new DyeShrinkingRecipe();

    @Override
    public Ingredient getInputIngredient() {
        return Ingredient.EMPTY; // tag driven; JEI entries are generated in ShrinkingRecipeMaker
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public boolean matches(ShrinkingRecipe.Input input, Level level) {
        ItemStack stack = input.item();
        return FamiliarDyes.canBeShrunk(stack);
    }

    @Override
    public ItemStack assemble(ShrinkingRecipe.Input input, HolderLookup.Provider registries) {
        return FamiliarDyes.shrink(input.item());
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY; // dynamic
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FRecipeRegistry.DYE_SHRINKING_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<DyeShrinkingRecipe> {
        public static final MapCodec<DyeShrinkingRecipe> CODEC = MapCodec.unit(INSTANCE);
        public static final StreamCodec<RegistryFriendlyByteBuf, DyeShrinkingRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public MapCodec<DyeShrinkingRecipe> codec() { return CODEC; }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, DyeShrinkingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}