package net.alshanex.familiarslib.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.registry.FBlockRegistry;
import net.alshanex.familiarslib.registry.FItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@JeiPlugin
public class FamiliarsLibJEIPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ShrinkingRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(FBlockRegistry.SHRINKING_STATION.get()), ShrinkingRecipeCategory.RECIPE_TYPE);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level != null) {
            registration.addRecipes(ShrinkingRecipeCategory.RECIPE_TYPE,
                    ShrinkingRecipeMaker.getRecipes(Minecraft.getInstance().level));
        }
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(FItemRegistry.FAMILIAR_EGG.get(), new ISubtypeInterpreter<>() {
            @Override
            public @Nullable Object getSubtypeData(ItemStack stack, UidContext context) {
                return stack.get(ComponentRegistry.FAMILIAR_TYPE);
            }

            @Override
            public @NotNull String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                ResourceLocation type = stack.get(ComponentRegistry.FAMILIAR_TYPE);
                return type == null ? "" : type.toString();
            }
        });
    }
}
