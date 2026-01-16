package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SetStorageModePacket {

    private final BlockPos blockPos;
    private final boolean storeMode;

    public SetStorageModePacket(BlockPos blockPos, boolean storeMode) {
        this.blockPos = blockPos;
        this.storeMode = storeMode;
    }

    public SetStorageModePacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.storeMode = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeBoolean(storeMode);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null) {
                FamiliarManager.handleSetStorageMode(serverPlayer, blockPos, storeMode);
            }
        });
        return true;
    }
}
