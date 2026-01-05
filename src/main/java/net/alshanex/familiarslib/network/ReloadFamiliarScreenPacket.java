package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ReloadFamiliarScreenPacket implements CustomPacketPayload {
    public static final Type<ReloadFamiliarScreenPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "reload_familiar_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReloadFamiliarScreenPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ReloadFamiliarScreenPacket::write, ReloadFamiliarScreenPacket::new);

    private final boolean shouldClose;

    public ReloadFamiliarScreenPacket(boolean shouldClose) {
        this.shouldClose = shouldClose;
    }

    public ReloadFamiliarScreenPacket(FriendlyByteBuf buf) {
        this.shouldClose = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(shouldClose);
    }

    public static void handle(ReloadFamiliarScreenPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            FamiliarManager.handleFamiliarSelectionScreenUpdate(packet.shouldClose);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
