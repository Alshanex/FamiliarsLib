package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.UUID;

public class FamiliarDeathPacket implements CustomPacketPayload {
    public static final Type<FamiliarDeathPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_death"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarDeathPacket> STREAM_CODEC =
            CustomPacketPayload.codec(FamiliarDeathPacket::write, FamiliarDeathPacket::new);

    private final UUID deadFamiliarId;
    public FamiliarDeathPacket(UUID deadFamiliarId) {
        this.deadFamiliarId = deadFamiliarId;
    }

    public FamiliarDeathPacket(FriendlyByteBuf buf) {
        this.deadFamiliarId = buf.readUUID();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(deadFamiliarId);
    }

    public static void handle(FamiliarDeathPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> FamiliarManager.handleFamiliarDeathPacket(packet.deadFamiliarId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
