package net.alshanex.familiarslib.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared adjustments for drawing familiars in screens. */
public final class FamiliarPreviewHelper {

    private static final float FILL = 0.85F;
    /** Constant tilt so previews are seen slightly from above. */
    public static final float TILT_DEGREES = 10F;

    /** Where a preview sits in its box. */
    public enum Anchor {
        /** Centered both ways. */
        CENTER,
        /** Centered horizontally, standing on the bottom edge of the box. */
        BOTTOM
    }

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

    /**
     * Draws a familiar in the box (x1, y1)-(x2, y2), slowly turning.
     */
    public static void renderInBox(GuiGraphics g, LivingEntity entity, int x1, int y1, int x2, int y2,
                                   float maxScale, float spin, Anchor anchor) {
        if (entity == null) return;

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        resetLook(entity);

        FamiliarPreviewBounds.Bounds bounds = FamiliarPreviewBounds.get(entity);
        if (bounds == null) {
            float halfDiagonal = Math.max(entity.getBbWidth(), 0.2F) * 0.7071F;
            bounds = new FamiliarPreviewBounds.Bounds(-halfDiagonal, 0F, halfDiagonal, Math.max(entity.getBbHeight(), 0.2F));
        }

        float boxW = x2 - x1;
        float boxH = y2 - y1;
        float scale = Math.min(maxScale, Math.min(
                boxH * FILL / Math.max(bounds.height(), 0.1F),
                boxW * FILL / Math.max(bounds.width(), 0.1F)));

        // Box point the drawing is pinned to, and the matching point of the drawing
        float pinY = anchor == Anchor.BOTTOM ? y2 : (y1 + y2) / 2F;
        float drawingY = anchor == Anchor.BOTTOM ? bounds.minY() : (bounds.minY() + bounds.maxY()) / 2F;
        float drawingX = (bounds.minX() + bounds.maxX()) / 2F;
        Vec3 renderOffset = dispatcher.getRenderer(entity).getRenderOffset(entity, 0F);

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((x1 + x2) / 2F, pinY, 100F);
        pose.scale(scale, -scale, scale); // y up from here on, same view space as the measure
        pose.translate(-drawingX, -drawingY, 0F);
        pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(TILT_DEGREES)));
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(45) + spin));
        // The dispatcher adds the renderer's offset (e.g. hovering familiars): cancel it
        pose.translate(-renderOffset.x, -renderOffset.y, -renderOffset.z);

        RenderSystem.setShaderLights(new Vector3f(0.2F, 1.0F, -1.0F), new Vector3f(-0.2F, -1.0F, 0.0F));
        renderWithoutShadow(g, dispatcher, entity, pose);
        pose.popPose();

        // Put the default item lights back so items drawn afterwards aren't lit wrong
        Lighting.setupFor3DItems();
    }

    /**
     * Faces the familiar straight ahead. Previews are built from saved data, which keeps the rotation the
     * familiar had in the world, so without this each one would face a different way.
     */
    public static void resetLook(LivingEntity entity) {
        entity.setYRot(0F);
        entity.yRotO = 0F;
        entity.yBodyRot = entity.yBodyRotO = 0F;
        entity.yHeadRot = entity.yHeadRotO = 0F;
        entity.setXRot(0F);
        entity.xRotO = 0F;
    }

    /**
     * Renders the entity at the pose's origin with its shadow turned off.
     */
    public static void renderWithoutShadow(GuiGraphics g, EntityRenderDispatcher dispatcher, LivingEntity entity, PoseStack pose) {
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(entity, 0, 0, 0, 0, 0, pose, g.bufferSource(), 15728880);
            g.flush();
        } catch (Exception ignored) {
            // A broken preview shouldn't take the whole screen down
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    private FamiliarPreviewHelper() {}
}