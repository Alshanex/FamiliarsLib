package net.alshanex.familiarslib.compendium;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.BiomeLayerColorData;
import net.alshanex.familiarslib.util.familiars.FamiliarColorOverrides;
import net.alshanex.familiarslib.util.familiars.FamiliarDyes;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.*;

/**
 * Works out every color each layer of a familiar can have, and every way to get it.
 * The original look is the base of every familiar, so it isn't listed as an option.
 */
final class CompendiumBuilder {
    /** How many biome names are listed per origin before "and N more". */
    static final int MAX_LISTED_BIOMES = 6;

    /** Collects one layer's options while building. */
    private static final class OptionBuilder {
        final List<CompendiumCatalog.Origin> origins = new ArrayList<>();
        final List<CompendiumCatalog.Step> steps = new ArrayList<>();
    }

    static CompendiumCatalog.Section build(ResourceLocation typeId, EntityType<?> type, CompendiumProfile profile, Registry<Biome> biomes,
                                           List<ResourceLocation> spells) {
        List<CompendiumProfile.Layer> layers = profile.layers();
        if (layers.isEmpty()) {
            return new CompendiumCatalog.Section(typeId, List.of(), List.copyOf(spells));
        }
        // Per layer: color -> how to get it (insertion order = display order)
        List<Map<Integer, OptionBuilder>> options = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            options.add(new LinkedHashMap<>());
        }

        // Natural random dyes
        if (profile.spawnsWithRandomDye()) {
            int dyeIndex = indexOfSlot(layers, AbstractSpellCastingPet.DYE_LAYER_SLOT);
            if (dyeIndex >= 0) {
                for (DyeColor dye : DyeColor.values()) {
                    option(options, dyeIndex, dye.getTextureDiffuseColor() & 0xFFFFFF).origins
                            .add(new CompendiumCatalog.Origin(CompendiumCatalog.OriginKind.NATURAL_DYE, dye.getName(), List.of(), 0));
                }
            }
        }

        // Biomes
        if (!profile.biomeData().isEmpty()) {
            // Per layer: color -> biomes that give it
            List<Map<Integer, List<ResourceLocation>>> byColor = new ArrayList<>();
            for (int i = 0; i < layers.size(); i++) {
                byColor.add(new LinkedHashMap<>());
            }
            for (Holder.Reference<Biome> biome : biomes.holders().toList()) {
                Map<Integer, Integer> colors = new HashMap<>();
                for (BiomeLayerColorData data : profile.biomeData()) {
                    colors.putAll(data.colorsFor(biome)); // later data wins, like applyTo order
                }
                colors.forEach((slot, rgb) -> {
                    int index = indexOfSlot(layers, slot);
                    if (index >= 0 && rgb >= 0) {
                        byColor.get(index).computeIfAbsent(rgb, c -> new ArrayList<>()).add(biome.key().location());
                    }
                });
            }
            for (int i = 0; i < layers.size(); i++) {
                int index = i;
                byColor.get(i).forEach((rgb, biomeIds) -> {
                    List<ResourceLocation> sorted = biomeIds.stream().sorted().toList();
                    List<ResourceLocation> listed = sorted.subList(0, Math.min(MAX_LISTED_BIOMES, sorted.size()));
                    option(options, index, rgb).origins.add(new CompendiumCatalog.Origin(
                            CompendiumCatalog.OriginKind.BIOMES, "", List.copyOf(listed), sorted.size() - listed.size()));
                });
            }
        }

        // Items: group by identical effect, then add one step per layer color the effect sets
        record Effect(Map<Integer, Integer> colors, boolean shrunk, boolean sneaking) {}
        Map<Effect, List<Item>> grouped = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);

            // As a normal item: override rules, then dyes on the dye slot
            Map<Integer, Integer> effect = FamiliarColorOverrides.INSTANCE.find(type, stack)
                    .map(FamiliarColorOverrides.Rule::layers)
                    .orElse(null);
            if (effect == null && profile.dyeable() && item instanceof DyeItem dyeItem) {
                effect = Map.of(AbstractSpellCastingPet.DYE_LAYER_SLOT, color(dyeItem));
            }
            addEffect(grouped, effect, false, false, item, Effect::new);

            // As a shrunk dye: "shrunk" override rules, then dyes on the eye slots
            if (FamiliarDyes.canBeShrunk(stack)) {
                ItemStack shrunk = FamiliarDyes.shrink(stack);
                Map<Integer, Integer> shrunkEffect = FamiliarColorOverrides.INSTANCE.find(type, shrunk)
                        .map(FamiliarColorOverrides.Rule::layers)
                        .orElse(null);
                if (shrunkEffect == null && item instanceof DyeItem dyeItem) {
                    if (profile.eyeDyeSlot() >= 0) {
                        addEffect(grouped, Map.of(profile.eyeDyeSlot(), color(dyeItem)), true, false, item, Effect::new);
                    }
                    if (profile.sneakingEyeDyeSlot() >= 0) {
                        addEffect(grouped, Map.of(profile.sneakingEyeDyeSlot(), color(dyeItem)), true, true, item, Effect::new);
                    }
                } else {
                    addEffect(grouped, shrunkEffect, true, false, item, Effect::new);
                }
            }
        }
        grouped.forEach((effect, items) -> {
            CompendiumCatalog.Step step = new CompendiumCatalog.Step(
                    BuiltInRegistries.ITEM.getKey(items.getFirst()), items.size() - 1, effect.shrunk(), effect.sneaking());
            effect.colors().forEach((slot, rgb) -> {
                int index = indexOfSlot(layers, slot);
                if (index >= 0 && rgb >= 0) { // resetting to the original look isn't an option
                    option(options, index, rgb).steps.add(step);
                }
            });
        });

        // Assemble
        List<CompendiumCatalog.LayerOptions> result = new ArrayList<>();
        for (int i = 0; i < layers.size(); i++) {
            List<CompendiumCatalog.ColorOption> list = new ArrayList<>();
            options.get(i).forEach((rgb, builder) ->
                    list.add(new CompendiumCatalog.ColorOption(rgb, List.copyOf(builder.origins), List.copyOf(builder.steps))));
            CompendiumProfile.Layer layer = layers.get(i);
            result.add(new CompendiumCatalog.LayerOptions(layer.slot(), layer.nameKey(), List.copyOf(list)));
        }
        return new CompendiumCatalog.Section(typeId, List.copyOf(result), List.copyOf(spells));
    }

    private static int color(DyeItem dyeItem) {
        return dyeItem.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
    }

    private interface EffectFactory<E> {
        E create(Map<Integer, Integer> colors, boolean shrunk, boolean sneaking);
    }

    private static <E> void addEffect(Map<E, List<Item>> grouped, Map<Integer, Integer> colors, boolean shrunk,
                                      boolean sneaking, Item item, EffectFactory<E> factory) {
        if (colors != null && !colors.isEmpty()) {
            grouped.computeIfAbsent(factory.create(new TreeMap<>(colors), shrunk, sneaking), e -> new ArrayList<>()).add(item);
        }
    }

    private static OptionBuilder option(List<Map<Integer, OptionBuilder>> options, int layerIndex, int rgb) {
        return options.get(layerIndex).computeIfAbsent(rgb, c -> new OptionBuilder());
    }

    private static int indexOfSlot(List<CompendiumProfile.Layer> layers, int slot) {
        for (int i = 0; i < layers.size(); i++) {
            if (layers.get(i).slot() == slot) return i;
        }
        return -1;
    }

    private CompendiumBuilder() {}
}