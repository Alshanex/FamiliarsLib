package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarRecall;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** Client -> server: recall this familiar with the held Recall Shard. */
public class ServerboundRecallFamiliarPacket implements CustomPacketPayload {
    public static final Type<ServerboundRecallFamiliarPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "recall_familiar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundRecallFamiliarPacket> STREAM_CODEC =
            CustomPacketPayload.codec(ServerboundRecallFamiliarPacket::write, ServerboundRecallFamiliarPacket::new);

    private final UUID familiarId;

    public ServerboundRecallFamiliarPacket(UUID familiarId) {
        this.familiarId = familiarId;
    }

    public ServerboundRecallFamiliarPacket(FriendlyByteBuf buf) {
        this.familiarId = buf.readUUID();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(familiarId);
    }

    public static void handle(ServerboundRecallFamiliarPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarRecall.recall(serverPlayer, packet.familiarId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}