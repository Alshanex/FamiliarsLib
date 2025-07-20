package net.alshanex.familiarslib.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores the linkage between familiar entities and pet beds for a player
 */
public class BedLinkData implements INBTSerializable<CompoundTag> {

    private final Map<UUID, BlockPos> familiarToBed = new HashMap<>();

    private final Map<BlockPos, UUID> bedToFamiliar = new HashMap<>();

    public void linkFamiliarToBed(UUID familiarId, BlockPos bedPos) {
        // Remove any existing links for both the familiar and the bed
        unlinkFamiliar(familiarId);
        unlinkBed(bedPos);

        // Create new link
        familiarToBed.put(familiarId, bedPos);
        bedToFamiliar.put(bedPos, familiarId);
    }

    public void unlinkFamiliar(UUID familiarId) {
        BlockPos bedPos = familiarToBed.remove(familiarId);
        if (bedPos != null) {
            bedToFamiliar.remove(bedPos);
        }
    }

    public void unlinkBed(BlockPos bedPos) {
        UUID familiarId = bedToFamiliar.remove(bedPos);
        if (familiarId != null) {
            familiarToBed.remove(familiarId);
        }
    }

    public BlockPos getLinkedBed(UUID familiarId) {
        return familiarToBed.get(familiarId);
    }

    public UUID getLinkedFamiliar(BlockPos bedPos) {
        return bedToFamiliar.get(bedPos);
    }

    public boolean isFamiliarLinked(UUID familiarId) {
        return familiarToBed.containsKey(familiarId);
    }

    public boolean isBedLinked(BlockPos bedPos) {
        return bedToFamiliar.containsKey(bedPos);
    }

    public Map<UUID, BlockPos> getAllLinks() {
        return new HashMap<>(familiarToBed);
    }

    public void clearAllLinks() {
        familiarToBed.clear();
        bedToFamiliar.clear();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();

        ListTag linksList = new ListTag();
        for (Map.Entry<UUID, BlockPos> entry : familiarToBed.entrySet()) {
            CompoundTag linkEntry = new CompoundTag();
            linkEntry.putUUID("familiarId", entry.getKey());
            linkEntry.putLong("bedPos", entry.getValue().asLong());
            linksList.add(linkEntry);
        }

        nbt.put("links", linksList);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        clearAllLinks();

        if (nbt.contains("links", Tag.TAG_LIST)) {
            ListTag linksList = nbt.getList("links", Tag.TAG_COMPOUND);
            for (int i = 0; i < linksList.size(); i++) {
                CompoundTag linkEntry = linksList.getCompound(i);
                if (linkEntry.hasUUID("familiarId") && linkEntry.contains("bedPos")) {
                    UUID familiarId = linkEntry.getUUID("familiarId");
                    BlockPos bedPos = BlockPos.of(linkEntry.getLong("bedPos"));
                    linkFamiliarToBed(familiarId, bedPos);
                }
            }
        }
    }
}
