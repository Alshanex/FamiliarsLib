package net.alshanex.familiarslib.setup;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.FamiliarEggItem;
import net.alshanex.familiarslib.particle.SleepingParticle;
import net.alshanex.familiarslib.registry.FBlockEntityRegistry;
import net.alshanex.familiarslib.registry.FBlockRegistry;
import net.alshanex.familiarslib.registry.FItemRegistry;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.alshanex.familiarslib.render.FamiliarEggBedRenderer;
import net.alshanex.familiarslib.render.ShrinkingStationBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(FParticleRegistry.SLEEP_PARTICLE.get(), SleepingParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(FBlockEntityRegistry.SHRINKING_STATION.get(), ShrinkingStationBlockEntityRenderer::new);
        ItemBlockRenderTypes.setRenderLayer(FBlockRegistry.SHRINKING_STATION.get(), RenderType.cutout());

        event.registerBlockEntityRenderer(FBlockEntityRegistry.PET_BED.get(), FamiliarEggBedRenderer::new);
    }

    /** The egg's models aren't tied to any block, so they have to be loaded by hand. */
    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation model : FamiliarEggBedRenderer.STAGES) {
            event.register(model);
        }
    }

    /** Tints the egg item with its familiar's color (the model's faces use tintindex 0). */
    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintIndex == 0
                        ? 0xFF000000 | FamiliarEggItem.eggColor(stack, Minecraft.getInstance().level) : -1,
                FItemRegistry.FAMILIAR_EGG.get());
    }
}