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

import java.util.UUID;

public class SelectFamiliarPacket implements CustomPacketPayload {
    public static final Type<SelectFamiliarPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "select_familiar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectFamiliarPacket> STREAM_CODEC =
            CustomPacketPayload.codec(SelectFamiliarPacket::write, SelectFamiliarPacket::new);

    private final UUID familiarId;

    public SelectFamiliarPacket(UUID familiarId) {
        this.familiarId = familiarId;
    }

    public SelectFamiliarPacket(FriendlyByteBuf buf) {
        this.familiarId = buf.readUUID();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(familiarId);
    }

    public static void handle(SelectFamiliarPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.handleFamiliarSelection(serverPlayer, packet.familiarId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
