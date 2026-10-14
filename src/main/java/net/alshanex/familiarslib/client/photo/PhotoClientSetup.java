package net.alshanex.familiarslib.client.photo;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.registry.FItemRegistry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Photos in hand draw their print (see FamiliarPhotoItemRenderer). */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class PhotoClientSetup {

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private FamiliarPhotoItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new FamiliarPhotoItemRenderer();
                }
                return renderer;
            }
        }, FItemRegistry.FAMILIAR_PHOTO.get());
    }
}
