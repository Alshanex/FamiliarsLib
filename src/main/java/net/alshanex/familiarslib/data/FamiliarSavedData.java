package net.alshanex.familiarslib.data;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Per-player familiar data, stored in the overworld's data folder so it is reachable from every dimension and even while the owner is offline.
 *
 * Holds two lists:
 *  - carried: familiars the player has with them (counted against the cap, quick-summon order)
 *  - housed: familiars that belong to a storage block, inside it or wandering around it
 */
public class FamiliarSavedData extends SavedData {
    private static final String PREFIX = FamiliarsLib.MODID + "_roster_";

    /**
     * A familiar that belongs to a storage block.
     * outside = true means it's wandering in the world (wander mode) and the entity is the live copy;
     * data is then the last snapshot taken of it.
     */
    public record HousedFamiliar(ResourceKey<Level> dimension, BlockPos pos, boolean outside, CompoundTag data) {
        public boolean isAt(ResourceKey<Level> dim, BlockPos blockPos) {
            return dimension.equals(dim) && pos.equals(blockPos);
        }
    }

    // LinkedHashMap keeps taming order (quick-summon slots rely on it)
    private final LinkedHashMap<UUID, CompoundTag> familiars = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, HousedFamiliar> housed = new LinkedHashMap<>();

    public static FamiliarSavedData create() {
        return new FamiliarSavedData();
    }

    public static FamiliarSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        FamiliarSavedData data = create();

        ListTag list = tag.getList("familiars", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (entry.hasUUID("id")) {
                data.familiars.put(entry.getUUID("id"), entry.getCompound("data"));
            }
        }

        ListTag housedList = tag.getList("housed", Tag.TAG_COMPOUND);
        for (int i = 0; i < housedList.size(); i++) {
            CompoundTag entry = housedList.getCompound(i);
            if (entry.hasUUID("id")) {
                ResourceLocation dimId = ResourceLocation.tryParse(entry.getString("dimension"));
                if (dimId == null) continue;
                data.housed.put(entry.getUUID("id"), new HousedFamiliar(
                        ResourceKey.create(Registries.DIMENSION, dimId),
                        BlockPos.of(entry.getLong("pos")),
                        entry.getBoolean("outside"),
                        entry.getCompound("data")));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        familiars.forEach((id, nbt) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.put("data", nbt);
            list.add(entry);
        });
        tag.put("familiars", list);

        ListTag housedList = new ListTag();
        housed.forEach((id, h) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            entry.putString("dimension", h.dimension().location().toString());
            entry.putLong("pos", h.pos().asLong());
            entry.putBoolean("outside", h.outside());
            entry.put("data", h.data());
            housedList.add(entry);
        });
        tag.put("housed", housedList);
        return tag;
    }

    public static FamiliarSavedData get(ServerPlayer player) {
        return get(player.server, player.getUUID());
    }

    public static FamiliarSavedData get(MinecraftServer server, UUID owner) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(FamiliarSavedData::create, FamiliarSavedData::load), PREFIX + owner);
    }

    // Carried familiars

    public boolean contains(UUID id) { return familiars.containsKey(id); }
    public int size() { return familiars.size(); }
    public boolean isEmpty() { return familiars.isEmpty(); }

    /** Returns a copy; changing it has no effect unless you put() it back. */
    @Nullable
    public CompoundTag getCopy(UUID id) {
        CompoundTag tag = familiars.get(id);
        return tag == null ? null : tag.copy();
    }

    /** Read-only live view, in taming order. Do NOT mutate the tags. */
    public Map<UUID, CompoundTag> view() {
        return Collections.unmodifiableMap(familiars);
    }

    public List<UUID> ids() {
        return new ArrayList<>(familiars.keySet());
    }

    @Nullable
    public UUID idAt(int index) {
        if (index < 0 || index >= familiars.size()) return null;
        int i = 0;
        for (UUID id : familiars.keySet()) {
            if (i++ == index) return id;
        }
        return null;
    }

    /** Insert or update. Updating an existing ID keeps its position. */
    public void put(UUID id, CompoundTag data) {
        familiars.put(id, data);
        setDirty();
    }

    public boolean putIfAbsent(UUID id, CompoundTag data) {
        if (familiars.putIfAbsent(id, data) == null) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean remove(UUID id) {
        if (familiars.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    // Housed familiars

    public boolean isHoused(UUID id) { return housed.containsKey(id); }

    @Nullable
    public HousedFamiliar getHoused(UUID id) { return housed.get(id); }

    /** Copy of a housed familiar's snapshot. */
    @Nullable
    public CompoundTag getHousedData(UUID id) {
        HousedFamiliar h = housed.get(id);
        return h == null ? null : h.data().copy();
    }

    public boolean isHousedAt(UUID id, ResourceKey<Level> dimension, BlockPos pos) {
        HousedFamiliar h = housed.get(id);
        return h != null && h.isAt(dimension, pos);
    }

    /** Carried -> stored inside the house at dimension/pos. */
    public boolean moveToHouse(UUID id, ResourceKey<Level> dimension, BlockPos pos) {
        CompoundTag data = familiars.remove(id);
        if (data == null) return false;
        data.putBoolean("isInHouse", true);
        housed.put(id, new HousedFamiliar(dimension, pos.immutable(), false, data));
        setDirty();
        return true;
    }

    /** Housed -> carried. No cap check: callers check before (or skip it on purpose). */
    public boolean moveToCarried(UUID id) {
        HousedFamiliar h = housed.remove(id);
        if (h == null) return false;
        CompoundTag data = h.data();
        data.putBoolean("isInHouse", false);
        familiars.put(id, data);
        setDirty();
        return true;
    }

    /** Insert or update a housed familiar (recall, wander-mode snapshot, legacy migration). */
    public void putHoused(UUID id, ResourceKey<Level> dimension, BlockPos pos, boolean outside, CompoundTag data) {
        familiars.remove(id);
        housed.put(id, new HousedFamiliar(dimension, pos.immutable(), outside, data));
        setDirty();
    }

    /** Legacy migration: only adds it if this familiar isn't known yet. */
    public boolean putHousedIfAbsent(UUID id, ResourceKey<Level> dimension, BlockPos pos, boolean outside, CompoundTag data) {
        if (housed.containsKey(id) || familiars.containsKey(id)) return false;
        putHoused(id, dimension, pos, outside, data);
        return true;
    }

    /** Switches a housed familiar between inside and wandering, keeping its snapshot. */
    public void setHousedOutside(UUID id, boolean outside) {
        HousedFamiliar h = housed.get(id);
        if (h != null && h.outside() != outside) {
            housed.put(id, new HousedFamiliar(h.dimension(), h.pos(), outside, h.data()));
            setDirty();
        }
    }

    public boolean removeHoused(UUID id) {
        if (housed.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }

    /** Snapshots of the familiars that belong to the house at dimension/pos (copies). */
    public Map<UUID, CompoundTag> housedAt(ResourceKey<Level> dimension, BlockPos pos) {
        Map<UUID, CompoundTag> result = new LinkedHashMap<>();
        housed.forEach((id, h) -> {
            if (h.isAt(dimension, pos)) result.put(id, h.data().copy());
        });
        return result;
    }

    /** Everything that belongs to one house goes back to carried (house broken). */
    public List<UUID> returnAllFromHouse(ResourceKey<Level> dimension, BlockPos pos) {
        List<UUID> ids = housed.entrySet().stream()
                .filter(e -> e.getValue().isAt(dimension, pos))
                .map(Map.Entry::getKey)
                .toList();
        ids.forEach(this::moveToCarried);
        return ids;
    }

    /** Every housed familiar back to carried. */
    public List<UUID> returnAllHoused() {
        List<UUID> ids = new ArrayList<>(housed.keySet());
        ids.forEach(this::moveToCarried);
        return ids;
    }
}