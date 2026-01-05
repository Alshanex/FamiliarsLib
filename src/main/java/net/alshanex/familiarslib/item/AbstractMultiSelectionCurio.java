package net.alshanex.familiarslib.item;

import io.redspace.ironsspellbooks.item.curios.CurioBaseItem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.SelectedFamiliarsComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractMultiSelectionCurio extends CurioBaseItem {
    public AbstractMultiSelectionCurio(Properties properties) {
        super(properties);
    }

    public static Set<UUID> getSelectedFamiliars(ItemStack itemStack) {
        var component = itemStack.get(ComponentRegistry.SELECTED_FAMILIARS.get());
        return component != null ? component.getSelectedFamiliars() : Set.of();
    }

    public static void setSelectedFamiliars(ItemStack itemStack, Set<UUID> familiars) {
        itemStack.set(ComponentRegistry.SELECTED_FAMILIARS.get(), new SelectedFamiliarsComponent(familiars));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        tooltipComponents.add(Component.translatable("tooltip.familiarslib.multi_selection_item.line1").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.empty());

        Set<UUID> selectedFamiliars = getSelectedFamiliars(stack);
        if (!selectedFamiliars.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.familiarslib.multi_selection_item.selected", selectedFamiliars.size()).withStyle(ChatFormatting.GOLD));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.familiarslib.multi_selection_item.none_selected").withStyle(ChatFormatting.RED));
        }
    }
}
