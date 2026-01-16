package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenFamiliarStoragePacket {

    private final BlockPos blockPos;

    public OpenFamiliarStoragePacket(BlockPos blockPos) {
        this.blockPos = blockPos;
    }

    public OpenFamiliarStoragePacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            FamiliarManager.openStorageScreen(blockPos);
        });
        return true;
    }
}
