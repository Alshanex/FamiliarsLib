package net.alshanex.familiarslib.client.photo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.camera.FamiliarPhoto;
import net.alshanex.familiarslib.item.camera.FamiliarPhotoItem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;

import javax.annotation.Nullable;

/**
 * A photo in an item frame is shown as a printed picture: the paper print (photo_print.png) with the captured
 * image of the familiar (see PhotoTextures) fitted inside the picture area. Blank photos render as a normal item.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class PhotoFrameRenderer {
    private static final ResourceLocation PRINT = FamiliarsLib.id("textures/block/photo_print.png");

    /** Print size in blocks: fits inside the frame's border, bigger when the frame is invisible. */
    public static final float SIZE = 0.625F;
    private static final float SIZE_INVISIBLE = 0.875F;

    // The picture area inside the white border, in pixels of the 64x64 print texture
    private static final float TEXTURE_SIZE = 64F;
    private static final float INNER_LEFT = 4F, INNER_RIGHT = 60F, INNER_TOP = 4F, INNER_BOTTOM = 52F;

    /** How much of the picture area the familiar can take, so it never touches the border. */
    private static final float FILL = 0.92F;

    private static final float PRINT_Z = -0.008F;
    private static final float IMAGE_Z = PRINT_Z - 0.001F;

    private PhotoFrameRenderer() {}

    @SubscribeEvent
    public static void onRenderItemInFrame(RenderItemInFrameEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof FamiliarPhotoItem)) return;
        FamiliarPhoto photo = stack.get(ComponentRegistry.FAMILIAR_PHOTO.get());
        if (photo == null) return;

        event.setCanceled(true);

        ItemFrame frame = event.getItemFrameEntity();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float size = frame.isInvisible() ? SIZE_INVISIBLE : SIZE;

        pose.pushPose();
        // Frames turn items in 45 degree steps; a photo turns in 90 degree steps, like a map
        int rotation = frame.getRotation();
        pose.mulPose(Axis.ZP.rotationDegrees(-rotation * 45F + (rotation % 4) * 90F));

        drawPhoto(photo, pose, buffer, light, size);
        pose.popPose();
    }

    /**
     * Draws a photo as item frames show it: the paper print with the captured image in its picture area
     * (just the paper for a blank photo).
     */
    public static void drawPhoto(@Nullable FamiliarPhoto photo, PoseStack pose, MultiBufferSource buffer, int light, float size) {
        float h = size / 2F;
        quad(buffer.getBuffer(RenderType.entityCutoutNoCull(PRINT)), pose.last(), -h, -h, h, h, PRINT_Z, light);
        if (photo != null) {
            drawImage(photo, pose, buffer, light, size);
        }
    }

    /** The captured image, as big as it fits in the picture area, standing on its bottom edge. */
    private static void drawImage(FamiliarPhoto photo, PoseStack pose, MultiBufferSource buffer, int light, float size) {
        PhotoTextures.PhotoTexture texture = PhotoTextures.get(photo);
        if (texture == null || texture.isEmpty()) return; // being captured: shows up next frame

        float pixel = size / TEXTURE_SIZE;
        float areaWidth = (INNER_RIGHT - INNER_LEFT) * pixel;
        float areaHeight = (INNER_BOTTOM - INNER_TOP) * pixel;
        float areaCenterX = -size / 2F + (INNER_LEFT + INNER_RIGHT) / 2F * pixel;
        float areaBottom = size / 2F - INNER_BOTTOM * pixel;

        float aspect = texture.width() / (float) texture.height();
        float width = areaWidth * FILL;
        float height = width / aspect;
        if (height > areaHeight * FILL) {
            height = areaHeight * FILL;
            width = height * aspect;
        }

        float margin = areaHeight * (1F - FILL) / 2F;
        float left = areaCenterX - width / 2F;
        float bottom = areaBottom + margin;
        quad(buffer.getBuffer(RenderType.entityCutoutNoCull(texture.id())), pose.last(),
                left, bottom, left + width, bottom + height, IMAGE_Z, light);
    }

    /**
     * A flat textured rectangle facing the viewer, the whole texture stretched over it. Seen from -Z, +X is the
     * viewer's left (as in vanilla's frame space, where maps also start at +X), so the texture's left edge goes there.
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose last, float x0, float y0, float x1, float y1, float z, int light) {
        vertex(consumer, last, x1, y1, z, 0F, 0F, light);
        vertex(consumer, last, x1, y0, z, 0F, 1F, light);
        vertex(consumer, last, x0, y0, z, 1F, 1F, light);
        vertex(consumer, last, x0, y1, z, 1F, 0F, light);
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose last, float x, float y, float z, float u, float v, int light) {
        consumer.addVertex(last, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(last, 0F, 0F, -1F);
    }
}