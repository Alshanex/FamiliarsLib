package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.client.compendium.ClientCompendium;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** Server -> client: every look this player has unlocked. */
public record CompendiumDiscoveriesPacket(List<String> keys) implements CustomPacketPayload {
    public static final Type<CompendiumDiscoveriesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "compendium_discoveries"));

    public static final StreamCodec<FriendlyByteBuf, CompendiumDiscoveriesPacket> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                    .map(CompendiumDiscoveriesPacket::new, CompendiumDiscoveriesPacket::keys)
                    .cast();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompendiumDiscoveriesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCompendium.setDiscoveries(packet.keys()));
    }
}
