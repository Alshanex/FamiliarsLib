package net.alshanex.familiarslib.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.ReleaseFamiliarPacket;
import net.alshanex.familiarslib.network.SelectFamiliarPacket;
import net.alshanex.familiarslib.registry.CapabilityRegistry;
import net.alshanex.familiarslib.setup.NetworkHandler;
import net.alshanex.familiarslib.util.consumables.ConsumableUtils;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class FamiliarSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 300;
    private static final int FAMILIAR_ITEM_HEIGHT = 80;
    private static final int SCROLL_SPEED = 20;

    private static final ResourceLocation GUI_ICONS_LOCATION = new ResourceLocation("textures/gui/icons.png");

    private static final ResourceLocation RED_MUSHROOM = new ResourceLocation("textures/block/red_mushroom.png");

    private final List<FamiliarEntry> familiarEntries = new ArrayList<>();
    private UUID selectedFamiliarId;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private float animationTime = 0;

    private int leftPanelX;
    private int rightPanelX;
    private int panelY;

    private Button releaseButton;

    public FamiliarSelectionScreen() {
        super(Component.translatable("ui.familiarslib.familiar_selection_screen"));
    }

    @Override
    protected void init() {
        super.init();
        loadFamiliarData();

        this.leftPanelX = (this.width - PANEL_WIDTH * 2 - 20) / 2;
        this.rightPanelX = this.leftPanelX + PANEL_WIDTH + 20;
        this.panelY = ((this.height - PANEL_HEIGHT) / 2) + 30;

        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonX = leftPanelX + (PANEL_WIDTH - buttonWidth) / 2;
        int buttonY = panelY + PANEL_HEIGHT - 85;

        releaseButton = Button.builder(
                Component.translatable("ui.familiarslib.release_familiar"),
                this::onReleaseButtonPressed
        ).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build();

        addRenderableWidget(releaseButton);

        updateReleaseButtonVisibility();
    }

    private void loadFamiliarData() {
        familiarEntries.clear();

        if (minecraft == null || minecraft.player == null) return;

        minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
            selectedFamiliarId = familiarData.getSelectedFamiliarId();

            Map<UUID, CompoundTag> familiars = familiarData.getAllFamiliars();

            for (Map.Entry<UUID, CompoundTag> entry : familiars.entrySet()) {
                UUID id = entry.getKey();
                CompoundTag nbt = entry.getValue();

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

                        float baseMaxHealth = nbt.contains("baseMaxHealth") ?
                                nbt.getFloat("baseMaxHealth") : familiar.getBaseMaxHealth();

                        FamiliarConsumableSystem.ConsumableData consumableData =
                                FamiliarConsumableSystem.loadConsumableDataFromNBT(nbt);

                        if (!nbt.contains("consumableData")) {
                            int legacyArmor = nbt.getInt("armorStacks");
                            int legacyEnraged = nbt.getInt("enragedStacks");
                            int legacyHealth = nbt.getInt("healthStacks");
                            boolean legacyBlocking = nbt.getBoolean("canBlock");

                            if (legacyArmor > 0 || legacyEnraged > 0 || legacyHealth > 0 || legacyBlocking) {
                                if (legacyHealth > 0) {
                                    int healthPercentage = legacyHealth * 10;
                                    healthPercentage = Math.min(healthPercentage, FamiliarConsumableSystem.ConsumableType.HEALTH.getMaxLimit());
                                    consumableData.setValue(FamiliarConsumableSystem.ConsumableType.HEALTH, healthPercentage);
                                }

                                if (legacyArmor > 0) {
                                    int armorPoints = Math.min(legacyArmor, FamiliarConsumableSystem.ConsumableType.ARMOR.getMaxLimit());
                                    consumableData.setValue(FamiliarConsumableSystem.ConsumableType.ARMOR, armorPoints);
                                }

                                if (legacyEnraged > 0) {
                                    int enragedStacks = Math.min(legacyEnraged, FamiliarConsumableSystem.ConsumableType.ENRAGED.getMaxLimit());
                                    consumableData.setValue(FamiliarConsumableSystem.ConsumableType.ENRAGED, enragedStacks);
                                }

                                if (legacyBlocking) {
                                    consumableData.setValue(FamiliarConsumableSystem.ConsumableType.BLOCKING, 1);
                                }

                                FamiliarConsumableSystem.saveConsumableDataToNBT(consumableData, nbt);
                                familiarData.addTamedFamiliar(id, nbt);
                            }
                        }

                        float displayHealth = nbt.getFloat("currentHealth");

                        if (displayHealth <= 0 && nbt.contains("healthPercentage")) {
                            float healthPercentage = nbt.getFloat("healthPercentage");
                            float maxHealthWithModifiers = ConsumableUtils.calculateMaxHealthWithModifiers(
                                    consumableData, baseMaxHealth);
                            displayHealth = healthPercentage * maxHealthWithModifiers;
                        }

                        if (displayHealth <= 0) {
                            displayHealth = baseMaxHealth;
                        }

                        int armor = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.ARMOR);
                        int enraged = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.ENRAGED);
                        boolean canBlock = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.BLOCKING) > 0;

                        familiarEntries.add(new FamiliarEntry(id, familiar, displayName, displayHealth, armor, enraged, canBlock));

                        float maxHealthWithModifiers = ConsumableUtils.calculateMaxHealthWithModifiers(
                                consumableData, baseMaxHealth);

                        FamiliarsLib.LOGGER.debug("FamiliarSelection: Loaded familiar {} - Health: {}/{}, Armor: {}, Enraged: {}, Blocking: {}",
                                id, displayHealth, maxHealthWithModifiers, armor, enraged, canBlock);
                    }
                }
            }
        });

        int visibleItems = 3;
        maxScroll = Math.max(0, (familiarEntries.size() - visibleItems) * FAMILIAR_ITEM_HEIGHT);
    }

    private void updateReleaseButtonVisibility() {
        if (releaseButton != null) {
            releaseButton.visible = selectedFamiliarId != null && !familiarEntries.isEmpty();
        }
    }

    private void onReleaseButtonPressed(Button button) {
        if (selectedFamiliarId == null) return;

        FamiliarEntry selectedEntry = getSelectedEntry();
        String familiarName = selectedEntry != null ? selectedEntry.displayName : "Unknown";

        Component title = Component.translatable("ui.familiarslib.confirm_release");
        Component message = Component.translatable("ui.familiarslib.confirm_release_message", familiarName);

        minecraft.setScreen(new ConfirmReleaseScreen(this, title, message, (confirmed) -> {
            if (confirmed) {
                NetworkHandler.sendToServer(new ReleaseFamiliarPacket(selectedFamiliarId));
            }
        }));
    }

    public void reloadFamiliarData() {
        try {
            FamiliarsLib.LOGGER.debug("=== FamiliarSelectionScreen.reloadFamiliarData() START ===");

            UUID previousSelected = selectedFamiliarId;
            int previousCount = familiarEntries.size();

            FamiliarsLib.LOGGER.debug("Previous state - Count: {}, Selected: {}", previousCount, previousSelected);

            familiarEntries.clear();

            if (minecraft == null || minecraft.player == null) {
                FamiliarsLib.LOGGER.warn("Minecraft or player is null, cannot reload familiar data");
                return;
            }

            minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
                Map<UUID, CompoundTag> familiars = familiarData.getAllFamiliars();
                UUID currentSelected = familiarData.getSelectedFamiliarId();

                FamiliarsLib.LOGGER.debug("Current data - Familiar count: {}, Selected: {}", familiars.size(), currentSelected);

                if (familiars.isEmpty()) {
                    FamiliarsLib.LOGGER.debug("No familiars found, closing screen");
                    onClose();
                    return;
                }

                for (Map.Entry<UUID, CompoundTag> entry : familiars.entrySet()) {
                    UUID id = entry.getKey();
                    CompoundTag nbt = entry.getValue();

                    FamiliarsLib.LOGGER.debug("Processing familiar: {} with saved health: {}", id, nbt.getFloat("currentHealth"));

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

                            float baseMaxHealth = nbt.contains("baseMaxHealth") ?
                                    nbt.getFloat("baseMaxHealth") : familiar.getBaseMaxHealth();

                            FamiliarConsumableSystem.ConsumableData consumableData =
                                    FamiliarConsumableSystem.loadConsumableDataFromNBT(nbt);

                            if (!nbt.contains("consumableData")) {
                                int legacyArmor = nbt.getInt("armorStacks");
                                int legacyEnraged = nbt.getInt("enragedStacks");
                                int legacyHealth = nbt.getInt("healthStacks");
                                boolean legacyBlocking = nbt.getBoolean("canBlock");

                                if (legacyArmor > 0 || legacyEnraged > 0 || legacyHealth > 0 || legacyBlocking) {
                                    if (legacyHealth > 0) {
                                        int healthPercentage = legacyHealth * 10;
                                        healthPercentage = Math.min(healthPercentage, FamiliarConsumableSystem.ConsumableType.HEALTH.getMaxLimit());
                                        consumableData.setValue(FamiliarConsumableSystem.ConsumableType.HEALTH, healthPercentage);
                                    }

                                    if (legacyArmor > 0) {
                                        int armorPoints = Math.min(legacyArmor, FamiliarConsumableSystem.ConsumableType.ARMOR.getMaxLimit());
                                        consumableData.setValue(FamiliarConsumableSystem.ConsumableType.ARMOR, armorPoints);
                                    }

                                    if (legacyEnraged > 0) {
                                        int enragedStacks = Math.min(legacyEnraged, FamiliarConsumableSystem.ConsumableType.ENRAGED.getMaxLimit());
                                        consumableData.setValue(FamiliarConsumableSystem.ConsumableType.ENRAGED, enragedStacks);
                                    }

                                    if (legacyBlocking) {
                                        consumableData.setValue(FamiliarConsumableSystem.ConsumableType.BLOCKING, 1);
                                    }

                                    FamiliarConsumableSystem.saveConsumableDataToNBT(consumableData, nbt);
                                    familiarData.addTamedFamiliar(id, nbt);
                                }
                            }

                            float displayHealth = nbt.getFloat("currentHealth");

                            if (displayHealth <= 0 && nbt.contains("healthPercentage")) {
                                float healthPercentage = nbt.getFloat("healthPercentage");
                                float maxHealthWithModifiers = ConsumableUtils.calculateMaxHealthWithModifiers(
                                        consumableData, baseMaxHealth);
                                displayHealth = healthPercentage * maxHealthWithModifiers;
                            }

                            if (displayHealth <= 0) {
                                displayHealth = baseMaxHealth;
                            }

                            int armor = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.ARMOR);
                            int enraged = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.ENRAGED);
                            boolean canBlock = consumableData.getValue(FamiliarConsumableSystem.ConsumableType.BLOCKING) > 0;

                            familiarEntries.add(new FamiliarEntry(id, familiar, displayName, displayHealth, armor, enraged, canBlock));

                            float maxHealthWithModifiers = ConsumableUtils.calculateMaxHealthWithModifiers(
                                    consumableData, baseMaxHealth);

                            FamiliarsLib.LOGGER.debug("Reloaded familiar {} - Health: {}/{}, Armor: {}, Enraged: {}, Blocking: {}",
                                    id, displayHealth, maxHealthWithModifiers, armor, enraged, canBlock);
                        }
                    } else {
                        FamiliarsLib.LOGGER.warn("Unknown entity type for familiar {}: {}", id, entityTypeString);
                    }
                }

                selectedFamiliarId = currentSelected;

                if (selectedFamiliarId != null) {
                    boolean foundSelected = familiarEntries.stream().anyMatch(entry -> entry.id.equals(selectedFamiliarId));
                    if (!foundSelected) {
                        FamiliarsLib.LOGGER.warn("Selected familiar {} not found in entries, selecting first available", selectedFamiliarId);
                        if (!familiarEntries.isEmpty()) {
                            selectedFamiliarId = familiarEntries.get(0).id;
                        } else {
                            selectedFamiliarId = null;
                        }
                    }
                }
            });

            int visibleItems = 3;
            maxScroll = Math.max(0, (familiarEntries.size() - visibleItems) * FAMILIAR_ITEM_HEIGHT);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            updateReleaseButtonVisibility();

            FamiliarsLib.LOGGER.debug("Reload completed - New count: {}, New selected: {}, Max scroll: {}",
                    familiarEntries.size(), selectedFamiliarId, maxScroll);

            FamiliarsLib.LOGGER.debug("=== FamiliarSelectionScreen.reloadFamiliarData() END ===");

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error reloading familiar data in FamiliarSelectionScreen: ", e);
            try {
                onClose();
            } catch (Exception closeError) {
                FamiliarsLib.LOGGER.error("Error closing screen after reload failure: ", closeError);
            }
        }
    }

    private Set<UUID> getAllSummonedFamiliarIds() {
        if (minecraft == null || minecraft.player == null) return new HashSet<>();

        return minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA)
                .map(PlayerFamiliarData::getSummonedFamiliarIds)
                .orElse(new HashSet<>());
    }

    private boolean isFamiliarSummoned(UUID familiarId) {
        if (minecraft == null || minecraft.player == null) return false;

        return minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA)
                .map(data -> data.isFamiliarSummoned(familiarId))
                .orElse(false);
    }

    private void drawHeartIcon(GuiGraphics guiGraphics, int x, int y) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.blit(GUI_ICONS_LOCATION, x, y - 1, 16, 0, 9, 9);
        guiGraphics.blit(GUI_ICONS_LOCATION, x, y - 1, 52, 0, 9, 9);

        RenderSystem.disableBlend();
    }

    private void drawArmorIcon(GuiGraphics guiGraphics, int x, int y) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.blit(GUI_ICONS_LOCATION, x, y, 43, 9, 9, 9);

        RenderSystem.disableBlend();
    }

    private void drawItemIcon(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.blit(texture, x, y, 0, 0, 16, 16, 16, 16);

        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        animationTime += partialTick;

        renderBackground(guiGraphics);

        renderSelectedFamiliar(guiGraphics, mouseX, mouseY, partialTick);

        renderFamiliarList(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSelectedFamiliar(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FamiliarEntry selectedEntry = getSelectedEntry();
        if (selectedEntry == null) return;

        PoseStack poseStack = guiGraphics.pose();

        if (isFamiliarSummoned(selectedEntry.id)) {
            Component statusComponent = Component.translatable("ui.familiarslib.familiar_summoned");
            int statusWidth = font.width(statusComponent);
            guiGraphics.drawString(font, statusComponent, leftPanelX + (PANEL_WIDTH - statusWidth) / 2, panelY + 20, 0xFFFF55);
        }

        poseStack.pushPose();
        poseStack.translate(leftPanelX + PANEL_WIDTH / 2, panelY + 100, 100);
        poseStack.scale(60, 60, 60);

        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(45));
        poseStack.mulPose(rotation);

        renderEntity(guiGraphics, selectedEntry.familiar, 0, 0, 0);
        poseStack.popPose();

        int infoY = panelY + 120;

        Component nameComponent = Component.literal(selectedEntry.displayName);
        int nameWidth = font.width(nameComponent);
        guiGraphics.drawString(font, nameComponent, leftPanelX + (PANEL_WIDTH - nameWidth) / 2, infoY, 0xFFFFFF);

        int boxX = leftPanelX + (PANEL_WIDTH - 120) / 2;
        int boxY = infoY + 25;
        int boxWidth = 120;
        int boxHeight = 60;

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xAAC0C0C0);
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF808080);
        guiGraphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF808080);
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFF808080);
        guiGraphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF808080);

        int halfWidth = boxWidth / 2;
        int halfHeight = boxHeight / 2;

        Component healthComponent = Component.literal(String.format("%.0f", selectedEntry.health));
        int healthTextWidth = font.width(healthComponent);
        int healthTotalWidth = 9 + 2 + healthTextWidth;
        int healthStartX = boxX + (halfWidth - healthTotalWidth) / 2;
        int healthY = boxY + (halfHeight - 9) / 2;
        drawHeartIcon(guiGraphics, healthStartX, healthY);
        guiGraphics.drawString(font, healthComponent, healthStartX + 11, healthY, 0xFF5555);

        Component armorComponent = Component.literal(String.valueOf(selectedEntry.armor));
        int armorTextWidth = font.width(armorComponent);
        int armorTotalWidth = 9 + 2 + armorTextWidth;
        int armorStartX = boxX + halfWidth + (halfWidth - armorTotalWidth) / 2;
        int armorY = boxY + (halfHeight - 9) / 2;
        drawArmorIcon(guiGraphics, armorStartX, armorY);
        guiGraphics.drawString(font, armorComponent, armorStartX + 11, armorY, 0xAAAAAA);

        Component blockComponent = Component.literal(selectedEntry.canBlock ? "1" : "0");
        int blockTextWidth = font.width(blockComponent);
        int blockTotalWidth = 16 + 2 + blockTextWidth;
        int blockStartX = boxX + (halfWidth - blockTotalWidth) / 2;
        int blockY = boxY + halfHeight + (halfHeight - 16) / 2;
        ItemStack shield = new ItemStack(Items.SHIELD);
        guiGraphics.renderItem(shield, blockStartX, blockY);
        int blockColor = selectedEntry.canBlock ? 0x55FF55 : 0xFF5555;
        guiGraphics.drawString(font, blockComponent, blockStartX + 18, blockY + 4, blockColor);

        Component enragedComponent = Component.literal(String.valueOf(selectedEntry.enraged));
        int enragedTextWidth = font.width(enragedComponent);
        int enragedTotalWidth = 16 + 2 + enragedTextWidth;
        int enragedStartX = boxX + halfWidth + (halfWidth - enragedTotalWidth) / 2;
        int enragedY = boxY + halfHeight + (halfHeight - 16) / 2;
        drawItemIcon(guiGraphics, RED_MUSHROOM, enragedStartX, (enragedY - 5));
        guiGraphics.drawString(font, enragedComponent, enragedStartX + 18, enragedY + 4, 0xFF55FF);
    }

    private UUID getCurrentSummonedFamiliarId() {
        if (minecraft == null || minecraft.player == null) return null;

        return minecraft.player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA)
                .map(PlayerFamiliarData::getCurrentSummonedFamiliarId)
                .orElse(null);
    }

    private void renderFamiliarList(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();

        int scissorX = rightPanelX;
        int scissorY = panelY;
        int scissorWidth = PANEL_WIDTH;
        int scissorHeight = PANEL_HEIGHT;

        enableScissor(scissorX, scissorY, scissorX + scissorWidth, scissorY + scissorHeight);

        int currentY = panelY - scrollOffset;

        for (int i = 0; i < familiarEntries.size(); i++) {
            FamiliarEntry entry = familiarEntries.get(i);

            if (currentY + FAMILIAR_ITEM_HEIGHT > panelY && currentY < panelY + PANEL_HEIGHT) {
                renderFamiliarItem(guiGraphics, entry, rightPanelX, currentY, mouseX, mouseY, partialTick);
            }

            currentY += FAMILIAR_ITEM_HEIGHT;
        }

        disableScissor();
    }

    private void renderFamiliarItem(GuiGraphics guiGraphics, FamiliarEntry entry, int x, int y, int mouseX, int mouseY, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();

        boolean isSelected = entry.id.equals(selectedFamiliarId);
        boolean isHovered = mouseX >= x && mouseX < x + PANEL_WIDTH &&
                mouseY >= y && mouseY < y + FAMILIAR_ITEM_HEIGHT;

        if (isSelected) {
            guiGraphics.fill(x, y, x + PANEL_WIDTH, y + FAMILIAR_ITEM_HEIGHT, 0x4400FF00);
        } else if (isHovered) {
            guiGraphics.fill(x, y, x + PANEL_WIDTH, y + FAMILIAR_ITEM_HEIGHT, 0x44FFFFFF);
        }

        poseStack.pushPose();
        poseStack.translate(x + 40, y + 50, 50);
        poseStack.scale(30, 30, 30);

        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(45));
        poseStack.mulPose(rotation);

        renderEntity(guiGraphics, entry.familiar, 0, 0, 0);
        poseStack.popPose();

        Component nameComponent = Component.literal(entry.displayName);
        guiGraphics.drawString(font, nameComponent, x + 85, y + 20, 0xFFFFFF);

        drawHeartIcon(guiGraphics, x + 85, y + 35);
        Component healthComponent = Component.literal(String.format("%.0f", entry.health));
        guiGraphics.drawString(font, healthComponent, x + 85 + 12, y + 35, 0xFF5555);

        drawArmorIcon(guiGraphics, x + 85, y + 49);
        Component armorComponent = Component.literal(String.valueOf(entry.armor));
        guiGraphics.drawString(font, armorComponent, x + 85 + 12, y + 50, 0xAAAAAA);

        if (isFamiliarSummoned(entry.id)) {
            Component summonedIndicator = Component.literal("★");
            guiGraphics.drawString(font, summonedIndicator, x + PANEL_WIDTH - 20, y + 10, 0xFFFF55);
        }
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
            guiGraphics.drawString(font, errorText, (int) x - textWidth / 2, (int) y, 0xFF5555);
        }

        RenderSystem.enableCull();

        poseStack.popPose();
    }

    private FamiliarEntry getSelectedEntry() {
        if (selectedFamiliarId == null) return null;

        return familiarEntries.stream()
                .filter(entry -> entry.id.equals(selectedFamiliarId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= rightPanelX && mouseX < rightPanelX + PANEL_WIDTH &&
                    mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

                int relativeY = (int) (mouseY - panelY + scrollOffset);
                int itemIndex = relativeY / FAMILIAR_ITEM_HEIGHT;

                if (itemIndex >= 0 && itemIndex < familiarEntries.size()) {
                    FamiliarEntry selected = familiarEntries.get(itemIndex);
                    selectedFamiliarId = selected.id;

                    NetworkHandler.sendToServer(new SelectFamiliarPacket(selected.id));

                    updateReleaseButtonVisibility();

                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX >= rightPanelX && mouseX < rightPanelX + PANEL_WIDTH &&
                mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

            scrollOffset -= (int) (scrollY * SCROLL_SPEED);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

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
        final float health;
        final int armor;
        final int enraged;
        final boolean canBlock;

        FamiliarEntry(UUID id, AbstractSpellCastingPet familiar, String displayName,
                      float health, int armor, int enraged, boolean canBlock) {
            this.id = id;
            this.familiar = familiar;
            this.displayName = displayName;
            this.health = health;
            this.armor = armor;
            this.enraged = enraged;
            this.canBlock = canBlock;
        }
    }

    private static class ConfirmReleaseScreen extends Screen {
        private final Screen parent;
        private final Component message;
        private final java.util.function.Consumer<Boolean> callback;

        protected ConfirmReleaseScreen(Screen parent, Component title, Component message,
                                       java.util.function.Consumer<Boolean> callback) {
            super(title);
            this.parent = parent;
            this.message = message;
            this.callback = callback;
        }

        @Override
        protected void init() {
            super.init();

            int buttonWidth = 60;
            int buttonHeight = 20;
            int spacing = 10;
            int totalWidth = buttonWidth * 2 + spacing;
            int startX = (this.width - totalWidth) / 2;
            int buttonY = this.height / 2 + 20;

            addRenderableWidget(Button.builder(
                    Component.translatable("gui.yes"),
                    (button) -> {
                        callback.accept(true);
                        minecraft.setScreen(parent);
                    }
            ).bounds(startX, buttonY, buttonWidth, buttonHeight).build());

            addRenderableWidget(Button.builder(
                    Component.translatable("gui.cancel"),
                    (button) -> {
                        callback.accept(false);
                        minecraft.setScreen(parent);
                    }
            ).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build());
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            renderBackground(guiGraphics);

            Component titleComponent = this.getTitle();
            int titleWidth = font.width(titleComponent);
            guiGraphics.drawString(font, titleComponent,
                    (this.width - titleWidth) / 2, this.height / 2 - 40, 0xFFFFFF);

            int messageWidth = font.width(message);
            guiGraphics.drawString(font, message,
                    (this.width - messageWidth) / 2, this.height / 2 - 10, 0xFFFFFF);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics) {
            guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);

            int boxWidth = 300;
            int boxHeight = 120;
            int boxX = (this.width - boxWidth) / 2;
            int boxY = (this.height - boxHeight) / 2;

            guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xCC222222);
            guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFFFFFFFF);
            guiGraphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFFFFFFFF);
            guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFFFFFFFF);
            guiGraphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFFFFFFFF);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) {
                callback.accept(false);
                minecraft.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}