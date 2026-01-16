package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class UpdateFamiliarStoragePacket {

    private final BlockPos blockPos;
    private final Map<UUID, CompoundTag> storedFamiliars;
    private final boolean storeMode;
    private final boolean canFamiliarsUseGoals;
    private final int maxDistance;

    public UpdateFamiliarStoragePacket(BlockPos blockPos, Map<UUID, CompoundTag> storedFamiliars, boolean storeMode, boolean canFamiliarsUseGoals, int maxDistance) {
        this.blockPos = blockPos;
        this.storedFamiliars = storedFamiliars;
        this.storeMode = storeMode;
        this.canFamiliarsUseGoals = canFamiliarsUseGoals;
        this.maxDistance = maxDistance;
    }

    // Backwards compatibility overload
    public UpdateFamiliarStoragePacket(BlockPos blockPos, Map<UUID, CompoundTag> storedFamiliars, boolean storeMode) {
        this(blockPos, storedFamiliars, storeMode, true, 25);
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

        this.storeMode = buf.readBoolean();
        this.canFamiliarsUseGoals = buf.readBoolean();
        this.maxDistance = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeVarInt(storedFamiliars.size());

        for (Map.Entry<UUID, CompoundTag> entry : storedFamiliars.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeNbt(entry.getValue());
        }

        buf.writeBoolean(storeMode);
        buf.writeBoolean(canFamiliarsUseGoals);
        buf.writeVarInt(maxDistance);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.handleStorageUpdate(blockPos, storedFamiliars, storeMode, canFamiliarsUseGoals, maxDistance);
        });
        return true;
    }
}