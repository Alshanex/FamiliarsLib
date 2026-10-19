package net.alshanex.familiarslib.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Draws a Familiar Egg on a Familiar Bed, cracking and wobbling as it gets closer to hatching. */
public class FamiliarEggBedRenderer<T extends AbstractFamiliarBedBlockEntity> implements BlockEntityRenderer<T> {

    /** Intact, first cracks, about to hatch. */
    public static final ModelResourceLocation[] STAGES = {
            stage("familiar_egg"),
            stage("familiar_egg_cracked_1"),
            stage("familiar_egg_cracked_2")
    };

    private static ModelResourceLocation stage(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "block/" + name));
    }

    public FamiliarEggBedRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(T bed, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Level level = bed.getLevel();
        if (!bed.hasEgg() || level == null) return;

        float progress = bed.getEggProgress(level.getGameTime());
        int stage = progress < AbstractFamiliarBedBlockEntity.EGG_CRACK_STAGE_1 ? 0
                : progress < AbstractFamiliarBedBlockEntity.EGG_CRACK_STAGE_2 ? 1 : 2;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(STAGES[stage]);

        Vec3 offset = bed.getEggRenderOffset();
        float scale = bed.getEggRenderScale();

        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);

        // Wobbles more and more in the last stage
        if (stage == 2) {
            float strength = Mth.clamp((progress - AbstractFamiliarBedBlockEntity.EGG_CRACK_STAGE_2)
                    / (1F - AbstractFamiliarBedBlockEntity.EGG_CRACK_STAGE_2), 0.3F, 1F);
            float time = level.getGameTime() + partialTick;
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 0.5F) * 8F * strength));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(time * 0.37F) * 4F * strength));
        }

        pose.scale(scale, scale, scale);
        pose.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffers.getBuffer(RenderType.cutout()), null, model,
                1F, 1F, 1F, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
}