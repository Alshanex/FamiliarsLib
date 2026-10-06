package net.alshanex.familiarslib.compendium;

import com.mojang.serialization.Codec;

import java.util.*;

/** The looks a player has unlocked in the compendium (see {@link CompendiumCatalog#key}). Saved on the player. */
public final class CompendiumDiscoveries {
    public static final Codec<CompendiumDiscoveries> CODEC = Codec.STRING.listOf().xmap(
            list -> new CompendiumDiscoveries(new HashSet<>(list)),
            discoveries -> new ArrayList<>(discoveries.keys));

    private final Set<String> keys;

    public CompendiumDiscoveries() {
        this(new HashSet<>());
    }

    private CompendiumDiscoveries(Set<String> keys) {
        this.keys = keys;
    }

    /** @return true if this look was new */
    public boolean add(String key) {
        return keys.add(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(keys);
    }
}
