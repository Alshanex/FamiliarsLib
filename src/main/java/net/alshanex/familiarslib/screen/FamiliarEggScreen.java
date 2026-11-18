package net.alshanex.familiarslib.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.client.FamiliarPreviewHelper;
import net.alshanex.familiarslib.client.compendium.ClientCompendium;
import net.alshanex.familiarslib.client.gui.GuiSprite;
import net.alshanex.familiarslib.client.gui.TexturedButton;
import net.alshanex.familiarslib.compendium.CompendiumCatalog;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.ClientboundRecallListPacket;
import net.alshanex.familiarslib.network.ServerboundRecallFamiliarPacket;
import net.alshanex.familiarslib.render.FamiliarEggBedRenderer;
import net.alshanex.familiarslib.render.LayerColorHolder;
import net.alshanex.familiarslib.util.familiars.EggColor;
import net.alshanex.familiarslib.util.familiars.FamiliarRecall;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lists the familiars that can be recalled with a Recall Shard, with a preview of the selected one. */
public class FamiliarEggScreen extends Screen {
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 220;
    private static final int LIST_W = 150;
    // Rows fit two lines of text inside the 2px card border; 7 of them fill the list frame (176px)
    private static final int ROW_H = 25;
    private static final int TEXT_INSET = 5;
    private static final int LIST_TOP = 24;
    private static final int VISIBLE_ROWS = (PANEL_H - LIST_TOP - 8) / ROW_H;

    private static final int RIGHT_X = LIST_W + 18;
    private static final int PREVIEW_H = 76;
    private static final int LAYER_ROW_H = 15;
    private static final int SWATCH = 8;
    /** One full turn of the familiar preview. */
    private static final long SPIN_PERIOD_MS = 12000L;

    private static final int COLOR_RELEASED = 0xAAAAAA;
    private static final int COLOR_FALLEN = 0xFF5555;

    private static final GuiSprite SHEET = GuiSprite.sheet(FamiliarsLib.id("textures/gui/recall_shard.png"), 512, 512);
    private static final GuiSprite PANEL = SHEET.at(0, 0, PANEL_W, PANEL_H);
    private static final GuiSprite ROW_SELECTED = SHEET.at(342, 66, 150, 20);
    private static final GuiSprite ROW_HOVER = SHEET.at(342, 88, 150, 20);
    private static final GuiSprite LAYER_SELECTED = SHEET.at(342, 110, 164, 11);
    private static final GuiSprite LAYER_HOVER = SHEET.at(342, 123, 164, 11);
    private static final GuiSprite SCROLL_THUMB = SHEET.at(508, 0, 2, 20);
    private static final TexturedButton.Sprites RECALL_BUTTON = new TexturedButton.Sprites(
            SHEET.at(342, 0, 164, 20), SHEET.at(342, 22, 164, 20), SHEET.at(342, 44, 164, 20));

    /** One entry of the layer list. {@code slot} -1 is the set color; {@code rgb} is what the egg would look like. */
    private record LayerChoice(int slot, Component name, int rgb, boolean original) {}

    private static final class Row {
        final ClientboundRecallListPacket.Entry entry;
        final Component name;
        final AbstractSpellCastingPet preview;
        final List<LayerChoice> layers;
        int selectedLayer;

        Row(ClientboundRecallListPacket.Entry entry, Component name, AbstractSpellCastingPet preview,
            List<LayerChoice> layers, int selectedLayer) {
            this.entry = entry;
            this.name = name;
            this.preview = preview;
            this.layers = layers;
            this.selectedLayer = selectedLayer;
        }
    }

    private final List<ClientboundRecallListPacket.Entry> entries;
    private final List<Row> rows = new ArrayList<>();
    private int selected = 0;
    private int scroll = 0;
    private int left;
    private int top;
    private Button recallButton;

    public FamiliarEggScreen(List<ClientboundRecallListPacket.Entry> entries) {
        super(Component.translatable("ui.familiarslib.recall_shard.title"));
        this.entries = entries;
    }

    public static void open(List<ClientboundRecallListPacket.Entry> entries) {
        Minecraft.getInstance().setScreen(new FamiliarEggScreen(entries));
    }

    @Override
    protected void init() {
        super.init();
        if (rows.isEmpty()) {
            buildRows();
        }

        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        int x = left + RIGHT_X;
        recallButton = new TexturedButton(x, top + PANEL_H - 28, left + PANEL_W - 8 - x, 20,
                Component.translatable("ui.familiarslib.recall_shard.recall"), RECALL_BUTTON, b -> recallSelected());
        addRenderableWidget(recallButton);
        updateButton();
    }

    private void buildRows() {
        if (minecraft == null || minecraft.level == null) return;
        for (ClientboundRecallListPacket.Entry entry : entries) {
            AbstractSpellCastingPet preview = null;
            EntityType<?> type = EntityType.byString(entry.data().getString("id")).orElse(null);
            if (type != null) {
                try {
                    Entity entity = type.create(minecraft.level);
                    if (entity instanceof AbstractSpellCastingPet familiar) {
                        familiar.load(entry.data());
                        familiar.setUUID(entry.familiarId());
                        preview = familiar;
                    }
                } catch (Exception ignored) {
                    // Preview is optional; the row still works without it
                }
            }

            List<LayerChoice> layers = new ArrayList<>();
            int selectedLayer = 0;
            if (preview != null) {
                EggColor eggColor = preview.getEggColor();
                layers.add(new LayerChoice(-1, Component.translatable("ui.familiarslib.recall_shard.default_color"), eggColor.color(), false));
                layers.addAll(layerChoices(type, preview, eggColor.color()));
                for (int i = 0; i < layers.size(); i++) {
                    if (layers.get(i).slot() == eggColor.layerSlot()) {
                        selectedLayer = i; // the familiar's own choice is preselected
                    }
                }
            }
            rows.add(new Row(entry, FamiliarRecall.familiarName(entry.data()), preview, layers, selectedLayer));
        }
    }

    /**
     * The familiar's layers, named like in the compendium.
     */
    private static List<LayerChoice> layerChoices(EntityType<?> type, AbstractSpellCastingPet familiar, int setColor) {
        Map<Integer, Component> names = new LinkedHashMap<>();
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        for (CompendiumCatalog.Section section : ClientCompendium.catalog().sections()) {
            if (section.entityType().equals(typeId)) {
                for (CompendiumCatalog.LayerOptions layer : section.layers()) {
                    names.put(layer.slot(), Component.translatable(layer.nameKey()));
                }
            }
        }
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            if (familiar.getLayerColor(slot) != LayerColorHolder.NO_LAYER_COLOR) {
                names.putIfAbsent(slot, Component.translatable("item.familiarslib.identity_vessel.layer", slot + 1));
            }
        }

        List<LayerChoice> choices = new ArrayList<>();
        names.forEach((slot, name) -> {
            boolean original = familiar.getLayerColor(slot) == LayerColorHolder.NO_LAYER_COLOR;
            choices.add(new LayerChoice(slot, name, EggColor.layerColor(familiar, slot, setColor), original));
        });
        return choices;
    }

    private Row selectedRow() {
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    private void updateButton() {
        if (recallButton != null) {
            recallButton.active = selectedRow() != null;
        }
    }

    private void recallSelected() {
        Row row = selectedRow();
        if (row == null) return;
        int layerSlot = !row.layers.isEmpty() ? row.layers.get(row.selectedLayer).slot() : -1;
        PacketDistributor.sendToServer(new ServerboundRecallFamiliarPacket(row.entry.familiarId(), layerSlot));
        onClose();
    }

    /** What the selected familiar's egg would look like, as the server will work it out. */
    private int previewEggColor(Row row) {
        if (row.layers.isEmpty()) return EggColor.DEFAULT_RGB;
        return row.layers.get(row.selectedLayer).rgb();
    }

    private boolean showLayerList(Row row) {
        return row != null && row.layers.size() > 1;
    }

    // Layout of the right column

    private int rightX() { return left + RIGHT_X; }
    private int rightW() { return PANEL_W - RIGHT_X - 8; }
    private int previewTop() { return top + LIST_TOP; }
    private int previewBottom(Row row) {
        if (!showLayerList(row)) return top + PANEL_H - 34;
        // The preview gives up height when the familiar has many layers, so every layer row fits
        int roomForLayers = row.layers.size() * LAYER_ROW_H + 14;
        return Math.min(previewTop() + PREVIEW_H, top + PANEL_H - 34 - roomForLayers);
    }
    private int layersTop(Row row) { return previewBottom(row) + 14; }
    private int visibleLayerRows(Row row) { return (top + PANEL_H - 34 - layersTop(row)) / LAYER_ROW_H; }

    // Rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        PANEL.draw(graphics, left, top);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 8, 0xFFFFFF);
        renderList(graphics, mouseX, mouseY);

        Row row = selectedRow();
        if (row == null) return;
        renderPreviews(graphics, row, mouseX, mouseY, partialTick);
        if (showLayerList(row)) {
            renderLayerList(graphics, row, mouseX, mouseY);
        }
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8;
        int hovered = rowAt(mouseX, mouseY);
        for (int i = scroll; i < Math.min(rows.size(), scroll + VISIBLE_ROWS); i++) {
            Row row = rows.get(i);
            int y = top + LIST_TOP + (i - scroll) * ROW_H;

            if (i == selected) {
                ROW_SELECTED.draw(graphics, x + 1, y + 1, LIST_W - 2, ROW_H - 3);
            } else if (i == hovered) {
                ROW_HOVER.draw(graphics, x + 1, y + 1, LIST_W - 2, ROW_H - 3);
            }

            int textWidth = LIST_W - TEXT_INSET * 2;
            graphics.drawString(font, fit(row.name.getString(), textWidth), x + TEXT_INSET, y + 3, 0xFFFFFF);

            ClientboundRecallListPacket.Entry entry = row.entry;
            Component status = Component.translatable(entry.dead()
                    ? "ui.familiarslib.recall_shard.fallen" : "ui.familiarslib.recall_shard.released");
            long days = entry.ageTicks() / 24000L;
            Component age = days == 0
                    ? Component.translatable("ui.familiarslib.recall_shard.today")
                    : Component.translatable("ui.familiarslib.recall_shard.days_ago", days);
            graphics.drawString(font, fit(status.copy().append(" · ").append(age).getString(), textWidth),
                    x + TEXT_INSET, y + 12, entry.dead() ? COLOR_FALLEN : COLOR_RELEASED);
        }

        if (rows.size() > VISIBLE_ROWS) {
            int trackTop = top + LIST_TOP;
            int trackHeight = VISIBLE_ROWS * ROW_H - 2;
            int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / rows.size());
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / (rows.size() - VISIBLE_ROWS);
            SCROLL_THUMB.draw(graphics, x + LIST_W + 1, thumbY, 2, thumbHeight);
        }
    }

    /** The familiar on the left half of the right column, its egg on the right half. */
    private void renderPreviews(GuiGraphics graphics, Row row, int mouseX, int mouseY, float partialTick) {
        int x = rightX();
        int half = rightW() / 2;
        int y1 = previewTop();
        int y2 = previewBottom(row);

        AbstractSpellCastingPet familiar = row.preview;
        if (familiar != null) {
            // Same as the familiar selection screen: fitted by what it really draws, centered, slowly turning
            float spin = (Util.getMillis() % SPIN_PERIOD_MS) / (float) SPIN_PERIOD_MS * 2F * (float) Math.PI;
            graphics.enableScissor(x + 1, y1 + 1, x + half, y2 - 1);
            FamiliarPreviewHelper.renderInBox(graphics, familiar, x + 4, y1 + 4, x + half - 2, y2 - 4,
                    50F, spin, FamiliarPreviewHelper.Anchor.CENTER);
            graphics.disableScissor();
        }

        // The egg: the same model as on the beds
        float eggSize = Math.min(half, y2 - y1) * 0.95F;
        renderEgg(graphics, x + half + half / 2F, (y1 + y2) / 2F, eggSize, previewEggColor(row));
    }

    /** Draws the intact egg model. */
    private void renderEgg(GuiGraphics graphics, float cx, float cy, float size, int rgb) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(FamiliarEggBedRenderer.STAGES[0]);
        float r = ((rgb >> 16) & 0xFF) / 255F;
        float g = ((rgb >> 8) & 0xFF) / 255F;
        float b = (rgb & 0xFF) / 255F;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(cx, cy, 150);
        pose.scale(size, -size, size);
        model.getTransforms().getTransform(ItemDisplayContext.GUI).apply(false, pose);
        pose.translate(-0.5F, -0.5F, -0.5F);

        Lighting.setupFor3DItems();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(Sheets.cutoutBlockSheet());
        RandomSource random = RandomSource.create(42L);
        List<Direction> sides = new ArrayList<>(List.of(Direction.values()));
        sides.add(null);
        for (Direction side : sides) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                consumer.putBulkData(pose.last(), quad, r, g, b, 1F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
        }
        graphics.flush();
        pose.popPose();
    }

    private void renderLayerList(GuiGraphics graphics, Row row, int mouseX, int mouseY) {
        int x = rightX();
        int w = rightW();
        int y = layersTop(row);
        graphics.drawString(font, Component.translatable("ui.familiarslib.recall_shard.egg_color"), x + 4, y - 11, 0xAAAAAA);

        int hovered = layerAt(row, mouseX, mouseY);
        int visible = Math.min(row.layers.size(), visibleLayerRows(row));
        for (int i = 0; i < visible; i++) {
            LayerChoice choice = row.layers.get(i);
            int rowY = y + i * LAYER_ROW_H;
            if (i == row.selectedLayer) {
                LAYER_SELECTED.draw(graphics, x + 1, rowY + 1, w - 2, LAYER_ROW_H - 2);
            } else if (i == hovered) {
                LAYER_HOVER.draw(graphics, x + 1, rowY + 1, w - 2, LAYER_ROW_H - 2);
            }

            int swatchX = x + 4;
            int swatchY = rowY + (LAYER_ROW_H - SWATCH) / 2;
            graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, 0xFF000000 | choice.rgb());
            graphics.renderOutline(swatchX, swatchY, SWATCH, SWATCH, 0xFF000000);

            Component label = choice.original()
                    ? choice.name().copy().append(" ").append(Component.translatable("ui.familiarslib.recall_shard.original"))
                    : choice.name();
            int labelX = swatchX + SWATCH + 4;
            graphics.drawString(font, fit(label.getString(), x + w - 4 - labelX), labelX, rowY + 3,
                    choice.original() ? COLOR_RELEASED : 0xFFFFFF);
        }
    }

    /** Cuts the text to the width with an ellipsis, so it never runs over a card's border. */
    private String fit(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…";
    }

    // Input

    private int rowAt(double mouseX, double mouseY) {
        int x = left + 8;
        int y = top + LIST_TOP;
        if (mouseX < x || mouseX >= x + LIST_W || mouseY < y || mouseY >= y + VISIBLE_ROWS * ROW_H) {
            return -1;
        }
        int index = scroll + (int) ((mouseY - y) / ROW_H);
        return index < rows.size() ? index : -1;
    }

    private int layerAt(Row row, double mouseX, double mouseY) {
        if (!showLayerList(row)) return -1;
        int x = rightX();
        int y = layersTop(row);
        int visible = Math.min(row.layers.size(), visibleLayerRows(row));
        if (mouseX < x || mouseX >= x + rightW() || mouseY < y || mouseY >= y + visible * LAYER_ROW_H) {
            return -1;
        }
        return (int) ((mouseY - y) / LAYER_ROW_H);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                selected = row;
                updateButton();
                return true;
            }
            Row current = selectedRow();
            int layer = current != null ? layerAt(current, mouseX, mouseY) : -1;
            if (layer >= 0) {
                current.selectedLayer = layer;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
        if (maxScroll > 0 && scrollY != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}