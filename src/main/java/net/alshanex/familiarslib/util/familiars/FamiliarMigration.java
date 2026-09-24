package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.FamiliarSavedData;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

public final class FamiliarMigration {

    /** Moves familiars from the old attachment format into SavedData. */
    public static void migrateLegacy(ServerPlayer player) {
        PlayerFamiliarData state = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        if (!state.hasLegacyFamiliars()) return;

        FamiliarSavedData store = FamiliarSavedData.get(player);
        int moved = 0;
        for (Map.Entry<UUID, CompoundTag> e : state.getLegacyFamiliars().entrySet()) {
            if (store.putIfAbsent(e.getKey(), e.getValue().copy())) moved++;
        }

        player.server.overworld().getDataStorage().save();

        state.clearLegacyFamiliars();
        FamiliarsLib.LOGGER.info("Migrated {} familiar(s) of {} to SavedData",
                moved, player.getGameProfile().getName());
    }

    private FamiliarMigration() {}
}
