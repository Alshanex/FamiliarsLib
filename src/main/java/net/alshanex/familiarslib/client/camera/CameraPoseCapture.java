package net.alshanex.familiarslib.client.camera;

import com.mojang.logging.LogUtils;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.FamiliarPose;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import org.slf4j.Logger;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.state.BoneSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a familiar's current pose on the client. Each familiar's GeckoLib manager keeps a snapshot of every
 * bone as of its last rendered frame, which is exactly what the player is looking at.
 */
public final class CameraPoseCapture {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CameraPoseCapture() {}

    public static FamiliarPose capture(AbstractSpellCastingPet familiar) {
        List<String> animations = new ArrayList<>();
        List<FamiliarPose.BonePose> bones = new ArrayList<>();

        try {
            AnimatableManager<?> manager = familiar.getAnimatableInstanceCache().getManagerForId(familiar.getId());
            if (manager != null) {
                for (AnimationController<?> controller : manager.getAnimationControllers().values()) {
                    if (controller.getAnimationState() == AnimationController.State.STOPPED) continue;
                    var current = controller.getCurrentAnimation();
                    if (current != null && animations.size() < FamiliarPose.MAX_ANIMATIONS) {
                        animations.add(controller.getName() + ":" + current.animation().name());
                    }
                }

                for (Map.Entry<String, BoneSnapshot> entry : new TreeMap<>(manager.getBoneSnapshotCollection()).entrySet()) {
                    if (bones.size() >= FamiliarPose.MAX_BONES) break;
                    BoneSnapshot snap = entry.getValue();
                    bones.add(new FamiliarPose.BonePose(entry.getKey(),
                            new Vector3f(snap.getRotX(), snap.getRotY(), snap.getRotZ()),
                            new Vector3f(snap.getOffsetX(), snap.getOffsetY(), snap.getOffsetZ()),
                            new Vector3f(snap.getScaleX(), snap.getScaleY(), snap.getScaleZ())));
                }
            }
        } catch (RuntimeException e) {
            // A photo without bone data is still a photo
            LOGGER.warn("Could not read the pose of {}", familiar, e);
        }

        return new FamiliarPose(
                Mth.wrapDegrees(familiar.getYHeadRot() - familiar.yBodyRot),
                familiar.getXRot(),
                familiar.getIsSitting(),
                animations,
                bones);
    }
}