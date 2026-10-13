package net.alshanex.familiarslib.item.camera;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.function.Function;

/**
 * Picks the familiar closest to the crosshair. The client aims at where familiars are <b>drawn</b> (their
 * animation can take the model far from the hitbox, e.g. mid-jump), the server checks that shot against a box
 * the client says it saw, kept near the real hitbox.
 */
public final class CameraTargeting {
    /** How far the camera can see. */
    public static final double RANGE = 48.0D;
    /** Max angle (degrees) between the crosshair and the edge of a familiar, without zoom. */
    public static final float MAX_ANGLE = 15.0F;
    /** Same, while zoomed in: the field of view is much narrower. */
    public static final float MAX_ANGLE_ZOOMED = 4.0F;
    /** How far the drawn familiar may be from its hitbox (jumps, leaps...), in blocks, plus the familiar's size. */
    public static final double MAX_AIM_OFFSET = 4.0D;
    /** Extra angle the server allows, since its copy of the player's rotation lags a little behind. */
    private static final float SERVER_TOLERANCE = 12.0F;

    private CameraTargeting() {}

    /**
     * @param seenBox where each familiar is visible: the drawn box on the client, the hitbox if unknown
     */
    @Nullable
    public static AbstractSpellCastingPet findTarget(Player player, float partialTick, boolean zoomed,
                                                     Function<AbstractSpellCastingPet, AABB> seenBox) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        float maxAngle = zoomed ? MAX_ANGLE_ZOOMED : MAX_ANGLE;

        AbstractSpellCastingPet best = null;
        double bestScore = Double.MAX_VALUE;
        // Wider than RANGE: a familiar's hitbox can be out of range while what's drawn is in it
        AABB area = new AABB(eye, eye).inflate(RANGE + MAX_AIM_OFFSET);

        for (AbstractSpellCastingPet familiar : level.getEntitiesOfClass(AbstractSpellCastingPet.class, area, CameraTargeting::canBePhotographed)) {
            AABB box = seenBox.apply(familiar);
            double distance = eye.distanceTo(box.getCenter());
            if (distance > RANGE) continue;

            double angle = angleTo(eye, look, box);
            if (angle > maxAngle) continue;
            if (!hasClearView(level, player, eye, box)) continue;

            // Closest to the crosshair wins; distance only breaks near-ties
            double score = angle + distance * 0.01D;
            if (score < bestScore) {
                bestScore = score;
                best = familiar;
            }
        }
        return best;
    }

    /**
     * Server side: the box the client says it aimed at, rebuilt from its offset to the familiar's position.
     * The offset is clamped so the drawn familiar can't be claimed to be anywhere far from the real one.
     */
    public static AABB seenBox(AbstractSpellCastingPet familiar, Vector3f aimOffset) {
        AABB hitbox = familiar.getBoundingBox();
        Vec3 offset = Float.isFinite(aimOffset.x) && Float.isFinite(aimOffset.y) && Float.isFinite(aimOffset.z)
                ? new Vec3(aimOffset)
                : hitbox.getCenter().subtract(familiar.position());
        double maxOffset = MAX_AIM_OFFSET + Math.max(hitbox.getXsize(), hitbox.getYsize());
        if (offset.length() > maxOffset) {
            offset = offset.normalize().scale(maxOffset);
        }
        return AABB.ofSize(familiar.position().add(offset), hitbox.getXsize(), hitbox.getYsize(), hitbox.getZsize());
    }

    /** Server side check of a shot the client asked for. */
    public static boolean isValidShot(Player player, AbstractSpellCastingPet familiar, AABB seenBox) {
        if (!canBePhotographed(familiar) || familiar.level() != player.level()) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        if (eye.distanceTo(seenBox.getCenter()) > RANGE + 4.0D) {
            return false;
        }
        return angleTo(eye, player.getViewVector(1.0F), seenBox) <= MAX_ANGLE + SERVER_TOLERANCE
                && hasClearView(player.level(), player, eye, seenBox);
    }

    public static boolean canBePhotographed(AbstractSpellCastingPet familiar) {
        return familiar.isAlive() && !familiar.isRemoved() && !familiar.isInvisible();
    }

    /**
     * Angle in degrees between the look direction and the nearest edge of the box
     * (0 when the crosshair is on it), so big familiars are as easy to frame as small ones.
     */
    public static double angleTo(Vec3 eye, Vec3 look, AABB box) {
        Vec3 toCenter = box.getCenter().subtract(eye);
        double distance = toCenter.length();
        if (distance < 1.0E-4D) {
            return 0.0D;
        }
        double cos = Mth.clamp(look.normalize().dot(toCenter) / distance, -1.0D, 1.0D);
        double angle = Math.toDegrees(Math.acos(cos));
        double radius = Math.max(box.getXsize(), box.getYsize()) * 0.5D;
        double angularRadius = Math.toDegrees(Math.atan2(radius, distance));
        return Math.max(0.0D, angle - angularRadius);
    }

    /** True if blocks don't hide both the center and the top of the box. */
    private static boolean hasClearView(Level level, Entity viewer, Vec3 eye, AABB box) {
        Vec3 center = box.getCenter();
        return isUnobstructed(level, viewer, eye, center)
                || isUnobstructed(level, viewer, eye, new Vec3(center.x, box.maxY - 0.1D, center.z));
    }

    private static boolean isUnobstructed(Level level, Entity viewer, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer))
                .getType() == HitResult.Type.MISS;
    }
}