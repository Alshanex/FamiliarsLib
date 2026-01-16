package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * Helper class for cleaning up bed claims when familiars are removed
 */
public class BedCleanupHelper {

    /**
     * Safety mechanism - checks if a bed should be unclaimed because no familiar is using it
     */
    public static void performSafetyCheck(AbstractFamiliarBedBlockEntity bedEntity, BlockPos bedPos, ServerLevel level) {
        if (!bedEntity.isBedTaken()) {
            return; // Bed is not claimed, nothing to check
        }

        // Create bounding box around the bed position
        AABB searchArea = new AABB(bedPos).inflate(10.0);

        // Check if there's any familiar within 10 blocks that is actively using this bed
        boolean familiarFoundUsingThisBed = !level.getEntitiesOfClass(
                AbstractSpellCastingPet.class,
                searchArea,
                familiar -> {
                    if (familiar == null || !familiar.isAlive()) {
                        return false;
                    }

                    // Check if the familiar has an active FindAndUsePetBedGoal targeting this bed
                    boolean hasActiveBedGoal = familiar.getGoalSelector().getAvailableGoals().stream()
                            .anyMatch(goal -> {
                                if (goal.getGoal() instanceof FamiliarGoals.FindAndUsePetBedGoal bedGoal && goal.isRunning()) {
                                    BlockPos targetPos = bedGoal.getTargetBedPos();
                                    boolean hasClaimed = bedGoal.hasClaimedBed();

                                    return targetPos != null && targetPos.equals(bedPos) && hasClaimed;
                                }
                                return false;
                            });

                    if (hasActiveBedGoal) {
                        return true; // Familiar has an active goal targeting this bed
                    }

                    // Fallback check: if the familiar is sitting and close to this bed
                    if (familiar.getIsSitting()) {
                        double distance = familiar.position().distanceTo(bedEntity.getSleepPosition());
                        if (distance <= 2.0) {
                            return true; // Familiar is sitting on this bed
                        }
                    }

                    return false;
                }
        ).isEmpty();

        if (!familiarFoundUsingThisBed) {
            FamiliarsLib.LOGGER.debug("Safety check: Unclaiming abandoned bed at {}", bedPos);
            bedEntity.setBedTaken(false);
        }
    }
}