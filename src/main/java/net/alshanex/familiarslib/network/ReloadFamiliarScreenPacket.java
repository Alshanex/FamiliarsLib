package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ReloadFamiliarScreenPacket {

    private final boolean shouldClose;

    public ReloadFamiliarScreenPacket(boolean shouldClose) {
        this.shouldClose = shouldClose;
    }

    public ReloadFamiliarScreenPacket(FriendlyByteBuf buf) {
        this.shouldClose = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBoolean(shouldClose);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.handleFamiliarSelectionScreenUpdate(shouldClose);
        });
        return true;
    }
}
