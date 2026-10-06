package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.client.compendium.ClientCompendium;
import net.alshanex.familiarslib.compendium.CompendiumCatalog;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the full compendium catalog. */
public record CompendiumCatalogPacket(CompendiumCatalog catalog) implements CustomPacketPayload {
    public static final Type<CompendiumCatalogPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "compendium_catalog"));

    public static final StreamCodec<FriendlyByteBuf, CompendiumCatalogPacket> STREAM_CODEC =
            CompendiumCatalog.STREAM_CODEC.map(CompendiumCatalogPacket::new, CompendiumCatalogPacket::catalog);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CompendiumCatalogPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientCompendium.setCatalog(packet.catalog()));
    }
}
