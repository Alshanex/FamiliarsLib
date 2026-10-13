package net.alshanex.familiarslib.client.camera;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.client.BoneTracking;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.event.GeoRenderEvent;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Where each familiar is actually drawn, which can be far from its hitbox (a jump animation can lift the model
 * blocks above it). Built from the world position of every GeckoLib bone, read right after the familiar renders.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class FamiliarRenderBounds {
    /** Bones are points (pivots): this pads the box to roughly cover the cubes around them. */
    private static final double MARGIN = 0.3D;
    /** A box older than this (the familiar stopped being rendered) falls back to the hitbox. */
    private static final int MAX_AGE_TICKS = 3;

    private record Seen(AABB box, long gameTime) {}

    private static final Map<AbstractSpellCastingPet, Seen> SEEN = new WeakHashMap<>();
    /** Bones this class turned tracking on for, per model, between a familiar's Pre and Post render events. */
    private static final Map<BakedGeoModel, List<GeoBone>> TRACKING = new IdentityHashMap<>();

    private FamiliarRenderBounds() {}

    /** The box the familiar was drawn in on the last frame, or its hitbox if it isn't being drawn. */
    public static AABB boxFor(AbstractSpellCastingPet familiar) {
        Seen seen = SEEN.get(familiar);
        if (seen != null && familiar.level().getGameTime() - seen.gameTime() <= MAX_AGE_TICKS) {
            return seen.box();
        }
        return familiar.getBoundingBox();
    }

    @SubscribeEvent
    public static void onPreRender(GeoRenderEvent.Entity.Pre event) {
        if (event.getEntity() instanceof AbstractSpellCastingPet familiar && shouldTrack(familiar)) {
            BakedGeoModel model = event.getModel();
            List<GeoBone> leftOver = TRACKING.remove(model);
            if (leftOver != null) BoneTracking.restore(leftOver); // a Post never came (render cancelled)
            TRACKING.put(model, BoneTracking.enable(model));
        }
    }

    @SubscribeEvent
    public static void onPostRender(GeoRenderEvent.Entity.Post event) {
        List<GeoBone> turnedOn = TRACKING.remove(event.getModel());
        try {
            if (event.getEntity() instanceof AbstractSpellCastingPet familiar && turnedOn != null) {
                recordBounds(familiar, event.getModel());
            }
        } finally {
            if (turnedOn != null) BoneTracking.restore(turnedOn);
        }
    }

    private static void recordBounds(AbstractSpellCastingPet familiar, BakedGeoModel model) {
        double[] bounds = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE,
                -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        BoneTracking.forEachBone(model, bone -> {
            if (bone.isHidden() || !bone.isTrackingMatrices()) return;
            // Bones are shared by every familiar of this model, but they hold this one's pose right now
            Vector3d pos = bone.getWorldPosition();
            if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
            bounds[0] = Math.min(bounds[0], pos.x);
            bounds[1] = Math.min(bounds[1], pos.y);
            bounds[2] = Math.min(bounds[2], pos.z);
            bounds[3] = Math.max(bounds[3], pos.x);
            bounds[4] = Math.max(bounds[4], pos.y);
            bounds[5] = Math.max(bounds[5], pos.z);
        });
        if (bounds[0] > bounds[3]) return; // no bones (or all hidden)

        AABB box = new AABB(bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]).inflate(MARGIN);
        SEEN.put(familiar, new Seen(atLeastHitboxSize(box, familiar.getBoundingBox()), familiar.level().getGameTime()));
    }

    /** Pivots can be close together on small models: never aim at something smaller than the hitbox. */
    private static AABB atLeastHitboxSize(AABB box, AABB hitbox) {
        Vec3 center = box.getCenter();
        return AABB.ofSize(center,
                Math.max(box.getXsize(), hitbox.getXsize()),
                Math.max(box.getYsize(), hitbox.getYsize()),
                Math.max(box.getZsize(), hitbox.getZsize()));
    }

    /** Familiars in the world (not photo/sculpture display copies), only while aiming with the camera. */
    private static boolean shouldTrack(AbstractSpellCastingPet familiar) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null
                && familiar.level() == mc.level
                && mc.level.getEntity(familiar.getId()) == familiar
                && CameraClientEvents.isCameraActive(mc.player);
    }
}