package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public class CapabilityRegistry {
    public static final Capability<PlayerFamiliarData> PLAYER_FAMILIAR_DATA =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerFamiliarData.class);
    }
}
