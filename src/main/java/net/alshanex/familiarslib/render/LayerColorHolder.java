package net.alshanex.familiarslib.render;

/**
 * Anything that can be drawn with {@link FamiliarColorLayer}: familiars, and entities that copy a familiar's look (like the illusionist's decoys).
 */
public interface LayerColorHolder {
    /** Layer color meaning "draw the original layer, untinted". */
    int NO_LAYER_COLOR = -1;

    /** 0xRRGGBB, or {@link #NO_LAYER_COLOR} to draw the original layer. */
    int getLayerColor(int slot);
}
