package net.alshanex.familiarslib.client.photo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.item.camera.FamiliarPhoto;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Photos held in hand: the same print as in item frames (the familiar's picture on the paper), filling the item's
 * space like a flat item's sprite. Only used in hands: familiar_photo_icon.json keeps the normal icon for the inventory,
 * the ground and item frames (where PhotoFrameRenderer draws framed photos).
 */
public class FamiliarPhotoItemRenderer extends BlockEntityWithoutLevelRenderer {

    public FamiliarPhotoItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FamiliarPhoto photo = stack.get(ComponentRegistry.FAMILIAR_PHOTO.get());

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(180F));
        PhotoFrameRenderer.drawPhoto(photo, poseStack, buffer, packedLight, 1F);
        poseStack.popPose();
    }
}