package net.alshanex.familiarslib.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmeticSlot;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Draws a familiar's cosmetic weapon item on a GeckoLib bone, replacing the familiar's own weapon.
 * Hats are drawn by {@link FamiliarHatLayer}.
 * <pre>
 * addRenderLayer(new FamiliarCosmeticLayer<>(this)
 *         .weapon(FamiliarCosmeticLayer.Placement.on("bm_weapon").rotation(-90, 180, 0).hideBone()
 *                 .context(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)));
 * </pre>
 */
public class FamiliarCosmeticLayer<T extends AbstractSpellCastingPet> extends BlockAndItemGeoLayer<T> {

    /** Where and how the weapon is drawn. Offsets are in model pixels (1/16 block), rotations in degrees. */
    public static final class Placement {
        private final String bone;
        private float x, y, z;
        private float rotX, rotY, rotZ;
        private float scale = 1.0F;
        private ItemDisplayContext context = ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        private boolean hideBone = false;

        private Placement(String bone) {
            this.bone = bone;
        }

        /** Attach to this bone (by its name in the .geo.json). */
        public static Placement on(String bone) {
            return new Placement(bone);
        }

        /** Moves the item from the bone's pivot, in model pixels. */
        public Placement offset(float x, float y, float z) {
            this.x = x; this.y = y; this.z = z;
            return this;
        }

        /** Rotates the item, in degrees, applied in X, then Y, then Z order. */
        public Placement rotation(float x, float y, float z) {
            this.rotX = x; this.rotY = y; this.rotZ = z;
            return this;
        }

        public Placement scale(float scale) {
            this.scale = scale;
            return this;
        }

        /** Which item model transform to use. Defaults to {@code THIRD_PERSON_RIGHT_HAND}. */
        public Placement context(ItemDisplayContext context) {
            this.context = context;
            return this;
        }

        /** Hide this bone (and its children) while a cosmetic weapon is equipped. */
        public Placement hideBone() {
            this.hideBone = true;
            return this;
        }

        public String bone() {
            return bone;
        }
    }

    @Nullable
    private Placement weapon;

    public FamiliarCosmeticLayer(GeoRenderer<T> renderer) {
        super(renderer);
    }

    public FamiliarCosmeticLayer<T> weapon(Placement placement) {
        this.weapon = placement;
        return this;
    }

    @Nullable
    private ItemStack weaponFor(T animatable) {
        ItemStack stack = animatable.getCosmetic(FamiliarCosmeticSlot.WEAPON);
        return stack.isEmpty() ? null : stack;
    }

    /**
     * Runs before the model is drawn. Bones are shared by every familiar using this model,
     * so the hidden state is set for each familiar right before it renders.
     */
    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                          MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                          int packedLight, int packedOverlay) {
        if (weapon == null || !weapon.hideBone) {
            return;
        }
        boolean hide = weaponFor(animatable) != null;
        bakedModel.getBone(weapon.bone).ifPresent(bone -> {
            bone.setHidden(hide);
            bone.setChildrenHidden(hide);
        });
    }

    @Nullable
    @Override
    protected ItemStack getStackForBone(GeoBone bone, T animatable) {
        return weapon != null && weapon.bone.equals(bone.getName()) ? weaponFor(animatable) : null;
    }

    @Override
    protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, T animatable) {
        return weapon != null ? weapon.context : ItemDisplayContext.NONE;
    }

    @Override
    protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, T animatable,
                                      MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
        if (weapon == null) {
            return;
        }
        poseStack.pushPose();
        // BlockAndItemGeoLayer.renderForBone rotated by the bone again (it's already in the pose):
        // undo that second rotation, in reverse order of RenderUtil.rotateMatrixAroundBone (Z, Y, X)
        poseStack.mulPose(Axis.XP.rotation(-bone.getRotX()));
        poseStack.mulPose(Axis.YP.rotation(-bone.getRotY()));
        poseStack.mulPose(Axis.ZP.rotation(-bone.getRotZ()));

        poseStack.translate(weapon.x / 16F, weapon.y / 16F, weapon.z / 16F);
        poseStack.mulPose(Axis.XP.rotationDegrees(weapon.rotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(weapon.rotY));
        poseStack.mulPose(Axis.ZP.rotationDegrees(weapon.rotZ));
        poseStack.scale(weapon.scale, weapon.scale, weapon.scale);
        super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
        poseStack.popPose();
    }
}