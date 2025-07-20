package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

public class MoveFamiliarPacket implements CustomPacketPayload {
    public static final Type<MoveFamiliarPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "move_familiar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoveFamiliarPacket> STREAM_CODEC =
            CustomPacketPayload.codec(MoveFamiliarPacket::write, MoveFamiliarPacket::new);

    private final BlockPos blockPos;
    private final UUID familiarId;
    private final boolean toStorage; // true = player -> storage, false = storage -> player

    public MoveFamiliarPacket(BlockPos blockPos, UUID familiarId, boolean toStorage) {
        this.blockPos = blockPos;
        this.familiarId = familiarId;
        this.toStorage = toStorage;
    }

    public MoveFamiliarPacket(FriendlyByteBuf buf) {
        this.blockPos = buf.readBlockPos();
        this.familiarId = buf.readUUID();
        this.toStorage = buf.readBoolean();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(blockPos);
        buf.writeUUID(familiarId);
        buf.writeBoolean(toStorage);
    }

    public static void handle(MoveFamiliarPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.handleMoveFamiliar(serverPlayer, packet.blockPos, packet.familiarId, packet.toStorage);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
