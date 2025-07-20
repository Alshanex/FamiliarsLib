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

public class OpenFamiliarStoragePacket implements CustomPacketPayload {
    public static final Type<OpenFamiliarStoragePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "open_familiar_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFamiliarStoragePacket> STREAM_CODEC =
            CustomPacketPayload.codec(OpenFamiliarStoragePacket::write, OpenFamiliarStoragePacket::new);

    private final BlockPos blockPos;

    public OpenFamiliarStoragePacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public OpenFamiliarStoragePacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public static void handle(OpenFamiliarStoragePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.openStorageScreen(packet.blockPos);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
