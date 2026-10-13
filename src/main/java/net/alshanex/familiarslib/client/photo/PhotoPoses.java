package net.alshanex.familiarslib.client.photo;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.FamiliarPose;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Frozen poses for client-only display copies of photographed familiars.
 */
public final class PhotoPoses {
    // Display copies are never added to the level, so nothing else holds them: weak keys let them go
    private static final Map<AbstractSpellCastingPet, FamiliarPose> POSES = new WeakHashMap<>();

    private PhotoPoses() {}

    public static void set(AbstractSpellCastingPet familiar, FamiliarPose pose) {
        POSES.put(familiar, pose);
    }

    @Nullable
    public static FamiliarPose get(AbstractSpellCastingPet familiar) {
        return POSES.get(familiar);
    }

    public static void apply(AnimationProcessor<?> processor, FamiliarPose pose) {
        for (FamiliarPose.BonePose bonePose : pose.bones()) {
            GeoBone bone = processor.getBone(bonePose.name());
            if (bone == null) continue; // the model changed since the photo was taken
            bone.setRotX(bonePose.rotation().x);
            bone.setRotY(bonePose.rotation().y);
            bone.setRotZ(bonePose.rotation().z);
            bone.setPosX(bonePose.position().x);
            bone.setPosY(bonePose.position().y);
            bone.setPosZ(bonePose.position().z);
            bone.setScaleX(bonePose.scale().x);
            bone.setScaleY(bonePose.scale().y);
            bone.setScaleZ(bonePose.scale().z);
        }
    }
}