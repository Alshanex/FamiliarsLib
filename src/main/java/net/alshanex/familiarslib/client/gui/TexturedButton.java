package net.alshanex.familiarslib.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A button drawn from a GUI texture sheet: one sprite each for normal, hovered and inactive.
 * Tabs use the inactive sprite as their "selected" look, since the screens mark the open tab inactive.
 */
public class TexturedButton extends Button {
    public record Sprites(GuiSprite normal, GuiSprite hovered, GuiSprite inactive) {}

    private final Sprites sprites;
    private final int inactiveTextColor;

    public TexturedButton(int x, int y, int width, int height, Component message, Sprites sprites, OnPress onPress) {
        this(x, y, width, height, message, sprites, 0xA0A0A0, onPress);
    }

    public TexturedButton(int x, int y, int width, int height, Component message, Sprites sprites, int inactiveTextColor, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.sprites = sprites;
        this.inactiveTextColor = inactiveTextColor;
    }

    /**
     * Hovered, or focused through the keyboard. A widget keeps its focus after a mouse click,
     * which shouldn't leave it looking highlighted.
     */
    public static boolean isHighlighted(AbstractWidget widget) {
        return widget.isHovered() || (widget.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard());
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiSprite sprite = !active ? sprites.inactive() : isHighlighted(this) ? sprites.hovered() : sprites.normal();
        sprite.draw(graphics, getX(), getY(), width, height);
        int color = active ? 0xFFFFFF : inactiveTextColor;
        renderString(graphics, Minecraft.getInstance().font, color | Mth.ceil(alpha * 255.0F) << 24);
    }
}
