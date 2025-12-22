package net.alshanex.familiarslib.util.consumables;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component for items that can be eaten by familiars to restore health
 */
public record FamiliarFoodComponent(int healing, float saturation) {

    public static final Codec<FamiliarFoodComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("healing").forGetter(FamiliarFoodComponent::healing),
                    Codec.FLOAT.fieldOf("saturation").forGetter(FamiliarFoodComponent::saturation)
            ).apply(instance, FamiliarFoodComponent::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarFoodComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FamiliarFoodComponent::healing,
                    ByteBufCodecs.FLOAT, FamiliarFoodComponent::saturation,
                    FamiliarFoodComponent::new
            );

    /**
     * Helper to create a component with just healing (default saturation 0)
     */
    public static FamiliarFoodComponent of(int healing) {
        return new FamiliarFoodComponent(healing, 0.0f);
    }
}
