package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/**
 * Helper to handle familiar deaths when inside storage blocks, not really needed since they invulnerable inside the blocks
 */
public class FamiliarDeathStorageHandler {

    public static void notifyFamiliarDeath(AbstractSpellCastingPet familiar) {
        if (familiar.level().isClientSide) return;

        UUID familiarId = familiar.getUUID();
        ServerLevel level = (ServerLevel) familiar.level();

        FamiliarsLib.LOGGER.debug("Notifying storage blocks about death of familiar {}", familiarId);

        if (familiar.getIsInHouse() && familiar.housePosition != null) {
            BlockEntity blockEntity = level.getBlockEntity(familiar.housePosition);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                storageEntity.handleFamiliarDeath(familiarId);
                FamiliarsLib.LOGGER.debug("Notified house at {} about familiar death", familiar.housePosition);
            }
        }
    }
}

