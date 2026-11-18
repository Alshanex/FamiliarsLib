package net.alshanex.familiarslib.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Measures what a familiar actually draws in a screen preview, so previews can be fitted to their box by the real picture instead of the hitbox.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class FamiliarPreviewBounds {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int CAPTURE_SIZE = 128;
    private static final int ALPHA_THRESHOLD = 16;
    /** Turn angles sampled: every 45 degrees covers the whole spin. */
    private static final int ANGLES = 8;
    private static final int MEASURES_PER_FRAME = 2;
    private static final float LOCATE_MARGIN = 1.5F;
    /** The view doubles each time the familiar touches its edge: 3 attempts cover up to 4x. */
    private static final int LOCATE_ATTEMPTS = 3;

    /**
     * What's drawn, in blocks, in the preview's view space (Y up), relative to the familiar's feet, for every
     * angle of the spin together.
     */
    public record Bounds(float minX, float minY, float maxX, float maxY) {
        public float width() { return maxX - minX; }
        public float height() { return maxY - minY; }
    }

    private static final Bounds EMPTY = new Bounds(0F, 0F, 0F, 0F);

    // Keyed by the preview entity itself: screens build their own copies, and they're dropped with the screen
    private static final Map<LivingEntity, Bounds> CACHE = new WeakHashMap<>();
    private static final Set<LivingEntity> PENDING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(1 << 20);

    private FamiliarPreviewBounds() {}

    /** The measured bounds, or null if they aren't ready yet (or nothing could be measured). */
    @Nullable
    public static Bounds get(LivingEntity entity) {
        Bounds bounds = CACHE.get(entity);
        if (bounds == null) {
            PENDING.add(entity);
            return null;
        }
        return bounds == EMPTY ? null : bounds;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (PENDING.isEmpty()) return;
        if (Minecraft.getInstance().level == null) {
            PENDING.clear();
            return;
        }

        Iterator<LivingEntity> iterator = PENDING.iterator();
        for (int i = 0; i < MEASURES_PER_FRAME && iterator.hasNext(); i++) {
            LivingEntity entity = iterator.next();
            iterator.remove();
            if (!CACHE.containsKey(entity)) {
                CACHE.put(entity, measure(entity));
            }
        }
    }

    private static Bounds measure(LivingEntity entity) {
        try {
            FamiliarPreviewHelper.resetLook(entity);
            float height = Math.max(entity.getBbHeight(), 0.2F);
            float width = Math.max(entity.getBbWidth(), 0.2F);
            float centerY = height / 2F;
            float halfView = Math.max(width, height) * 1.25F + LOCATE_MARGIN;

            for (int attempt = 0; attempt < LOCATE_ATTEMPTS; attempt++) {
                boolean lastAttempt = attempt == LOCATE_ATTEMPTS - 1;
                float[] union = null;
                boolean touched = false;
                for (int a = 0; a < ANGLES; a++) {
                    float spin = (float) (2 * Math.PI * a / ANGLES);
                    NativeImage shot = renderShot(entity, spin, centerY, halfView);
                    int[] box = drawnBounds(shot);
                    shot.close();
                    if (box == null) continue;
                    if (touchesEdge(box)) touched = true;
                    float[] view = toViewSpace(box, centerY, halfView);
                    union = union == null ? view : new float[]{
                            Math.min(union[0], view[0]), Math.min(union[1], view[1]),
                            Math.max(union[2], view[2]), Math.max(union[3], view[3])};
                }
                if (union == null) return EMPTY;
                if (!touched || lastAttempt) {
                    return new Bounds(union[0], union[1], union[2], union[3]);
                }
                halfView *= 2F;
            }
        } catch (Exception e) {
            LOGGER.error("Could not measure the preview of {}", entity.getType(), e);
        }
        return EMPTY;
    }

    /** Renders the familiar exactly as {@link FamiliarPreviewHelper#renderInBox} does, into a transparent image. */
    private static NativeImage renderShot(LivingEntity entity, float spin, float centerY, float halfView) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureTarget target = new TextureTarget(CAPTURE_SIZE, CAPTURE_SIZE, true, Minecraft.ON_OSX);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        boolean matricesChanged = false;
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        try {
            minecraft.renderBuffers().bufferSource().endBatch();

            target.setClearColor(0F, 0F, 0F, 0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-halfView, halfView,
                    centerY - halfView, centerY + halfView, -100F, 100F), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.pushMatrix();
            matricesChanged = true;
            modelView.identity();
            RenderSystem.applyModelViewMatrix();

            Lighting.setupForEntityInInventory();
            FogRenderer.setupNoFog();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

            Vec3 renderOffset = dispatcher.getRenderer(entity).getRenderOffset(entity, 0F);
            PoseStack pose = new PoseStack();
            pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(FamiliarPreviewHelper.TILT_DEGREES)));
            pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(45) + spin));
            pose.translate(-renderOffset.x, -renderOffset.y, -renderOffset.z);

            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(BUFFER);
            dispatcher.setRenderShadow(false);
            try {
                dispatcher.render(entity, 0, 0, 0, 0, 0, pose, buffers, LightTexture.FULL_BRIGHT);
            } finally {
                dispatcher.setRenderShadow(true);
            }
            buffers.endBatch();
            minecraft.renderBuffers().bufferSource().endBatch();

            NativeImage image = new NativeImage(CAPTURE_SIZE, CAPTURE_SIZE, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, false);
            image.flipY();
            return image;
        } finally {
            if (matricesChanged) {
                modelView.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.restoreProjectionMatrix();
            }
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }

    private static boolean touchesEdge(int[] box) {
        return box[0] == 0 || box[1] == 0 || box[2] == CAPTURE_SIZE - 1 || box[3] == CAPTURE_SIZE - 1;
    }

    /** Pixel box -> {minX, minY, maxX, maxY} in blocks, in view space (Y up). */
    private static float[] toViewSpace(int[] box, float centerY, float halfView) {
        float blocksPerPixel = 2F * halfView / CAPTURE_SIZE;
        float left = -halfView;
        float top = centerY + halfView;
        return new float[]{
                left + box[0] * blocksPerPixel,
                top - (box[3] + 1) * blocksPerPixel,
                left + (box[2] + 1) * blocksPerPixel,
                top - box[1] * blocksPerPixel};
    }

    /** {minX, minY, maxX, maxY} of the drawn pixels, or null if nothing was drawn. */
    @Nullable
    private static int[] drawnBounds(NativeImage image) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getPixelRGBA(x, y) >>> 24) & 0xFF;
                if (alpha > ALPHA_THRESHOLD) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        return maxX < 0 ? null : new int[]{minX, minY, maxX, maxY};
    }
}