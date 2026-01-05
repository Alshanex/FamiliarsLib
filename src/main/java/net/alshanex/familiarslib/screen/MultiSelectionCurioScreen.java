package net.alshanex.familiarslib.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.AbstractMultiSelectionCurio;
import net.alshanex.familiarslib.network.UpdateMultiSelectionCurioPacket;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
import net.alshanex.familiarslib.util.consumables.ConsumableUtils;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class MultiSelectionCurioScreen extends Screen {
    private static final int CELL_SIZE = 120;
    private static final int GRID_COLS = 3;
    private static final int GRID_ROWS = 2;
    private static final int PANEL_WIDTH = GRID_COLS * CELL_SIZE + 40;
    private static final int PANEL_HEIGHT = GRID_ROWS * CELL_SIZE + 40;
    private static final int SCROLL_SPEED = 20;

    private final List<FamiliarGridEntry> familiarEntries = new ArrayList<>();
    private final Set<UUID> selectedFamiliars = new HashSet<>();
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private float animationTime = 0;

    private int gridStartX;
    private int gridStartY;

    private static final ResourceLocation HEART_CONTAINER = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/heart/container.png");
    private static final ResourceLocation HEART_FULL = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/heart/full.png");
    private static final ResourceLocation ARMOR_FULL = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/armor_full.png");
    private static final ResourceLocation RED_MUSHROOM = ResourceLocation.withDefaultNamespace("textures/block/red_mushroom.png");

    public MultiSelectionCurioScreen() {
        super(Component.translatable("screen.familiarslib.multi_selection_curio"));
    }

    @Override
    protected void init() {
        super.init();
        loadFamiliarData();
        loadSelectedFamiliars();

        this.gridStartX = (this.width - PANEL_WIDTH) / 2 + 20;

        int availableHeight = this.height - 55;
        this.gridStartY = 40 + (availableHeight - PANEL_HEIGHT) / 2;
        this.gridStartY = Math.max(40, this.gridStartY);

        calculateMaxScroll();
    }

    private void loadFamiliarData() {
        familiarEntries.clear();

        if (minecraft == null || minecraft.player == null) return;

        PlayerFamiliarData familiarData = minecraft.player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
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

                    // Legacy migration (same as before)
                    if (!nbt.contains("consumableData")) {
                        // ... same legacy migration code ...
                    }

                    // NEW SYSTEM: Use direct health value
                    float displayHealth = nbt.getFloat("currentHealth");

                    // Fallback for old saves with percentage system
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

                    familiarEntries.add(new FamiliarGridEntry(id, familiar, displayName, displayHealth, armor, enraged, canBlock));

                    float maxHealthWithModifiers = ConsumableUtils.calculateMaxHealthWithModifiers(
                            consumableData, baseMaxHealth);

                    FamiliarsLib.LOGGER.debug("Multi Selection curio: Loaded familiar {} - Health: {}/{}, Armor: {}, Enraged: {}, Blocking: {}",
                            id, displayHealth, maxHealthWithModifiers, armor, enraged, canBlock);
                }
            }
        }
    }

    private void loadSelectedFamiliars() {
        selectedFamiliars.clear();

        if (minecraft == null || minecraft.player == null) return;

        Set<UUID> equippedFamiliars = CurioUtils.getSelectedFamiliarsFromEquipped(minecraft.player);
        if (!equippedFamiliars.isEmpty()) {
            selectedFamiliars.addAll(equippedFamiliars);
            return;
        }

        ItemStack heldItem = minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(heldItem.getItem() instanceof AbstractMultiSelectionCurio)) {
            heldItem = minecraft.player.getItemInHand(InteractionHand.OFF_HAND);
        }

        if (heldItem.getItem() instanceof AbstractMultiSelectionCurio) {
            selectedFamiliars.addAll(AbstractMultiSelectionCurio.getSelectedFamiliars(heldItem));
        }
    }

    private void calculateMaxScroll() {
        int totalRows = (int) Math.ceil((double) familiarEntries.size() / GRID_COLS);
        int visibleRows = GRID_ROWS;

        // Si tenemos más filas que las visibles, calculamos cuánto necesitamos scrollear
        if (totalRows > visibleRows) {
            maxScroll = (totalRows - visibleRows) * CELL_SIZE;
        } else {
            maxScroll = 0;
        }

        FamiliarsLib.LOGGER.debug("Total familiars: {}, Total rows: {}, Visible rows: {}, Max scroll: {}",
                familiarEntries.size(), totalRows, visibleRows, maxScroll);
    }

    private void drawHeartIcon(GuiGraphics guiGraphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        guiGraphics.blit(HEART_CONTAINER, x, y - 1, 0, 0, 9, 9, 9, 9);
        guiGraphics.blit(HEART_FULL, x, y - 1, 0, 0, 9, 9, 9, 9);

        RenderSystem.disableBlend();
    }

    private void drawArmorIcon(GuiGraphics guiGraphics, int x, int y) {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        guiGraphics.blit(ARMOR_FULL, x, y, 0, 0, 9, 9, 9, 9);

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

        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // Título
        Component title = Component.translatable("screen.familiarslib.multi_selection_curio");
        int titleWidth = font.width(title);
        guiGraphics.drawString(font, title, (width - titleWidth) / 2, 10, 0xFFFFFF);

        // Contador de seleccionados
        Component counter = Component.translatable("screen.familiarslib.multi_selection_curio.selected_count", selectedFamiliars.size(), 10);
        int counterWidth = font.width(counter);
        guiGraphics.drawString(font, counter, (width - counterWidth) / 2, 25, 0xFFFF55);

        renderFamiliarGrid(guiGraphics, mouseX, mouseY, partialTick);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderFamiliarGrid(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Enable scissor test para el scroll
        int scissorX = gridStartX;
        int scissorY = gridStartY;
        int scissorWidth = PANEL_WIDTH - 40;
        int scissorHeight = GRID_ROWS * CELL_SIZE + 20;

        enableScissor(scissorX, scissorY, scissorX + scissorWidth, scissorY + scissorHeight);

        int currentIndex = 0;

        for (FamiliarGridEntry entry : familiarEntries) {
            int col = currentIndex % GRID_COLS;
            int row = currentIndex / GRID_COLS;

            int cellX = gridStartX + col * CELL_SIZE;
            int cellY = gridStartY + row * CELL_SIZE - scrollOffset;

            // Renderizar si cualquier parte de la celda está visible
            if (cellY + CELL_SIZE > gridStartY - 10 && cellY < gridStartY + scissorHeight + 10) {
                renderFamiliarCell(guiGraphics, entry, cellX, cellY, mouseX, mouseY, partialTick);
            }

            currentIndex++;
        }

        disableScissor();
    }

    private void renderFamiliarCell(GuiGraphics guiGraphics, FamiliarGridEntry entry, int x, int y, int mouseX, int mouseY, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();

        boolean isSelected = selectedFamiliars.contains(entry.id);

        // Verificar hover solo si la celda está visible
        boolean cellVisible = y + CELL_SIZE > gridStartY && y < gridStartY + GRID_ROWS * CELL_SIZE;
        boolean isHovered = cellVisible && mouseX >= x + 5 && mouseX < x + CELL_SIZE - 5 &&
                mouseY >= y + 5 && mouseY < y + CELL_SIZE - 5;

        // Fondo de la celda
        int backgroundColor = 0x44000000;
        if (isSelected) {
            backgroundColor = 0x4400FF00; // Verde para seleccionado
        } else if (isHovered) {
            backgroundColor = 0x44FFFFFF; // Blanco para hover
        }

        guiGraphics.fill(x + 5, y + 5, x + CELL_SIZE - 5, y + CELL_SIZE - 5, backgroundColor);

        // Borde
        int borderColor = isSelected ? 0xFF00FF00 : (isHovered ? 0xFFFFFFFF : 0xFF666666);
        guiGraphics.renderOutline(x + 5, y + 5, CELL_SIZE - 10, CELL_SIZE - 10, borderColor);

        // Renderizar familiar en 3D
        poseStack.pushPose();
        poseStack.translate(x + CELL_SIZE / 2, y + 45, 50); // Subir un poco para hacer espacio a los atributos
        poseStack.scale(25, 25, 25); // Reducir un poco el tamaño

        Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(45));
        poseStack.mulPose(rotation);

        renderEntity(guiGraphics, entry.familiar, 0, 0, 0);
        poseStack.popPose();

        // Nombre del familiar
        String displayName = entry.displayName;
        if (font.width(displayName) > CELL_SIZE - 20) {
            displayName = font.plainSubstrByWidth(displayName, CELL_SIZE - 25) + "...";
        }

        int nameWidth = font.width(displayName);
        guiGraphics.drawString(font, displayName, x + (CELL_SIZE - nameWidth) / 2, y + 55, 0xFFFFFF);

        // Atributos en recuadros separados (como en FamiliarSelectionScreen)
        renderAttributesInQuadrants(guiGraphics, entry, x, y + 68);

        // Indicador de selección
        if (isSelected) {
            Component checkmark = Component.literal("✓");
            guiGraphics.drawString(font, checkmark, x + CELL_SIZE - 20, y + 10, 0x00FF00);
        }
    }

    private void renderAttributesInQuadrants(GuiGraphics guiGraphics, FamiliarGridEntry entry, int x, int y) {
        // Crear recuadro para las estadísticas (similar a FamiliarSelectionScreen pero más pequeño)
        int boxX = x + (CELL_SIZE - 80) / 2; // Centrar el recuadro de 80 de ancho
        int boxY = y;
        int boxWidth = 80;
        int boxHeight = 40;

        // Dibujar fondo gris claro con bordes
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xAAC0C0C0); // Gris claro con transparencia
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 1, 0xFF808080); // Borde superior
        guiGraphics.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0xFF808080); // Borde inferior
        guiGraphics.fill(boxX, boxY, boxX + 1, boxY + boxHeight, 0xFF808080); // Borde izquierdo
        guiGraphics.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, 0xFF808080); // Borde derecho

        // Posiciones para cada estadística (dividido en 4 cuadrantes)
        int halfWidth = boxWidth / 2;
        int halfHeight = boxHeight / 2;

        // Arriba izquierda - Vida
        Component healthComponent = Component.literal(String.format("%.0f", entry.health));
        int healthTextWidth = font.width(healthComponent);
        int healthTotalWidth = 9 + 2 + healthTextWidth;
        int healthStartX = boxX + (halfWidth - healthTotalWidth) / 2;
        int healthY = boxY + (halfHeight - 9) / 2;
        drawHeartIcon(guiGraphics, healthStartX, healthY);
        guiGraphics.drawString(font, healthComponent, healthStartX + 11, healthY, 0xFF5555);

        // Arriba derecha - Armadura
        Component armorComponent = Component.literal(String.valueOf(entry.armor));
        int armorTextWidth = font.width(armorComponent);
        int armorTotalWidth = 9 + 2 + armorTextWidth;
        int armorStartX = boxX + halfWidth + (halfWidth - armorTotalWidth) / 2;
        int armorY = boxY + (halfHeight - 9) / 2;
        drawArmorIcon(guiGraphics, armorStartX, armorY);
        guiGraphics.drawString(font, armorComponent, armorStartX + 11, armorY, 0xAAAAAA);

        // Abajo izquierda - Can block
        Component blockComponent = Component.literal(entry.canBlock ? "1" : "0");
        int blockTextWidth = font.width(blockComponent);
        int blockTotalWidth = 16 + 2 + blockTextWidth;
        int blockStartX = boxX + (halfWidth - blockTotalWidth) / 2;
        int blockY = boxY + halfHeight + (halfHeight - 16) / 2;
        ItemStack shield = new ItemStack(Items.SHIELD);
        guiGraphics.renderItem(shield, blockStartX, blockY);
        int blockColor = entry.canBlock ? 0x55FF55 : 0xFF5555;
        guiGraphics.drawString(font, blockComponent, blockStartX + 18, blockY + 4, blockColor);

        // Abajo derecha - Enraged stacks
        Component enragedComponent = Component.literal(String.valueOf(entry.enraged));
        int enragedTextWidth = font.width(enragedComponent);
        int enragedTotalWidth = 16 + 2 + enragedTextWidth;
        int enragedStartX = boxX + halfWidth + (halfWidth - enragedTotalWidth) / 2;
        int enragedY = boxY + halfHeight + (halfHeight - 16) / 2;
        drawItemIcon(guiGraphics, RED_MUSHROOM, enragedStartX, (enragedY - 5));
        guiGraphics.drawString(font, enragedComponent, enragedStartX + 18, enragedY + 4, 0xFF55FF);
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
            int currentIndex = 0;
            for (FamiliarGridEntry entry : familiarEntries) {
                int col = currentIndex % GRID_COLS;
                int row = currentIndex / GRID_COLS;

                int cellX = gridStartX + col * CELL_SIZE;
                int cellY = gridStartY + row * CELL_SIZE - scrollOffset;

                // Verificar que la celda esté visible y dentro del área de scroll
                boolean cellVisible = cellY + CELL_SIZE > gridStartY && cellY < gridStartY + GRID_ROWS * CELL_SIZE;
                boolean mouseInCell = mouseX >= cellX + 5 && mouseX < cellX + CELL_SIZE - 5 &&
                        mouseY >= cellY + 5 && mouseY < cellY + CELL_SIZE - 5;

                if (cellVisible && mouseInCell) {
                    toggleFamiliarSelection(entry.id);
                    return true;
                }

                currentIndex++;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void toggleFamiliarSelection(UUID familiarId) {
        if (selectedFamiliars.contains(familiarId)) {
            selectedFamiliars.remove(familiarId);
        } else if (selectedFamiliars.size() < 10) {
            selectedFamiliars.add(familiarId);
        }

        // Enviar actualización al servidor
        PacketDistributor.sendToServer(new UpdateMultiSelectionCurioPacket(new HashSet<>(selectedFamiliars)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Solo verificar que no sea scroll hacia los lados y que tengamos contenido para scrollear
        if (maxScroll > 0) {
            scrollOffset -= (int) (scrollY * SCROLL_SPEED);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
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

    private static class FamiliarGridEntry {
        final UUID id;
        final AbstractSpellCastingPet familiar;
        final String displayName;
        final float health;
        final int armor;
        final int enraged;
        final boolean canBlock;

        FamiliarGridEntry(UUID id, AbstractSpellCastingPet familiar, String displayName,
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
}