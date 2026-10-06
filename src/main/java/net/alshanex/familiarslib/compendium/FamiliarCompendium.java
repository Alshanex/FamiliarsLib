package net.alshanex.familiarslib.compendium;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.CompendiumCatalogPacket;
import net.alshanex.familiarslib.network.CompendiumDiscoveriesPacket;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

/**
 * The familiar compendium: every color each layer of each registered familiar can have, and which ones each player has unlocked.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class FamiliarCompendium {
    private static final Map<Supplier<? extends EntityType<?>>, CompendiumProfile> PROFILES = new LinkedHashMap<>();

    private static volatile CompendiumCatalog catalog = CompendiumCatalog.EMPTY;

    /** Adds a familiar to the compendium. Call during mod setup; familiars are listed in registration order. */
    public static synchronized void register(Supplier<? extends EntityType<?>> type, CompendiumProfile profile) {
        PROFILES.put(type, profile);
    }

    public static CompendiumCatalog catalog() {
        return catalog;
    }

    // Building

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // After /reload: data (biome colors, overrides) and tags are loaded, so tag based rules resolve.
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isReady()) {
            rebuild(server);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        rebuild(event.getServer());
    }

    private static void rebuild(MinecraftServer server) {
        catalog = build(server);
        PacketDistributor.sendToAllPlayers(new CompendiumCatalogPacket(catalog));
    }

    /**
     * Every familiar in the game gets a section: registered ones (with color layers) first, in registration
     * order, then any other {@link AbstractSpellCastingPet}. The client hides familiars and tabs with nothing to show.
     */
    private static synchronized CompendiumCatalog build(MinecraftServer server) {
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Level level = server.overworld();

        Map<EntityType<?>, CompendiumProfile> profiles = new LinkedHashMap<>();
        PROFILES.forEach((type, profile) -> profiles.put(type.get(), profile));

        List<EntityType<?>> types = new ArrayList<>(profiles.keySet());
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!profiles.containsKey(type) && type.canSummon() && type.getCategory() != MobCategory.MISC) {
                types.add(type);
            }
        }

        List<CompendiumCatalog.Section> sections = new ArrayList<>();
        int total = 0; // layer colors
        for (EntityType<?> type : types) {
            AbstractSpellCastingPet familiar = createFamiliar(type, level);
            if (familiar == null) {
                continue; // not a familiar
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            CompendiumProfile profile = profiles.getOrDefault(type, CompendiumProfile.builder().build());
            CompendiumCatalog.Section section = CompendiumBuilder.build(id, type, profile, biomes, spellsOf(familiar));
            sections.add(section);
            total += section.optionCount();
        }
        FamiliarsLib.LOGGER.info("Familiar compendium: {} familiars, {} layer colors", sections.size(), total);
        return new CompendiumCatalog(List.copyOf(sections));
    }

    /** A temporary familiar (never added to the world), or null if this type isn't a familiar. */
    @Nullable
    private static AbstractSpellCastingPet createFamiliar(EntityType<?> type, Level level) {
        try {
            Entity entity = type.create(level);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                return familiar;
            }
            if (entity != null) {
                entity.discard();
            }
        } catch (Exception e) {
            FamiliarsLib.LOGGER.debug("Familiar compendium: skipping {}", BuiltInRegistries.ENTITY_TYPE.getKey(type), e);
        }
        return null;
    }

    /** The familiar's spells. Read on the server because spell tags only exist there. */
    private static List<ResourceLocation> spellsOf(AbstractSpellCastingPet familiar) {
        try {
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            for (AbstractSpell spell : familiar.getCompendiumSpells()) {
                if (spell != null && spell != SpellRegistry.none()) {
                    ids.add(spell.getSpellResource());
                }
            }
            return List.copyOf(ids);
        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Familiar compendium: couldn't read the spells of {}", BuiltInRegistries.ENTITY_TYPE.getKey(familiar.getType()), e);
            return List.of();
        }
    }

    // Discoveries

    /** Unlocks each of the familiar's current layer colors for this player, if they're in the compendium. */
    public static void discover(ServerPlayer player, AbstractSpellCastingPet familiar) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(familiar.getType());
        for (CompendiumCatalog.Section section : catalog.sections()) {
            if (!section.entityType().equals(typeId)) {
                continue;
            }
            CompendiumDiscoveries discoveries = player.getData(AttachmentRegistry.COMPENDIUM_DISCOVERIES);
            boolean changed = false;
            for (CompendiumCatalog.LayerOptions layer : section.layers()) {
                int color = familiar.getLayerColor(layer.slot());
                if (color >= 0) { // the original look is always unlocked
                    changed |= discoveries.add(CompendiumCatalog.key(typeId, layer.slot(), color));
                }
            }
            if (changed) {
                player.setData(AttachmentRegistry.COMPENDIUM_DISCOVERIES, discoveries); // marks it for saving
                sendDiscoveries(player);
            }
            return;
        }
    }

    public static void sendDiscoveries(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new CompendiumDiscoveriesPacket(List.copyOf(player.getData(AttachmentRegistry.COMPENDIUM_DISCOVERIES).keys())));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new CompendiumCatalogPacket(catalog));
            sendDiscoveries(player);
        }
    }

    private FamiliarCompendium() {}
}