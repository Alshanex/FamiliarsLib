package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncFamiliarDataPacket {

    private final CompoundTag familiarData;

    public SyncFamiliarDataPacket(CompoundTag familiarData) {
        this.familiarData = familiarData;
    }

    public SyncFamiliarDataPacket(FriendlyByteBuf buf) {
        this.familiarData = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(familiarData);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.syncFamiliarData(familiarData);
        });
        return true;
    }
}
