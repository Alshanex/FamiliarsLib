package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.network.RequestFamiliarSelectionPacket;
import net.alshanex.familiarslib.network.SummonPetPackage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.alshanex.familiarslib.event.KeyMappings.SCREEN_KEYMAP;
import static net.alshanex.familiarslib.event.KeyMappings.SUMMONING_KEYMAP;

@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        handleKeybinds();
    }

    private static void handleKeybinds() {
        while (SUMMONING_KEYMAP.consume()) {
            PacketDistributor.sendToServer(new SummonPetPackage());
        }
        while (SCREEN_KEYMAP.consume()) {
            PacketDistributor.sendToServer(new RequestFamiliarSelectionPacket());
        }
    }
}
