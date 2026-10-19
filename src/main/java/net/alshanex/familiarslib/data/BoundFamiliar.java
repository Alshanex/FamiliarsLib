package net.alshanex.familiarslib.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A familiar held inside a Familiar Egg: its full saved data and the player it belongs to.
 */
public record BoundFamiliar(UUID familiarId, UUID owner, CompoundTag data) {

    public static final Codec<BoundFamiliar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("familiar").forGetter(BoundFamiliar::familiarId),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(BoundFamiliar::owner),
            CompoundTag.CODEC.fieldOf("data").forGetter(BoundFamiliar::data)
    ).apply(instance, BoundFamiliar::new));

    public static final StreamCodec<ByteBuf, BoundFamiliar> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BoundFamiliar::familiarId,
            UUIDUtil.STREAM_CODEC, BoundFamiliar::owner,
            ByteBufCodecs.COMPOUND_TAG, BoundFamiliar::data,
            BoundFamiliar::new
    );

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("familiar", familiarId);
        tag.putUUID("owner", owner);
        tag.put("data", data.copy());
        return tag;
    }

    @Nullable
    public static BoundFamiliar fromTag(CompoundTag tag) {
        if (!tag.hasUUID("familiar") || !tag.hasUUID("owner") || !tag.contains("data")) {
            return null;
        }
        return new BoundFamiliar(tag.getUUID("familiar"), tag.getUUID("owner"), tag.getCompound("data").copy());
    }
}