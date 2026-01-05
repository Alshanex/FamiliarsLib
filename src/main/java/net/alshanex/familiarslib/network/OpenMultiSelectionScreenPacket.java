package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenMultiSelectionScreenPacket implements CustomPacketPayload {
    public static final Type<OpenMultiSelectionScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "open_multi_selection_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMultiSelectionScreenPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenMultiSelectionScreenPacket::write, OpenMultiSelectionScreenPacket::new);

    public OpenMultiSelectionScreenPacket() {}

    public OpenMultiSelectionScreenPacket(FriendlyByteBuf buf) {}

    public void write(FriendlyByteBuf buf) {}

    public static void handle(OpenMultiSelectionScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.openMultiSelectionScreen();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
