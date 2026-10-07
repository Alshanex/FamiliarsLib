package net.alshanex.familiarslib.compendium;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Every color each layer of each familiar can have, and how to get it.
 * <p>
 * Layers are collected separately: the original look is always available, and each other color of
 * each layer is its own entry, unlocked independently.
 */
public record CompendiumCatalog(List<Section> sections) {
    public static final CompendiumCatalog EMPTY = new CompendiumCatalog(List.of());

    /** One familiar: its layers, and the spells it can cast. */
    public record Section(ResourceLocation entityType, List<LayerOptions> layers, List<ResourceLocation> spells) {
        public int optionCount() {
            return layers.stream().mapToInt(l -> l.options().size()).sum();
        }
    }

    /** One layer of a familiar and every color it can have (besides the original look). */
    public record LayerOptions(int slot, String nameKey, List<ColorOption> options) {}

    /**
     * One color of one layer. {@code origins}: familiars that spawn with it.
     * {@code steps}: items that give it to a familiar (each step is one item effect, with how many other items do the same).
     */
    public record ColorOption(int color, List<Origin> origins, List<Step> steps) {}

    public enum OriginKind { NATURAL_DYE, BIOMES }

    /**
     * A way to find a familiar that spawns with this color.
     * NATURAL_DYE: {@code value} is the dye name. BIOMES: {@code biomes} lists some of them, {@code moreBiomes} how many more.
     */
    public record Origin(OriginKind kind, String value, List<ResourceLocation> biomes, int moreBiomes) {}

    /**
     * "Use this item on it". {@code alternatives} = how many other items do the same.
     * {@code shrunk}: the item has to be shrunk first. {@code sneaking}: use it while sneaking.
     */
    public record Step(ResourceLocation item, int alternatives, boolean shrunk, boolean sneaking) {}

    /** Identifies one unlocked layer color: entity id + layer slot + color. Used for the player's discoveries. */
    public static String key(ResourceLocation entityType, int slot, int color) {
        return entityType + "|" + slot + "|" + Integer.toHexString(color);
    }

    // Network

    public static final StreamCodec<FriendlyByteBuf, CompendiumCatalog> STREAM_CODEC = StreamCodec.of(
            (buf, catalog) -> {
                buf.writeVarInt(catalog.sections().size());
                for (Section section : catalog.sections()) {
                    buf.writeResourceLocation(section.entityType());
                    buf.writeVarInt(section.spells().size());
                    section.spells().forEach(buf::writeResourceLocation);
                    buf.writeVarInt(section.layers().size());
                    for (LayerOptions layer : section.layers()) {
                        buf.writeVarInt(layer.slot());
                        buf.writeUtf(layer.nameKey());
                        buf.writeVarInt(layer.options().size());
                        for (ColorOption option : layer.options()) {
                            buf.writeInt(option.color());
                            buf.writeVarInt(option.origins().size());
                            for (Origin origin : option.origins()) {
                                buf.writeEnum(origin.kind());
                                buf.writeUtf(origin.value());
                                buf.writeVarInt(origin.biomes().size());
                                origin.biomes().forEach(buf::writeResourceLocation);
                                buf.writeVarInt(origin.moreBiomes());
                            }
                            buf.writeVarInt(option.steps().size());
                            for (Step step : option.steps()) {
                                buf.writeResourceLocation(step.item());
                                buf.writeVarInt(step.alternatives());
                                buf.writeBoolean(step.shrunk());
                                buf.writeBoolean(step.sneaking());
                            }
                        }
                    }
                }
            },
            buf -> {
                int sectionCount = buf.readVarInt();
                List<Section> sections = new ArrayList<>(sectionCount);
                for (int s = 0; s < sectionCount; s++) {
                    ResourceLocation type = buf.readResourceLocation();
                    int spellCount = buf.readVarInt();
                    List<ResourceLocation> spells = new ArrayList<>(spellCount);
                    for (int i = 0; i < spellCount; i++) spells.add(buf.readResourceLocation());
                    int layerCount = buf.readVarInt();
                    List<LayerOptions> layers = new ArrayList<>(layerCount);
                    for (int l = 0; l < layerCount; l++) {
                        int slot = buf.readVarInt();
                        String nameKey = buf.readUtf();
                        int optionCount = buf.readVarInt();
                        List<ColorOption> options = new ArrayList<>(optionCount);
                        for (int o = 0; o < optionCount; o++) {
                            int color = buf.readInt();
                            int originCount = buf.readVarInt();
                            List<Origin> origins = new ArrayList<>(originCount);
                            for (int i = 0; i < originCount; i++) {
                                OriginKind kind = buf.readEnum(OriginKind.class);
                                String value = buf.readUtf();
                                int biomeCount = buf.readVarInt();
                                List<ResourceLocation> biomes = new ArrayList<>(biomeCount);
                                for (int b = 0; b < biomeCount; b++) biomes.add(buf.readResourceLocation());
                                origins.add(new Origin(kind, value, biomes, buf.readVarInt()));
                            }
                            int stepCount = buf.readVarInt();
                            List<Step> steps = new ArrayList<>(stepCount);
                            for (int i = 0; i < stepCount; i++) {
                                steps.add(new Step(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
                            }
                            options.add(new ColorOption(color, origins, steps));
                        }
                        layers.add(new LayerOptions(slot, nameKey, options));
                    }
                    sections.add(new Section(type, layers, spells));
                }
                return new CompendiumCatalog(sections);
            });
}