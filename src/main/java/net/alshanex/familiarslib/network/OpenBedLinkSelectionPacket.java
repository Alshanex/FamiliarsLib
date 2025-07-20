package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class OpenBedLinkSelectionPacket implements CustomPacketPayload {
    public static final Type<OpenBedLinkSelectionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "open_bed_link_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBedLinkSelectionPacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenBedLinkSelectionPacket::write, OpenBedLinkSelectionPacket::new);

    private final BlockPos bedPos;

    public OpenBedLinkSelectionPacket(BlockPos bedPos) {
        this.bedPos = bedPos;
    }

    public OpenBedLinkSelectionPacket(FriendlyByteBuf buf) {
        this.bedPos = buf.readBlockPos();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(bedPos);
    }

    public static void handle(OpenBedLinkSelectionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.openBedScreen(packet.bedPos);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
