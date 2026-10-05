package net.alshanex.familiarslib.compat.jei;

import net.alshanex.familiarslib.recipe.*;
import net.alshanex.familiarslib.registry.FRecipeRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmetics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class ShrinkingRecipeMaker {

    private ShrinkingRecipeMaker() {
        // Private constructor
    }

    public static List<ShrinkingRecipe> getRecipes(Level level) {
        List<ShrinkingRecipe> recipes = new ArrayList<>();

        // 1. Get all registered shrinking recipes
        List<RecipeHolder<ShrinkingRecipe>> holders = level.getRecipeManager().getAllRecipesFor(FRecipeRegistry.SHRINKING_RECIPE_TYPE.get());

        for (RecipeHolder<ShrinkingRecipe> holder : holders) {
            ShrinkingRecipe recipe = holder.value();

            // If it's a fixed recipe (JSON based), add it directly
            if (recipe instanceof FixedShrinkingRecipe) {
                recipes.add(recipe);
            }
            // If it's the Food recipe, we need to generate display variants for every food item
            else if (recipe instanceof FoodShrinkingRecipe foodRecipe) {
                generateFoodRecipes(recipes, foodRecipe, level);
            }
            // Same for cosmetics: one display entry per item in the hat and weapon tags
            else if (recipe instanceof CosmeticShrinkingRecipe cosmeticRecipe) {
                generateCosmeticRecipes(recipes, cosmeticRecipe, level);
            }
        }

        return recipes;
    }

    private static void generateFoodRecipes(List<ShrinkingRecipe> recipes, FoodShrinkingRecipe foodRecipe, Level level) {
        // Iterate through all items in the game
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            ShrinkingRecipe.Input input = new ShrinkingRecipe.Input(stack);

            // Check if the dynamic food recipe accepts this specific item
            if (foodRecipe.matches(input, level)) {
                // Calculate what the result would be
                ItemStack result = foodRecipe.assemble(input, level.registryAccess());

                if (!result.isEmpty()) {
                    // Create a "fake" FixedShrinkingRecipe just for JEI to display
                    recipes.add(new FixedShrinkingRecipe(Ingredient.of(stack), result));
                }
            }
        }
    }

    private static void generateCosmeticRecipes(List<ShrinkingRecipe> recipes, CosmeticShrinkingRecipe cosmeticRecipe, Level level) {
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            ShrinkingRecipe.Input input = new ShrinkingRecipe.Input(stack);
            if (cosmeticRecipe.matches(input, level)) {
                recipes.add(new FixedShrinkingRecipe(Ingredient.of(stack), cosmeticRecipe.assemble(input, level.registryAccess())));
            }
        }
    }
}
