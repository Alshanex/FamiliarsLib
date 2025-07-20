package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UpdateFamiliarStoragePacket implements CustomPacketPayload {
    public static final Type<UpdateFamiliarStoragePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "update_familiar_storage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFamiliarStoragePacket> STREAM_CODEC =
            CustomPacketPayload.codec(UpdateFamiliarStoragePacket::write, UpdateFamiliarStoragePacket::new);

    private final BlockPos blockPos;
    private final Map<UUID, CompoundTag> storedFamiliars;
    private final boolean storeMode; // NUEVO: incluir el modo

    public UpdateFamiliarStoragePacket(BlockPos blockPos, Map<UUID, CompoundTag> storedFamiliars, boolean storeMode) {
        this.blockPos = blockPos;
        this.storedFamiliars = storedFamiliars;
        this.storeMode = storeMode;
    }

    // Sobrecarga para mantener compatibilidad
    public UpdateFamiliarStoragePacket(BlockPos blockPos, Map<UUID, CompoundTag> storedFamiliars) {
        this(blockPos, storedFamiliars, false); // Por defecto Wander Mode
    }

    public UpdateFamiliarStoragePacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();

        int size = buf.readVarInt();
        this.storedFamiliars = new HashMap<>();

        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            CompoundTag nbt = buf.readNbt();
            this.storedFamiliars.put(id, nbt);
        }

        this.storeMode = buf.readBoolean(); // NUEVO: leer el modo
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeVarInt(storedFamiliars.size());

        for (Map.Entry<UUID, CompoundTag> entry : storedFamiliars.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeNbt(entry.getValue());
        }

        buf.writeBoolean(storeMode);
    }

    public static void handle(UpdateFamiliarStoragePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.handleStorageUpdate(packet.blockPos, packet.storedFamiliars, packet.storeMode);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
