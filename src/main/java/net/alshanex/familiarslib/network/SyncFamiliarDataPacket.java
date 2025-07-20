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

public class SyncFamiliarDataPacket implements CustomPacketPayload {
    public static final Type<SyncFamiliarDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "sync_familiar_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFamiliarDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SyncFamiliarDataPacket::write, SyncFamiliarDataPacket::new);

    private final CompoundTag familiarData;

    public SyncFamiliarDataPacket(CompoundTag familiarData) {
        this.familiarData = familiarData;
    }

    public SyncFamiliarDataPacket(FriendlyByteBuf buf) {
        this.familiarData = buf.readNbt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeNbt(familiarData);
    }

    public static void handle(SyncFamiliarDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.syncFamiliarData(packet.familiarData);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
