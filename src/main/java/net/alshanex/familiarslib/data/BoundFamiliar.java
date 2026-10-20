package net.alshanex.familiarslib.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.alshanex.familiarslib.util.familiars.EggColor;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a Familiar Egg on a bed (or a bound egg) will hatch into.
 */
public record BoundFamiliar(UUID familiarId, UUID owner, CompoundTag data, int color, boolean fresh) {

    public static final Codec<BoundFamiliar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("familiar").forGetter(BoundFamiliar::familiarId),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(BoundFamiliar::owner),
            CompoundTag.CODEC.fieldOf("data").forGetter(BoundFamiliar::data),
            Codec.INT.optionalFieldOf("color", EggColor.DEFAULT_RGB).forGetter(BoundFamiliar::color),
            Codec.BOOL.optionalFieldOf("fresh", false).forGetter(BoundFamiliar::fresh)
    ).apply(instance, BoundFamiliar::new));

    public static final StreamCodec<ByteBuf, BoundFamiliar> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BoundFamiliar::familiarId,
            UUIDUtil.STREAM_CODEC, BoundFamiliar::owner,
            ByteBufCodecs.COMPOUND_TAG, BoundFamiliar::data,
            ByteBufCodecs.INT, BoundFamiliar::color,
            ByteBufCodecs.BOOL, BoundFamiliar::fresh,
            BoundFamiliar::new
    );

    /** A recalled familiar. */
    public static BoundFamiliar recalled(UUID familiarId, UUID owner, CompoundTag data, int color) {
        return new BoundFamiliar(familiarId, owner, data, color, false);
    }

    /** A new familiar of this type, for this owner. */
    public static BoundFamiliar fresh(ResourceLocation type, UUID owner, int color) {
        CompoundTag data = new CompoundTag();
        data.putString("id", type.toString());
        return new BoundFamiliar(UUID.randomUUID(), owner, data, color, true);
    }

    /** The familiar's entity type id. */
    @Nullable
    public ResourceLocation type() {
        return ResourceLocation.tryParse(data.getString("id"));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("familiar", familiarId);
        tag.putUUID("owner", owner);
        tag.put("data", data.copy());
        tag.putInt("color", color);
        tag.putBoolean("fresh", fresh);
        return tag;
    }

    @Nullable
    public static BoundFamiliar fromTag(CompoundTag tag) {
        if (!tag.hasUUID("familiar") || !tag.hasUUID("owner") || !tag.contains("data")) {
            return null;
        }
        int color = tag.contains("color") ? tag.getInt("color") : EggColor.DEFAULT_RGB;
        return new BoundFamiliar(tag.getUUID("familiar"), tag.getUUID("owner"), tag.getCompound("data").copy(),
                color, tag.getBoolean("fresh"));
    }
}