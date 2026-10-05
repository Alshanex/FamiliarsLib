package net.alshanex.familiarslib.compat.jei;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.recipe.ShrinkingRecipe;
import net.alshanex.familiarslib.registry.FBlockRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ShrinkingRecipeCategory extends AbstractRecipeCategory<ShrinkingRecipe> {
    public static final RecipeType<ShrinkingRecipe> RECIPE_TYPE =
            RecipeType.create(FamiliarsLib.MODID, "shrinking_station", ShrinkingRecipe.class);

    public static final int width = 82;
    public static final int height = 34;

    public ShrinkingRecipeCategory(IGuiHelper guiHelper) {
        super(
                RECIPE_TYPE,
                Component.translatable("block." + FamiliarsLib.MODID + ".shrinking_station"),
                guiHelper.createDrawableItemLike(FBlockRegistry.SHRINKING_STATION.get()),
                width,
                height
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ShrinkingRecipe recipe, IFocusGroup focuses) {
        // Input Slot
        builder.addInputSlot(1, 9)
                .setStandardSlotBackground()
                .addIngredients(recipe.getInputIngredient());

        // Output Slot
        builder.addOutputSlot(61, 9)
                .setOutputSlotBackground()
                .addItemStack(recipe.getResultItem(Minecraft.getInstance().level.registryAccess()));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, ShrinkingRecipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(26, 9);
    }
}
