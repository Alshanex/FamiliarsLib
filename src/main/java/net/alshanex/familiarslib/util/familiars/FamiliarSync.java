package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.FamiliarRoster;
import net.alshanex.familiarslib.data.FamiliarSavedData;
import net.alshanex.familiarslib.network.FamiliarSnapshotsPacket;
import net.alshanex.familiarslib.network.FamiliarStatePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class FamiliarSync {
    private static final int MAX_BATCH_BYTES = 256 * 1024;

    /** Cheap: IDs and state only. Used after any change. */
    public static void state(ServerPlayer player) {
        FamiliarRoster roster = FamiliarRoster.of(player);
        PacketDistributor.sendToPlayer(player, new FamiliarStatePacket(
                roster.getFamiliarIds(),
                roster.getSelectedFamiliarId(),
                roster.getCurrentSummonedFamiliarId(),
                roster.getSummonedFamiliarIds(),
                FamiliarRoster.maxFamiliars()));
    }

    /** State + one snapshot. Used when a familiar is added or its snapshot changed. */
    public static void snapshot(ServerPlayer player, UUID familiarId) {
        state(player);
        CompoundTag tag = FamiliarSavedData.get(player).view().get(familiarId);
        if (tag != null) {
            PacketDistributor.sendToPlayer(player, new FamiliarSnapshotsPacket(false, Map.of(familiarId, tag)));
        }
    }

    /** State + every snapshot, batched. Used right before opening a screen. */
    public static void full(ServerPlayer player) {
        state(player);

        Map<UUID, CompoundTag> batch = new LinkedHashMap<>();
        int batchBytes = 0;
        boolean first = true;

        for (Map.Entry<UUID, CompoundTag> e : FamiliarSavedData.get(player).view().entrySet()) {
            int size = e.getValue().sizeInBytes();
            if (size > MAX_BATCH_BYTES) {
                FamiliarsLib.LOGGER.warn("Familiar {} snapshot is unusually large ({} bytes)", e.getKey(), size);
            }
            if (!batch.isEmpty() && batchBytes + size > MAX_BATCH_BYTES) {
                PacketDistributor.sendToPlayer(player, new FamiliarSnapshotsPacket(first, batch));
                first = false;
                batch = new LinkedHashMap<>();
                batchBytes = 0;
            }
            batch.put(e.getKey(), e.getValue());
            batchBytes += size;
        }
        // Always send when first (clears a stale client cache even if the roster is empty)
        if (first || !batch.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new FamiliarSnapshotsPacket(first, batch));
        }
    }

    private FamiliarSync() {}
}
