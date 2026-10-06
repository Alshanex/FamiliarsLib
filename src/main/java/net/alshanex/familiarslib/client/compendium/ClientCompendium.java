package net.alshanex.familiarslib.client.compendium;

import net.alshanex.familiarslib.compendium.CompendiumCatalog;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** Client copy of the compendium catalog and this player's unlocked looks. Plain data, safe to load anywhere. */
public final class ClientCompendium {
    private static volatile CompendiumCatalog catalog = CompendiumCatalog.EMPTY;
    private static volatile Set<String> discovered = Set.of();

    public static CompendiumCatalog catalog() {
        return catalog;
    }

    public static boolean isDiscovered(String key) {
        return discovered.contains(key);
    }

    public static void setCatalog(CompendiumCatalog newCatalog) {
        catalog = newCatalog;
    }

    public static void setDiscoveries(Collection<String> keys) {
        discovered = Set.copyOf(new HashSet<>(keys));
    }

    private ClientCompendium() {}
}
