package net.alshanex.familiarslib.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.*;

/**
 * Stores the tamed familiars for the player
 */
public class PlayerFamiliarData implements INBTSerializable<CompoundTag> {

    // Max limit of familiars per player
    public static final int MAX_FAMILIAR_LIMIT = 10;

    private final Map<UUID, CompoundTag> tamedFamiliars = new HashMap<>();
    private UUID selectedFamiliarId = null;
    private UUID currentSummonedFamiliarId = null;
    private final Set<UUID> summonedFamiliarIds = new HashSet<>();

    public void addTamedFamiliar(UUID familiarId, CompoundTag familiarData) {
        tamedFamiliars.put(familiarId, familiarData);
    }

    public boolean canTameMoreFamiliars() {
        return tamedFamiliars.size() < MAX_FAMILIAR_LIMIT;
    }

    public boolean tryAddTamedFamiliar(UUID familiarId, CompoundTag familiarData) {
        if (!canTameMoreFamiliars()) {
            return false;
        }
        addTamedFamiliar(familiarId, familiarData);
        return true;
    }

    public int getRemainingFamiliarSlots() {
        return MAX_FAMILIAR_LIMIT - tamedFamiliars.size();
    }

    public boolean isAtMaxCapacity() {
        return tamedFamiliars.size() >= MAX_FAMILIAR_LIMIT;
    }

    public void removeTamedFamiliar(UUID familiarId) {
        tamedFamiliars.remove(familiarId);
        if (Objects.equals(selectedFamiliarId, familiarId)) {
            selectedFamiliarId = null;
        }
        if (Objects.equals(currentSummonedFamiliarId, familiarId)) {
            currentSummonedFamiliarId = null;
        }
        summonedFamiliarIds.remove(familiarId);
    }

    public CompoundTag getFamiliarData(UUID familiarId) {
        return tamedFamiliars.get(familiarId);
    }

    public Map<UUID, CompoundTag> getAllFamiliars() {
        return new HashMap<>(tamedFamiliars);
    }

    public boolean hasFamiliar(UUID familiarId) {
        return tamedFamiliars.containsKey(familiarId);
    }

    public UUID getSelectedFamiliarId() {
        return selectedFamiliarId;
    }

    public void setSelectedFamiliarId(UUID familiarId) {
        this.selectedFamiliarId = familiarId;
    }

    public UUID getCurrentSummonedFamiliarId() {
        return currentSummonedFamiliarId;
    }

    public void setCurrentSummonedFamiliarId(UUID familiarId) {
        this.currentSummonedFamiliarId = familiarId;
    }

    public void addSummonedFamiliar(UUID familiarId) {
        summonedFamiliarIds.add(familiarId);
    }

    public void removeSummonedFamiliar(UUID familiarId) {
        summonedFamiliarIds.remove(familiarId);
        if (Objects.equals(currentSummonedFamiliarId, familiarId)) {
            currentSummonedFamiliarId = null;
        }
    }

    public boolean isFamiliarSummoned(UUID familiarId) {
        return summonedFamiliarIds.contains(familiarId);
    }

    public Set<UUID> getSummonedFamiliarIds() {
        return new HashSet<>(summonedFamiliarIds);
    }

    public int getSummonedFamiliarCount() {
        return summonedFamiliarIds.size();
    }

    public void clearAllSummoned() {
        summonedFamiliarIds.clear();
        currentSummonedFamiliarId = null;
    }

    public boolean isEmpty() {
        return tamedFamiliars.isEmpty();
    }

    public int getFamiliarCount() {
        return tamedFamiliars.size();
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();

        ListTag familiarsList = new ListTag();
        for (Map.Entry<UUID, CompoundTag> entry : tamedFamiliars.entrySet()) {
            CompoundTag familiarEntry = new CompoundTag();
            familiarEntry.putUUID("id", entry.getKey());
            familiarEntry.put("data", entry.getValue());
            familiarsList.add(familiarEntry);
        }
        nbt.put("familiars", familiarsList);

        if (selectedFamiliarId != null) {
            nbt.putUUID("selectedFamiliar", selectedFamiliarId);
        }

        if (currentSummonedFamiliarId != null) {
            nbt.putUUID("currentSummoned", currentSummonedFamiliarId);
        }

        if (!summonedFamiliarIds.isEmpty()) {
            ListTag summonedList = new ListTag();
            for (UUID summonedId : summonedFamiliarIds) {
                CompoundTag summonedEntry = new CompoundTag();
                summonedEntry.putUUID("summonedId", summonedId);
                summonedList.add(summonedEntry);
            }
            nbt.put("summonedFamiliars", summonedList);
        }

        nbt.putInt("familiarCount", tamedFamiliars.size());
        nbt.putLong("saveTime", System.currentTimeMillis());

        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        tamedFamiliars.clear();
        selectedFamiliarId = null;
        currentSummonedFamiliarId = null;
        summonedFamiliarIds.clear();

        if (nbt.contains("familiars", Tag.TAG_LIST)) {
            ListTag familiarsList = nbt.getList("familiars", Tag.TAG_COMPOUND);
            for (int i = 0; i < familiarsList.size(); i++) {
                CompoundTag familiarEntry = familiarsList.getCompound(i);
                if (familiarEntry.hasUUID("id")) {
                    UUID id = familiarEntry.getUUID("id");
                    CompoundTag data = familiarEntry.getCompound("data");
                    tamedFamiliars.put(id, data);
                }
            }
        }

        if (nbt.hasUUID("selectedFamiliar")) {
            selectedFamiliarId = nbt.getUUID("selectedFamiliar");
        }

        if (nbt.hasUUID("currentSummoned")) {
            currentSummonedFamiliarId = nbt.getUUID("currentSummoned");
        }

        if (nbt.contains("summonedFamiliars", Tag.TAG_LIST)) {
            ListTag summonedList = nbt.getList("summonedFamiliars", Tag.TAG_COMPOUND);
            for (int i = 0; i < summonedList.size(); i++) {
                CompoundTag summonedEntry = summonedList.getCompound(i);
                if (summonedEntry.hasUUID("summonedId")) {
                    UUID summonedId = summonedEntry.getUUID("summonedId");
                    summonedFamiliarIds.add(summonedId);
                }
            }
        }
    }
}