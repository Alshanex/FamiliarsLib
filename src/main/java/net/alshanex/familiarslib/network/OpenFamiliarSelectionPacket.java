package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenFamiliarSelectionPacket implements CustomPacketPayload {
    public static final Type<OpenFamiliarSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "open_familiar_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFamiliarSelectionPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenFamiliarSelectionPacket::write, OpenFamiliarSelectionPacket::new);

    public OpenFamiliarSelectionPacket() {}

    public OpenFamiliarSelectionPacket(FriendlyByteBuf buf) {}

    public void write(FriendlyByteBuf buf) {}

    public static void handle(OpenFamiliarSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.openFamiliarSelectionScreen();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
