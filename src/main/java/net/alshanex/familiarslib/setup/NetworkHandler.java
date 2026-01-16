package net.alshanex.familiarslib.setup;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {

    private static SimpleChannel INSTANCE;

    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(FamiliarsLib.MODID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        // Server-bound packets (Client -> Server)
        net.messageBuilder(SummonPetPackage.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SummonPetPackage::new)
                .encoder(SummonPetPackage::toBytes)
                .consumerMainThread(SummonPetPackage::handle)
                .add();

        net.messageBuilder(RequestFamiliarSelectionPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(RequestFamiliarSelectionPacket::new)
                .encoder(RequestFamiliarSelectionPacket::toBytes)
                .consumerMainThread(RequestFamiliarSelectionPacket::handle)
                .add();

        net.messageBuilder(SelectFamiliarPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SelectFamiliarPacket::new)
                .encoder(SelectFamiliarPacket::toBytes)
                .consumerMainThread(SelectFamiliarPacket::handle)
                .add();

        net.messageBuilder(UpdateMultiSelectionCurioPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateMultiSelectionCurioPacket::new)
                .encoder(UpdateMultiSelectionCurioPacket::toBytes)
                .consumerMainThread(UpdateMultiSelectionCurioPacket::handle)
                .add();

        net.messageBuilder(ReleaseFamiliarPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ReleaseFamiliarPacket::new)
                .encoder(ReleaseFamiliarPacket::toBytes)
                .consumerMainThread(ReleaseFamiliarPacket::handle)
                .add();

        net.messageBuilder(MoveFamiliarPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(MoveFamiliarPacket::new)
                .encoder(MoveFamiliarPacket::toBytes)
                .consumerMainThread(MoveFamiliarPacket::handle)
                .add();

        net.messageBuilder(SetStorageModePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetStorageModePacket::new)
                .encoder(SetStorageModePacket::toBytes)
                .consumerMainThread(SetStorageModePacket::handle)
                .add();

        net.messageBuilder(UpdateStorageSettingsPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(UpdateStorageSettingsPacket::new)
                .encoder(UpdateStorageSettingsPacket::toBytes)
                .consumerMainThread(UpdateStorageSettingsPacket::handle)
                .add();

        // Client-bound packets (Server -> Client)
        net.messageBuilder(FamiliarDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FamiliarDataPacket::new)
                .encoder(FamiliarDataPacket::toBytes)
                .consumerMainThread(FamiliarDataPacket::handle)
                .add();

        net.messageBuilder(SyncFamiliarDataPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(SyncFamiliarDataPacket::new)
                .encoder(SyncFamiliarDataPacket::toBytes)
                .consumerMainThread(SyncFamiliarDataPacket::handle)
                .add();

        net.messageBuilder(UpdateFamiliarStoragePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(UpdateFamiliarStoragePacket::new)
                .encoder(UpdateFamiliarStoragePacket::toBytes)
                .consumerMainThread(UpdateFamiliarStoragePacket::handle)
                .add();

        net.messageBuilder(OpenFamiliarStoragePacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenFamiliarStoragePacket::new)
                .encoder(OpenFamiliarStoragePacket::toBytes)
                .consumerMainThread(OpenFamiliarStoragePacket::handle)
                .add();

        net.messageBuilder(OpenFamiliarSelectionPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenFamiliarSelectionPacket::new)
                .encoder(OpenFamiliarSelectionPacket::toBytes)
                .consumerMainThread(OpenFamiliarSelectionPacket::handle)
                .add();

        net.messageBuilder(FamiliarDeathPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(FamiliarDeathPacket::new)
                .encoder(FamiliarDeathPacket::toBytes)
                .consumerMainThread(FamiliarDeathPacket::handle)
                .add();

        net.messageBuilder(OpenMultiSelectionScreenPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(OpenMultiSelectionScreenPacket::new)
                .encoder(OpenMultiSelectionScreenPacket::toBytes)
                .consumerMainThread(OpenMultiSelectionScreenPacket::handle)
                .add();

        net.messageBuilder(ReloadFamiliarScreenPacket.class, id(), NetworkDirection.PLAY_TO_CLIENT)
                .decoder(ReloadFamiliarScreenPacket::new)
                .encoder(ReloadFamiliarScreenPacket::toBytes)
                .consumerMainThread(ReloadFamiliarScreenPacket::handle)
                .add();
    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <MSG> void sendToAllPlayers(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }

    public static <MSG> void sendToPlayersTrackingEntity(MSG message, Entity entity) {
        INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }

    public static <MSG> void sendToPlayersTrackingEntityAndSelf(MSG message, Entity entity) {
        sendToPlayersTrackingEntity(message, entity);
        if (entity instanceof ServerPlayer serverPlayer) {
            sendToPlayer(message, serverPlayer);
        }
    }
}
