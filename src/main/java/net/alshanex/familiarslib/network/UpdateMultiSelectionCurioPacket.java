package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.CurioUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class UpdateMultiSelectionCurioPacket {

    private final Set<UUID> selectedFamiliars;

    public UpdateMultiSelectionCurioPacket(Set<UUID> selectedFamiliars) {
        this.selectedFamiliars = selectedFamiliars;
    }

    public UpdateMultiSelectionCurioPacket(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        this.selectedFamiliars = new HashSet<>();

        for (int i = 0; i < size; i++) {
            this.selectedFamiliars.add(buf.readUUID());
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeVarInt(selectedFamiliars.size());

        for (UUID uuid : selectedFamiliars) {
            buf.writeUUID(uuid);
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null) {
                CurioUtils.updateMultiSelectionCurio(serverPlayer, selectedFamiliars);
            }
        });
        return true;
    }
}
