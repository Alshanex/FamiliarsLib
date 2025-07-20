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

import java.util.*;

public class FamiliarDataPacket implements CustomPacketPayload {
    public static final Type<FamiliarDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarDataPacket> STREAM_CODEC =
            CustomPacketPayload.codec(FamiliarDataPacket::write, FamiliarDataPacket::new);

    private final Map<UUID, CompoundTag> familiars;
    private final UUID selectedFamiliarId;
    private final UUID currentSummonedFamiliarId;
    private final Set<UUID> summonedFamiliarIds; // NUEVO: Set de todos los familiares summoneados

    public FamiliarDataPacket(Map<UUID, CompoundTag> familiars, UUID selectedFamiliarId, UUID currentSummonedFamiliarId, Set<UUID> summonedFamiliarIds) {
        this.familiars = familiars;
        this.selectedFamiliarId = selectedFamiliarId;
        this.currentSummonedFamiliarId = currentSummonedFamiliarId;
        this.summonedFamiliarIds = summonedFamiliarIds != null ? summonedFamiliarIds : new HashSet<>();
    }

    // Constructor de compatibilidad hacia atrás
    public FamiliarDataPacket(Map<UUID, CompoundTag> familiars, UUID selectedFamiliarId, UUID currentSummonedFamiliarId) {
        this(familiars, selectedFamiliarId, currentSummonedFamiliarId, new HashSet<>());
    }

    public FamiliarDataPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.familiars = new HashMap<>();

        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            CompoundTag nbt = buf.readNbt();
            this.familiars.put(id, nbt);
        }

        this.selectedFamiliarId = buf.readBoolean() ? buf.readUUID() : null;
        this.currentSummonedFamiliarId = buf.readBoolean() ? buf.readUUID() : null;

        // NUEVO: Leer set de familiares summoneados
        int summonedSize = buf.readVarInt();
        this.summonedFamiliarIds = new HashSet<>();
        for (int i = 0; i < summonedSize; i++) {
            this.summonedFamiliarIds.add(buf.readUUID());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(familiars.size());

        for (Map.Entry<UUID, CompoundTag> entry : familiars.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeNbt(entry.getValue());
        }

        buf.writeBoolean(selectedFamiliarId != null);
        if (selectedFamiliarId != null) {
            buf.writeUUID(selectedFamiliarId);
        }

        buf.writeBoolean(currentSummonedFamiliarId != null);
        if (currentSummonedFamiliarId != null) {
            buf.writeUUID(currentSummonedFamiliarId);
        }

        buf.writeVarInt(summonedFamiliarIds.size());
        for (UUID summonedId : summonedFamiliarIds) {
            buf.writeUUID(summonedId);
        }
    }

    public static void handle(FamiliarDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.handleFamiliarDataPacket(packet.familiars, packet.selectedFamiliarId, packet.currentSummonedFamiliarId, packet.summonedFamiliarIds);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}