package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class UpdateStorageSettingsPacket {

    private final BlockPos blockPos;
    private final boolean canFamiliarsUseGoals;
    private final int maxDistance;

    public UpdateStorageSettingsPacket(BlockPos blockPos, boolean canFamiliarsUseGoals, int maxDistance) {
        this.blockPos = blockPos;
        this.canFamiliarsUseGoals = canFamiliarsUseGoals;
        this.maxDistance = maxDistance;
    }

    public UpdateStorageSettingsPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.canFamiliarsUseGoals = buf.readBoolean();
        this.maxDistance = buf.readVarInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeBoolean(canFamiliarsUseGoals);
        buf.writeVarInt(maxDistance);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null) {
                FamiliarManager.handleUpdateStorageSettings(serverPlayer, blockPos, canFamiliarsUseGoals, maxDistance);
            }
        });
        return true;
    }
}