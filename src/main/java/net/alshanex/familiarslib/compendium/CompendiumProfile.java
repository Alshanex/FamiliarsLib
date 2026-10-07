package net.alshanex.familiarslib.compendium;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.BiomeLayerColorData;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes one familiar to the compendium: which color layers it has, and how its colors are decided when it spawns.
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
    private final int eyeDyeSlot;
    private final int sneakingEyeDyeSlot;

    private CompendiumProfile(Builder builder) {
        this.layers = List.copyOf(builder.layers);
        this.dyeable = builder.dyeable;
        this.spawnsWithRandomDye = builder.spawnsWithRandomDye;
        this.biomeData = List.copyOf(builder.biomeData);
        this.eyeDyeSlot = builder.eyeDyeSlot;
        this.sneakingEyeDyeSlot = builder.sneakingEyeDyeSlot;
    }

    public List<Layer> layers() { return layers; }
    public boolean dyeable() { return dyeable; }
    public boolean spawnsWithRandomDye() { return spawnsWithRandomDye; }
    public List<BiomeLayerColorData> biomeData() { return biomeData; }
    public int eyeDyeSlot() { return eyeDyeSlot; }
    public int sneakingEyeDyeSlot() { return sneakingEyeDyeSlot; }

    public static Builder builder() {
        return new Builder();
    }

    /** A builder starting from this profile, to add to it. */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.layers.addAll(layers);
        builder.dyeable = dyeable;
        builder.spawnsWithRandomDye = spawnsWithRandomDye;
        builder.biomeData.addAll(biomeData);
        builder.eyeDyeSlot = eyeDyeSlot;
        builder.sneakingEyeDyeSlot = sneakingEyeDyeSlot;
        return builder;
    }

    public boolean hasLayer(int slot) {
        return layers.stream().anyMatch(layer -> layer.slot() == slot);
    }

    /**
     * Fills in what the familiar itself declares, for anything the registered profile doesn't already cover:
     * the dye slot ({@code canBeDyed()}), the eyes ({@code getEyeDyeLayerSlot()}) and the second eyes
     * ({@code getSneakingEyeDyeLayerSlot()}). Layers added this way use generic names from Familiars Lib;
     * register them with your own names to override those.
     */
    public CompendiumProfile withDefaultsFrom(AbstractSpellCastingPet familiar) {
        Builder builder = toBuilder();
        if (familiar.canBeDyed()) {
            builder.dyeable = true;
            if (!hasLayer(AbstractSpellCastingPet.DYE_LAYER_SLOT)) {
                builder.layer(AbstractSpellCastingPet.DYE_LAYER_SLOT, "compendium.familiarslib.layer.dye");
            }
        }
        int eyes = familiar.getEyeDyeLayerSlot();
        if (eyes >= 0 && eyeDyeSlot < 0) {
            builder.eyeDyeSlot = eyes;
            if (!hasLayer(eyes)) {
                builder.layer(eyes, "compendium.familiarslib.layer.eyes");
            }
        }
        int sneakingEyes = familiar.getSneakingEyeDyeLayerSlot();
        if (sneakingEyes >= 0 && sneakingEyeDyeSlot < 0) {
            builder.sneakingEyeDyeSlot = sneakingEyes;
            if (!hasLayer(sneakingEyes)) {
                builder.layer(sneakingEyes, "compendium.familiarslib.layer.second_eyes");
            }
        }
        return builder.build();
    }

    public static final class Builder {
        private final List<Layer> layers = new ArrayList<>();
        private boolean dyeable;
        private boolean spawnsWithRandomDye;
        private final List<BiomeLayerColorData> biomeData = new ArrayList<>();
        private int eyeDyeSlot = -1;
        private int sneakingEyeDyeSlot = -1;

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

        /** Shrunk dyes color this slot (match {@code getEyeDyeLayerSlot()}). Also add it with {@link #layer}. */
        public Builder eyeDyeable(int slot) {
            this.eyeDyeSlot = slot;
            return this;
        }

        /** Shrunk dyes color this slot while sneaking (match {@code getSneakingEyeDyeLayerSlot()}). Also add it with {@link #layer}. */
        public Builder sneakingEyeDyeable(int slot) {
            this.sneakingEyeDyeSlot = slot;
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