package net.alshanex.familiarslib.data;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Per-player familiar roster, stored in the overworld's data folder so it is reachable from every dimension and even while the owner is offline.
 */
public class FamiliarSavedData extends SavedData {
    private static final String PREFIX = FamiliarsLib.MODID + "_roster_";

    // LinkedHashMap keeps taming order (quick-summon slots rely on it)
    private final LinkedHashMap<UUID, CompoundTag> familiars = new LinkedHashMap<>();

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
        return tag;
    }

    public static FamiliarSavedData get(ServerPlayer player) {
        return get(player.server, player.getUUID());
    }

    public static FamiliarSavedData get(MinecraftServer server, UUID owner) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new Factory<>(FamiliarSavedData::create, FamiliarSavedData::load), PREFIX + owner);
    }

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
}
