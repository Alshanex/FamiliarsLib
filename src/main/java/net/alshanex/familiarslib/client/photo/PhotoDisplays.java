package net.alshanex.familiarslib.client.photo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.FamiliarPhoto;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/** Client-only copies of photographed familiars, with the photo's look and pose. Cached per photo. */
public final class PhotoDisplays {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_SIZE = 64;

    private static final Map<FamiliarPhoto, AbstractSpellCastingPet> DISPLAYS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<FamiliarPhoto, AbstractSpellCastingPet> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private PhotoDisplays() {}

    @Nullable
    public static AbstractSpellCastingPet displayFor(FamiliarPhoto photo) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return null;

        AbstractSpellCastingPet cached = DISPLAYS.get(photo);
        if (cached != null && cached.level() == level) return cached;

        AbstractSpellCastingPet display = create(photo, level);
        if (display != null) DISPLAYS.put(photo, display);
        return display;
    }

    @Nullable
    private static AbstractSpellCastingPet create(FamiliarPhoto photo, Level level) {
        try {
            Entity entity = BuiltInRegistries.ENTITY_TYPE.getOptional(photo.entityType())
                    .map(type -> (Entity) type.create(level))
                    .orElse(null);
            if (!(entity instanceof AbstractSpellCastingPet familiar)) return null;

            for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
                familiar.setLayerColor(slot, photo.appearance().color(slot));
            }
            familiar.setCosmetic(FamiliarCosmeticSlot.HAT, photo.appearance().hat());
            familiar.setCosmetic(FamiliarCosmeticSlot.WEAPON, photo.appearance().weapon());
            PhotoPoses.set(familiar, photo.pose());
            return familiar;
        } catch (Exception e) {
            LOGGER.error("Could not build the familiar of a photo ({})", photo.entityType(), e);
            return null;
        }
    }

    /**
     * Same vertical adjustment as the familiar selection screen and the compendium (FamiliarPreviewHelper):
     * cancels the renderer's render offset and applies the familiar's compendium Y offset, so flying
     * familiars don't look like they float away from the bottom of the picture.
     */
    public static void applyAnchor(PoseStack poseStack, AbstractSpellCastingPet familiar) {
        Vec3 renderOffset = Minecraft.getInstance().getEntityRenderDispatcher()
                .getRenderer(familiar).getRenderOffset(familiar, 0F);
        poseStack.translate(0F, familiar.getCompendiumPreviewOffsetY() - (float) renderOffset.y, 0F);
    }

    /** Draws it anchored at the pose stack origin, facing +Z, with every render layer and no shadow. */
    public static void render(AbstractSpellCastingPet familiar, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffer, int light) {
        familiar.setYRot(0);
        familiar.yRotO = 0;
        familiar.yBodyRot = familiar.yBodyRotO = 0;
        familiar.yHeadRot = familiar.yHeadRotO = 0;
        familiar.setXRot(0);
        familiar.xRotO = 0;

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        applyAnchor(poseStack, familiar);
        dispatcher.setRenderShadow(false);
        try {
            dispatcher.render(familiar, 0, 0, 0, 0, partialTick, poseStack, buffer, light);
        } catch (Exception e) {
            LOGGER.debug("Photo render failed for {}", familiar, e);
        } finally {
            dispatcher.setRenderShadow(true);
            poseStack.popPose();
        }
    }
}