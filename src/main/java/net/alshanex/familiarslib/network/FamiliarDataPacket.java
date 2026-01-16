package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

public class FamiliarDataPacket {

    private final Map<UUID, CompoundTag> familiars;
    private final UUID selectedFamiliarId;
    private final UUID currentSummonedFamiliarId;
    private final Set<UUID> summonedFamiliarIds;

    public FamiliarDataPacket(Map<UUID, CompoundTag> familiars, UUID selectedFamiliarId, UUID currentSummonedFamiliarId, Set<UUID> summonedFamiliarIds) {
        this.familiars = familiars;
        this.selectedFamiliarId = selectedFamiliarId;
        this.currentSummonedFamiliarId = currentSummonedFamiliarId;
        this.summonedFamiliarIds = summonedFamiliarIds != null ? summonedFamiliarIds : new HashSet<>();
    }

    // Backwards compatibility constructor
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

        int summonedSize = buf.readVarInt();
        this.summonedFamiliarIds = new HashSet<>();
        for (int i = 0; i < summonedSize; i++) {
            this.summonedFamiliarIds.add(buf.readUUID());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.handleFamiliarDataPacket(familiars, selectedFamiliarId, currentSummonedFamiliarId, summonedFamiliarIds);
        });
        return true;
    }
}