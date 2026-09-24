package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.ClientFamiliarData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

/** One batch of full snapshots. replaceAll=true on the first batch of a full sync. */
public class FamiliarSnapshotsPacket implements CustomPacketPayload {
    public static final Type<FamiliarSnapshotsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_snapshots"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarSnapshotsPacket> STREAM_CODEC =
            CustomPacketPayload.codec(FamiliarSnapshotsPacket::write, FamiliarSnapshotsPacket::new);

    private final boolean replaceAll;
    private final Map<UUID, CompoundTag> entries;

    public FamiliarSnapshotsPacket(boolean replaceAll, Map<UUID, CompoundTag> entries) {
        this.replaceAll = replaceAll;
        this.entries = new LinkedHashMap<>();
        entries.forEach((id, tag) -> this.entries.put(id, tag.copy()));
    }

    public FamiliarSnapshotsPacket(FriendlyByteBuf buf) {
        this.replaceAll = buf.readBoolean();
        int size = buf.readVarInt();
        this.entries = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            CompoundTag tag = buf.readNbt();
            if (tag != null) entries.put(id, tag);
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(replaceAll);
        buf.writeVarInt(entries.size());
        entries.forEach((id, tag) -> {
            buf.writeUUID(id);
            buf.writeNbt(tag);
        });
    }

    public static void handle(FamiliarSnapshotsPacket p, IPayloadContext context) {
        context.enqueueWork(() -> ClientFamiliarData.get().applySnapshots(p.replaceAll, p.entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
