package net.alshanex.familiarslib.recipe;

import net.alshanex.familiarslib.registry.FRecipeRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.Ingredient;

public abstract class ShrinkingRecipe implements Recipe<ShrinkingRecipe.Input> {

    public abstract Ingredient getInputIngredient();

    public int priority() {
        return 0;
    }

    @Override
    public RecipeType<?> getType() {
        return FRecipeRegistry.SHRINKING_RECIPE_TYPE.get();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    public record Input(ItemStack item) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return item;
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
