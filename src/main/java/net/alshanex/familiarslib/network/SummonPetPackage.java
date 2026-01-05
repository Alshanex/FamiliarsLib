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

public class SummonPetPackage implements CustomPacketPayload {
    public static final Type<SummonPetPackage> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "summon"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SummonPetPackage> STREAM_CODEC =
            CustomPacketPayload.codec(SummonPetPackage::write, SummonPetPackage::new);

    public SummonPetPackage() {}

    public SummonPetPackage(FriendlyByteBuf buf) {}

    public void write(FriendlyByteBuf buf) {}

    public static void handle(SummonPetPackage packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                FamiliarManager.handleFamiliarSummonPackage(serverPlayer);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
