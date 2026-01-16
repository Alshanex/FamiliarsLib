package net.alshanex.familiarslib.setup;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.particle.SleepingParticle;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FParticleRegistry.SLEEP_PARTICLE.get(), SleepingParticle.Provider::new);
    }
}
