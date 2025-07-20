package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SetStorageModePacket implements CustomPacketPayload {
    public static final Type<SetStorageModePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "set_storage_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetStorageModePacket> STREAM_CODEC =
            CustomPacketPayload.codec(SetStorageModePacket::write, SetStorageModePacket::new);

    private final BlockPos blockPos;
    private final boolean storeMode;

    public SetStorageModePacket(BlockPos blockPos, boolean storeMode) {
        this.blockPos = blockPos;
        this.storeMode = storeMode;
    }

    public SetStorageModePacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.storeMode = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeBoolean(storeMode);
    }

    public static void handle(SetStorageModePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.handleSetStorageMode(serverPlayer, packet.blockPos, packet.storeMode);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
