package net.alshanex.familiarslib.util.consumables;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem.ConsumableType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record FamiliarConsumableComponent(ConsumableType type, int tier) {

    public static final Codec<FamiliarConsumableComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ConsumableType.CODEC.fieldOf("type").forGetter(FamiliarConsumableComponent::type),
                    Codec.intRange(1, 3).fieldOf("tier").forGetter(FamiliarConsumableComponent::tier)
            ).apply(instance, FamiliarConsumableComponent::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarConsumableComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ConsumableType.STREAM_CODEC, FamiliarConsumableComponent::type,
                    ByteBufCodecs.VAR_INT, FamiliarConsumableComponent::tier,
                    FamiliarConsumableComponent::new
            );

    /**
     * Get the bonus amount this consumable provides
     */
    public int getBonus() {
        return type.getTierBonus(tier);
    }

    /**
     * Get the limit for this consumable's tier
     */
    public int getLimit() {
        return type.getTierLimit(tier);
    }

    /**
     * Check if this tier is valid for the consumable type
     */
    public boolean isValidTier() {
        return tier >= 1 && tier <= type.getMaxTier();
    }
}
