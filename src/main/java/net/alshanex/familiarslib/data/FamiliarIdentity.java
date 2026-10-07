package net.alshanex.familiarslib.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A familiar identity stored in an IdentityVesselItem the familiar type it came from, and its layer colors
 * (one per layer slot, -1 = that layer was in its original look).
 */
public record FamiliarIdentity(ResourceLocation entityType, List<Integer> colors) {
    public static final Codec<FamiliarIdentity> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(FamiliarIdentity::entityType),
            Codec.INT.listOf().fieldOf("colors").forGetter(FamiliarIdentity::colors)
    ).apply(instance, FamiliarIdentity::new));

    public static final StreamCodec<ByteBuf, FamiliarIdentity> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FamiliarIdentity::entityType,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list()), FamiliarIdentity::colors,
            FamiliarIdentity::new);

    public FamiliarIdentity {
        colors = List.copyOf(colors);
    }

    public int color(int slot) {
        return slot >= 0 && slot < colors.size() ? colors.get(slot) : -1;
    }
}
