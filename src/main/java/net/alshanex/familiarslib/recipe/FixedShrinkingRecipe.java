package net.alshanex.familiarslib.recipe;

import net.alshanex.familiarslib.registry.FRecipeRegistry;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class FixedShrinkingRecipe extends ShrinkingRecipe {
    private final Ingredient input;
    private final ItemStack result;

    public FixedShrinkingRecipe(Ingredient input, ItemStack result) {
        this.input = input;
        this.result = result;
    }

    @Override
    public Ingredient getInputIngredient() {
        return input;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean matches(Input input, Level level) {
        if (!this.input.test(input.item())) {
            return false;
        }

        FamiliarConsumableComponent inputComp = input.item().get(ComponentRegistry.FAMILIAR_CONSUMABLE.get());
        FamiliarConsumableComponent resultComp = this.result.get(ComponentRegistry.FAMILIAR_CONSUMABLE.get());

        if (resultComp != null && inputComp != null) {
            if (inputComp.equals(resultComp)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack assemble(Input input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FRecipeRegistry.FIXED_SHRINKING_SERIALIZER.get();
    }

    public static class Serializer implements RecipeSerializer<FixedShrinkingRecipe> {
        public static final MapCodec<FixedShrinkingRecipe> CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
                Ingredient.CODEC.fieldOf("input").forGetter(r -> r.input),
                ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result)
        ).apply(builder, FixedShrinkingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, FixedShrinkingRecipe> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, r -> r.input,
                ItemStack.STREAM_CODEC, r -> r.result,
                FixedShrinkingRecipe::new
        );

        @Override
        public MapCodec<FixedShrinkingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FixedShrinkingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
