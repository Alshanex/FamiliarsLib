package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SummonPetPackage {

    public SummonPetPackage() {}

    public SummonPetPackage(FriendlyByteBuf buf) {}

    public void toBytes(FriendlyByteBuf buf) {}

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer serverPlayer = ctx.getSender();
            if (serverPlayer != null) {
                FamiliarManager.handleFamiliarSummonPackage(serverPlayer);
            }
        });
        return true;
    }
}
