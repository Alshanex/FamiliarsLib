package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.AbstractFamiliarBedBlock;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.data.BedLinkData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class FamiliarBedHelper {
    public static void snapToExactBedPosition(AbstractSpellCastingPet familiar) {
        if (familiar.getSummoner() instanceof ServerPlayer serverPlayer) {
            BedLinkData linkData = serverPlayer.getData(AttachmentRegistry.BED_LINK_DATA);
            BlockPos linkedBedPos = linkData.getLinkedBed(familiar.getUUID());

            if (linkedBedPos != null) {
                double distanceToBed = familiar.blockPosition().distSqr(linkedBedPos);
                if (distanceToBed <= 9.0) {

                    if (familiar.level().getBlockState(linkedBedPos).getBlock() instanceof AbstractFamiliarBedBlock) {
                        BlockEntity be = familiar.level().getBlockEntity(linkedBedPos);
                        if (be instanceof AbstractFamiliarBedBlockEntity petBed && petBed.isLinkedToPet(familiar.getUUID())) {
                            Vec3 exactPos = petBed.getSleepPosition();
                            double distance = familiar.position().distanceTo(exactPos);

                            if (distance <= 3.0) {
                                familiar.setPos(exactPos.x, exactPos.y, exactPos.z);
                                if (!familiar.level().isClientSide) {
                                    FamiliarsLib.LOGGER.debug("Snapped pet " + familiar.getUUID() + " to linked bed position: " + exactPos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static boolean isOnCompatibleBed(AbstractSpellCastingPet familiar) {
        BlockPos petPos = familiar.blockPosition();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = petPos.offset(x, y, z);

                    if (familiar.level().getBlockState(checkPos).getBlock() instanceof AbstractFamiliarBedBlock) {
                        BlockEntity be = familiar.level().getBlockEntity(checkPos);
                        if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                            boolean inCorrectPosition = petBed.isPositionCorrectForSleeping(familiar.position());

                            // First check new linking system
                            if (petBed.isLinkedToPet(familiar.getUUID())) {
                                return inCorrectPosition;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }
}
