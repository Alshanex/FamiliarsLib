
package net.alshanex.familiarslib.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.MoveFamiliarPacket;
import net.alshanex.familiarslib.network.SetStorageModePacket;
import net.alshanex.familiarslib.registry.CapabilityRegistry;
import net.alshanex.familiarslib.setup.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Familiar Storage blocks screen - Store Mode
 */
public class FamiliarStorageScreen extends Screen {
    private static final int BASE_PANEL_WIDTH = 200;
    private static final int BASE_PANEL_HEIGHT = 320;
    private static final int BASE_BUTTON_PANEL_WIDTH = 140;
    private static final int BASE_FAMILIAR_ITEM_HEIGHT = 60;
    private static final int BASE_SCROLL_SPEED = 20;
    private static final int BASE_PADDING = 20;

    private int PANEL_WIDTH;
    private int PANEL_HEIGHT;
    private int BUTTON_PANEL_WIDTH;
    private int FAMILIAR_ITEM_HEIGHT;
    private int SCROLL_SPEED;
    private int PADDING;

    private final BlockPos blockPos;
    private final List<FamiliarEntry> storedFamiliars = new ArrayList<>();
    private final List<FamiliarEntry> playerFamiliars = new ArrayList<>();

    private UUID selectedStoredFamiliar = null;
    private UUID selectedPlayerFamiliar = null;

    private int storedScrollOffset = 0;
    private int playerScrollOffset = 0;
    private int storedMaxScroll = 0;
    private int playerMaxScroll = 0;

    private float animationTime = 0;

    private int storedPanelX;
    private int buttonPanelX;
    private int playerPanelX;
    private int panelY;

    private Button storeButton;
    private Button retrieveButton;
    private Button switchToWanderModeButton;

    public FamiliarStorageScreen(BlockPos blockPos) {
        super(Component.translatable("ui.familiarslib.familiar_storage_screen"));
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        super.init();

        double scale = Math.min(this.width / 1000.0, this.height / 600.0);
        scale = Math.max(0.7, Math.min(1.5, scale));

        PANEL_WIDTH = (int) (BASE_PANEL_WIDTH * scale);
        PANEL_HEIGHT = (int) (BASE_PANEL_HEIGHT * scale);
        BUTTON_PANEL_WIDTH = (int) (BASE_BUTTON_PANEL_WIDTH * scale);
        FAMILIAR_ITEM_HEIGHT = (int) (BASE_FAMILIAR_ITEM_HEIGHT * scale);
        SCROLL_SPEED = (int) (BASE_SCROLL_SPEED * scale);
        PADDING = (int) (BASE_PADDING * scale);

        int totalWidth = PANEL_WIDTH * 2 + BUTTON_PANEL_WIDTH + PADDING * 2;
        int startX = (this.width - totalWidth) / 2;

        this.storedPanelX = startX;
        this.buttonPanelX = startX + PANEL_WIDTH + PADDING;
        this.playerPanelX = startX + PANEL_WIDTH + BUTTON_PANEL_WIDTH + PADDING * 2;
        this.panelY = (this.height - PANEL_HEIGHT) / 2 + 30;

        int buttonWidth = Math.max(120, (int) (120 * scale));
        int buttonHeight = Math.max(20, (int) (20 * scale));
        int smallButtonHeight = Math.max(18, (int) (18 * scale));

        this.switchToWanderModeButton = Button.builder(
                        Component.translatable("ui.familiarslib.familiar_storage_screen.switch_to_wander"),
                        button -> switchToWanderMode())
                .pos(buttonPanelX + (BUTTON_PANEL_WIDTH - buttonWidth) / 2, panelY)
                .size(buttonWidth, buttonHeight)
                .build();

        int screenCenterY = this.height / 2;
        int moveButtonWidth = Math.max(80, (int) (80 * scale));

        this.storeButton = Button.builder(Component.literal("←"), button -> moveToStorage())
                .pos(buttonPanelX + (BUTTON_PANEL_WIDTH - moveButtonWidth) / 2, screenCenterY - 30)
                .size(moveButtonWidth, smallButtonHeight)
                .build();

        this.retrieveButton = Button.builder(Component.literal("→"), button -> moveToPlayer())
                .pos(buttonPanelX + (BUTTON_PANEL_WIDTH - moveButtonWidth) / 2, screenCenterY + 30)
                .size(moveButtonWidth, smallButtonHeight)
                .build();

        addRenderableWidget(switchToWanderModeButton);
        addRenderableWidget(storeButton);
        addRenderableWidget(retrieveButton);

        loadFamiliarData();
        updateButtonStates();
    }

    private void switchToWanderMode() {
        NetworkHandler.sendToServer(new SetStorageModePacket(blockPos, false));
        this.onClose();
    }

    private void loadFamiliarData() {
        storedFamiliars.clear();
        playerFamiliars.clear();

        if (minecraft == null || minecraft.player == null) return;

        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            Map<UUID, CompoundTag> stored = storageEntity.getStoredFamiliars();
            for (Map.Entry<UUID, CompoundTag> entry : stored.entrySet()) {
                UUID id = entry.getKey();
                CompoundTag nbt = entry.getValue();

                FamiliarEntry familiarEntry = createFamiliarEntry(id, nbt);
                if (familiarEntry != null) {
                    storedFamiliars.add(familiarEntry);
                }
            }
        }

        minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
            Map<UUID, CompoundTag> playerFams = familiarData.getAllFamiliars();

            for (Map.Entry<UUID, CompoundTag> entry : playerFams.entrySet()) {
                UUID id = entry.getKey();
                CompoundTag nbt = entry.getValue();

                FamiliarEntry familiarEntry = createFamiliarEntry(id, nbt);
                if (familiarEntry != null) {
                    playerFamiliars.add(familiarEntry);
                }
            }
        });

        calculateMaxScroll();
    }

    private FamiliarEntry createFamiliarEntry(UUID id, CompoundTag nbt) {
        if (minecraft == null || minecraft.level == null) return null;

        String entityTypeString = nbt.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType != null) {
            Entity entity = entityType.create(minecraft.level);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                familiar.load(nbt);
                familiar.setUUID(id);

                String displayName = familiar.hasCustomName() ?
                        familiar.getCustomName().getString() :
                        familiar.getType().getDescription().getString();

                return new FamiliarEntry(id, familiar, displayName);
            }
        }

        return null;
    }

    private void calculateMaxScroll() {
        int visibleItems = PANEL_HEIGHT / FAMILIAR_ITEM_HEIGHT;
        storedMaxScroll = Math.max(0, (storedFamiliars.size() - visibleItems) * FAMILIAR_ITEM_HEIGHT);
        playerMaxScroll = Math.max(0, (playerFamiliars.size() - visibleItems) * FAMILIAR_ITEM_HEIGHT);
    }

    public void reloadFamiliarData() {
        selectedStoredFamiliar = null;
        selectedPlayerFamiliar = null;

        loadFamiliarData();
        updateButtonStates();

        storedScrollOffset = Math.max(0, Math.min(storedScrollOffset, storedMaxScroll));
        playerScrollOffset = Math.max(0, Math.min(playerScrollOffset, playerMaxScroll));
    }

    private void updateButtonStates() {
        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
                storeButton.active = selectedPlayerFamiliar != null && storageEntity.canStoreFamiliar();
                retrieveButton.active = selectedStoredFamiliar != null && familiarData.canTameMoreFamiliars();
            });
        }
    }

    private void moveToStorage() {
        if (selectedPlayerFamiliar != null) {
            NetworkHandler.sendToServer(new MoveFamiliarPacket(blockPos, selectedPlayerFamiliar, true));
            selectedPlayerFamiliar = null;
        }
    }

    private void moveToPlayer() {
        if (selectedStoredFamiliar != null) {
            NetworkHandler.sendToServer(new MoveFamiliarPacket(blockPos, selectedStoredFamiliar, false));
            selectedStoredFamiliar = null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        animationTime += partialTick;
        renderBackground(guiGraphics);

        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            Component modeTitle = Component.translatable("ui.familiarslib.familiar_storage_screen.store_mode_title");
            int titleWidth = font.width(modeTitle);
            guiGraphics.drawString(font, modeTitle, (this.width - titleWidth) / 2, panelY - 50, 0x00FF00);

            renderTitle(guiGraphics, Component.translatable("ui.familiarslib.familiar_storage_screen.storage_familiars",
                    storageEntity.getStoredFamiliarCount(),
                    storageEntity.getMaxStoredFamiliars()), storedPanelX, panelY - 20);
            renderTitle(guiGraphics, Component.translatable("ui.familiarslib.familiar_storage_screen.player_familiars"), playerPanelX, panelY - 20);
        }

        renderFamiliarPanel(guiGraphics, storedFamiliars, storedPanelX, panelY, storedScrollOffset, selectedStoredFamiliar, mouseX, mouseY, partialTick);
        renderFamiliarPanel(guiGraphics, playerFamiliars, playerPanelX, panelY, playerScrollOffset, selectedPlayerFamiliar, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTitle(GuiGraphics guiGraphics, Component title, int x, int y) {
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, x + (PANEL_WIDTH - titleWidth) / 2, y, 0xFFFFFF);
    }

    private void renderFamiliarPanel(GuiGraphics guiGraphics, List<FamiliarEntry> familiars, int panelX, int panelY, int scrollOffset, UUID selectedId, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + PANEL_WIDTH + 2, panelY + PANEL_HEIGHT + 2, 0xFF555555);
        guiGraphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF222222);

        enableScissor(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT);

        int currentY = panelY - scrollOffset;

        for (FamiliarEntry entry : familiars) {
            if (currentY + FAMILIAR_ITEM_HEIGHT > panelY && currentY < panelY + PANEL_HEIGHT) {
                renderFamiliarItem(guiGraphics, entry, panelX, currentY, selectedId, mouseX, mouseY, partialTick);
            }
            currentY += FAMILIAR_ITEM_HEIGHT;
        }

        disableScissor();
    }

    private void renderFamiliarItem(GuiGraphics guiGraphics, FamiliarEntry entry, int x, int y, UUID selectedId, int mouseX, int mouseY, float partialTick) {
        boolean isSelected = entry.id.equals(selectedId);
        boolean isHovered = mouseX >= x && mouseX < x + PANEL_WIDTH &&
                mouseY >= y && mouseY < y + FAMILIAR_ITEM_HEIGHT;

        if (isSelected) {
            guiGraphics.fill(x, y, x + PANEL_WIDTH, y + FAMILIAR_ITEM_HEIGHT, 0x4400FF00);
        } else if (isHovered) {
            guiGraphics.fill(x, y, x + PANEL_WIDTH, y + FAMILIAR_ITEM_HEIGHT, 0x44FFFFFF);
        }

        float entityScale = Math.max(15, FAMILIAR_ITEM_HEIGHT * 0.4f);
        int entityX = x + (int)(FAMILIAR_ITEM_HEIGHT * 0.5f);
        int entityY = y + (int)(FAMILIAR_ITEM_HEIGHT * 0.7f);

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(entityX, entityY, 50);
        poseStack.scale(entityScale, entityScale, entityScale);

        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(45));
        poseStack.mulPose(rotation);

        renderEntity(guiGraphics, entry.familiar, 0, 0, 0);
        poseStack.popPose();

        Component nameComponent = Component.literal(entry.displayName);
        int textX = x + (int)(FAMILIAR_ITEM_HEIGHT * 1.1f);
        int textY = y + FAMILIAR_ITEM_HEIGHT / 2 - font.lineHeight / 2;
        guiGraphics.drawString(font, nameComponent, textX, textY, 0xFFFFFF);
    }

    private void renderEntity(GuiGraphics guiGraphics, LivingEntity entity, float x, float y, float z) {
        if (entity == null) return;

        PoseStack poseStack = guiGraphics.pose();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(1.0f, -1.0f, 1.0f);

        float rotationY = (animationTime * 0.02f) % (2 * (float) Math.PI);
        Quaternionf rotation = new Quaternionf().rotateY(rotationY);
        poseStack.mulPose(rotation);

        Quaternionf tiltRotation = new Quaternionf().rotateX((float) Math.toRadians(-10));
        poseStack.mulPose(tiltRotation);

        RenderSystem.setShaderLights(
                new Vector3f(0.2f, 1.0f, -1.0f),
                new Vector3f(-0.2f, -1.0f, 0.0f)
        );

        RenderSystem.disableCull();

        try {
            var bufferSource = guiGraphics.bufferSource();
            entityRenderDispatcher.render(entity, 0, 0, 0, 0, 0, poseStack, bufferSource, 15728880);
            guiGraphics.flush();
        } catch (Exception e) {
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate(x, y, z);

            Component errorText = Component.literal("Error");
            int textWidth = font.width(errorText);
            guiGraphics.drawString(font, errorText, (int)x - textWidth/2, (int)y, 0xFF5555);
        }

        RenderSystem.enableCull();
        poseStack.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= storedPanelX && mouseX < storedPanelX + PANEL_WIDTH &&
                    mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

                int relativeY = (int) (mouseY - panelY + storedScrollOffset);
                int itemIndex = relativeY / FAMILIAR_ITEM_HEIGHT;

                if (itemIndex >= 0 && itemIndex < storedFamiliars.size()) {
                    selectedStoredFamiliar = storedFamiliars.get(itemIndex).id;
                    selectedPlayerFamiliar = null;
                    updateButtonStates();
                    return true;
                }
            }

            if (mouseX >= playerPanelX && mouseX < playerPanelX + PANEL_WIDTH &&
                    mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

                int relativeY = (int) (mouseY - panelY + playerScrollOffset);
                int itemIndex = relativeY / FAMILIAR_ITEM_HEIGHT;

                if (itemIndex >= 0 && itemIndex < playerFamiliars.size()) {
                    selectedPlayerFamiliar = playerFamiliars.get(itemIndex).id;
                    selectedStoredFamiliar = null;
                    updateButtonStates();
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= storedPanelX && mouseX < storedPanelX + PANEL_WIDTH &&
                mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

            storedScrollOffset -= (int) (scrollY * SCROLL_SPEED);
            storedScrollOffset = Math.max(0, Math.min(storedScrollOffset, storedMaxScroll));
            return true;
        }

        if (mouseX >= playerPanelX && mouseX < playerPanelX + PANEL_WIDTH &&
                mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

            playerScrollOffset -= (int) (scrollY * SCROLL_SPEED);
            playerScrollOffset = Math.max(0, Math.min(playerScrollOffset, playerMaxScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void enableScissor(int x1, int y1, int x2, int y2) {
        double scale = minecraft.getWindow().getGuiScale();
        RenderSystem.enableScissor(
                (int) (x1 * scale),
                (int) (minecraft.getWindow().getHeight() - y2 * scale),
                (int) ((x2 - x1) * scale),
                (int) ((y2 - y1) * scale)
        );
    }

    private void disableScissor() {
        RenderSystem.disableScissor();
    }

    private static class FamiliarEntry {
        final UUID id;
        final AbstractSpellCastingPet familiar;
        final String displayName;

        FamiliarEntry(UUID id, AbstractSpellCastingPet familiar, String displayName) {
            this.id = id;
            this.familiar = familiar;
            this.displayName = displayName;
        }
    }
}