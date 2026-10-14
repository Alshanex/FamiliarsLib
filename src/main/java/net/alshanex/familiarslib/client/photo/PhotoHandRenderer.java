package net.alshanex.familiarslib.client.photo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.camera.FamiliarPhoto;
import net.alshanex.familiarslib.item.camera.FamiliarPhotoItem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Photos in first person are held like vanilla maps, arms included: with both hands in front of you (main hand,
 * nothing in the off hand, tilting as you look down), otherwise to the side in one hand.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class PhotoHandRenderer {
    /** Vanilla map scale, and the map's size with its border (142/128 of the map itself). */
    private static final float MAP_SCALE = 0.38F;
    private static final float PRINT_SIZE = 142F / 128F;

    private PhotoHandRenderer() {}

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (!(stack.getItem() instanceof FamiliarPhotoItem) || player == null) return;
        event.setCanceled(true);

        FamiliarPhoto photo = stack.get(ComponentRegistry.FAMILIAR_PHOTO.get());
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = event.getMultiBufferSource();
        int light = event.getPackedLight();
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;

        poseStack.pushPose();
        if (mainHand && player.getOffhandItem().isEmpty()) {
            renderTwoHanded(player, photo, poseStack, buffer, light,
                    event.getInterpolatedPitch(), event.getEquipProgress(), event.getSwingProgress());
        } else {
            HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
            renderOneHanded(player, photo, poseStack, buffer, light, event.getEquipProgress(), arm, event.getSwingProgress());
        }
        poseStack.popPose();
    }

    // Vanilla: ItemInHandRenderer.renderTwoHandedMap
    private static void renderTwoHanded(AbstractClientPlayer player, @Nullable FamiliarPhoto photo, PoseStack poseStack,
                                        MultiBufferSource buffer, int light, float pitch, float equipProgress, float swingProgress) {
        float swingRoot = Mth.sqrt(swingProgress);
        float bobY = -0.2F * Mth.sin(swingProgress * Mth.PI);
        float bobZ = -0.4F * Mth.sin(swingRoot * Mth.PI);
        poseStack.translate(0.0F, -bobY / 2.0F, bobZ);
        float tilt = calculateMapTilt(pitch);
        poseStack.translate(0.0F, 0.04F + equipProgress * -1.2F + tilt * -0.5F, -0.72F);
        poseStack.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
        if (!player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            renderMapHand(player, poseStack, buffer, light, HumanoidArm.RIGHT);
            renderMapHand(player, poseStack, buffer, light, HumanoidArm.LEFT);
            poseStack.popPose();
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(swingRoot * Mth.PI) * 20.0F));
        poseStack.scale(2.0F, 2.0F, 2.0F);
        renderPrint(photo, poseStack, buffer, light);
    }

    // Vanilla: ItemInHandRenderer.renderOneHandedMap
    private static void renderOneHanded(AbstractClientPlayer player, @Nullable FamiliarPhoto photo, PoseStack poseStack,
                                        MultiBufferSource buffer, int light, float equipProgress, HumanoidArm arm, float swingProgress) {
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(side * 0.125F, -0.125F, 0.0F);
        if (!player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 10.0F));
            renderPlayerArm(player, poseStack, buffer, light, equipProgress, swingProgress, arm);
            poseStack.popPose();
        }
        poseStack.pushPose();
        poseStack.translate(side * 0.51F, -0.08F + equipProgress * -1.2F, -0.75F);
        float swingRoot = Mth.sqrt(swingProgress);
        float swingSin = Mth.sin(swingRoot * Mth.PI);
        float x = -0.5F * swingSin;
        float y = 0.4F * Mth.sin(swingRoot * Mth.TWO_PI);
        float z = -0.3F * Mth.sin(swingProgress * Mth.PI);
        poseStack.translate(side * x, y - 0.3F * swingSin, z);
        poseStack.mulPose(Axis.XP.rotationDegrees(swingSin * -45.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * swingSin * -30.0F));
        renderPrint(photo, poseStack, buffer, light);
        poseStack.popPose();
    }

    /**
     * Where vanilla's renderMap draws the map. Vanilla turns its space so the map's rows go down; the print
     * is drawn in frame space (see PhotoFrameRenderer.drawPhoto), which is that space turned upright again,
     * so only the half turn towards the viewer is left.
     */
    private static void renderPrint(@Nullable FamiliarPhoto photo, PoseStack poseStack, MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(MAP_SCALE, MAP_SCALE, MAP_SCALE);
        PhotoFrameRenderer.drawPhoto(photo, poseStack, buffer, light, PRINT_SIZE);
        poseStack.popPose();
    }

    // Vanilla: ItemInHandRenderer.calculateMapTilt
    private static float calculateMapTilt(float pitch) {
        float tilt = 1.0F - pitch / 45.0F + 0.1F;
        tilt = Mth.clamp(tilt, 0.0F, 1.0F);
        return -Mth.cos(tilt * Mth.PI) * 0.5F + 0.5F;
    }

    // Vanilla: ItemInHandRenderer.renderMapHand
    private static void renderMapHand(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer, int light, HumanoidArm arm) {
        PlayerRenderer renderer = playerRenderer(player);
        if (renderer == null) return;
        poseStack.pushPose();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -41.0F));
        poseStack.translate(side * 0.3F, -1.1F, 0.45F);
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(poseStack, buffer, light, player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, light, player);
        }
        poseStack.popPose();
    }

    // Vanilla: ItemInHandRenderer.renderPlayerArm
    private static void renderPlayerArm(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffer, int light,
                                        float equipProgress, float swingProgress, HumanoidArm arm) {
        PlayerRenderer renderer = playerRenderer(player);
        if (renderer == null) return;
        boolean right = arm != HumanoidArm.LEFT;
        float side = right ? 1.0F : -1.0F;
        float swingRoot = Mth.sqrt(swingProgress);
        float x = -0.3F * Mth.sin(swingRoot * Mth.PI);
        float y = 0.4F * Mth.sin(swingRoot * Mth.TWO_PI);
        float z = -0.4F * Mth.sin(swingProgress * Mth.PI);
        poseStack.translate(side * (x + 0.64000005F), y + -0.6F + equipProgress * -0.6F, z + -0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        float swingSquared = Mth.sin(swingProgress * swingProgress * Mth.PI);
        float swingSin = Mth.sin(swingRoot * Mth.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * swingSin * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * swingSquared * -20.0F));
        poseStack.translate(side * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -135.0F));
        poseStack.translate(side * 5.6F, 0.0F, 0.0F);
        if (right) {
            renderer.renderRightHand(poseStack, buffer, light, player);
        } else {
            renderer.renderLeftHand(poseStack, buffer, light, player);
        }
    }

    @Nullable
    private static PlayerRenderer playerRenderer(AbstractClientPlayer player) {
        // Through Object: javac rejects testing EntityRenderer<? super ...> against PlayerRenderer
        Object renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        return renderer instanceof PlayerRenderer playerRenderer ? playerRenderer : null;
    }
}
