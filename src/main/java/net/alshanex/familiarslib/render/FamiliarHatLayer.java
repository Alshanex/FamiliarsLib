package net.alshanex.familiarslib.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws a familiar's cosmetic hat on a dedicated armor bone, the same way GeckoLib draws helmets on
 * its own entities ({@link ItemArmorGeoLayer}), so every hat lines up the same way with no per-hat offsets.
 * <ul>
 *     <li>GeckoLib armor (e.g. Iron's Spellbooks wizard hats) and vanilla helmets: drawn as worn armor.</li>
 *     <li>Mob heads and skulls: drawn like on a player.</li>
 *     <li>Any other item (carved pumpkin...): drawn like vanilla draws it on a player's head.</li>
 * </ul>
 * <b>Model setup:</b> add a bone (default name {@value #DEFAULT_BONE}, the same one Iron's Spellbooks
 * mobs use) with one invisible cube ({@code "uv": {}}). The bone's <b>pivot</b> is where the head meets
 * the neck: helmets sit on it. The cube's <b>width</b> is the size the hat should have: a player's head
 * is 8 wide, and a helmet over it is 10, so use a 10 wide cube for a hat that fits an 8 wide head.
 * Everything is scaled from that, so moving the bone or resizing the cube in Blockbench is all the tuning needed.
 */
public class FamiliarHatLayer<T extends AbstractSpellCastingPet> extends ItemArmorGeoLayer<T> {
    public static final String DEFAULT_BONE = "armorBipedHead";

    /** Width, in pixels, of a vanilla helmet (8 wide head + 1 pixel each side). Hats are scaled relative to it. */
    private static final float HELMET_WIDTH = 10F;

    private final String boneName;

    public FamiliarHatLayer(GeoRenderer<T> renderer) {
        this(renderer, DEFAULT_BONE);
    }

    public FamiliarHatLayer(GeoRenderer<T> renderer, String boneName) {
        super(renderer);
        this.boneName = boneName;
    }

    @Nullable
    @Override
    protected ItemStack getArmorItemForBone(GeoBone bone, T animatable) {
        if (!bone.getName().equals(boneName)) {
            return null;
        }
        ItemStack hat = animatable.getCosmetic(FamiliarCosmeticSlot.HAT);
        return hat.isEmpty() ? null : hat;
    }

    @NotNull
    @Override
    protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack, T animatable) {
        return EquipmentSlot.HEAD;
    }

    @NotNull
    @Override
    protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot, ItemStack stack, T animatable, HumanoidModel<?> baseModel) {
        return baseModel.head;
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        ItemStack hat = getArmorItemForBone(bone, animatable);
        if (hat == null) {
            return;
        }

        if (isWornAsArmor(animatable, hat)) {
            // GeckoLib's own path: GeckoLib armor, vanilla helmets and skulls
            super.renderForBone(poseStack, animatable, bone, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
        } else {
            renderItemAsHat(poseStack, animatable, bone, hat, bufferSource, packedLight);
        }
    }

    /** True for anything GeckoLib can draw as a worn helmet: skulls, helmet armor items and GeckoLib armor. */
    protected boolean isWornAsArmor(T animatable, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof AbstractSkullBlock) {
            return true;
        }
        if (stack.getItem() instanceof ArmorItem armorItem && armorItem.getEquipmentSlot() == EquipmentSlot.HEAD) {
            return true;
        }
        return GeoRenderProvider.of(stack).getGeoArmorRenderer(animatable, stack, EquipmentSlot.HEAD, null) instanceof GeoArmorRenderer<?>;
    }

    /**
     * Draws a non-armor item (e.g. a carved pumpkin) the way vanilla's CustomHeadLayer draws it on a
     * player's head, scaled to the armor bone so it matches the size of helmets.
     */
    protected void renderItemAsHat(PoseStack poseStack, T animatable, GeoBone bone, ItemStack stack,
                                   MultiBufferSource bufferSource, int packedLight) {
        float fit = fitScale(bone);

        poseStack.pushPose();
        RenderUtil.translateAndRotateMatrixForBone(poseStack, bone); // same as GeckoLib's skull path
        poseStack.scale(fit, fit, fit);
        // Into vanilla's Y-down model space, then exactly what CustomHeadLayer does for non-skull items
        poseStack.scale(-1, -1, 1);
        poseStack.translate(0.0F, -0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.625F, -0.625F, -0.625F);
        Minecraft.getInstance().getItemRenderer().renderStatic(animatable, stack, ItemDisplayContext.HEAD, false,
                poseStack, bufferSource, animatable.level(), packedLight, OverlayTexture.NO_OVERLAY, animatable.getId());
        poseStack.popPose();
    }

    /** Armor bone cube width / vanilla helmet width: the same ratio GeckoLib uses to fit helmets. */
    protected float fitScale(GeoBone bone) {
        if (bone.getCubes().isEmpty()) {
            return 1F;
        }
        GeoCube cube = bone.getCubes().getFirst();
        return (float) cube.size().x() / HELMET_WIDTH;
    }
}