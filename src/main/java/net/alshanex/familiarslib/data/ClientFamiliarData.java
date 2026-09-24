package net.alshanex.familiarslib.data;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Client-side mirror. Same read API the screens used on PlayerFamiliarData. Main thread only. */
public final class ClientFamiliarData {
    private static final ClientFamiliarData INSTANCE = new ClientFamiliarData();
    public static ClientFamiliarData get() { return INSTANCE; }

    private final List<UUID> order = new ArrayList<>();
    private final Map<UUID, CompoundTag> snapshots = new HashMap<>();
    private final Set<UUID> summoned = new HashSet<>();
    private UUID selected;
    private UUID currentSummoned;
    private int maxFamiliars = 10;

    public void applyState(List<UUID> newOrder, @Nullable UUID selected, @Nullable UUID currentSummoned,
                           Set<UUID> summoned, int maxFamiliars) {
        order.clear();
        order.addAll(newOrder);
        snapshots.keySet().retainAll(new HashSet<>(newOrder)); // drop released/dead/stored ones
        this.summoned.clear();
        this.summoned.addAll(summoned);
        this.selected = selected;
        this.currentSummoned = currentSummoned;
        this.maxFamiliars = maxFamiliars;
    }

    public void applySnapshots(boolean replaceAll, Map<UUID, CompoundTag> incoming) {
        if (replaceAll) snapshots.clear();
        snapshots.putAll(incoming);
    }

    public void removeTamedFamiliar(UUID id) {
        order.remove(id);
        snapshots.remove(id);
        summoned.remove(id);
        if (Objects.equals(selected, id)) selected = null;
        if (Objects.equals(currentSummoned, id)) currentSummoned = null;
    }

    public void clear() {
        order.clear();
        snapshots.clear();
        summoned.clear();
        selected = null;
        currentSummoned = null;
    }

    /** Familiars whose snapshot has arrived, in taming order. */
    public Map<UUID, CompoundTag> getAllFamiliars() {
        Map<UUID, CompoundTag> result = new LinkedHashMap<>();
        for (UUID id : order) {
            CompoundTag tag = snapshots.get(id);
            if (tag != null) result.put(id, tag);
        }
        return result;
    }

    @Nullable public CompoundTag getFamiliarData(UUID id) { return snapshots.get(id); }
    public boolean hasFamiliar(UUID id) { return order.contains(id); }
    public int getFamiliarCount() { return order.size(); }
    public boolean isEmpty() { return order.isEmpty(); }
    public int getMaxFamiliars() { return maxFamiliars; }
    public boolean canTameMoreFamiliars() { return order.size() < maxFamiliars; }
    public int getRemainingFamiliarSlots() { return Math.max(0, maxFamiliars - order.size()); }
    @Nullable public UUID getFamiliarIdByIndex(int i) { return i >= 0 && i < order.size() ? order.get(i) : null; }
    @Nullable public UUID getSelectedFamiliarId() { return selected; }
    @Nullable public UUID getCurrentSummonedFamiliarId() { return currentSummoned; }
    public Set<UUID> getSummonedFamiliarIds() { return new HashSet<>(summoned); }
    public boolean isFamiliarSummoned(UUID id) { return summoned.contains(id); }
}
