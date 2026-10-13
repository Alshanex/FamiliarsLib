package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.camera.FamiliarCameraItem;
import net.alshanex.familiarslib.item.camera.FamiliarPose;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector3f;

/**
 * Client -> server: "I photographed this familiar, it was drawn around here, and this is the pose I saw it in".
 * aimOffset is the center of where the familiar was drawn, relative to its position (animations can take the
 * model far from its hitbox). The server re-checks the shot and reads the colors/cosmetics itself; only the pose
 * and that offset come from the client, since animations only exist client side.
 */
public record ServerboundTakePhotoPacket(int entityId, Vector3f aimOffset, FamiliarPose pose) implements CustomPacketPayload {

    public static final Type<ServerboundTakePhotoPacket> TYPE = new Type<>(FamiliarsLib.id("take_familiar_photo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundTakePhotoPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ServerboundTakePhotoPacket::entityId,
            ByteBufCodecs.VECTOR3F, ServerboundTakePhotoPacket::aimOffset,
            FamiliarPose.STREAM_CODEC, ServerboundTakePhotoPacket::pose,
            ServerboundTakePhotoPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ServerboundTakePhotoPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarCameraItem.takePhoto(serverPlayer, packet.entityId(), packet.aimOffset(), packet.pose());
            }
        });
    }
}
