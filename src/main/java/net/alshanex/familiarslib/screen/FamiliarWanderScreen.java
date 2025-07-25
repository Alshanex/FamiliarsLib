package net.alshanex.familiarslib.screen;

import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.network.SetStorageModePacket;
import net.alshanex.familiarslib.network.UpdateStorageSettingsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Familiar Storage Wander Mode screen
 */
public class FamiliarWanderScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;

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

        // Switch to Store Mode button
        this.switchToStoreModeButton = Button.builder(
                        Component.translatable("ui.familiarslib.familiar_storage_screen.switch_to_store"),
                        button -> switchToStoreMode())
                .pos(panelX + 50, panelY + 40)
                .size(200, 20)
                .build();

        // Toggle Goals button
        this.toggleGoalsButton = Button.builder(
                        getGoalsButtonText(),
                        button -> toggleGoals())
                .pos(panelX + 50, panelY + 80)
                .size(200, 20)
                .build();

        // Distance slider
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
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Render panel background
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF555555);
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF222222);

        // Render title
        Component title = Component.translatable("ui.familiarslib.familiar_wander_screen.title");
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, panelX + (PANEL_WIDTH - titleWidth) / 2, panelY + 10, 0xFFFFFF);

        // Render distance label
        Component distanceLabel = Component.translatable("ui.familiarslib.familiar_wander_screen.max_distance",
                String.valueOf(maxDistance));
        guiGraphics.drawString(font, distanceLabel, panelX + 90, panelY + 145, 0xFFFFFF);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);
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