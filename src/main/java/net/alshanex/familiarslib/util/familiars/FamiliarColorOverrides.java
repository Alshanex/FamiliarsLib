package net.alshanex.familiarslib.util.familiars;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.render.LayerColorHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.function.Predicate;

/**
 * Data-driven "use item on familiar -> recolor layers" rules, loaded from
 * {@code data/<namespace>/familiar_color_overrides/*.json}.
 */
public class FamiliarColorOverrides extends SimplePreparableReloadListener<Map<ResourceLocation, List<Pair<String, JsonElement>>>> {
    public static final FamiliarColorOverrides INSTANCE = new FamiliarColorOverrides();
    public static final String DIRECTORY = "familiar_color_overrides";

    private static final FileToIdConverter FILES = FileToIdConverter.json(DIRECTORY);

    private static final Codec<List<String>> STRING_OR_LIST = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
            either -> either.map(List::of, list -> list),
            list -> list.size() == 1 ? Either.left(list.get(0)) : Either.right(list));

    private static final Codec<Integer> SLOT = Codec.STRING.comapFlatMap(value -> {
        try {
            int slot = Integer.parseInt(value.trim());
            if (slot >= 0 && slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS) {
                return DataResult.success(slot);
            }
        } catch (NumberFormatException ignored) {
        }
        return DataResult.error(() -> "Invalid layer slot '" + value + "': expected 0 to "
                + (AbstractSpellCastingPet.LAYER_COLOR_SLOTS - 1));
    }, String::valueOf);

    private static final Codec<Integer> DEFAULT_KEYWORD = Codec.STRING.comapFlatMap(
            value -> value.trim().equalsIgnoreCase("default")
                    ? DataResult.success(LayerColorHolder.NO_LAYER_COLOR)
                    : DataResult.error(() -> "not \"default\""),
            rgb -> "default");

    /** A color, or "default" for the original layer. */
    private static final Codec<Integer> LAYER_COLOR = Codec.either(DEFAULT_KEYWORD, FamiliarColorCodecs.COLOR).xmap(
            either -> either.map(rgb -> rgb, rgb -> rgb),
            rgb -> rgb == LayerColorHolder.NO_LAYER_COLOR ? Either.left(rgb) : Either.right(rgb));

    private record RawEntry(List<String> familiars, List<String> items, Map<Integer, Integer> layers,
                            boolean consume, Optional<ResourceLocation> sound) {
        static final Codec<RawEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                STRING_OR_LIST.fieldOf("familiar").forGetter(RawEntry::familiars),
                STRING_OR_LIST.fieldOf("item").forGetter(RawEntry::items),
                Codec.unboundedMap(SLOT, LAYER_COLOR).fieldOf("layers").forGetter(RawEntry::layers),
                Codec.BOOL.optionalFieldOf("consume", true).forGetter(RawEntry::consume),
                ResourceLocation.CODEC.optionalFieldOf("sound").forGetter(RawEntry::sound)
        ).apply(instance, RawEntry::new));
    }

    private record RawFile(boolean replace, List<RawEntry> entries) {
        static final Codec<RawFile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(RawFile::replace),
                RawEntry.CODEC.listOf().fieldOf("entries").forGetter(RawFile::entries)
        ).apply(instance, RawFile::new));
    }

    // Compiled rules

    /** One loaded rule. */
    public record Rule(List<Predicate<EntityType<?>>> familiars, List<Predicate<ItemStack>> items,
                       Map<Integer, Integer> layers, boolean consume, Optional<ResourceLocation> sound) {
        public boolean matches(AbstractSpellCastingPet familiar, ItemStack stack) {
            return matches(familiar.getType(), stack);
        }

        public boolean matches(EntityType<?> type, ItemStack stack) {
            return familiars.stream().anyMatch(p -> p.test(type)) && items.stream().anyMatch(p -> p.test(stack));
        }

        /** True if applying this rule would change at least one layer. */
        public boolean wouldChange(AbstractSpellCastingPet familiar) {
            return layers.entrySet().stream().anyMatch(e -> familiar.getLayerColor(e.getKey()) != e.getValue());
        }
    }

    private volatile List<Rule> rules = List.of();

    private FamiliarColorOverrides() {}

    /** The last loaded rule matching this familiar and item, if any. */
    public Optional<Rule> find(AbstractSpellCastingPet familiar, ItemStack stack) {
        return find(familiar.getType(), stack);
    }

    /** The last loaded rule matching this familiar type and item, if any. */
    public Optional<Rule> find(EntityType<?> type, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        List<Rule> list = rules;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).matches(type, stack)) {
                return Optional.of(list.get(i));
            }
        }
        return Optional.empty();
    }

    /** Reads every copy of every file, lowest priority pack first (same order tags use). */
    @Override
    protected Map<ResourceLocation, List<Pair<String, JsonElement>>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, List<Pair<String, JsonElement>>> files = new TreeMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> stack : FILES.listMatchingResourceStacks(resourceManager).entrySet()) {
            ResourceLocation fileId = FILES.fileToId(stack.getKey());
            List<Pair<String, JsonElement>> copies = new ArrayList<>();
            for (Resource resource : stack.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    copies.add(Pair.of(resource.sourcePackId(), JsonParser.parseReader(reader)));
                } catch (IOException | JsonParseException e) {
                    FamiliarsLib.LOGGER.error("Couldn't read familiar color overrides {} from {}", fileId, resource.sourcePackId(), e);
                }
            }
            files.put(fileId, copies);
        }
        return files;
    }

    @Override
    protected void apply(Map<ResourceLocation, List<Pair<String, JsonElement>>> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<Rule> loaded = new ArrayList<>();

        for (Map.Entry<ResourceLocation, List<Pair<String, JsonElement>>> file : files.entrySet()) {
            ResourceLocation fileId = file.getKey();
            List<Rule> fileOverrides = new ArrayList<>();

            for (Pair<String, JsonElement> copy : file.getValue()) {
                String pack = copy.getFirst();
                RawFile.CODEC.parse(JsonOps.INSTANCE, copy.getSecond())
                        .resultOrPartial(error -> FamiliarsLib.LOGGER.error("Familiar color overrides {} from {}: {}", fileId, pack, error))
                        .ifPresent(raw -> {
                            if (raw.replace()) {
                                fileOverrides.clear();
                            }
                            for (RawEntry entry : raw.entries()) {
                                compile(entry, fileId, pack).ifPresent(fileOverrides::add);
                            }
                        });
            }
            loaded.addAll(fileOverrides);
        }

        this.rules = List.copyOf(loaded);
        FamiliarsLib.LOGGER.info("Loaded {} familiar color overrides", loaded.size());
    }

    private static Optional<Rule> compile(RawEntry raw, ResourceLocation fileId, String pack) {
        List<Predicate<EntityType<?>>> familiars = new ArrayList<>();
        for (String value : raw.familiars()) {
            Predicate<EntityType<?>> predicate = familiarPredicate(value, fileId, pack);
            if (predicate != null) familiars.add(predicate);
        }
        List<Predicate<ItemStack>> items = new ArrayList<>();
        for (String value : raw.items()) {
            Predicate<ItemStack> predicate = itemPredicate(value, fileId, pack);
            if (predicate != null) items.add(predicate);
        }
        if (familiars.isEmpty() || items.isEmpty() || raw.layers().isEmpty()) {
            FamiliarsLib.LOGGER.warn("Familiar color overrides {} from {}: skipping an entry with no valid familiar, item or layer", fileId, pack);
            return Optional.empty();
        }
        return Optional.of(new Rule(List.copyOf(familiars), List.copyOf(items), Map.copyOf(raw.layers()), raw.consume(), raw.sound()));
    }

    private static Predicate<EntityType<?>> familiarPredicate(String value, ResourceLocation fileId, String pack) {
        boolean isTag = value.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? value.substring(1) : value);
        if (id == null) {
            FamiliarsLib.LOGGER.error("Familiar color overrides {} from {}: invalid familiar '{}'", fileId, pack, value);
            return null;
        }
        if (isTag) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, id);
            return type -> type.is(tag);
        }
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            // Not an error: the familiar may belong to a mod that isn't installed
            FamiliarsLib.LOGGER.debug("Familiar color overrides {} from {}: unknown familiar '{}', ignoring it", fileId, pack, value);
            return null;
        }
        return type -> id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    private static Predicate<ItemStack> itemPredicate(String value, ResourceLocation fileId, String pack) {
        boolean isTag = value.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? value.substring(1) : value);
        if (id == null) {
            FamiliarsLib.LOGGER.error("Familiar color overrides {} from {}: invalid item '{}'", fileId, pack, value);
            return null;
        }
        if (isTag) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            return stack -> stack.is(tag);
        }
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            FamiliarsLib.LOGGER.debug("Familiar color overrides {} from {}: unknown item '{}', ignoring it", fileId, pack, value);
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return stack -> stack.is(item);
    }
}