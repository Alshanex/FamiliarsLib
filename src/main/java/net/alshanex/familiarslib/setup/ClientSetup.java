package net.alshanex.familiarslib.setup;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.particle.SleepingParticle;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FParticleRegistry.SLEEP_PARTICLE.get(), SleepingParticle.Provider::new);
    }
}
