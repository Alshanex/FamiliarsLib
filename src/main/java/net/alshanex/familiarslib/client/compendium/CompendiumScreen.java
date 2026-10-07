package net.alshanex.familiarslib.client.compendium;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.compendium.CompendiumAbility;
import net.alshanex.familiarslib.compendium.CompendiumCatalog;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector2i;
import org.joml.Vector3f;

import java.util.*;

/**
 * The familiar compendium, drawn as an open book.
 */
public class CompendiumScreen extends Screen {
    private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "textures/gui/compendium_book.png");
    private static final ResourceLocation WIDGETS = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "textures/gui/compendium_widgets.png");

    // Book texture: 400x240 inside a 512x256 file.
    private static final int BOOK_W = 400;
    private static final int BOOK_H = 240;

    // Left page
    private static final int LIST_X = 28;
    private static final int LIST_Y = 36;
    private static final int LIST_W = 158;
    private static final int ROW_H = 14;
    private static final int LIST_ROWS = 12;

    // Right page: the preview frame (part of the book texture)
    private static final int FRAME_X = 210;
    private static final int FRAME_Y = 21;
    private static final int FRAME_W = 55;
    private static final int FRAME_H = 90;

    // Right page: the column next to the preview (fixed)
    private static final int SIDE_X = 271;
    private static final int SIDE_RIGHT = 378;
    private static final int SIDE_W = SIDE_RIGHT - SIDE_X;
    private static final int SIDE_TOP = FRAME_Y;
    private static final int SIDE_BOTTOM = FRAME_Y + FRAME_H;

    // Area below both (scrolls)
    private static final int BELOW_X = 207;
    private static final int BELOW_RIGHT = 378;
    private static final int BELOW_W = BELOW_RIGHT - BELOW_X;
    private static final int BELOW_TOP = 117;
    private static final int BELOW_BOTTOM = 208;
    private static final int FOOTER_Y = 214;

    // Bookmark tabs on the right edge
    private static final int TAB_X = 394;
    private static final int TAB_Y = 30;
    private static final int TAB_STEP = 26;

    // Sizes
    private static final int LINE_H = 10;
    private static final int SWATCH = 12;
    private static final int SWATCH_STEP = 14;
    private static final int SWATCH_GAP_BELOW = 2;
    private static final int LABEL_H = 11;
    private static final int SPELL_ICON = 16;
    private static final int SPELL_STEP = 19;

    // Ink colors
    private static final int INK = 0xFF4A3422;
    private static final int INK_LIGHT = 0xFF8A6A4A;
    private static final int INK_HOVER = 0xFF96281E;
    private static final int INK_TITLE = 0xFF5A2A14;
    private static final int RIBBON_RED = 0xFF96201E;

    private enum Tab {
        ABOUT("about"),
        ABILITIES("abilities"),
        COLORS("colors");

        final String key;

        Tab(String key) {
            this.key = key;
        }

        Component title() {
            return Component.translatable("screen.familiarslib.compendium.tab." + key);
        }

        ItemStack icon() {
            return switch (this) {
                case ABOUT -> new ItemStack(Items.BOOK);
                case ABILITIES -> new ItemStack(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "lesser_spell_slot_upgrade")));
                case COLORS -> new ItemStack(Items.WHITE_DYE);
            };
        }
    }

    private int left;
    private int top;
    private int selected = 0;
    private int listPage = 0;
    private Tab tab = Tab.ABOUT;
    private double scroll = 0;

    /** The familiars with something to show (filled in init). */
    private List<CompendiumCatalog.Section> visible = List.of();

    /** The colors being previewed, per familiar (one per layer, -1 = original). */
    private final Map<ResourceLocation, int[]> previewColors = new HashMap<>();
    private final Map<ResourceLocation, AbstractSpellCastingPet> previews = new HashMap<>();

    public CompendiumScreen() {
        super(Component.translatable("screen.familiarslib.compendium.title"));
    }

    // Data helpers

    @Nullable
    private CompendiumCatalog.Section current() {
        return visible.isEmpty() ? null : visible.get(Math.min(selected, visible.size() - 1));
    }

    @Nullable
    private AbstractSpellCastingPet previewFor(ResourceLocation typeId) {
        return previews.computeIfAbsent(typeId, id -> {
            if (minecraft == null || minecraft.level == null) return null;
            Entity entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(minecraft.level);
            return entity instanceof AbstractSpellCastingPet pet ? pet : null;
        });
    }

    private boolean hasTab(CompendiumCatalog.Section section, Tab tab) {
        AbstractSpellCastingPet preview = previewFor(section.entityType());
        return switch (tab) {
            case ABOUT -> preview != null && (!preview.getCompendiumDescription().isEmpty() || !preview.getCompendiumObtaining().isEmpty());
            case ABILITIES -> !section.spells().isEmpty() || (preview != null && !preview.getCompendiumAbilities().isEmpty());
            case COLORS -> section.optionCount() > 0;
        };
    }

    private List<Tab> tabsFor(CompendiumCatalog.Section section) {
        List<Tab> tabs = new ArrayList<>();
        for (Tab t : Tab.values()) {
            if (hasTab(section, t)) tabs.add(t);
        }
        return tabs;
    }

    private static boolean unlocked(CompendiumCatalog.Section section, CompendiumCatalog.LayerOptions layer, CompendiumCatalog.ColorOption option) {
        return ClientCompendium.isDiscovered(CompendiumCatalog.key(section.entityType(), layer.slot(), option.color()));
    }

    private static int unlockedCount(CompendiumCatalog.Section section) {
        int found = 0;
        for (CompendiumCatalog.LayerOptions layer : section.layers()) {
            for (CompendiumCatalog.ColorOption option : layer.options()) {
                if (unlocked(section, layer, option)) found++;
            }
        }
        return found;
    }

    private int[] colorsFor(CompendiumCatalog.Section section) {
        int[] colors = previewColors.computeIfAbsent(section.entityType(), id -> new int[0]);
        if (colors.length != section.layers().size()) { // first time, or the catalog changed
            colors = new int[section.layers().size()];
            Arrays.fill(colors, -1);
            previewColors.put(section.entityType(), colors);
        }
        return colors;
    }

    // Widgets

    @Override
    protected void init() {
        left = (width - BOOK_W) / 2;
        top = (height - BOOK_H) / 2;
        visible = ClientCompendium.catalog().sections().stream()
                .filter(section -> !tabsFor(section).isEmpty())
                .toList();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();

        // Familiar list
        int listPages = Math.max(1, (visible.size() + LIST_ROWS - 1) / LIST_ROWS);
        listPage = Math.min(listPage, listPages - 1);
        for (int row = 0; row < LIST_ROWS; row++) {
            int index = listPage * LIST_ROWS + row;
            if (index >= visible.size()) break;
            addRenderableWidget(new FamiliarEntry(left + LIST_X, top + LIST_Y + row * ROW_H, index, visible.get(index)));
        }
        if (listPage > 0) {
            addRenderableWidget(new PageArrow(left + 24, top + 214, false, () -> { listPage--; rebuild(); }));
        }
        if (listPage < listPages - 1) {
            addRenderableWidget(new PageArrow(left + 166, top + 214, true, () -> { listPage++; rebuild(); }));
        }

        CompendiumCatalog.Section section = current();
        if (section == null) {
            return;
        }

        // Only the tabs this familiar has content for
        List<Tab> tabs = tabsFor(section);
        if (!tabs.contains(tab)) {
            tab = tabs.getFirst();
            scroll = 0;
        }
        for (int i = 0; i < tabs.size(); i++) {
            addRenderableWidget(new BookTab(left + TAB_X, top + TAB_Y + i * TAB_STEP, tabs.get(i)));
        }

        // Colors tab: back to the original look
        if (tab == Tab.COLORS) {
            addRenderableWidget(new InkTextButton(left + BELOW_X, top + FOOTER_Y, Component.translatable("screen.familiarslib.compendium.reset"),
                    () -> Arrays.fill(colorsFor(section), -1)));
        }
    }

    /** A familiar in the left page's list: ink text, progress on the right, a ribbon when open. */
    private class FamiliarEntry extends AbstractButton {
        private final int index;
        private final CompendiumCatalog.Section section;

        FamiliarEntry(int x, int y, int index, CompendiumCatalog.Section section) {
            super(x, y, LIST_W, ROW_H - 2, BuiltInRegistries.ENTITY_TYPE.get(section.entityType()).getDescription());
            this.index = index;
            this.section = section;
        }

        @Override
        public void onPress() {
            selected = index;
            scroll = 0;
            rebuild();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean open = index == selected;
            int color = open ? INK_TITLE : isHovered() ? INK_HOVER : INK;
            if (open) {
                graphics.blit(WIDGETS, getX() - 9, getY() + 1, 40, 0, 7, 12, 64, 64);
            }
            graphics.drawString(font, getMessage(), getX(), getY() + 2, color, false);
            if (section.optionCount() > 0) {
                String progress = unlockedCount(section) + "/" + section.optionCount();
                graphics.drawString(font, progress, getX() + width - font.width(progress), getY() + 2, INK_LIGHT, false);
            }
            if (open || isHovered()) {
                graphics.fill(getX(), getY() + 11, getX() + font.width(getMessage()), getY() + 12, color);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** A leather bookmark tab on the book's right edge, with an item icon. Red when open. */
    private class BookTab extends AbstractButton {
        private final Tab target;
        private final ItemStack icon;

        BookTab(int x, int y, Tab target) {
            super(x, y, 22, 22, target.title());
            this.target = target;
            this.icon = target.icon();
        }

        @Override
        public void onPress() {
            tab = target;
            scroll = 0;
            rebuild();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean open = tab == target;
            int shift = open ? 2 : isHovered() ? 1 : 0; // the open tab sticks out a little more
            graphics.blit(WIDGETS, getX() + shift, getY(), open ? 24 : 0, 24, 22, 22, 64, 64);
            graphics.renderItem(icon, getX() + shift + 3, getY() + 3);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** A plain ink text button, underlined on hover. */
    private class InkTextButton extends AbstractButton {
        private final Runnable action;

        InkTextButton(int x, int y, Component text, Runnable action) {
            super(x, y, font.width(text), 10, text);
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int color = isHovered() ? INK_HOVER : INK;
            graphics.drawString(font, getMessage(), getX(), getY() + 1, color, false);
            if (isHovered()) {
                graphics.fill(getX(), getY() + 10, getX() + width, getY() + 11, color);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** An inked page-turn arrow. */
    private static class PageArrow extends AbstractButton {
        private final boolean forward;
        private final Runnable action;

        PageArrow(int x, int y, boolean forward, Runnable action) {
            super(x, y, 18, 10, Component.empty());
            this.forward = forward;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blit(WIDGETS, getX(), getY(), forward ? 0 : 20, isHovered() ? 12 : 0, 18, 10, 64, 64);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {
            soundManager.play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    // Page layout

    private sealed interface El permits TextEl, IconEl, RuleEl, SwatchEl, SpellEl {}
    private record TextEl(FormattedCharSequence text, int x, int y, int color) implements El {}
    private record IconEl(ResourceLocation texture, int x, int y) implements El {}
    private record RuleEl(int x, int y, int width) implements El {}
    private record SwatchEl(int x, int y, int layer, int option) implements El {}
    private record SpellEl(int x, int y, AbstractSpell spell) implements El {}

    private final class Flow {
        final List<El> side = new ArrayList<>();
        final List<El> below = new ArrayList<>();
        int sideY = SIDE_TOP;
        boolean sideOpen = true;
        int belowY = 0;

        /** Where a block of this height goes: true = side (and reserves it), false = below. */
        boolean placeInSide(int height, int spaceBefore) {
            if (sideOpen) {
                int y = sideY == SIDE_TOP ? sideY : sideY + spaceBefore;
                if (y + height <= SIDE_BOTTOM) {
                    sideY = y;
                    return true;
                }
                sideOpen = false;
            }
            if (belowY > 0) belowY += spaceBefore;
            return false;
        }

        /** A paragraph: as many lines as fit next to the preview, the rest re-wrapped to the full width below. */
        void paragraph(Component text, int color, int spaceBefore) {
            if (sideOpen) {
                List<FormattedText> lines = font.getSplitter().splitLines(text, SIDE_W, Style.EMPTY);
                int y = sideY == SIDE_TOP ? sideY : sideY + spaceBefore;
                int placed = 0;
                while (placed < lines.size() && y + LINE_H <= SIDE_BOTTOM) {
                    side.add(new TextEl(Language.getInstance().getVisualOrder(lines.get(placed)), SIDE_X, y, color));
                    y += LINE_H;
                    placed++;
                }
                if (placed == lines.size()) {
                    sideY = y;
                    return;
                }
                sideOpen = false;
                if (placed > 0) {
                    // Continue the same paragraph below, re-wrapped to the wider area
                    List<FormattedText> rest = new ArrayList<>();
                    for (int i = placed; i < lines.size(); i++) {
                        if (!rest.isEmpty()) rest.add(FormattedText.of(" "));
                        rest.add(lines.get(i));
                    }
                    belowText(FormattedText.composite(rest), color);
                    return;
                }
            }
            if (belowY > 0) belowY += spaceBefore;
            belowText(text, color);
        }

        private void belowText(FormattedText text, int color) {
            for (FormattedCharSequence line : Language.getInstance().getVisualOrder(font.getSplitter().splitLines(text, BELOW_W, Style.EMPTY))) {
                below.add(new TextEl(line, BELOW_X, belowY, color));
                belowY += LINE_H;
            }
        }

        /** A heading line with a rule under it. */
        void heading(Component text, int spaceBefore) {
            int height = LINE_H + 2;
            if (placeInSide(height, spaceBefore)) {
                side.add(new TextEl(text.getVisualOrderText(), SIDE_X, sideY, INK_TITLE));
                side.add(new RuleEl(SIDE_X, sideY + 9, SIDE_W));
                sideY += height;
            } else {
                below.add(new TextEl(text.getVisualOrderText(), BELOW_X, belowY, INK_TITLE));
                below.add(new RuleEl(BELOW_X, belowY + 9, BELOW_W));
                belowY += height;
            }
        }

        /**
         * An ability that always starts next to the preview: its header goes there, and its description
         * fills the rest of the column and continues below both if it's too long.
         */
        void abilityStartingBeside(CompendiumAbility ability) {
            sideY = layoutAbilityHeader(ability, side, SIDE_X, sideY, SIDE_W);
            paragraph(ability.description(), INK, 0);
        }

        void ability(CompendiumAbility ability, int spaceBefore) {
            if (placeInSide(abilityHeight(ability, SIDE_W), spaceBefore)) {
                sideY = layoutAbility(ability, side, SIDE_X, sideY, SIDE_W);
            } else {
                belowY = layoutAbility(ability, below, BELOW_X, belowY, BELOW_W);
            }
        }

        void spells(List<AbstractSpell> spells, int spaceBefore) {
            heading(Component.translatable("screen.familiarslib.compendium.spells"), spaceBefore);
            int i = 0;
            while (i < spells.size()) {
                boolean inSide = placeInSide(SPELL_ICON + 2, 0); // SPELL_STEP already leaves a gap between rows
                int x0 = inSide ? SIDE_X : BELOW_X;
                int perRow = Math.max(1, ((inSide ? SIDE_W : BELOW_W) + 3) / SPELL_STEP);
                int y = inSide ? sideY : belowY;
                for (int c = 0; c < perRow && i < spells.size(); c++, i++) {
                    (inSide ? side : below).add(new SpellEl(x0 + 1 + c * SPELL_STEP, y + 1, spells.get(i)));
                }
                if (inSide) sideY += SPELL_STEP; else belowY += SPELL_STEP;
            }
        }

        /** Each layer is one block (label + its swatch rows): it goes entirely next to the preview, or entirely below. */
        void colors(CompendiumCatalog.Section section) {
            for (int l = 0; l < section.layers().size(); l++) {
                CompendiumCatalog.LayerOptions layer = section.layers().get(l);
                int count = layer.options().size() + 1; // + original
                int sideRows = (count + perRow(SIDE_W) - 1) / perRow(SIDE_W);
                boolean inSide = placeInSide(LABEL_H + sideRows * SWATCH_STEP - SWATCH_GAP_BELOW, l == 0 ? 0 : 4);

                List<El> out = inSide ? side : below;
                int x0 = inSide ? SIDE_X : BELOW_X;
                int perRow = perRow(inSide ? SIDE_W : BELOW_W);
                int y = inSide ? sideY : belowY;

                out.add(new TextEl(Component.translatable(layer.nameKey()).getVisualOrderText(), x0, y + 1, INK));
                y += LABEL_H;
                for (int i = 0; i < count; i++) {
                    out.add(new SwatchEl(x0 + 1 + (i % perRow) * SWATCH_STEP, y + 1 + (i / perRow) * SWATCH_STEP, l, i - 1));
                }
                y += ((count + perRow - 1) / perRow) * SWATCH_STEP;
                if (inSide) sideY = y; else belowY = y;
            }
        }

        private static int perRow(int width) {
            return Math.max(1, (width + 2) / SWATCH_STEP);
        }
    }

    private int abilityHeight(CompendiumAbility ability, int width) {
        int nameWidth = width - (ability.icon() != null ? 19 : 0);
        int nameLines = font.split(ability.name(), nameWidth).size();
        int head = ability.icon() != null ? Math.max(18, nameLines * LINE_H + (nameLines == 1 ? 4 : 0)) : nameLines * LINE_H;
        return head + 2 + font.split(ability.description(), width).size() * LINE_H;
    }

    /** Icon, name and the underline. Returns the y where the description starts. */
    private int layoutAbilityHeader(CompendiumAbility ability, List<El> out, int x, int y, int width) {
        int nameX = x;
        if (ability.icon() != null) {
            out.add(new IconEl(ability.icon(), x, y));
            nameX = x + 19;
        }
        List<FormattedCharSequence> name = font.split(ability.name(), width - (nameX - x));
        int nameY = y + (ability.icon() != null && name.size() == 1 ? 4 : 0);
        for (FormattedCharSequence line : name) {
            out.add(new TextEl(line, nameX, nameY, INK_TITLE));
            nameY += LINE_H;
        }
        int lineY = Math.max(nameY, ability.icon() != null ? y + 18 : nameY);
        // Underline the name like a heading
        out.add(new RuleEl(x, lineY - 1, width));
        return lineY + 2;
    }

    private int layoutAbility(CompendiumAbility ability, List<El> out, int x, int y, int width) {
        int lineY = layoutAbilityHeader(ability, out, x, y, width);
        for (FormattedCharSequence line : font.split(ability.description(), width)) {
            out.add(new TextEl(line, x, lineY, INK));
            lineY += LINE_H;
        }
        return lineY;
    }

    private Flow layout(CompendiumCatalog.Section section) {
        Flow flow = new Flow();
        AbstractSpellCastingPet preview = previewFor(section.entityType());
        switch (tab) {
            case ABOUT -> {
                List<Component> obtaining = preview == null ? List.of() : preview.getCompendiumObtaining();
                List<Component> description = preview == null ? List.of() : preview.getCompendiumDescription();
                if (!obtaining.isEmpty()) {
                    flow.heading(Component.translatable("screen.familiarslib.compendium.obtaining"), 0);
                    for (Component paragraph : obtaining) flow.paragraph(paragraph, INK, 3);
                    flow.sideOpen = false; // the description always starts below both
                }
                for (int i = 0; i < description.size(); i++) {
                    flow.paragraph(description.get(i), INK, i == 0 && !obtaining.isEmpty() ? 2 : 5);
                }
            }
            case ABILITIES -> {
                List<CompendiumAbility> abilities = new ArrayList<>(preview == null ? List.of() : preview.getCompendiumAbilities());
                // Abilities without icons fit best next to the preview: put them first
                abilities.sort(Comparator.comparing(ability -> ability.icon() != null));
                for (int i = 0; i < abilities.size(); i++) {
                    if (i == 0) {
                        flow.abilityStartingBeside(abilities.get(i)); // the first one always starts next to the preview
                    } else {
                        flow.ability(abilities.get(i), 6);
                    }
                }
                List<AbstractSpell> spells = new ArrayList<>();
                for (ResourceLocation id : section.spells()) {
                    AbstractSpell spell = SpellRegistry.getSpell(id);
                    if (spell != null && spell != SpellRegistry.none()) spells.add(spell);
                }
                if (!spells.isEmpty()) {
                    flow.spells(spells, abilities.isEmpty() ? 0 : 8);
                }
            }
            case COLORS -> flow.colors(section);
        }
        return flow;
    }

    // Rendering

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(BOOK, left, top, 0, 0, BOOK_W, BOOK_H, 512, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // Left page title with a small ornament
        drawCentered(graphics, title, left + 105, top + 16, INK_TITLE);
        int lineY = top + 28;
        graphics.fill(left + 40, lineY, left + 99, lineY + 1, INK_LIGHT);
        graphics.fill(left + 111, lineY, left + 170, lineY + 1, INK_LIGHT);
        graphics.fill(left + 103, lineY - 2, left + 107, lineY + 3, INK);

        CompendiumCatalog.Section section = current();
        if (section == null) {
            drawCentered(graphics, Component.translatable("screen.familiarslib.compendium.empty"), left + 292, top + 100, INK_LIGHT);
            return;
        }

        int[] colors = colorsFor(section);
        renderPreview(graphics, section, colors, mouseX, mouseY);

        if (tab == Tab.COLORS) {
            Component progress = Component.translatable("screen.familiarslib.compendium.progress", unlockedCount(section), section.optionCount());
            graphics.drawString(font, progress, left + BELOW_RIGHT - 8 - font.width(progress), top + FOOTER_Y + 1, INK_LIGHT, false);
        }

        Flow flow = layout(section);
        El hovered = null;
        for (El el : flow.side) {
            if (renderEl(graphics, section, colors, el, left, top, mouseX, mouseY)) hovered = el;
        }
        int belowOrigin = top + BELOW_TOP - (int) scroll;
        graphics.enableScissor(left + BELOW_X - 1, top + BELOW_TOP - 1, left + BELOW_RIGHT + 2, top + BELOW_BOTTOM + 1);
        boolean mouseInBelow = mouseY >= top + BELOW_TOP && mouseY <= top + BELOW_BOTTOM;
        for (El el : flow.below) {
            if (renderEl(graphics, section, colors, el, left, belowOrigin, mouseX, mouseInBelow ? mouseY : Integer.MIN_VALUE)) hovered = el;
        }
        graphics.disableScissor();

        for (var widget : children()) {
            if (widget instanceof BookTab bookTab && bookTab.isHovered()) {
                renderTabTooltip(graphics, bookTab);
            }
        }

        if (hovered instanceof SwatchEl swatch) {
            graphics.renderComponentTooltip(font, swatchTooltip(section, swatch), mouseX, mouseY);
        } else if (hovered instanceof SpellEl spell) {
            graphics.renderTooltip(font, spell.spell().getDisplayName(minecraft != null ? minecraft.player : null), mouseX, mouseY);
        }
    }

    /** The tab's name just right of the tab, centered on it (or left of the book if there's no room). */
    private void renderTabTooltip(GuiGraphics graphics, BookTab bookTab) {
        int tabRight = bookTab.getX() + bookTab.getWidth() + 2;
        int tabCenterY = bookTab.getY() + bookTab.getHeight() / 2;
        ClientTooltipPositioner positioner = (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
            int x = tabRight + 6;
            if (x + tooltipWidth + 4 > screenWidth) {
                x = bookTab.getX() - tooltipWidth - 6;
            }
            return new Vector2i(x, tabCenterY - tooltipHeight / 2);
        };
        graphics.renderTooltip(font, List.of(bookTab.getMessage().getVisualOrderText()), positioner, 0, 0);
    }

    /** Draws one element at (originX + x, originY + y). Returns true if it's a hoverable element under the mouse. */
    private boolean renderEl(GuiGraphics graphics, CompendiumCatalog.Section section, int[] colors, El el,
                             int originX, int originY, int mouseX, int mouseY) {
        switch (el) {
            case TextEl text -> graphics.drawString(font, text.text(), originX + text.x(), originY + text.y(), text.color(), false);
            case IconEl icon -> graphics.blit(icon.texture(), originX + icon.x(), originY + icon.y(), 0, 0, 16, 16, 16, 16);
            case RuleEl rule -> graphics.fill(originX + rule.x(), originY + rule.y(), originX + rule.x() + rule.width(), originY + rule.y() + 1, INK_LIGHT);
            case SwatchEl swatch -> {
                int x = originX + swatch.x();
                int y = originY + swatch.y();
                renderSwatch(graphics, section, swatch, colors[swatch.layer()], x, y);
                return mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH;
            }
            case SpellEl spell -> {
                int x = originX + spell.x();
                int y = originY + spell.y();
                graphics.fill(x - 1, y - 1, x + SPELL_ICON + 1, y + SPELL_ICON + 1, INK);
                graphics.blit(spell.spell().getSpellIconResource(), x, y, 0, 0, SPELL_ICON, SPELL_ICON, SPELL_ICON, SPELL_ICON);
                return mouseX >= x && mouseX < x + SPELL_ICON && mouseY >= y && mouseY < y + SPELL_ICON;
            }
        }
        return false;
    }

    private void drawCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private void renderPreview(GuiGraphics graphics, CompendiumCatalog.Section section, int[] colors, int mouseX, int mouseY) {
        AbstractSpellCastingPet preview = previewFor(section.entityType());
        if (preview == null || minecraft == null) {
            return;
        }
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            preview.setLayerColor(slot, AbstractSpellCastingPet.NO_LAYER_COLOR);
        }
        for (int i = 0; i < section.layers().size(); i++) {
            preview.setLayerColor(section.layers().get(i).slot(), colors[i]);
        }

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 renderOffset = minecraft.getEntityRenderDispatcher().getRenderer(preview).getRenderOffset(preview, partialTick);
        float xOffset = (float) renderOffset.x + preview.getCompendiumPreviewOffsetX();
        float yOffset = 0.0625F + (float) renderOffset.y - preview.getCompendiumPreviewOffsetY();

        int x = left + FRAME_X;
        int y = top + FRAME_Y;
        int scale = (int) (Math.min(40, (FRAME_H - 16) / Math.max(preview.getBbHeight(), 0.4F) * 0.75F) * preview.getCompendiumPreviewScale());
        renderFollowingMouse(graphics, x + 3, y + 3, x + FRAME_W - 3, y + FRAME_H - 3, scale, xOffset, yOffset, mouseX, mouseY, preview);
    }

    private static void renderFollowingMouse(GuiGraphics graphics, int x1, int y1, int x2, int y2, int scale,
                                             float xOffset, float yOffset, float mouseX, float mouseY, LivingEntity entity) {
        float centerX = (x1 + x2) / 2.0F;
        float centerY = (y1 + y2) / 2.0F;
        graphics.enableScissor(x1, y1, x2, y2);
        float lookX = (float) Math.atan((centerX - mouseX) / 40.0F);
        float lookY = (float) Math.atan((centerY - mouseY) / 40.0F);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraOrientation = new Quaternionf().rotateX(lookY * 20.0F * ((float) Math.PI / 180F));
        pose.mul(cameraOrientation);

        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + lookX * 20.0F;
        entity.setYRot(180.0F + lookX * 40.0F);
        entity.setXRot(-lookY * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        float entityScale = entity.getScale();
        Vector3f translate = new Vector3f(xOffset * entityScale, entity.getBbHeight() / 2.0F + yOffset * entityScale, 0.0F);
        InventoryScreen.renderEntityInInventory(graphics, centerX, centerY, scale / entityScale, translate, pose, cameraOrientation, entity);

        entity.yBodyRot = bodyRot;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
        graphics.disableScissor();
    }

    private void renderSwatch(GuiGraphics graphics, CompendiumCatalog.Section section, SwatchEl swatch, int selectedColor, int x, int y) {
        CompendiumCatalog.LayerOptions layer = section.layers().get(swatch.layer());
        boolean isSelected;
        if (swatch.option() < 0) {
            // Original look: always available
            graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFFE8D9B5);
            graphics.drawString(font, "O", x + 3, y + 2, INK, false);
            isSelected = selectedColor == -1;
        } else {
            CompendiumCatalog.ColorOption option = layer.options().get(swatch.option());
            if (unlocked(section, layer, option)) {
                graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | option.color());
                isSelected = selectedColor == option.color();
            } else {
                graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFFD6C49C);
                graphics.drawString(font, "?", x + 3, y + 2, INK_LIGHT, false);
                isSelected = false;
            }
        }
        graphics.renderOutline(x, y, SWATCH, SWATCH, INK);
        if (isSelected) {
            graphics.renderOutline(x - 1, y - 1, SWATCH + 2, SWATCH + 2, RIBBON_RED);
        }
    }

    private List<Component> swatchTooltip(CompendiumCatalog.Section section, SwatchEl swatch) {
        CompendiumCatalog.LayerOptions layer = section.layers().get(swatch.layer());
        List<Component> lines = new ArrayList<>();
        if (swatch.option() < 0) {
            lines.add(Component.translatable(layer.nameKey()).append(": ")
                    .append(Component.translatable("compendium.familiarslib.original_color")));
            lines.add(Component.translatable("screen.familiarslib.compendium.always_unlocked").withStyle(ChatFormatting.GRAY));
            return lines;
        }

        CompendiumCatalog.ColorOption option = layer.options().get(swatch.option());
        if (unlocked(section, layer, option)) {
            // Already unlocked: no need to explain how to get it
            lines.add(Component.translatable(layer.nameKey()).append(": ").append(colorText(option.color())));
            lines.add(Component.translatable("screen.familiarslib.compendium.click_to_preview").withStyle(ChatFormatting.GREEN));
            return lines;
        } else {
            lines.add(Component.translatable(layer.nameKey()).append(": ")
                    .append(Component.translatable("screen.familiarslib.compendium.locked").withStyle(ChatFormatting.GRAY)));
        }

        lines.add(Component.translatable("screen.familiarslib.compendium.how_to").withStyle(ChatFormatting.YELLOW));
        for (CompendiumCatalog.Origin origin : option.origins()) {
            lines.add(Component.literal(" • ").append(describe(origin)).withStyle(ChatFormatting.GRAY));
        }
        for (CompendiumCatalog.Step step : option.steps()) {
            Item item = BuiltInRegistries.ITEM.get(step.item());
            MutableComponent itemName = item.getDescription().copy();
            if (step.shrunk()) {
                itemName = Component.translatable("compendium.familiarslib.step.shrunk", itemName);
            }
            if (step.alternatives() > 0) {
                itemName = Component.translatable("compendium.familiarslib.step.alternatives", itemName, step.alternatives());
            }
            String key = step.sneaking() ? "compendium.familiarslib.step.sneaking" : "compendium.familiarslib.step";
            lines.add(Component.literal(" • ").append(Component.translatable(key, itemName)).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    private MutableComponent describe(CompendiumCatalog.Origin origin) {
        return switch (origin.kind()) {
            case NATURAL_DYE -> Component.translatable("compendium.familiarslib.origin.natural_dye",
                    Component.translatable("color.minecraft." + origin.value()));
            case BIOMES -> {
                MutableComponent names = Component.empty();
                for (int i = 0; i < origin.biomes().size(); i++) {
                    if (i > 0) names.append(", ");
                    names.append(Component.translatable(Util.makeDescriptionId("biome", origin.biomes().get(i))));
                }
                if (origin.moreBiomes() > 0) {
                    names.append(Component.translatable("compendium.familiarslib.origin.more_biomes", origin.moreBiomes()));
                }
                yield Component.translatable("compendium.familiarslib.origin.biomes", names);
            }
        };
    }

    private static Component colorText(int rgb) {
        return Component.literal("■ ").withStyle(style -> style.withColor(TextColor.fromRgb(rgb)))
                .append(Component.literal(String.format("#%06X", rgb)).withStyle(ChatFormatting.WHITE));
    }

    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        CompendiumCatalog.Section section = current();
        if (tab != Tab.COLORS || button != 0 || section == null) {
            return false;
        }
        Flow flow = layout(section);
        SwatchEl clicked = findSwatch(flow.side, left, top, mouseX, mouseY);
        if (clicked == null && mouseY >= top + BELOW_TOP && mouseY <= top + BELOW_BOTTOM) {
            clicked = findSwatch(flow.below, left, top + BELOW_TOP - (int) scroll, mouseX, mouseY);
        }
        if (clicked == null) {
            return false;
        }
        CompendiumCatalog.LayerOptions layer = section.layers().get(clicked.layer());
        int color;
        if (clicked.option() < 0) {
            color = -1;
        } else {
            CompendiumCatalog.ColorOption option = layer.options().get(clicked.option());
            if (!unlocked(section, layer, option)) {
                return true; // locked: can't preview it yet
            }
            color = option.color();
        }
        colorsFor(section)[clicked.layer()] = color;
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    @Nullable
    private static SwatchEl findSwatch(List<El> elements, int originX, int originY, double mouseX, double mouseY) {
        for (El el : elements) {
            if (el instanceof SwatchEl swatch) {
                int x = originX + swatch.x();
                int y = originY + swatch.y();
                if (mouseX >= x && mouseX < x + SWATCH && mouseY >= y && mouseY < y + SWATCH) {
                    return swatch;
                }
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CompendiumCatalog.Section section = current();
        if (section != null) {
            int max = Math.max(0, layout(section).belowY - (BELOW_BOTTOM - BELOW_TOP));
            scroll = Math.max(0, Math.min(max, scroll - scrollY * LINE_H));
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}