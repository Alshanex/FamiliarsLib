package net.alshanex.familiarslib.client.photo;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.client.BoneTracking;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.FamiliarPhoto;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.*;

/**
 * Turns photos into real images: the familiar (with its look, pose, hat and weapon) is rendered once into an
 * off-screen buffer, and the result is cropped to the pixels that were actually drawn. The cropped image is
 * what item frames show, so nothing can ever stick out of the picture.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class PhotoTextures {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Capture resolution (square). A 1 block familiar ends up roughly 100px tall. */
    private static final int CAPTURE_SIZE = 256;
    /** Three-quarter view, like most photos. */
    public static final float VIEW_ANGLE = 25F;
    /** Pixels more transparent than this don't count as part of the picture. */
    private static final int ALPHA_THRESHOLD = 16;
    /** Transparent border kept around the crop, in pixels. */
    private static final int PADDING = 2;
    /** Captures per frame, so a wall of new photos doesn't cause a hitch. */
    private static final int CAPTURES_PER_FRAME = 2;
    private static final int CACHE_SIZE = 64;
    /** Extra room around the familiar in the first, wide shot, in blocks. */
    private static final float LOCATE_MARGIN = 1.5F;
    /** The wide shot doubles in size each time it misses the familiar: 3 attempts cover up to 4x. */
    private static final int LOCATE_ATTEMPTS = 3;

    /**
     * A captured photo: the image (width/height in pixels) and measures of what was drawn, in blocks at real size
     * (familiar + hat + weapon). An empty one (nothing drawn) is kept too, so it isn't retried every frame.
     */
    public record PhotoTexture(@Nullable ResourceLocation id, int width, int height, float drawnWidth, float drawnHeight,
                               float drawnBottom, float centerX, float centerZ) {
        static final PhotoTexture EMPTY = new PhotoTexture(null, 0, 0, 0F, 0F, 0F, 0F, 0F);

        public boolean isEmpty() {
            return id == null;
        }
    }

    private static final Map<FamiliarPhoto, PhotoTexture> TEXTURES = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<FamiliarPhoto, PhotoTexture> eldest) {
            if (size() > CACHE_SIZE) {
                release(eldest.getValue());
                return true;
            }
            return false;
        }
    };
    private static final Set<FamiliarPhoto> PENDING = new LinkedHashSet<>();
    private static final ByteBufferBuilder BUFFER = new ByteBufferBuilder(1 << 20);
    private static int nextId = 0;

    private PhotoTextures() {}

    /** The image for this photo, or null if it's not ready yet (it will be on a following frame). */
    @Nullable
    public static PhotoTexture get(FamiliarPhoto photo) {
        PhotoTexture texture = TEXTURES.get(photo);
        if (texture == null) {
            PENDING.add(photo);
        }
        return texture;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (PENDING.isEmpty()) return;
        if (Minecraft.getInstance().level == null) {
            PENDING.clear();
            return;
        }

        Iterator<FamiliarPhoto> iterator = PENDING.iterator();
        for (int i = 0; i < CAPTURES_PER_FRAME && iterator.hasNext(); i++) {
            FamiliarPhoto photo = iterator.next();
            iterator.remove();
            if (!TEXTURES.containsKey(photo)) {
                TEXTURES.put(photo, capture(photo));
            }
        }
    }

    /**
     * Two shots: a wide one to find where the familiar is drawn (a jump or leap in the photo's pose can put the
     * model blocks away from its feet), then a tight one around it so it's captured at full detail.
     */
    private static PhotoTexture capture(FamiliarPhoto photo) {
        AbstractSpellCastingPet familiar = PhotoDisplays.displayFor(photo);
        if (familiar == null) return PhotoTexture.EMPTY;

        EntityDimensions dims = familiar.getType().getDimensions();
        try {
            float centerY = dims.height() / 2F;
            float halfView = Math.max(dims.width(), dims.height()) * 1.25F + LOCATE_MARGIN;
            float[] found = null;
            for (int attempt = 0; attempt < LOCATE_ATTEMPTS && found == null; attempt++) {
                NativeImage shot = renderShot(familiar, VIEW_ANGLE, 0F, centerY, halfView);
                int[] box = drawnBounds(shot);
                shot.close();
                boolean lastAttempt = attempt == LOCATE_ATTEMPTS - 1;
                if (box != null && (!touchesEdge(box) || lastAttempt)) {
                    found = toViewSpace(box, 0F, centerY, halfView);
                } else {
                    halfView *= 2F;
                }
            }
            if (found == null) return PhotoTexture.EMPTY;

            float slack = 4F * halfView / CAPTURE_SIZE;
            float frameX = (found[0] + found[2]) / 2F;
            float frameY = (found[1] + found[3]) / 2F;
            float frameHalf = Math.max(found[2] - found[0], found[3] - found[1]) / 2F + slack;

            BakedGeoModel baked = BoneTracking.bakedModelOf(familiar);
            List<GeoBone> tracked = baked == null ? List.of() : BoneTracking.enable(baked);
            NativeImage shot;
            float[] center;
            try {
                shot = renderShot(familiar, VIEW_ANGLE, frameX, frameY, frameHalf);
                center = posedCenter(baked);
            } finally {
                BoneTracking.restore(tracked);
            }
            int[] box = drawnBounds(shot);
            if (box == null) {
                shot.close();
                return PhotoTexture.EMPTY;
            }

            float blocksPerPixel = 2F * frameHalf / CAPTURE_SIZE;
            float drawnWidth = (box[2] - box[0] + 1) * blocksPerPixel;
            float drawnHeight = (box[3] - box[1] + 1) * blocksPerPixel;
            float drawnBottom = toViewSpace(box, frameX, frameY, frameHalf)[1];

            NativeImage cropped = crop(shot, box);
            shot.close();

            ResourceLocation id = FamiliarsLib.id("dynamic/familiar_photo_" + nextId++);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(cropped));
            return new PhotoTexture(id, cropped.getWidth(), cropped.getHeight(), drawnWidth, drawnHeight,
                    drawnBottom, center[0], center[1]);
        } catch (Exception e) {
            LOGGER.error("Could not capture the photo of {}", photo.entityType(), e);
        }
        return PhotoTexture.EMPTY;
    }

    /**
     * {x, z} center of the posed bones, relative to the familiar's position in its own facing space.
     * Bones are pivots, not geometry, which is precise enough to center it. {0, 0} if unknown.
     */
    private static float[] posedCenter(@Nullable BakedGeoModel baked) {
        if (baked == null) return new float[]{0F, 0F};
        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        BoneTracking.forEachBone(baked, bone -> {
            if (bone.isHidden() || !bone.isTrackingMatrices()) return;
            Vector3d pos = bone.getLocalPosition();
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.z)) return;
            bounds[0] = Math.min(bounds[0], pos.x);
            bounds[1] = Math.min(bounds[1], pos.z);
            bounds[2] = Math.max(bounds[2], pos.x);
            bounds[3] = Math.max(bounds[3], pos.z);
        });
        if (bounds[0] > bounds[2]) return new float[]{0F, 0F};
        return new float[]{(float) ((bounds[0] + bounds[2]) / 2D), (float) ((bounds[1] + bounds[3]) / 2D)};
    }

    private static boolean touchesEdge(int[] box) {
        return box[0] == 0 || box[1] == 0 || box[2] == CAPTURE_SIZE - 1 || box[3] == CAPTURE_SIZE - 1;
    }

    /** Pixel box of a shot -> {minX, minY, maxX, maxY} in blocks, in the shot's view space (Y up). */
    private static float[] toViewSpace(int[] box, float centerX, float centerY, float halfView) {
        float blocksPerPixel = 2F * halfView / CAPTURE_SIZE;
        float left = centerX - halfView;
        float top = centerY + halfView;
        return new float[]{
                left + box[0] * blocksPerPixel,
                top - (box[3] + 1) * blocksPerPixel,
                left + (box[2] + 1) * blocksPerPixel,
                top - box[1] * blocksPerPixel};
    }

    /** Renders the familiar facing the camera (slightly turned) into a transparent off-screen image. */
    private static NativeImage renderShot(AbstractSpellCastingPet familiar, float viewYaw, float centerX, float centerY, float halfView) {
        Minecraft minecraft = Minecraft.getInstance();
        TextureTarget target = new TextureTarget(CAPTURE_SIZE, CAPTURE_SIZE, true, Minecraft.ON_OSX);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        boolean matricesChanged = false;
        try {
            minecraft.renderBuffers().bufferSource().endBatch();

            target.setClearColor(0F, 0F, 0F, 0F);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(centerX - halfView, centerX + halfView,
                    centerY - halfView, centerY + halfView, -100F, 100F), VertexSorting.ORTHOGRAPHIC_Z);
            modelView.pushMatrix();
            matricesChanged = true;
            modelView.identity();
            RenderSystem.applyModelViewMatrix();

            Lighting.setupForEntityInInventory();
            FogRenderer.setupNoFog();
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

            PoseStack poseStack = new PoseStack();
            poseStack.mulPose(Axis.YP.rotationDegrees(viewYaw));
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(BUFFER);
            PhotoDisplays.render(familiar, 0F, poseStack, buffers, LightTexture.FULL_BRIGHT);
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

    private static NativeImage crop(NativeImage source, int[] box) {
        int minX = Math.max(0, box[0] - PADDING);
        int minY = Math.max(0, box[1] - PADDING);
        int maxX = Math.min(source.getWidth() - 1, box[2] + PADDING);
        int maxY = Math.min(source.getHeight() - 1, box[3] + PADDING);

        NativeImage cropped = new NativeImage(maxX - minX + 1, maxY - minY + 1, true);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                cropped.setPixelRGBA(x - minX, y - minY, source.getPixelRGBA(x, y));
            }
        }
        return cropped;
    }

    private static void release(PhotoTexture texture) {
        if (texture.id() != null) {
            Minecraft.getInstance().getTextureManager().release(texture.id());
        }
    }
}