package net.alshanex.familiarslib.client;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import software.bernie.geckolib.event.GeoRenderEvent;

@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class FamiliarPoseOffsetHandler {

    @SubscribeEvent
    public static void onFamiliarPreRender(GeoRenderEvent.Entity.Pre event) {
        if (event.getEntity() instanceof AbstractSpellCastingPet pet) {
            float y = pet.getPoseOffsetY();
            if (y != 0F) {
                event.getPoseStack().translate(0F, y, 0F);
            }
        }
    }

    private FamiliarPoseOffsetHandler() {}
}