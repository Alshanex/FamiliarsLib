package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class FamiliarDeathPacket {

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

        int size = buf.readVarInt();
        this.remainingFamiliars = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            CompoundTag nbt = buf.readNbt();
            this.remainingFamiliars.put(id, nbt);
        }

        this.newSelectedFamiliarId = buf.readBoolean() ? buf.readUUID() : null;
        this.currentSummonedFamiliarId = buf.readBoolean() ? buf.readUUID() : null;
        this.familiarData = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(deadFamiliarId);

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

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.handleFamiliarDeathPacket(deadFamiliarId, remainingFamiliars, newSelectedFamiliarId,
                    currentSummonedFamiliarId, familiarData);
        });
        return true;
    }
}
