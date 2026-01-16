package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenMultiSelectionScreenPacket {

    public OpenMultiSelectionScreenPacket() {}

    public OpenMultiSelectionScreenPacket(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.openMultiSelectionScreen();
        });
        return true;
    }
}
