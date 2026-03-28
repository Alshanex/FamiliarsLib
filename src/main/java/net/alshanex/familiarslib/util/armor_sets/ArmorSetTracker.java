package net.alshanex.familiarslib.util.armor_sets;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Tracks which registered armor sets each player currently has fully equipped.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ArmorSetTracker {

    private static final List<ArmorSetDefinition> REGISTERED_SETS = new ArrayList<>();

    private static final Map<UUID, Set<Integer>> activeSets = new HashMap<>();

    private static final int CHECK_INTERVAL = 10;

    public static void register(ArmorSetDefinition definition) {
        REGISTERED_SETS.add(definition);
        FamiliarsLib.LOGGER.debug("Registered armor set: {}", definition.getName());
    }

    public static void register(String name,
                                Supplier<? extends Item> helmet,
                                Supplier<? extends net.minecraft.world.item.Item> chestplate,
                                Supplier<? extends net.minecraft.world.item.Item> leggings,
                                Supplier<? extends net.minecraft.world.item.Item> boots) {
        register(new ArmorSetDefinition(name, helmet, chestplate, leggings, boots));
    }

    public static boolean isWearingSet(ServerPlayer player, ArmorSetDefinition setDef) {
        Set<Integer> active = activeSets.get(player.getUUID());
        if (active == null) return false;
        int index = REGISTERED_SETS.indexOf(setDef);
        return index >= 0 && active.contains(index);
    }

    public static List<String> getActiveSetNames(ServerPlayer player) {
        Set<Integer> active = activeSets.get(player.getUUID());
        if (active == null || active.isEmpty()) return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (int i : active) {
            if (i < REGISTERED_SETS.size()) {
                names.add(REGISTERED_SETS.get(i).getName());
            }
        }
        return names;
    }

    public static List<ArmorSetDefinition> getRegisteredSets() {
        return Collections.unmodifiableList(REGISTERED_SETS);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;
        if (serverPlayer.tickCount % CHECK_INTERVAL != 0) return;
        if (REGISTERED_SETS.isEmpty()) return;

        Set<Integer> current = activeSets.computeIfAbsent(serverPlayer.getUUID(), k -> new HashSet<>());
        for (int i = 0; i < REGISTERED_SETS.size(); i++) {
            boolean equipped = REGISTERED_SETS.get(i).isFullSetEquipped(serverPlayer);
            if (equipped) {
                current.add(i);
            } else {
                current.remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        activeSets.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        activeSets.remove(event.getOriginal().getUUID());
    }
}
