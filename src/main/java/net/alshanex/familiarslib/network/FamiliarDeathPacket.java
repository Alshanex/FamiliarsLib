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

import java.util.Map;
import java.util.UUID;

public class FamiliarDeathPacket implements CustomPacketPayload {
    public static final Type<FamiliarDeathPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_death"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarDeathPacket> STREAM_CODEC =
            CustomPacketPayload.codec(FamiliarDeathPacket::write, FamiliarDeathPacket::new);

    private final UUID deadFamiliarId;
    private final Map<UUID, CompoundTag> remainingFamiliars;
    private final UUID newSelectedFamiliarId;
    private final UUID currentSummonedFamiliarId;
    private final CompoundTag familiarData;

    public FamiliarDeathPacket(UUID deadFamiliarId, Map<UUID, CompoundTag> remainingFamiliars,
                               UUID newSelectedFamiliarId, UUID currentSummonedFamiliarId,
                               CompoundTag familiarData) {
        this.deadFamiliarId = deadFamiliarId;
        this.remainingFamiliars = remainingFamiliars;
        this.newSelectedFamiliarId = newSelectedFamiliarId;
        this.currentSummonedFamiliarId = currentSummonedFamiliarId;
        this.familiarData = familiarData;
    }

    public FamiliarDeathPacket(FriendlyByteBuf buf) {
        this.deadFamiliarId = buf.readUUID();

        // Leer familiares restantes
        int size = buf.readVarInt();
        this.remainingFamiliars = new java.util.HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            CompoundTag nbt = buf.readNbt();
            this.remainingFamiliars.put(id, nbt);
        }

        this.newSelectedFamiliarId = buf.readBoolean() ? buf.readUUID() : null;
        this.currentSummonedFamiliarId = buf.readBoolean() ? buf.readUUID() : null;
        this.familiarData = buf.readNbt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(deadFamiliarId);

        // Escribir familiares restantes
        buf.writeVarInt(remainingFamiliars.size());
        for (Map.Entry<UUID, CompoundTag> entry : remainingFamiliars.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeNbt(entry.getValue());
        }

        buf.writeBoolean(newSelectedFamiliarId != null);
        if (newSelectedFamiliarId != null) {
            buf.writeUUID(newSelectedFamiliarId);
        }

        buf.writeBoolean(currentSummonedFamiliarId != null);
        if (currentSummonedFamiliarId != null) {
            buf.writeUUID(currentSummonedFamiliarId);
        }

        buf.writeNbt(familiarData);
    }

    public static void handle(FamiliarDeathPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.handleFamiliarDeathPacket(packet.deadFamiliarId, packet.remainingFamiliars, packet.newSelectedFamiliarId,
                    packet.currentSummonedFamiliarId, packet.familiarData);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
