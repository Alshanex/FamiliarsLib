package net.alshanex.familiarslib.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared adjustments for drawing familiars in screens (selection, storage, multi-selection). */
public final class FamiliarPreviewHelper {

    /**
     * Applies the familiar's preview adjustments.
     */
    public static void applyPreviewAdjustments(PoseStack poseStack, LivingEntity entity) {
        Vec3 renderOffset = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).getRenderOffset(entity, 0F);
        float x = 0F;
        float y = (float) -renderOffset.y;
        float scale = 1F;
        if (entity instanceof AbstractSpellCastingPet familiar) {
            x += familiar.getCompendiumPreviewOffsetX();
            y += familiar.getCompendiumPreviewOffsetY();
            scale = familiar.getCompendiumPreviewScale();
        }
        poseStack.translate(x, y, 0F);
        if (scale != 1F) {
            poseStack.scale(scale, scale, scale);
        }
    }

    private FamiliarPreviewHelper() {}
}
