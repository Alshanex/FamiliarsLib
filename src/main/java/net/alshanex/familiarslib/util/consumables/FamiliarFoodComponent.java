package net.alshanex.familiarslib.util.consumables;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record FamiliarFoodComponent(int healing, float saturation) {
    public static final Codec<FamiliarFoodComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("healing").forGetter(FamiliarFoodComponent::healing),
                    Codec.FLOAT.fieldOf("saturation").forGetter(FamiliarFoodComponent::saturation)
            ).apply(instance, FamiliarFoodComponent::new)
    );

    /**
     * Helper to create a component with just healing (default saturation 0)
     */
    public static FamiliarFoodComponent of(int healing) {
        return new FamiliarFoodComponent(healing, 0.0f);
    }
}
