package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class SelectFamiliarPacket {

    private final UUID familiarId;

    public SelectFamiliarPacket(UUID familiarId) {
        this.familiarId = familiarId;
    }

    public SelectFamiliarPacket(FriendlyByteBuf buf) {
        this.familiarId = buf.readUUID();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUUID(familiarId);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null) {
                FamiliarManager.handleFamiliarSelection(serverPlayer, familiarId);
            }
        });
        return true;
    }
}
