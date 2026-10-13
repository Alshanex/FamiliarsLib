package net.alshanex.familiarslib.item.camera;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * The pose a familiar had when its photo was taken.
 */
public record FamiliarPose(float headYaw, float headPitch, boolean sitting, List<String> animations, List<BonePose> bones) {
    public static final int MAX_BONES = 256;
    public static final int MAX_ANIMATIONS = 16;
    public static final int MAX_NAME_LENGTH = 96;

    public static final FamiliarPose EMPTY = new FamiliarPose(0F, 0F, false, List.of(), List.of());

    public record BonePose(String name, Vector3f rotation, Vector3f position, Vector3f scale) {
        public static final Codec<BonePose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(BonePose::name),
                ExtraCodecs.VECTOR3F.fieldOf("rot").forGetter(BonePose::rotation),
                ExtraCodecs.VECTOR3F.fieldOf("pos").forGetter(BonePose::position),
                ExtraCodecs.VECTOR3F.fieldOf("scale").forGetter(BonePose::scale)
        ).apply(instance, BonePose::new));

        public static final StreamCodec<ByteBuf, BonePose> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), BonePose::name,
                ByteBufCodecs.VECTOR3F, BonePose::rotation,
                ByteBufCodecs.VECTOR3F, BonePose::position,
                ByteBufCodecs.VECTOR3F, BonePose::scale,
                BonePose::new);

        public BonePose {
            rotation = new Vector3f(rotation);
            position = new Vector3f(position);
            scale = new Vector3f(scale);
        }

        /** Server side: the values come from the client, so keep them finite and in a sane range. */
        BonePose sanitized() {
            return new BonePose(trim(name),
                    new Vector3f(clean(rotation.x, -Mth.TWO_PI * 4, Mth.TWO_PI * 4, 0F),
                            clean(rotation.y, -Mth.TWO_PI * 4, Mth.TWO_PI * 4, 0F),
                            clean(rotation.z, -Mth.TWO_PI * 4, Mth.TWO_PI * 4, 0F)),
                    new Vector3f(clean(position.x, -64F, 64F, 0F),
                            clean(position.y, -64F, 64F, 0F),
                            clean(position.z, -64F, 64F, 0F)),
                    new Vector3f(clean(scale.x, 0F, 16F, 1F),
                            clean(scale.y, 0F, 16F, 1F),
                            clean(scale.z, 0F, 16F, 1F)));
        }
    }

    public static final Codec<FamiliarPose> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("head_yaw", 0F).forGetter(FamiliarPose::headYaw),
            Codec.FLOAT.optionalFieldOf("head_pitch", 0F).forGetter(FamiliarPose::headPitch),
            Codec.BOOL.optionalFieldOf("sitting", false).forGetter(FamiliarPose::sitting),
            Codec.STRING.listOf().optionalFieldOf("animations", List.of()).forGetter(FamiliarPose::animations),
            BonePose.CODEC.listOf().optionalFieldOf("bones", List.of()).forGetter(FamiliarPose::bones)
    ).apply(instance, FamiliarPose::new));

    public static final StreamCodec<ByteBuf, FamiliarPose> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FamiliarPose::headYaw,
            ByteBufCodecs.FLOAT, FamiliarPose::headPitch,
            ByteBufCodecs.BOOL, FamiliarPose::sitting,
            ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH).apply(ByteBufCodecs.list(MAX_ANIMATIONS)), FamiliarPose::animations,
            BonePose.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_BONES)), FamiliarPose::bones,
            FamiliarPose::new);

    public FamiliarPose {
        animations = List.copyOf(animations);
        bones = List.copyOf(bones);
    }

    public Optional<BonePose> bone(String name) {
        return bones.stream().filter(b -> b.name().equals(name)).findFirst();
    }

    /** The first controller's animation name, without the "controller:" prefix. */
    public Optional<String> mainAnimation() {
        return animations.stream().findFirst().map(entry -> {
            int split = entry.indexOf(':');
            return split >= 0 ? entry.substring(split + 1) : entry;
        });
    }

    /** Server side: pose data comes from the client, clean it before storing it in an item. */
    public FamiliarPose sanitized() {
        return new FamiliarPose(
                Mth.wrapDegrees(clean(headYaw, -360F, 360F, 0F)),
                clean(headPitch, -90F, 90F, 0F),
                sitting,
                animations.stream().limit(MAX_ANIMATIONS).map(FamiliarPose::trim).toList(),
                bones.stream().limit(MAX_BONES).map(BonePose::sanitized).toList());
    }

    private static float clean(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Mth.clamp(value, min, max) : fallback;
    }

    private static String trim(String value) {
        return value.length() > MAX_NAME_LENGTH ? value.substring(0, MAX_NAME_LENGTH) : value;
    }
}