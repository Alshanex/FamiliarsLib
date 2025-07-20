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

public class LinkFamiliarToBedPacket implements CustomPacketPayload {
    public static final Type<LinkFamiliarToBedPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "link_familiar_to_bed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkFamiliarToBedPacket> STREAM_CODEC =
            CustomPacketPayload.codec(LinkFamiliarToBedPacket::write, LinkFamiliarToBedPacket::new);

    private final BlockPos bedPos;
    private final UUID familiarId; // null means unlink

    public LinkFamiliarToBedPacket(BlockPos bedPos, UUID familiarId) {
        this.bedPos = bedPos;
        this.familiarId = familiarId;
    }

    public LinkFamiliarToBedPacket(FriendlyByteBuf buf) {
        this.bedPos = buf.readBlockPos();
        this.familiarId = buf.readBoolean() ? buf.readUUID() : null;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(bedPos);
        buf.writeBoolean(familiarId != null);
        if (familiarId != null) {
            buf.writeUUID(familiarId);
        }
    }

    public static void handle(LinkFamiliarToBedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.linkFamiliarToBed(serverPlayer, packet.bedPos, packet.familiarId);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

