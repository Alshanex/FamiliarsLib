package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class QuickSummonPacket implements CustomPacketPayload {
    public static final Type<QuickSummonPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "quick_summon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickSummonPacket> STREAM_CODEC =
            CustomPacketPayload.codec(QuickSummonPacket::write, QuickSummonPacket::new);

    private final int familiarIndex;

    public QuickSummonPacket(int familiarIndex) {
        this.familiarIndex = familiarIndex;
    }

    public QuickSummonPacket(FriendlyByteBuf buf) {
        this.familiarIndex = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(familiarIndex);
    }

    public static void handle(QuickSummonPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.handleQuickSummon(serverPlayer, packet.familiarIndex);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
