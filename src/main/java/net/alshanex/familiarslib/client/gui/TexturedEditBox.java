package net.alshanex.familiarslib.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * An edit box with its frame drawn from a GUI texture sheet. The frame covers the bounds given here;
 * the text area sits 4px in from the left and is vertically centered.
 */
public class TexturedEditBox extends EditBox {
    private static final int PAD_X = 4;

    private final GuiSprite frame;
    private final GuiSprite focusedFrame;
    private final int frameWidth;
    private final int frameHeight;
    private final int padY;

    public TexturedEditBox(Font font, int x, int y, int width, int height, Component message, GuiSprite frame, GuiSprite focusedFrame) {
        super(font, x + PAD_X, y + (height - 8) / 2, width - PAD_X * 2, 8, message);
        this.frame = frame;
        this.focusedFrame = focusedFrame;
        this.frameWidth = width;
        this.frameHeight = height;
        this.padY = (height - 8) / 2;
        setBordered(false);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        (isFocused() ? focusedFrame : frame).draw(graphics, getX() - PAD_X, getY() - padY, frameWidth, frameHeight);
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
