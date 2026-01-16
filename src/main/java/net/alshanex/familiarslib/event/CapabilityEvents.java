package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.capabilities.PlayerFamiliarProvider;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.registry.CapabilityRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID)
public class CapabilityEvents {

    private static final ResourceLocation FAMILIAR_DATA_ID =
            new ResourceLocation(FamiliarsLib.MODID, "player_familiar_data");

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).isPresent()) {
                event.addCapability(FAMILIAR_DATA_ID, new PlayerFamiliarProvider());
            }
        }
    }

    // Persist data on death (respawn) and dimension change
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(newData -> {
                newData.copyFrom(oldData);
            });
        });
        event.getOriginal().invalidateCaps();
    }
}
