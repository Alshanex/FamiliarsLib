package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class RequestFamiliarSelectionPacket implements CustomPacketPayload {
    public static final Type<RequestFamiliarSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "request_familiar_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestFamiliarSelectionPacket> STREAM_CODEC =
            CustomPacketPayload.codec(RequestFamiliarSelectionPacket::write, RequestFamiliarSelectionPacket::new);

    public RequestFamiliarSelectionPacket() {}

    public RequestFamiliarSelectionPacket(FriendlyByteBuf buf) {}

    public void write(FriendlyByteBuf buf) {}

    public static void handle(RequestFamiliarSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.requestFamiliarSelectionScreen(serverPlayer);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
