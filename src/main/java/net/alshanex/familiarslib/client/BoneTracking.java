package net.alshanex.familiarslib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns GeckoLib bone tracking (bone positions recorded while rendering) on only for as long as it's needed.
 */
public final class BoneTracking {
    private BoneTracking() {}

    /** Turns tracking on for the model's untracked bones. Returns them, for {@link #restore}. */
    public static List<GeoBone> enable(BakedGeoModel model) {
        List<GeoBone> turnedOn = new ArrayList<>();
        forEachBone(model, bone -> {
            if (!bone.isTrackingMatrices()) {
                bone.setTrackingMatrices(true);
                turnedOn.add(bone);
            }
        });
        return turnedOn;
    }

    /** Turns tracking back off for bones {@link #enable} turned on. */
    public static void restore(List<GeoBone> turnedOn) {
        for (GeoBone bone : turnedOn) {
            bone.setTrackingMatrices(false);
        }
    }

    /** The baked model an entity is drawn with, if its renderer is GeckoLib's. */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BakedGeoModel bakedModelOf(Entity entity) {
        Object entityRenderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(entityRenderer instanceof GeoEntityRenderer renderer) || !(entity instanceof GeoAnimatable animatable)) {
            return null;
        }
        GeoModel model = renderer.getGeoModel();
        return model.getBakedModel(model.getModelResource(animatable));
    }

    public static void forEachBone(BakedGeoModel model, Consumer<GeoBone> action) {
        for (GeoBone bone : model.topLevelBones()) {
            forEachBone(bone, action);
        }
    }

    private static void forEachBone(GeoBone bone, Consumer<GeoBone> action) {
        action.accept(bone);
        for (GeoBone child : bone.getChildBones()) {
            forEachBone(child, action);
        }
    }
}