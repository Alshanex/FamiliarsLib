package net.alshanex.familiarslib.data;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Familiars that were released or died in one level, kept per owner so they can be brought back with a Recall Shard.
 * Each level has its own copy, so a familiar can only be recalled in the dimension where it was lost.
 */
public class RecallableFamiliarsData extends SavedData {
    private static final String NAME = FamiliarsLib.MODID + "_recallable_familiars";

    /** Oldest entries are dropped past this, per player and per level. */
    public static final int MAX_PER_PLAYER = 20;

    public enum Kind { RELEASED, DEAD }

    /** {@code data} is the familiar's saved NBT, ready to go back into the owner's roster. */
    public record Entry(UUID familiarId, CompoundTag data, Kind kind, long gameTime) {}

    private final Map<UUID, List<Entry>> byOwner = new HashMap<>();

    public static RecallableFamiliarsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RecallableFamiliarsData::new, RecallableFamiliarsData::load, null), NAME);
    }

    public void add(UUID owner, UUID familiarId, CompoundTag data, Kind kind, long gameTime) {
        List<Entry> entries = byOwner.computeIfAbsent(owner, id -> new ArrayList<>());
        entries.removeIf(e -> e.familiarId().equals(familiarId));
        entries.add(new Entry(familiarId, data.copy(), kind, gameTime));
        while (entries.size() > MAX_PER_PLAYER) {
            entries.remove(0);
        }
        setDirty();
    }

    /** The owner's entries, newest first. */
    public List<Entry> entriesFor(UUID owner) {
        List<Entry> entries = new ArrayList<>(byOwner.getOrDefault(owner, List.of()));
        Collections.reverse(entries);
        return entries;
    }

    public Optional<Entry> find(UUID owner, UUID familiarId) {
        return byOwner.getOrDefault(owner, List.of()).stream()
                .filter(e -> e.familiarId().equals(familiarId))
                .findFirst();
    }

    public void remove(UUID owner, UUID familiarId) {
        List<Entry> entries = byOwner.get(owner);
        if (entries != null && entries.removeIf(e -> e.familiarId().equals(familiarId))) {
            if (entries.isEmpty()) {
                byOwner.remove(owner);
            }
            setDirty();
        }
    }

    // Save / load

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag players = new ListTag();
        byOwner.forEach((owner, entries) -> {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("owner", owner);
            ListTag list = new ListTag();
            for (Entry entry : entries) {
                CompoundTag e = new CompoundTag();
                e.putUUID("id", entry.familiarId());
                e.putString("kind", entry.kind().name());
                e.putLong("time", entry.gameTime());
                e.put("data", entry.data());
                list.add(e);
            }
            playerTag.put("entries", list);
            players.add(playerTag);
        });
        tag.put("players", players);
        return tag;
    }

    private static RecallableFamiliarsData load(CompoundTag tag, HolderLookup.Provider registries) {
        RecallableFamiliarsData result = new RecallableFamiliarsData();
        for (Tag p : tag.getList("players", Tag.TAG_COMPOUND)) {
            CompoundTag playerTag = (CompoundTag) p;
            if (!playerTag.hasUUID("owner")) continue;
            List<Entry> entries = new ArrayList<>();
            for (Tag e : playerTag.getList("entries", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) e;
                if (!entry.hasUUID("id")) continue;
                Kind kind;
                try {
                    kind = Kind.valueOf(entry.getString("kind"));
                } catch (IllegalArgumentException ex) {
                    kind = Kind.RELEASED;
                }
                entries.add(new Entry(entry.getUUID("id"), entry.getCompound("data"), kind, entry.getLong("time")));
            }
            if (!entries.isEmpty()) {
                result.byOwner.put(playerTag.getUUID("owner"), entries);
            }
        }
        return result;
    }
}