package net.alshanex.familiarslib.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.MoveFamiliarPacket;
import net.alshanex.familiarslib.network.SetStorageModePacket;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Familiar Storage blocks screen
 */
public class FamiliarStorageScreen extends Screen {
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 300;
    private static final int BUTTON_PANEL_WIDTH = 100;
    private static final int FAMILIAR_ITEM_HEIGHT = 60;
    private static final int SCROLL_SPEED = 20;

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
    private Button storeModeButton;
    private Button wanderModeButton;

    private boolean isStoreMode = false;

    public FamiliarStorageScreen(BlockPos blockPos) {
        super(Component.translatable("ui.familiarslib.familiar_storage_screen"));
        this.blockPos = blockPos;
    }

    @Override
    protected void init() {
        super.init();

        int totalWidth = PANEL_WIDTH * 2 + BUTTON_PANEL_WIDTH + 40;
        int startX = (this.width - totalWidth) / 2;

        this.storedPanelX = startX;
        this.buttonPanelX = startX + PANEL_WIDTH + 20;
        this.playerPanelX = startX + PANEL_WIDTH + BUTTON_PANEL_WIDTH + 40;
        this.panelY = (this.height - PANEL_HEIGHT) / 2 + 20;

        this.storeModeButton = Button.builder(Component.translatable("ui.familiarslib.familiar_storage_screen.store_mode"), button -> setStoreMode())
                .pos(buttonPanelX + 10, panelY + 40)
                .size(80, 20)
                .build();

        this.wanderModeButton = Button.builder(Component.translatable("ui.familiarslib.familiar_storage_screen.wander_mode"), button -> setWanderMode())
                .pos(buttonPanelX + 10, panelY + 65)
                .size(80, 20)
                .build();

        this.storeButton = Button.builder(Component.literal("←"), button -> moveToStorage())
                .pos(buttonPanelX + 10, panelY + 120)
                .size(80, 20)
                .build();

        this.retrieveButton = Button.builder(Component.literal("→"), button -> moveToPlayer())
                .pos(buttonPanelX + 10, panelY + 160)
                .size(80, 20)
                .build();

        addRenderableWidget(storeModeButton);
        addRenderableWidget(wanderModeButton);
        addRenderableWidget(storeButton);
        addRenderableWidget(retrieveButton);

        loadFamiliarData();
        updateButtonStates();
    }

    private void setStoreMode() {
        if (!isStoreMode) {
            PacketDistributor.sendToServer(new SetStorageModePacket(blockPos, true));
            isStoreMode = true;
            updateButtonStates();
        }
    }

    private void setWanderMode() {
        if (isStoreMode) {
            PacketDistributor.sendToServer(new SetStorageModePacket(blockPos, false));
            isStoreMode = false;
            updateButtonStates();
        }
    }

    private void loadFamiliarData() {
        storedFamiliars.clear();
        playerFamiliars.clear();

        if (minecraft == null || minecraft.player == null) return;

        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            isStoreMode = storageEntity.isStoreMode();

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

        PlayerFamiliarData familiarData = minecraft.player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        Map<UUID, CompoundTag> playerFams = familiarData.getAllFamiliars();

        for (Map.Entry<UUID, CompoundTag> entry : playerFams.entrySet()) {
            UUID id = entry.getKey();
            CompoundTag nbt = entry.getValue();

            FamiliarEntry familiarEntry = createFamiliarEntry(id, nbt);
            if (familiarEntry != null) {
                playerFamiliars.add(familiarEntry);
            }
        }

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
            PlayerFamiliarData familiarData = minecraft.player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

            storeModeButton.active = !isStoreMode;
            wanderModeButton.active = isStoreMode;

            if (isStoreMode) {
                storeButton.active = selectedPlayerFamiliar != null && storageEntity.canStoreFamiliar();
                retrieveButton.active = selectedStoredFamiliar != null && familiarData.canTameMoreFamiliars();
            } else {
                storeButton.active = false;
                retrieveButton.active = false;
            }
        }
    }

    private void moveToStorage() {
        if (selectedPlayerFamiliar != null) {
            PacketDistributor.sendToServer(new MoveFamiliarPacket(blockPos, selectedPlayerFamiliar, true));
            selectedPlayerFamiliar = null;
        }
    }

    private void moveToPlayer() {
        if (selectedStoredFamiliar != null) {
            PacketDistributor.sendToServer(new MoveFamiliarPacket(blockPos, selectedStoredFamiliar, false));
            selectedStoredFamiliar = null;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        animationTime += partialTick;
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            PlayerFamiliarData familiarData = minecraft.player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            // Render mode title
            renderTitle(guiGraphics, Component.translatable("ui.familiarslib.familiar_storage_screen.storage_familiars",
                    storageEntity.getStoredFamiliarCount(),
                    storageEntity.getMaxStoredFamiliars()), storedPanelX, panelY - 30);
            renderTitle(guiGraphics, Component.translatable("ui.familiarslib.familiar_storage_screen.player_familiars"), playerPanelX, panelY - 30);
        }

        // Render panels
        renderFamiliarPanel(guiGraphics, storedFamiliars, storedPanelX, panelY, storedScrollOffset, selectedStoredFamiliar, mouseX, mouseY, partialTick);
        renderFamiliarPanel(guiGraphics, playerFamiliars, playerPanelX, panelY, playerScrollOffset, selectedPlayerFamiliar, mouseX, mouseY, partialTick);

        // Render mode info
        renderModeInfo(guiGraphics);

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

        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(x + 30, y + 40, 50);
        poseStack.scale(25, 25, 25);

        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(45));
        poseStack.mulPose(rotation);

        renderEntity(guiGraphics, entry.familiar, 0, 0, 0);
        poseStack.popPose();

        Component nameComponent = Component.literal(entry.displayName);
        guiGraphics.drawString(font, nameComponent, x + 65, y + 25, 0xFFFFFF);
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

    private void renderModeInfo(GuiGraphics guiGraphics) {
        Component modeInfo = isStoreMode ? Component.translatable("ui.familiarslib.familiar_storage_screen.store_mode") : Component.translatable("ui.familiarslib.familiar_storage_screen.wander_mode");
        int color = isStoreMode ? 0x00FF00 : 0xFFAA00;
        guiGraphics.drawString(font, modeInfo, (buttonPanelX - 20) + (PANEL_WIDTH / 4) - (modeInfo.getString().length() / 2), panelY + 20, color);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
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

    //Helper to store familiars data
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