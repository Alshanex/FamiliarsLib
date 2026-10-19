package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.screen.RecallShardScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server -> client: the familiars this player can recall in their current level. Opens the recall screen. */
public class ClientboundRecallListPacket implements CustomPacketPayload {
    public static final Type<ClientboundRecallListPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "recall_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundRecallListPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ClientboundRecallListPacket::write, ClientboundRecallListPacket::new);

    /** {@code ageTicks}: game ticks since it was released or died. */
    public record Entry(UUID familiarId, CompoundTag data, boolean dead, long ageTicks) {}

    private final List<Entry> entries;

    public ClientboundRecallListPacket(List<Entry> entries) {
        this.entries = entries;
    }

    public ClientboundRecallListPacket(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Entry> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buf.readUUID();
            CompoundTag data = buf.readNbt();
            boolean dead = buf.readBoolean();
            long age = buf.readVarLong();
            read.add(new Entry(id, data != null ? data : new CompoundTag(), dead, age));
        }
        this.entries = read;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeUUID(entry.familiarId());
            buf.writeNbt(entry.data());
            buf.writeBoolean(entry.dead());
            buf.writeVarLong(entry.ageTicks());
        }
    }

    public static void handle(ClientboundRecallListPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> RecallShardScreen.open(packet.entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}