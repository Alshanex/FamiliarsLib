package net.alshanex.familiarslib.data;

import net.alshanex.familiarslib.FamiliarsServerConfig;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/** Server-side view combining the player's state attachment and their SavedData roster. */
public final class FamiliarRoster {
    private final PlayerFamiliarData state;
    private final FamiliarSavedData store;

    private FamiliarRoster(PlayerFamiliarData state, FamiliarSavedData store) {
        this.state = state;
        this.store = store;
    }

    public static FamiliarRoster of(ServerPlayer player) {
        return new FamiliarRoster(player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA),
                FamiliarSavedData.get(player));
    }

    public static int maxFamiliars() {
        return FamiliarsServerConfig.SPEC.isLoaded()
                ? FamiliarsServerConfig.MAX_FAMILIARS.get()
                : FamiliarsServerConfig.MAX_FAMILIARS.getDefault();
    }

    public boolean canTameMoreFamiliars() { return store.size() < maxFamiliars(); }
    public int getRemainingFamiliarSlots() { return Math.max(0, maxFamiliars() - store.size()); }
    public boolean isAtMaxCapacity() { return store.size() >= maxFamiliars(); }

    /** Insert or update, no cap check (use for updates and returns that must never fail). */
    public void addTamedFamiliar(UUID id, CompoundTag data) { store.put(id, data); }

    /** Insert with cap check. Updating an already-owned familiar always succeeds. */
    public boolean tryAddTamedFamiliar(UUID id, CompoundTag data) {
        if (!store.contains(id) && !canTameMoreFamiliars()) return false;
        store.put(id, data);
        return true;
    }

    public void removeTamedFamiliar(UUID id) {
        boolean wasSelected = id.equals(state.getSelectedFamiliarId());
        store.remove(id);
        state.forget(id);
        if (wasSelected && !store.isEmpty()) {
            state.setSelectedFamiliarId(store.idAt(0));
        }
    }

    public void retainSummoned(Predicate<UUID> keep) {
        state.retainSummoned(keep);
    }

    public boolean hasFamiliar(UUID id) { return store.contains(id); }

    /** Returns a COPY. If you change it and want the change kept, call addTamedFamiliar(id, tag). */
    @Nullable
    public CompoundTag getFamiliarData(UUID id) { return store.getCopy(id); }

    /** Shallow copy in taming order. Treat the tags as read-only. */
    public Map<UUID, CompoundTag> getAllFamiliars() { return new LinkedHashMap<>(store.view()); }

    public List<UUID> getFamiliarIds() { return store.ids(); }
    public int getFamiliarCount() { return store.size(); }
    public boolean isEmpty() { return store.isEmpty(); }
    @Nullable public UUID getFamiliarIdByIndex(int index) { return store.idAt(index); }

    public void markDirty() { store.setDirty(); }

    @Nullable public UUID getSelectedFamiliarId() { return state.getSelectedFamiliarId(); }
    public void setSelectedFamiliarId(@Nullable UUID id) { state.setSelectedFamiliarId(id); }
    @Nullable public UUID getCurrentSummonedFamiliarId() { return state.getCurrentSummonedFamiliarId(); }
    public void setCurrentSummonedFamiliarId(@Nullable UUID id) { state.setCurrentSummonedFamiliarId(id); }
    public void addSummonedFamiliar(UUID id) { state.addSummonedFamiliar(id); }
    public void removeSummonedFamiliar(UUID id) { state.removeSummonedFamiliar(id); }
    public boolean isFamiliarSummoned(UUID id) { return state.isFamiliarSummoned(id); }
    public Set<UUID> getSummonedFamiliarIds() { return state.getSummonedFamiliarIds(); }
    public int getSummonedFamiliarCount() { return state.getSummonedFamiliarCount(); }
    public void clearAllSummoned() { state.clearAllSummoned(); }

    /** Repairs state that points at familiars no longer in the roster. */
    public void validate() {
        UUID selected = state.getSelectedFamiliarId();
        if (selected != null && !store.contains(selected)) state.setSelectedFamiliarId(null);
        if (state.getSelectedFamiliarId() == null && !store.isEmpty()) state.setSelectedFamiliarId(store.idAt(0));
        state.retainSummoned(store::contains);
    }
}
