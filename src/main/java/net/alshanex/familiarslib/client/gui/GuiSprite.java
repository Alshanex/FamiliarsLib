package net.alshanex.familiarslib.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * A region of a GUI texture sheet. Drawn at its own size, or resized while keeping a 2px border crisp.
 */
public record GuiSprite(ResourceLocation texture, int u, int v, int width, int height, int textureWidth, int textureHeight) {
    private static final int EDGE = 2;

    public static GuiSprite sheet(ResourceLocation texture, int textureWidth, int textureHeight) {
        return new GuiSprite(texture, 0, 0, textureWidth, textureHeight, textureWidth, textureHeight);
    }

    public GuiSprite at(int u, int v, int width, int height) {
        return new GuiSprite(texture, u, v, width, height, textureWidth, textureHeight);
    }

    public void draw(GuiGraphics graphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }

    /**
     * Draws the sprite at its own width but shorter: the top part and the last {@code bottomRows} rows,
     * cutting the middle out. Taller than the sprite draws it unchanged.
     */
    public void drawShortened(GuiGraphics graphics, int x, int y, int drawHeight, int bottomRows) {
        if (drawHeight >= height) {
            draw(graphics, x, y);
            return;
        }
        int bottom = Math.min(bottomRows, drawHeight);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(texture, x, y, u, v, width, drawHeight - bottom, textureWidth, textureHeight);
        graphics.blit(texture, x, y + drawHeight - bottom, u, v + height - bottom, width, bottom, textureWidth, textureHeight);
        RenderSystem.disableBlend();
    }

    public void draw(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight) {
        if (drawWidth == width && drawHeight == height) {
            draw(graphics, x, y);
            return;
        }
        if (drawWidth <= 0 || drawHeight <= 0) return;

        int ex = Math.min(EDGE, Math.min(drawWidth, width) / 2);
        int ey = Math.min(EDGE, Math.min(drawHeight, height) / 2);
        int innerWidth = drawWidth - ex * 2;
        int innerHeight = drawHeight - ey * 2;
        int sourceInnerWidth = width - ex * 2;
        int sourceInnerHeight = height - ey * 2;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        part(graphics, x, y, ex, ey, u, v, ex, ey);
        part(graphics, x + ex, y, innerWidth, ey, u + ex, v, sourceInnerWidth, ey);
        part(graphics, x + drawWidth - ex, y, ex, ey, u + width - ex, v, ex, ey);
        part(graphics, x, y + ey, ex, innerHeight, u, v + ey, ex, sourceInnerHeight);
        part(graphics, x + ex, y + ey, innerWidth, innerHeight, u + ex, v + ey, sourceInnerWidth, sourceInnerHeight);
        part(graphics, x + drawWidth - ex, y + ey, ex, innerHeight, u + width - ex, v + ey, ex, sourceInnerHeight);
        part(graphics, x, y + drawHeight - ey, ex, ey, u, v + height - ey, ex, ey);
        part(graphics, x + ex, y + drawHeight - ey, innerWidth, ey, u + ex, v + height - ey, sourceInnerWidth, ey);
        part(graphics, x + drawWidth - ex, y + drawHeight - ey, ex, ey, u + width - ex, v + height - ey, ex, ey);
        RenderSystem.disableBlend();
    }

    private void part(GuiGraphics graphics, int x, int y, int drawWidth, int drawHeight, int sourceU, int sourceV, int sourceWidth, int sourceHeight) {
        if (drawWidth <= 0 || drawHeight <= 0 || sourceWidth <= 0 || sourceHeight <= 0) return;
        graphics.blit(texture, x, y, drawWidth, drawHeight, sourceU, sourceV, sourceWidth, sourceHeight, textureWidth, textureHeight);
    }
}
