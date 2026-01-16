package net.alshanex.familiarslib.item;

import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.SelectedFamiliarsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractMultiSelectionCurio extends CurioBaseItem {
    public AbstractMultiSelectionCurio(Properties properties) {
        super(properties);
    }

    public static Set<UUID> getSelectedFamiliars(ItemStack itemStack) {
        var component = ComponentRegistry.SELECTED_FAMILIARS.get(itemStack);
        return component != null ? component.getSelectedFamiliars() : Set.of();
    }

    public static void setSelectedFamiliars(ItemStack itemStack, Set<UUID> familiars) {
        ComponentRegistry.SELECTED_FAMILIARS.set(itemStack, new SelectedFamiliarsComponent(familiars));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemStack, Level context, @NotNull List<Component> tooltipComponents, @NotNull TooltipFlag flag) {
        super.appendHoverText(itemStack, context, tooltipComponents, flag);
        tooltipComponents.add(Component.translatable("tooltip.familiarslib.multi_selection_item.line1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.empty());

        Set<UUID> selectedFamiliars = getSelectedFamiliars(itemStack);
        if (!selectedFamiliars.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.familiarslib.multi_selection_item.selected", selectedFamiliars.size()).withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.familiarslib.multi_selection_item.none_selected").withStyle(ChatFormatting.RED));
        }
    }
}
