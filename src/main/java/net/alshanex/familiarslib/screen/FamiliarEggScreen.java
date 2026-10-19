package net.alshanex.familiarslib.screen;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.ClientboundRecallListPacket;
import net.alshanex.familiarslib.network.ServerboundRecallFamiliarPacket;
import net.alshanex.familiarslib.util.familiars.FamiliarRecall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** Lists the familiars that can be recalled with a Recall Shard, with a preview of the selected one. */
public class FamiliarEggScreen extends Screen {
    private static final int PANEL_W = 280;
    private static final int PANEL_H = 190;
    private static final int LIST_W = 150;
    private static final int ROW_H = 22;
    private static final int VISIBLE_ROWS = 6;
    private static final int LIST_TOP = 24;

    private static final int COLOR_PANEL = 0xE0101010;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_SELECTED = 0x50FFFFFF;
    private static final int COLOR_HOVER = 0x20FFFFFF;
    private static final int COLOR_RELEASED = 0xAAAAAA;
    private static final int COLOR_FALLEN = 0xFF5555;

    private record Row(ClientboundRecallListPacket.Entry entry, Component name, AbstractSpellCastingPet preview) {}

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

        int previewX = left + LIST_W + 16;
        recallButton = Button.builder(Component.translatable("ui.familiarslib.recall_shard.recall"), b -> recallSelected())
                .bounds(previewX, top + PANEL_H - 28, left + PANEL_W - 8 - previewX, 20)
                .build();
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
            rows.add(new Row(entry, FamiliarRecall.familiarName(entry.data()), preview));
        }
    }

    private void updateButton() {
        if (recallButton != null) {
            recallButton.active = selected >= 0 && selected < rows.size();
        }
    }

    private void recallSelected() {
        if (selected < 0 || selected >= rows.size()) return;
        PacketDistributor.sendToServer(new ServerboundRecallFamiliarPacket(rows.get(selected).entry().familiarId()));
        onClose();
    }

    // Rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + PANEL_W, top + PANEL_H, COLOR_PANEL);
        graphics.renderOutline(left, top, PANEL_W, PANEL_H, COLOR_BORDER);
        // Divider between the list and the preview
        graphics.fill(left + LIST_W + 10, top + LIST_TOP, left + LIST_W + 11, top + PANEL_H - 8, COLOR_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, left + PANEL_W / 2, top + 8, 0xFFFFFF);
        renderList(graphics, mouseX, mouseY);
        renderPreview(graphics, mouseX, mouseY);
    }

    private void renderList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 8;
        int hovered = rowAt(mouseX, mouseY);
        for (int i = scroll; i < Math.min(rows.size(), scroll + VISIBLE_ROWS); i++) {
            Row row = rows.get(i);
            int y = top + LIST_TOP + (i - scroll) * ROW_H;

            if (i == selected) {
                graphics.fill(x, y, x + LIST_W, y + ROW_H - 2, COLOR_SELECTED);
            } else if (i == hovered) {
                graphics.fill(x, y, x + LIST_W, y + ROW_H - 2, COLOR_HOVER);
            }

            graphics.drawString(font, font.plainSubstrByWidth(row.name().getString(), LIST_W - 6), x + 3, y + 2, 0xFFFFFF);

            ClientboundRecallListPacket.Entry entry = row.entry();
            Component status = Component.translatable(entry.dead()
                    ? "ui.familiarslib.recall_shard.fallen" : "ui.familiarslib.recall_shard.released");
            long days = entry.ageTicks() / 24000L;
            Component age = days == 0
                    ? Component.translatable("ui.familiarslib.recall_shard.today")
                    : Component.translatable("ui.familiarslib.recall_shard.days_ago", days);
            graphics.drawString(font, status.copy().append(" · ").append(age), x + 3, y + 11,
                    entry.dead() ? COLOR_FALLEN : COLOR_RELEASED);
        }

        // Scrollbar
        if (rows.size() > VISIBLE_ROWS) {
            int trackTop = top + LIST_TOP;
            int trackHeight = VISIBLE_ROWS * ROW_H - 2;
            int thumbHeight = Math.max(10, trackHeight * VISIBLE_ROWS / rows.size());
            int thumbY = trackTop + (trackHeight - thumbHeight) * scroll / (rows.size() - VISIBLE_ROWS);
            graphics.fill(x + LIST_W + 1, trackTop, x + LIST_W + 3, trackTop + trackHeight, 0x40FFFFFF);
            graphics.fill(x + LIST_W + 1, thumbY, x + LIST_W + 3, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }

    private void renderPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selected < 0 || selected >= rows.size()) return;
        AbstractSpellCastingPet familiar = rows.get(selected).preview();
        if (familiar == null) return;

        int x1 = left + LIST_W + 16;
        int x2 = left + PANEL_W - 8;
        int y1 = top + LIST_TOP;
        int y2 = top + PANEL_H - 34;
        float size = Math.max(familiar.getBbHeight(), familiar.getBbWidth());
        int scale = Mth.clamp((int) (60 / Math.max(size, 0.5F)), 10, 50);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, scale, 0.0625F, mouseX, mouseY, familiar);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int row = rowAt(mouseX, mouseY);
            if (row >= 0) {
                selected = row;
                updateButton();
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