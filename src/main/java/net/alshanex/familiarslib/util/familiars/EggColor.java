package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.render.LayerColorHolder;

/**
 * The color of a familiar's Familiar Egg: a set color, optionally tied to one of its layers.
 */
public record EggColor(int layerSlot, int color) {
    public static final int DEFAULT_RGB = 0xfdf5e0;
    public static final EggColor DEFAULT = of(DEFAULT_RGB);

    public static EggColor of(int rgb) {
        return new EggColor(-1, rgb & 0xFFFFFF);
    }

    public static EggColor fromLayer(int layerSlot, int rgb) {
        return new EggColor(layerSlot, rgb & 0xFFFFFF);
    }

    public int resolve(LayerColorHolder familiar) {
        return layerColor(familiar, layerSlot, color);
    }

    public static int layerColor(LayerColorHolder familiar, int slot, int fallback) {
        if (slot >= 0 && slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS) {
            int layer = familiar.getLayerColor(slot);
            if (layer != LayerColorHolder.NO_LAYER_COLOR) {
                return layer & 0xFFFFFF;
            }
        }
        return fallback;
    }
}