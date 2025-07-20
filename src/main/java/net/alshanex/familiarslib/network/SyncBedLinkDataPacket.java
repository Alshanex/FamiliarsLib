package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class SyncBedLinkDataPacket implements CustomPacketPayload {
    public static final Type<SyncBedLinkDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "sync_bed_link_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBedLinkDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SyncBedLinkDataPacket::write, SyncBedLinkDataPacket::new);

    private final CompoundTag bedLinkData;

    public SyncBedLinkDataPacket(CompoundTag bedLinkData) {
        this.bedLinkData = bedLinkData;
    }

    public SyncBedLinkDataPacket(FriendlyByteBuf buf) {
        this.bedLinkData = buf.readNbt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(bedLinkData);
    }

    public static void handle(SyncBedLinkDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.syncBedData(packet.bedLinkData);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
