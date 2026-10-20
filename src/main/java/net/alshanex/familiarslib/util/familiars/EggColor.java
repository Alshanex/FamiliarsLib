package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.render.LayerColorHolder;

/**
 * The color of a familiar's Familiar Egg: either a set color, or the color of one of its layers.
 */
public record EggColor(int layerSlot, int color) {
    /** The fossil beige of the original egg texture. */
    public static final int DEFAULT_RGB = 0xF3D9AE;
    public static final EggColor DEFAULT = of(DEFAULT_RGB);

    /** Always this color (0xRRGGBB). */
    public static EggColor of(int rgb) {
        return new EggColor(-1, rgb & 0xFFFFFF);
    }

    /**
     * The color of this layer. Layers with their original look have no color of their own,
     * so {@code originalLookRgb} is used then: pick the main color of that layer's texture.
     */
    public static EggColor fromLayer(int layerSlot, int originalLookRgb) {
        return new EggColor(layerSlot, originalLookRgb & 0xFFFFFF);
    }

    /** The final 0xRRGGBB for this familiar. */
    public int resolve(LayerColorHolder familiar) {
        if (layerSlot >= 0) {
            int layer = familiar.getLayerColor(layerSlot);
            if (layer != LayerColorHolder.NO_LAYER_COLOR) {
                return layer & 0xFFFFFF;
            }
        }
        return color;
    }
}