package net.alshanex.familiarslib.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.BedLinkData;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.LinkFamiliarToBedPacket;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BedLinkSelectionScreen extends Screen {
    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 300;
    private static final int FAMILIAR_ITEM_HEIGHT = 80;
    private static final int SCROLL_SPEED = 20;

    private final BlockPos bedPos;
    private final List<FamiliarEntry> familiarEntries = new ArrayList<>();
    private UUID selectedFamiliarId;
    private UUID currentLinkedFamiliarId;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private float animationTime = 0;

    private int leftPanelX;
    private int rightPanelX;
    private int panelY;

    // Button position variables for click detection
    private int buttonY;
    private int linkButtonX;
    private int unlinkButtonX;
    private int buttonWidth;
    private int buttonHeight;

    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");

    public BedLinkSelectionScreen(BlockPos bedPos) {
        super(Component.translatable("ui.familiarslib.familiar_bed_screen"));
        this.bedPos = bedPos;
    }

    @Override
    protected void init() {
        super.init();
        loadFamiliarData();

        this.leftPanelX = (this.width - PANEL_WIDTH * 2 - 20) / 2;
        this.rightPanelX = this.leftPanelX + PANEL_WIDTH + 20;
        this.panelY = ((this.height - PANEL_HEIGHT) / 2) + 30;
    }

    private void loadFamiliarData() {
        familiarEntries.clear();

        if (minecraft == null || minecraft.player == null) return;

        PlayerFamiliarData familiarData = minecraft.player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        BedLinkData linkData = minecraft.player.getData(AttachmentRegistry.BED_LINK_DATA);

        currentLinkedFamiliarId = linkData.getLinkedFamiliar(bedPos);
        selectedFamiliarId = currentLinkedFamiliarId;

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

                    float health = nbt.getFloat("currentHealth");

                    // Check if this familiar is already linked to another bed
                    BlockPos linkedBed = linkData.getLinkedBed(id);
                    boolean isLinkedToOtherBed = linkedBed != null && !linkedBed.equals(bedPos);

                    familiarEntries.add(new FamiliarEntry(id, familiar, displayName, health, isLinkedToOtherBed));
                }
            }
        }

        int visibleItems = 3;
        maxScroll = Math.max(0, (familiarEntries.size() - visibleItems) * FAMILIAR_ITEM_HEIGHT);
    }

    public void reloadFamiliarData() {
        try {
            FamiliarsLib.LOGGER.info("=== BedLinkSelectionScreen.reloadFamiliarData() START ===");

            // Guardar estado anterior para debugging
            UUID previousSelected = selectedFamiliarId;
            UUID previousLinked = currentLinkedFamiliarId;
            int previousCount = familiarEntries.size();

            FamiliarsLib.LOGGER.info("Previous state - Count: {}, Selected: {}, Linked: {}",
                    previousCount, previousSelected, previousLinked);

            // Limpiar entradas actuales
            familiarEntries.clear();

            // Verificar que tenemos acceso al jugador y sus datos
            if (minecraft == null || minecraft.player == null) {
                FamiliarsLib.LOGGER.warn("Minecraft or player is null, cannot reload familiar data");
                return;
            }

            PlayerFamiliarData familiarData = minecraft.player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            BedLinkData linkData = minecraft.player.getData(AttachmentRegistry.BED_LINK_DATA);

            if (familiarData == null || linkData == null) {
                FamiliarsLib.LOGGER.warn("FamiliarData or BedLinkData is null");
                return;
            }

            // Obtener familiar actualmente vinculado a esta cama
            currentLinkedFamiliarId = linkData.getLinkedFamiliar(bedPos);
            selectedFamiliarId = currentLinkedFamiliarId; // Por defecto, seleccionar el vinculado

            FamiliarsLib.LOGGER.info("Current linked familiar for bed {}: {}", bedPos, currentLinkedFamiliarId);

            Map<UUID, CompoundTag> familiars = familiarData.getAllFamiliars();
            FamiliarsLib.LOGGER.info("Total familiars available: {}", familiars.size());

            // Si no hay familiares, cerrar la pantalla
            if (familiars.isEmpty()) {
                FamiliarsLib.LOGGER.info("No familiars found, closing screen");
                onClose();
                return;
            }

            // Recargar entradas de familiares
            for (Map.Entry<UUID, CompoundTag> entry : familiars.entrySet()) {
                UUID id = entry.getKey();
                CompoundTag nbt = entry.getValue();

                FamiliarsLib.LOGGER.debug("Processing familiar: {} with health: {}", id, nbt.getFloat("currentHealth"));

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

                        float health = nbt.getFloat("currentHealth");

                        // Check if this familiar is already linked to another bed
                        BlockPos linkedBed = linkData.getLinkedBed(id);
                        boolean isLinkedToOtherBed = linkedBed != null && !linkedBed.equals(bedPos);

                        familiarEntries.add(new FamiliarEntry(id, familiar, displayName, health, isLinkedToOtherBed));
                        FamiliarsLib.LOGGER.debug("Added familiar entry: {} ({}) - linked to other bed: {}",
                                displayName, id, isLinkedToOtherBed);
                    }
                } else {
                    FamiliarsLib.LOGGER.warn("Unknown entity type for familiar {}: {}", id, entityTypeString);
                }
            }

            // Si no hay familiar seleccionado pero hay familiares disponibles, seleccionar el primero
            if (selectedFamiliarId == null && !familiarEntries.isEmpty()) {
                selectedFamiliarId = familiarEntries.get(0).id;
                FamiliarsLib.LOGGER.info("No familiar selected, defaulting to first: {}", selectedFamiliarId);
            }

            // Verificar que el familiar seleccionado existe en nuestras entradas
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

            // Calcular scroll máximo
            int visibleItems = 3;
            maxScroll = Math.max(0, (familiarEntries.size() - visibleItems) * FAMILIAR_ITEM_HEIGHT);

            // Asegurar que el scroll esté dentro de los límites
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            FamiliarsLib.LOGGER.info("Reload completed - New count: {}, New selected: {}, New linked: {}, Max scroll: {}",
                    familiarEntries.size(), selectedFamiliarId, currentLinkedFamiliarId, maxScroll);

            FamiliarsLib.LOGGER.info("=== BedLinkSelectionScreen.reloadFamiliarData() END ===");

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error reloading familiar data in BedLinkSelectionScreen: ", e);
            // En caso de error, cerrar la pantalla para evitar estados inconsistentes
            try {
                onClose();
            } catch (Exception closeError) {
                FamiliarsLib.LOGGER.error("Error closing screen after reload failure: ", closeError);
            }
        }
    }

    private void drawHeartIcon(GuiGraphics guiGraphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        guiGraphics.blit(HEART_CONTAINER, x, y - 1, 0, 0, 9, 9, 9, 9);
        guiGraphics.blit(HEART_FULL, x, y - 1, 0, 0, 9, 9, 9, 9);

        RenderSystem.disableBlend();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        animationTime += partialTick;

        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Render title
        Component titleComponent = Component.translatable("ui.familiarslib.familiar_bed_screen.tooltip1");
        int titleWidth = font.width(titleComponent);
        guiGraphics.drawString(font, titleComponent, (width - titleWidth) / 2, panelY - 40, 0xFFFFFF);

        // Render current link status
        if (currentLinkedFamiliarId != null) {
            FamiliarEntry currentLinked = getFamiliarEntry(currentLinkedFamiliarId);
            if (currentLinked != null) {
                Component statusComponent = Component.translatable("ui.familiarslib.familiar_bed_screen.tooltip2", currentLinked.displayName);
                int statusWidth = font.width(statusComponent);
                guiGraphics.drawString(font, statusComponent, (width - statusWidth) / 2, panelY - 20, 0x55FF55);
            }
        } else {
            Component statusComponent = Component.translatable("ui.familiarslib.familiar_bed_screen.tooltip3");
            int statusWidth = font.width(statusComponent);
            guiGraphics.drawString(font, statusComponent, (width - statusWidth) / 2, panelY - 20, 0xFFFF55);
        }

        renderSelectedFamiliar(guiGraphics, mouseX, mouseY, partialTick);
        renderFamiliarList(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderSelectedFamiliar(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        FamiliarEntry selectedEntry = getFamiliarEntry(selectedFamiliarId);
        if (selectedEntry == null) return;

        PoseStack poseStack = guiGraphics.pose();

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

        infoY += 20;
        Component healthComponent = Component.literal(String.format("%.1f", selectedEntry.health));
        int healthWidth = font.width(healthComponent);
        drawHeartIcon(guiGraphics, leftPanelX + (PANEL_WIDTH - healthWidth) / 2 - 12, infoY);
        guiGraphics.drawString(font, healthComponent, leftPanelX + (PANEL_WIDTH - healthWidth) / 2, infoY, 0xFF5555);

        if (selectedEntry.isLinkedToOtherBed) {
            infoY += 20;
            Component warningComponent = Component.translatable("ui.familiarslib.familiar_bed_screen.tooltip4");
            int warningWidth = font.width(warningComponent);
            guiGraphics.drawString(font, warningComponent, leftPanelX + (PANEL_WIDTH - warningWidth) / 2, infoY, 0xFFAA00);
        }

        // Render buttons 20 pixels below the last info line
        renderButtons(guiGraphics, mouseX, mouseY, partialTick, infoY + 20);
    }

    private void renderFamiliarList(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int scissorX = rightPanelX;
        int scissorY = panelY;
        int scissorWidth = PANEL_WIDTH;
        int scissorHeight = PANEL_HEIGHT;

        enableScissor(scissorX, scissorY, scissorX + scissorWidth, scissorY + scissorHeight);

        int currentY = panelY - scrollOffset;

        for (int i = 0; i < familiarEntries.size(); i++) {
            FamiliarEntry entry = familiarEntries.get(i);

            if (currentY + FAMILIAR_ITEM_HEIGHT > panelY && currentY < panelY + scissorHeight) {
                renderFamiliarItem(guiGraphics, entry, rightPanelX, currentY, mouseX, mouseY, partialTick);
            }

            currentY += FAMILIAR_ITEM_HEIGHT;
        }

        disableScissor();
    }

    private void renderFamiliarItem(GuiGraphics guiGraphics, FamiliarEntry entry, int x, int y, int mouseX, int mouseY, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();

        boolean isSelected = entry.id.equals(selectedFamiliarId);
        boolean isCurrentlyLinked = entry.id.equals(currentLinkedFamiliarId);
        boolean isHovered = mouseX >= x && mouseX < x + PANEL_WIDTH &&
                mouseY >= y && mouseY < y + FAMILIAR_ITEM_HEIGHT;

        if (isCurrentlyLinked) {
            guiGraphics.fill(x, y, x + PANEL_WIDTH, y + FAMILIAR_ITEM_HEIGHT, 0x4400AA00);
        } else if (isSelected) {
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

        Component healthComponent = Component.literal(String.format("%.0f", entry.health));
        drawHeartIcon(guiGraphics, x + 85, y + 35);
        guiGraphics.drawString(font, healthComponent, x + 97, y + 35, 0xFF5555);

        if (entry.isLinkedToOtherBed) {
            Component warningComponent = Component.translatable("ui.familiarslib.familiar_bed_screen.tooltip5");
            guiGraphics.drawString(font, warningComponent, x + 85, y + 50, 0xFFAA00);
        }

        if (isCurrentlyLinked) {
            Component linkedIndicator = Component.translatable("ui.familiarslib.familiar_bed_screen.tooltip6");
            guiGraphics.drawString(font, linkedIndicator, x + PANEL_WIDTH - 60, y + 10, 0x00FF00);
        }
    }

    private void renderButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int buttonY) {
        int buttonWidth = 80;
        int buttonHeight = 20;
        int buttonSpacing = 10;

        // Calculate center position for buttons
        int totalButtonWidth = buttonWidth * 2 + buttonSpacing;
        int startX = leftPanelX + (PANEL_WIDTH - totalButtonWidth) / 2;

        // Link button
        int linkButtonX = startX;
        boolean canLink = selectedFamiliarId != null && !selectedFamiliarId.equals(currentLinkedFamiliarId);
        int linkButtonColor = canLink ? 0xFF006600 : 0xFF333333;
        boolean linkHovered = mouseX >= linkButtonX && mouseX < linkButtonX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + buttonHeight;

        if (linkHovered && canLink) {
            linkButtonColor = 0xFF008800;
        }

        guiGraphics.fill(linkButtonX, buttonY, linkButtonX + buttonWidth, buttonY + buttonHeight, linkButtonColor);
        guiGraphics.fill(linkButtonX, buttonY, linkButtonX + buttonWidth, buttonY + 1, 0xFFFFFFFF);
        guiGraphics.fill(linkButtonX, buttonY, linkButtonX + 1, buttonY + buttonHeight, 0xFFFFFFFF);
        guiGraphics.fill(linkButtonX + buttonWidth - 1, buttonY, linkButtonX + buttonWidth, buttonY + buttonHeight, 0xFF000000);
        guiGraphics.fill(linkButtonX, buttonY + buttonHeight - 1, linkButtonX + buttonWidth, buttonY + buttonHeight, 0xFF000000);

        Component linkText = Component.translatable("ui.familiarslib.familiar_bed_screen.link");
        int linkTextWidth = font.width(linkText);
        int linkTextColor = canLink ? 0xFFFFFF : 0x666666;
        guiGraphics.drawString(font, linkText, linkButtonX + (buttonWidth - linkTextWidth) / 2, buttonY + 6, linkTextColor);

        // Unlink button
        int unlinkButtonX = startX + buttonWidth + buttonSpacing;
        boolean canUnlink = currentLinkedFamiliarId != null;
        int unlinkButtonColor = canUnlink ? 0xFF660000 : 0xFF333333;
        boolean unlinkHovered = mouseX >= unlinkButtonX && mouseX < unlinkButtonX + buttonWidth &&
                mouseY >= buttonY && mouseY < buttonY + buttonHeight;

        if (unlinkHovered && canUnlink) {
            unlinkButtonColor = 0xFF880000;
        }

        guiGraphics.fill(unlinkButtonX, buttonY, unlinkButtonX + buttonWidth, buttonY + buttonHeight, unlinkButtonColor);
        guiGraphics.fill(unlinkButtonX, buttonY, unlinkButtonX + buttonWidth, buttonY + 1, 0xFFFFFFFF);
        guiGraphics.fill(unlinkButtonX, buttonY, unlinkButtonX + 1, buttonY + buttonHeight, 0xFFFFFFFF);
        guiGraphics.fill(unlinkButtonX + buttonWidth - 1, buttonY, unlinkButtonX + buttonWidth, buttonY + buttonHeight, 0xFF000000);
        guiGraphics.fill(unlinkButtonX, buttonY + buttonHeight - 1, unlinkButtonX + buttonWidth, buttonY + buttonHeight, 0xFF000000);

        Component unlinkText = Component.translatable("ui.familiarslib.familiar_bed_screen.unlink");
        int unlinkTextWidth = font.width(unlinkText);
        int unlinkTextColor = canUnlink ? 0xFFFFFF : 0x666666;
        guiGraphics.drawString(font, unlinkText, unlinkButtonX + (buttonWidth - unlinkTextWidth) / 2, buttonY + 6, unlinkTextColor);

        // Store button positions for click detection
        this.buttonY = buttonY;
        this.linkButtonX = linkButtonX;
        this.unlinkButtonX = unlinkButtonX;
        this.buttonWidth = buttonWidth;
        this.buttonHeight = buttonHeight;
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

    private FamiliarEntry getFamiliarEntry(UUID familiarId) {
        if (familiarId == null) return null;
        return familiarEntries.stream()
                .filter(entry -> entry.id.equals(familiarId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check familiar list clicks
            if (mouseX >= rightPanelX && mouseX < rightPanelX + PANEL_WIDTH &&
                    mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

                int relativeY = (int) (mouseY - panelY + scrollOffset);
                int itemIndex = relativeY / FAMILIAR_ITEM_HEIGHT;

                if (itemIndex >= 0 && itemIndex < familiarEntries.size()) {
                    FamiliarEntry selected = familiarEntries.get(itemIndex);
                    selectedFamiliarId = selected.id;
                    return true;
                }
            }

            // Check button clicks - Using stored positions
            if (buttonY > 0) { // Make sure buttons have been rendered
                // Link button
                if (mouseX >= linkButtonX && mouseX < linkButtonX + buttonWidth &&
                        mouseY >= buttonY && mouseY < buttonY + buttonHeight) {

                    if (selectedFamiliarId != null && !selectedFamiliarId.equals(currentLinkedFamiliarId)) {
                        PacketDistributor.sendToServer(new LinkFamiliarToBedPacket(bedPos, selectedFamiliarId));
                        onClose();
                    }
                    return true;
                }

                // Unlink button
                if (mouseX >= unlinkButtonX && mouseX < unlinkButtonX + buttonWidth &&
                        mouseY >= buttonY && mouseY < buttonY + buttonHeight) {

                    if (currentLinkedFamiliarId != null) {
                        PacketDistributor.sendToServer(new LinkFamiliarToBedPacket(bedPos, null));
                        onClose();
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= rightPanelX && mouseX < rightPanelX + PANEL_WIDTH &&
                mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

            scrollOffset -= (int) (scrollY * SCROLL_SPEED);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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

    private static class FamiliarEntry {
        final UUID id;
        final AbstractSpellCastingPet familiar;
        final String displayName;
        final float health;
        final boolean isLinkedToOtherBed;

        FamiliarEntry(UUID id, AbstractSpellCastingPet familiar, String displayName,
                      float health, boolean isLinkedToOtherBed) {
            this.id = id;
            this.familiar = familiar;
            this.displayName = displayName;
            this.health = health;
            this.isLinkedToOtherBed = isLinkedToOtherBed;
        }
    }
}