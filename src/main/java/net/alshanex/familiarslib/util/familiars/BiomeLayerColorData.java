package net.alshanex.familiarslib.util.familiars;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;

import java.util.*;

/**
 * Biome-based layer colors for one familiar, loaded from {@code data/<namespace>/<directory>/*.json}.
 * <p>
 * Each familiar declares which JSON fields map to which of its layer slots. For example a druid
 * with {@code "wood"} on slot 0 and {@code "leaf"} on slot 1 reads files like:
 * <pre>
 * {
 *   "entries": [
 *     { "biome": "minecraft:cherry_grove", "wood": "#5A3540", "leaf": "#F2A7C8" },
 *     { "biome": "#minecraft:is_forest", "wood": "#8B6A44", "leaf": "#6FAE3F" }
 *   ]
 * }
 * </pre>
 * <ul>
 *     <li>"biome" is a biome id, or a biome tag when it starts with '#'.</li>
 *     <li>Color fields are optional: a missing one keeps that layer's original look.
 *         Colors use {@link FamiliarColorCodecs#COLOR} ("#RRGGBB" or a dye name).</li>
 *     <li>Exact biome entries win over tag entries. Later files and later entries override earlier ones.</li>
 *     <li>A biome with no matching entry keeps the original look.</li>
 * </ul>
 */
public class BiomeLayerColorData extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    // JSON color field and the layer slot it sets
    public record Layer(String field, int slot) {}

    // One parsed entry: layer slot -> 0xRRGGBB
    private record Entry(Map<Integer, Integer> colors) {}

    private final String directory;
    private final List<Layer> layers;

    private volatile Map<ResourceKey<Biome>, Entry> byBiome = Map.of();
    private volatile List<Map.Entry<TagKey<Biome>, Entry>> byTag = List.of();

    public BiomeLayerColorData(String directory, Layer... layers) {
        super(GSON, directory);
        this.directory = directory;
        this.layers = List.of(layers);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceKey<Biome>, Entry> biomes = new HashMap<>();
        List<Map.Entry<TagKey<Biome>, Entry>> tags = new ArrayList<>();

        // Sorted so the result doesn't depend on hash order
        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
            ResourceLocation fileId = file.getKey();
            if (!file.getValue().isJsonObject() || !file.getValue().getAsJsonObject().has("entries")
                    || !file.getValue().getAsJsonObject().get("entries").isJsonArray()) {
                FamiliarsLib.LOGGER.error("{} file {}: expected an object with an \"entries\" array", directory, fileId);
                continue;
            }

            int index = 0;
            for (JsonElement element : file.getValue().getAsJsonObject().getAsJsonArray("entries")) {
                parseEntry(fileId, index++, element, biomes, tags);
            }
        }

        this.byBiome = Map.copyOf(biomes);
        this.byTag = List.copyOf(tags);
        FamiliarsLib.LOGGER.info("Loaded {} biome and {} biome tag colors from {}", biomes.size(), tags.size(), directory);
    }

    private void parseEntry(ResourceLocation fileId, int index, JsonElement element,
                            Map<ResourceKey<Biome>, Entry> biomes, List<Map.Entry<TagKey<Biome>, Entry>> tags) {
        if (!element.isJsonObject()) {
            FamiliarsLib.LOGGER.error("{} file {}, entry {}: expected an object", directory, fileId, index);
            return;
        }
        JsonObject json = element.getAsJsonObject();

        if (!json.has("biome") || !json.get("biome").isJsonPrimitive()) {
            FamiliarsLib.LOGGER.error("{} file {}, entry {}: missing \"biome\"", directory, fileId, index);
            return;
        }
        String biome = json.get("biome").getAsString();
        boolean isTag = biome.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? biome.substring(1) : biome);
        if (id == null) {
            FamiliarsLib.LOGGER.error("{} file {}, entry {}: invalid biome '{}'", directory, fileId, index, biome);
            return;
        }

        Map<Integer, Integer> colors = new HashMap<>();
        for (Layer layer : layers) {
            if (!json.has(layer.field())) {
                continue;
            }
            FamiliarColorCodecs.COLOR.parse(JsonOps.INSTANCE, json.get(layer.field()))
                    .resultOrPartial(error -> FamiliarsLib.LOGGER.error("{} file {}, entry {}, \"{}\": {}",
                            directory, fileId, index, layer.field(), error))
                    .ifPresent(rgb -> colors.put(layer.slot(), rgb));
        }

        Entry entry = new Entry(Map.copyOf(colors));
        if (isTag) {
            tags.add(Map.entry(TagKey.create(Registries.BIOME, id), entry));
        } else {
            biomes.put(ResourceKey.create(Registries.BIOME, id), entry);
        }
    }

    /** Applies the colors for the biome the familiar is standing in, if there is an entry for it. */
    public void applyTo(AbstractSpellCastingPet familiar, LevelReader level) {
        Holder<Biome> biome = level.getBiome(familiar.blockPosition());

        Entry entry = biome.unwrapKey().map(byBiome::get).orElse(null);
        if (entry == null) {
            List<Map.Entry<TagKey<Biome>, Entry>> tags = byTag;
            for (int i = tags.size() - 1; i >= 0; i--) { // last one wins
                if (biome.is(tags.get(i).getKey())) {
                    entry = tags.get(i).getValue();
                    break;
                }
            }
        }
        if (entry == null) {
            return;
        }

        entry.colors().forEach(familiar::setLayerColor);
    }
}