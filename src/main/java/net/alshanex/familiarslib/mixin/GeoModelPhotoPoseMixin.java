package net.alshanex.familiarslib.mixin;

import net.alshanex.familiarslib.client.photo.PhotoPoses;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.FamiliarPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * Client only. After GeckoLib animates a model (and after the model's own setCustomAnimations), puts the bones
 * of photo display copies in the pose saved in their photo.
 */
@Mixin(value = GeoModel.class, remap = false)
public abstract class GeoModelPhotoPoseMixin<T extends GeoAnimatable> {
    @Shadow
    public abstract AnimationProcessor<T> getAnimationProcessor();

    @Inject(method = "handleAnimations", at = @At("RETURN"))
    private void familiarslib$applyPhotoPose(T animatable, long instanceId, AnimationState<T> animationState,
                                                   float partialTick, CallbackInfo ci) {
        if (animatable instanceof AbstractSpellCastingPet familiar) {
            FamiliarPose pose = PhotoPoses.get(familiar);
            if (pose != null) {
                PhotoPoses.apply(getAnimationProcessor(), pose);
            }
        }
    }
}