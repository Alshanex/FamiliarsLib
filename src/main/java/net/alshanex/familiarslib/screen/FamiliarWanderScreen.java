package net.alshanex.familiarslib.screen;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.client.gui.GuiSprite;
import net.alshanex.familiarslib.client.gui.TexturedButton;
import net.alshanex.familiarslib.network.SetStorageModePacket;
import net.alshanex.familiarslib.network.UpdateStorageSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Familiar Storage Wander Mode screen
 */
public class FamiliarWanderScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;
    private static final int TEXT_COLOR = 0x404040;

    private static final GuiSprite SHEET = GuiSprite.sheet(FamiliarsLib.id("textures/gui/familiar_wander.png"), 512, 256);
    private static final GuiSprite PANEL = SHEET.at(0, 0, PANEL_WIDTH, PANEL_HEIGHT);
    private static final TexturedButton.Sprites BUTTON = new TexturedButton.Sprites(
            SHEET.at(302, 0, 200, 20), SHEET.at(302, 22, 200, 20), SHEET.at(302, 44, 200, 20));
    private static final GuiSprite SLIDER_TRACK = SHEET.at(302, 66, 200, 20);
    private static final GuiSprite SLIDER_HANDLE = SHEET.at(504, 0, 8, 20);
    private static final GuiSprite SLIDER_HANDLE_HOVER = SHEET.at(504, 22, 8, 20);

    private final BlockPos blockPos;
    private boolean canFamiliarsUseGoals = true;
    private int maxDistance = 25;

    private Button switchToStoreModeButton;
    private Button toggleGoalsButton;
    private DistanceSlider distanceSlider;

    private int panelX;
    private int panelY;

    public FamiliarWanderScreen(BlockPos blockPos) {
        super(Component.translatable("ui.familiarslib.familiar_wander_screen"));
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        super.init();

        this.panelX = (this.width - PANEL_WIDTH) / 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2;

        loadStorageData();

        this.switchToStoreModeButton = new TexturedButton(panelX + 50, panelY + 40, 200, 20,
                Component.translatable("ui.familiarslib.familiar_storage_screen.switch_to_store"),
                BUTTON, button -> switchToStoreMode());

        this.toggleGoalsButton = new TexturedButton(panelX + 50, panelY + 80, 200, 20,
                getGoalsButtonText(), BUTTON, button -> toggleGoals());

        this.distanceSlider = new DistanceSlider(panelX + 50, panelY + 160, 200, 20, maxDistance);

        addRenderableWidget(switchToStoreModeButton);
        addRenderableWidget(toggleGoalsButton);
        addRenderableWidget(distanceSlider);
    }

    private void loadStorageData() {
        if (minecraft == null || minecraft.level == null) return;

        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            this.canFamiliarsUseGoals = storageEntity.canFamiliarsUseGoals();
            this.maxDistance = storageEntity.getMaxDistance();
        }
    }

    private Component getGoalsButtonText() {
        return canFamiliarsUseGoals ?
                Component.translatable("ui.familiarslib.familiar_wander_screen.goals_enabled") :
                Component.translatable("ui.familiarslib.familiar_wander_screen.goals_disabled");
    }

    private void switchToStoreMode() {
        PacketDistributor.sendToServer(new SetStorageModePacket(blockPos, true));
        this.onClose();
    }

    private void toggleGoals() {
        canFamiliarsUseGoals = !canFamiliarsUseGoals;
        toggleGoalsButton.setMessage(getGoalsButtonText());

        // Send update to server
        PacketDistributor.sendToServer(new UpdateStorageSettingsPacket(blockPos, canFamiliarsUseGoals, maxDistance));
    }

    private void updateMaxDistance(int newDistance) {
        maxDistance = newDistance;
        // Send update to server
        PacketDistributor.sendToServer(new UpdateStorageSettingsPacket(blockPos, canFamiliarsUseGoals, maxDistance));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Render title
        Component title = Component.translatable("ui.familiarslib.familiar_wander_screen.title");
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, panelX + (PANEL_WIDTH - titleWidth) / 2, panelY + 10, TEXT_COLOR, false);

        // Render distance label
        Component distanceLabel = Component.translatable("ui.familiarslib.familiar_wander_screen.max_distance",
                String.valueOf(maxDistance));
        guiGraphics.drawString(font, distanceLabel, panelX + 90, panelY + 145, TEXT_COLOR, false);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);
        PANEL.draw(guiGraphics, panelX, panelY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Custom slider for distance
    private class DistanceSlider extends AbstractSliderButton {
        private static final int MIN_DISTANCE = 3;
        private static final int MAX_DISTANCE = 25;

        public DistanceSlider(int x, int y, int width, int height, double initialValue) {
            super(x, y, width, height, Component.empty(), (initialValue - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE));
            updateMessage();
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            SLIDER_TRACK.draw(guiGraphics, getX(), getY(), width, height);
            GuiSprite handle = TexturedButton.isHighlighted(this) ? SLIDER_HANDLE_HOVER : SLIDER_HANDLE;
            handle.draw(guiGraphics, getX() + (int) (value * (width - 8)), getY(), 8, height);
            renderScrollingString(guiGraphics, Minecraft.getInstance().font, 2, 0xFFFFFF | Mth.ceil(alpha * 255.0F) << 24);
        }

        @Override
        protected void updateMessage() {
            int currentDistance = (int) Math.round(MIN_DISTANCE + this.value * (MAX_DISTANCE - MIN_DISTANCE));
            this.setMessage(Component.literal(String.valueOf(currentDistance)));
        }

        @Override
        protected void applyValue() {
            int newDistance = (int) Math.round(MIN_DISTANCE + this.value * (MAX_DISTANCE - MIN_DISTANCE));
            updateMaxDistance(newDistance);
        }
    }
}
