package net.alshanex.familiarslib.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Stores the tamed familiars for the player
 */
public class PlayerFamiliarData implements INBTSerializable<CompoundTag> {

    private UUID selectedFamiliarId = null;
    private UUID currentSummonedFamiliarId = null;
    private final Set<UUID> summonedFamiliarIds = new HashSet<>();

    // Only non-empty for players whose data was saved by the old version
    private final Map<UUID, CompoundTag> legacyFamiliars = new LinkedHashMap<>();

    @Nullable
    public UUID getSelectedFamiliarId() { return selectedFamiliarId; }
    public void setSelectedFamiliarId(@Nullable UUID id) { this.selectedFamiliarId = id; }

    @Nullable public UUID getCurrentSummonedFamiliarId() { return currentSummonedFamiliarId; }
    public void setCurrentSummonedFamiliarId(@Nullable UUID id) { this.currentSummonedFamiliarId = id; }

    public void addSummonedFamiliar(UUID id) { summonedFamiliarIds.add(id); }

    public void removeSummonedFamiliar(UUID id) {
        summonedFamiliarIds.remove(id);
        if (Objects.equals(currentSummonedFamiliarId, id)) currentSummonedFamiliarId = null;
    }

    public boolean isFamiliarSummoned(UUID id) { return summonedFamiliarIds.contains(id); }
    public Set<UUID> getSummonedFamiliarIds() { return new HashSet<>(summonedFamiliarIds); }
    public int getSummonedFamiliarCount() { return summonedFamiliarIds.size(); }

    public void clearAllSummoned() {
        summonedFamiliarIds.clear();
        currentSummonedFamiliarId = null;
    }

    /** Drops every reference to a familiar that no longer belongs to the player. */
    public void forget(UUID id) {
        if (Objects.equals(selectedFamiliarId, id)) selectedFamiliarId = null;
        if (Objects.equals(currentSummonedFamiliarId, id)) currentSummonedFamiliarId = null;
        summonedFamiliarIds.remove(id);
    }

    public void retainSummoned(Predicate<UUID> stillOwned) {
        summonedFamiliarIds.removeIf(id -> !stillOwned.test(id));
        if (currentSummonedFamiliarId != null && !stillOwned.test(currentSummonedFamiliarId)) {
            currentSummonedFamiliarId = null;
        }
    }

    // Legacy (pre-SavedData)

    public boolean hasLegacyFamiliars() { return !legacyFamiliars.isEmpty(); }
    public Map<UUID, CompoundTag> getLegacyFamiliars() { return Collections.unmodifiableMap(legacyFamiliars); }
    public void clearLegacyFamiliars() { legacyFamiliars.clear(); }


    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();

        // Keep writing unmigrated data back so nothing is lost before migration runs
        if (!legacyFamiliars.isEmpty()) {
            ListTag list = new ListTag();
            legacyFamiliars.forEach((id, data) -> {
                CompoundTag e = new CompoundTag();
                e.putUUID("id", id);
                e.put("data", data);
                list.add(e);
            });
            nbt.put("familiars", list);
        }

        if (selectedFamiliarId != null) nbt.putUUID("selectedFamiliar", selectedFamiliarId);
        if (currentSummonedFamiliarId != null) nbt.putUUID("currentSummoned", currentSummonedFamiliarId);

        if (!summonedFamiliarIds.isEmpty()) {
            ListTag summoned = new ListTag();
            for (UUID id : summonedFamiliarIds) {
                CompoundTag e = new CompoundTag();
                e.putUUID("summonedId", id);
                summoned.add(e);
            }
            nbt.put("summonedFamiliars", summoned);
        }
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        legacyFamiliars.clear();
        selectedFamiliarId = null;
        currentSummonedFamiliarId = null;
        summonedFamiliarIds.clear();

        if (nbt.contains("familiars", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("familiars", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (e.hasUUID("id")) legacyFamiliars.put(e.getUUID("id"), e.getCompound("data"));
            }
        }
        if (nbt.hasUUID("selectedFamiliar")) selectedFamiliarId = nbt.getUUID("selectedFamiliar");
        if (nbt.hasUUID("currentSummoned")) currentSummonedFamiliarId = nbt.getUUID("currentSummoned");
        if (nbt.contains("summonedFamiliars", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("summonedFamiliars", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (e.hasUUID("summonedId")) summonedFamiliarIds.add(e.getUUID("summonedId"));
            }
        }
    }
}