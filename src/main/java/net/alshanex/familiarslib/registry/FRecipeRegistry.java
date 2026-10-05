package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.recipe.CosmeticShrinkingRecipe;
import net.alshanex.familiarslib.recipe.FixedShrinkingRecipe;
import net.alshanex.familiarslib.recipe.FoodShrinkingRecipe;
import net.alshanex.familiarslib.recipe.ShrinkingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Shrinking station recipes.
 * <ul>
 *     <li>{@code familiarslib:shrinking_recipe}: fixed input -> result, written in JSON</li>
 *     <li>{@code familiarslib:food_shrinking}: any food -> familiar food</li>
 *     <li>{@code familiarslib:cosmetic_shrinking}: any item in the hat/weapon tags -> familiar cosmetic</li>
 * </ul>
 */
public class FRecipeRegistry {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, FamiliarsLib.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, FamiliarsLib.MODID);

    // The RecipeType is bound to the abstract base class, so every kind of shrinking recipe shares it
    public static final DeferredHolder<RecipeType<?>, RecipeType<ShrinkingRecipe>> SHRINKING_RECIPE_TYPE = RECIPE_TYPES.register("shrinking_recipe",
            registry -> new RecipeType<ShrinkingRecipe>() {
                @Override
                public String toString() { return registry.toString(); }
            });

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FixedShrinkingRecipe>> FIXED_SHRINKING_SERIALIZER =
            RECIPE_SERIALIZERS.register("shrinking_recipe", FixedShrinkingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoodShrinkingRecipe>> FOOD_SHRINKING_SERIALIZER =
            RECIPE_SERIALIZERS.register("food_shrinking", FoodShrinkingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CosmeticShrinkingRecipe>> COSMETIC_SHRINKING_SERIALIZER =
            RECIPE_SERIALIZERS.register("cosmetic_shrinking", CosmeticShrinkingRecipe.Serializer::new);

    public static void register(IEventBus eventBus) {
        // Old recipe files with "type": "alshanex_familiars:shrinking_recipe" / "food_shrinking" keep loading
        FMigration.alias(RECIPE_TYPES, "shrinking_recipe");
        FMigration.alias(RECIPE_SERIALIZERS, "shrinking_recipe", "food_shrinking");
        RECIPE_TYPES.register(eventBus);
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
