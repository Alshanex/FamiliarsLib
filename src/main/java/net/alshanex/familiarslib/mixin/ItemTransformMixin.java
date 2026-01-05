package net.alshanex.familiarslib.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.item.AbstractFamiliarTotem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class ItemTransformMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void scaleConsumableItems(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand,
                                      PoseStack poseStack, MultiBufferSource bufferSource,
                                      int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {

        // Check if the item has the familiar consumable component (which makes it miniaturized)
        if (FamiliarConsumableIntegration.isConsumableItem(itemStack) || itemStack.has(ComponentRegistry.FAMILIAR_FOOD) || itemStack.getItem() instanceof AbstractFamiliarTotem) {
            // Scale down for all contexts except GUI contexts
            if (shouldScaleItem(displayContext) == 1) {
                poseStack.scale(0.5f, 0.5f, 0.5f);
            }
            if (shouldScaleItem(displayContext) == 2) {
                poseStack.scale(0.7f, 0.7f, 0.7f);
            }
        }
    }

    /**
     * Determines if an item should be scaled based on its display context
     * We want to scale everything except inventory/GUI displays
     */
    private static int shouldScaleItem(ItemDisplayContext context) {
        return switch (context) {
            case GUI, FIXED -> 2; // Don't scale in inventory or fixed displays
            case GROUND, HEAD, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND,
                 FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> 1; // Scale in world/hand
            default -> 1; // Scale by default for any new contexts
        };
    }
}
