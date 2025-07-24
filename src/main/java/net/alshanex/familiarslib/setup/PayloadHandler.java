package net.alshanex.familiarslib.setup;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.network.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = FamiliarsLib.MODID)
public class PayloadHandler {
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar payloadRegistrar = event.registrar(FamiliarsLib.MODID).versioned("1.0.0").optional();

        payloadRegistrar.playToServer(LinkFamiliarToBedPacket.TYPE, LinkFamiliarToBedPacket.STREAM_CODEC, LinkFamiliarToBedPacket::handle);
        payloadRegistrar.playToServer(MoveFamiliarPacket.TYPE, MoveFamiliarPacket.STREAM_CODEC, MoveFamiliarPacket::handle);
        payloadRegistrar.playToServer(SetStorageModePacket.TYPE, SetStorageModePacket.STREAM_CODEC, SetStorageModePacket::handle);
        payloadRegistrar.playToServer(UpdateStorageSettingsPacket.TYPE, UpdateStorageSettingsPacket.STREAM_CODEC, UpdateStorageSettingsPacket::handle);

        payloadRegistrar.playToClient(FamiliarDataPacket.TYPE, FamiliarDataPacket.STREAM_CODEC, FamiliarDataPacket::handle);
        payloadRegistrar.playToClient(SyncFamiliarDataPacket.TYPE, SyncFamiliarDataPacket.STREAM_CODEC, SyncFamiliarDataPacket::handle);
        payloadRegistrar.playToClient(OpenBedLinkSelectionPacket.TYPE, OpenBedLinkSelectionPacket.STREAM_CODEC, OpenBedLinkSelectionPacket::handle);
        payloadRegistrar.playToClient(SyncBedLinkDataPacket.TYPE, SyncBedLinkDataPacket.STREAM_CODEC, SyncBedLinkDataPacket::handle);
        payloadRegistrar.playToClient(UpdateFamiliarStoragePacket.TYPE, UpdateFamiliarStoragePacket.STREAM_CODEC, UpdateFamiliarStoragePacket::handle);
        payloadRegistrar.playToClient(OpenFamiliarStoragePacket.TYPE, OpenFamiliarStoragePacket.STREAM_CODEC, OpenFamiliarStoragePacket::handle);
    }
}
