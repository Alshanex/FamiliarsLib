package net.alshanex.familiarslib.client.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Helpers for screens drawn at a fixed size from textures.
 */
public final class GuiFit {
    /** Space kept free around the layout, in GUI pixels. */
    public static final int MARGIN = 4;

    /**
     * The scale (at most 1) that makes a layout of contentWidth x contentHeight fit in the screen.
     */
    public static float fitScale(int screenWidth, int screenHeight, int contentWidth, int contentHeight) {
        float raw = Math.min(
                (screenWidth - MARGIN * 2) / (float) contentWidth,
                (screenHeight - MARGIN * 2) / (float) contentHeight);
        if (raw >= 1F) return 1F;
        if (raw <= 0F) return 0.1F;

        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        float snapped = (float) (Math.floor(guiScale * raw) / guiScale);
        return snapped >= raw * 0.85F ? snapped : raw;
    }

    /** Size of the layout area for a screen of this size drawn at this scale. */
    public static int virtual(int size, float scale) {
        return (int) Math.ceil(size / scale);
    }

    /**
     * Clips drawing to the rectangle, given in the current pose's coordinates.
     */
    public static void enableScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        Matrix4f matrix = g.pose().last().pose();
        Vector3f a = matrix.transformPosition(x1, y1, 0, new Vector3f());
        Vector3f b = matrix.transformPosition(x2, y2, 0, new Vector3f());

        Window window = Minecraft.getInstance().getWindow();
        double s = window.getGuiScale();
        int left = (int) Math.floor(Math.min(a.x, b.x) * s);
        int right = (int) Math.ceil(Math.max(a.x, b.x) * s);
        int top = (int) Math.floor(Math.min(a.y, b.y) * s);
        int bottom = (int) Math.ceil(Math.max(a.y, b.y) * s);

        RenderSystem.enableScissor(left, window.getHeight() - bottom, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public static void disableScissor() {
        RenderSystem.disableScissor();
    }

    private GuiFit() {}
}