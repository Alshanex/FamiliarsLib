package net.alshanex.familiarslib.registry;

import net.minecraft.world.food.FoodProperties;

public class FoodRegistry {
    public static final FoodProperties BLUEBERRY = new FoodProperties.Builder().nutrition(2).saturationModifier(0.2f)
            .fast().build();
}
