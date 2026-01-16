package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class MoveFamiliarPacket {

    private final BlockPos blockPos;
    private final UUID familiarId;
    private final boolean toStorage; // true = player -> storage, false = storage -> player

    public MoveFamiliarPacket(BlockPos blockPos, UUID familiarId, boolean toStorage) {
        this.blockPos = blockPos;
        this.familiarId = familiarId;
        this.toStorage = toStorage;
    }

    public MoveFamiliarPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.familiarId = buf.readUUID();
        this.toStorage = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeUUID(familiarId);
        buf.writeBoolean(toStorage);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null) {
                FamiliarManager.handleMoveFamiliar(serverPlayer, blockPos, familiarId, toStorage);
            }
        });
        return true;
    }
}
