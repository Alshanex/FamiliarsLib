package net.alshanex.familiarslib.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.texture.AutoGlowingTexture;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.ClientUtil;

/**
 * Renders one colorable part of a familiar on top of its base texture.
 */
public class FamiliarColorLayer<T extends Entity & GeoAnimatable & LayerColorHolder> extends GeoRenderLayer<T> {
    private final int slot;
    @Nullable
    private final ResourceLocation defaultTexture;
    private final ResourceLocation tintTexture;
    private boolean glowing = false;

    public FamiliarColorLayer(GeoRenderer<T> renderer, int slot,
                              @Nullable ResourceLocation defaultTexture, ResourceLocation tintTexture) {
        super(renderer);
        this.slot = slot;
        this.defaultTexture = defaultTexture;
        this.tintTexture = tintTexture;
    }

    /**
     * Adds an emissive pass using the layer textures' {@code _glowmask} files.
     * Both the default and the "_bw" texture need one.
     */
    public FamiliarColorLayer<T> glowing() {
        this.glowing = true;
        return this;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        int rgb = animatable.getLayerColor(slot);
        boolean tinted = rgb != LayerColorHolder.NO_LAYER_COLOR;
        ResourceLocation texture = tinted ? tintTexture : defaultTexture;
        if (texture == null) {
            return;
        }

        // Use the renderer's alpha, so the layer fades together with the body (e.g. 15% for invisible familiars seen by spectators).
        int rendererAlpha = getRenderer().getRenderColor(animatable, partialTick, packedLight).argbInt() & 0xFF000000;
        int colour = rendererAlpha | (tinted ? rgb : 0xFFFFFF);

        // Normal pass. packedOverlay is passed through so the layer flashes red with the body when hurt
        RenderType layerType = getLayerRenderType(animatable, texture);
        if (layerType != null) {
            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, layerType,
                    bufferSource.getBuffer(layerType), partialTick, packedLight, packedOverlay, colour);
        }

        // Emissive pass, full brightness like AutoGlowingGeoLayer
        if (glowing) {
            RenderType glowType = getGlowRenderType(animatable, texture);
            if (glowType != null) {
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glowType,
                        bufferSource.getBuffer(glowType), partialTick, LightTexture.FULL_SKY, packedOverlay, colour);
            }
        }
    }

    /**
     * Mirrors how the familiar renderers draw the body: cutout normally, translucent when invisible
     * but still visible to this player (spectators), nothing when fully invisible.
     */
    @Nullable
    protected RenderType getLayerRenderType(T animatable, ResourceLocation texture) {
        if (!animatable.isInvisible()) {
            return RenderType.entityCutoutNoCull(texture);
        }
        if (!animatable.isInvisibleTo(ClientUtil.getClientPlayer())) {
            return RenderType.entityTranslucent(texture);
        }
        return null;
    }

    /** Same state handling as AutoGlowingGeoLayer: invisibility, spectators and the glowing effect. */
    @Nullable
    protected RenderType getGlowRenderType(T animatable, ResourceLocation texture) {
        boolean invisible = animatable.isInvisible();
        ResourceLocation emissive = AutoGlowingTexture.getEmissiveResource(texture);

        if (invisible && !animatable.isInvisibleTo(ClientUtil.getClientPlayer())) {
            return RenderType.itemEntityTranslucentCull(emissive);
        }

        if (Minecraft.getInstance().shouldEntityAppearGlowing(animatable)) {
            if (invisible) {
                return RenderType.outline(emissive);
            }
            return AutoGlowingTexture.getOutlineRenderType(texture);
        }

        return invisible ? null : AutoGlowingTexture.getRenderType(texture);
    }
}