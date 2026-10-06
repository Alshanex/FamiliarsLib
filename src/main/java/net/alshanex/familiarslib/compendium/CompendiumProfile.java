package net.alshanex.familiarslib.compendium;

import net.alshanex.familiarslib.util.familiars.BiomeLayerColorData;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one familiar to the compendium: which color layers it has, and how its colors are decided when it spawns.
 * Interactions (dyes and override rules) are read from the loaded data automatically.
 * <pre>
 * CompendiumProfile.builder()
 *         .layer(HunterPetEntity.HAT_LAYER, "compendium.mymod.layer.hat")
 *         .layer(HunterPetEntity.DOG_LAYER, "compendium.mymod.layer.dog")
 *         .spawnsFromBiomes(MyColorData.HUNTER)
 *         .build();
 * </pre>
 */
public final class CompendiumProfile {
    /** A color slot of the familiar and the translation key of its name (e.g. "Wood"). */
    public record Layer(int slot, String nameKey) {}

    private final List<Layer> layers;
    private final boolean dyeable;
    private final boolean spawnsWithRandomDye;
    private final List<BiomeLayerColorData> biomeData;

    private CompendiumProfile(Builder builder) {
        this.layers = List.copyOf(builder.layers);
        this.dyeable = builder.dyeable;
        this.spawnsWithRandomDye = builder.spawnsWithRandomDye;
        this.biomeData = List.copyOf(builder.biomeData);
    }

    public List<Layer> layers() { return layers; }
    public boolean dyeable() { return dyeable; }
    public boolean spawnsWithRandomDye() { return spawnsWithRandomDye; }
    public List<BiomeLayerColorData> biomeData() { return biomeData; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Layer> layers = new ArrayList<>();
        private boolean dyeable;
        private boolean spawnsWithRandomDye;
        private final List<BiomeLayerColorData> biomeData = new ArrayList<>();

        /** A color layer the compendium tracks, in display order. */
        public Builder layer(int slot, String nameKey) {
            layers.add(new Layer(slot, nameKey));
            return this;
        }

        /** The owner can recolor the dye slot with dyes (matches {@code canBeDyed()}). */
        public Builder dyeable() {
            this.dyeable = true;
            return this;
        }

        /** Natural spawns get a random dye color, or the original look (matches {@code FamiliarColors.rollNaturalDye}). */
        public Builder spawnsWithRandomDye() {
            this.spawnsWithRandomDye = true;
            return this;
        }

        /** Spawn colors come from these biome data loaders, applied in this order. */
        public Builder spawnsFromBiomes(BiomeLayerColorData... data) {
            biomeData.addAll(List.of(data));
            return this;
        }

        public CompendiumProfile build() {
            return new CompendiumProfile(this);
        }
    }
}
