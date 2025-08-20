package net.alshanex.familiarslib.util.consumables;

import net.alshanex.familiarslib.util.consumables.FamiliarConsumableComponent;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem.ConsumableType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Helper class for adding consumable tooltips to any item
 */
public class ConsumableTooltipHelper {

    /**
     * Adds consumable tooltip information to an item stack if it has the consumable component
     */
    public static void addConsumableTooltip(ItemStack stack, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        FamiliarConsumableComponent component = stack.get(ComponentRegistry.FAMILIAR_CONSUMABLE.get());
        if (component == null) {
            return;
        }

        // Add consumable information to tooltip
        String typeKey = getTypeTranslationKey(component.type());
        int bonus = component.getBonus();
        int limit = component.getLimit();
        if(component.type() == ConsumableType.SPELL_LEVEL){
            limit+=1;
        }
        String unit = getUnitSuffix(component.type());

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.type",
                Component.translatable(typeKey)).withStyle(ChatFormatting.GOLD));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.bonus",
                bonus + unit).withStyle(ChatFormatting.GREEN));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.limit",
                limit + unit).withStyle(ChatFormatting.BLUE));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.tier",
                component.tier()).withStyle(ChatFormatting.YELLOW));
    }

    private static String getTypeTranslationKey(ConsumableType type) {
        return switch (type) {
            case ARMOR -> "consumable.type.armor";
            case HEALTH -> "consumable.type.health";
            case SPELL_POWER -> "consumable.type.spell_power";
            case SPELL_RESIST -> "consumable.type.spell_resist";
            case SPELL_LEVEL -> "consumable.type.spell_level";
            case ENRAGED -> "consumable.type.enraged";
            case BLOCKING -> "consumable.type.blocking";
        };
    }

    private static String getUnitSuffix(ConsumableType type) {
        return switch (type) {
            case ARMOR -> "";
            case HEALTH -> "%";
            case SPELL_POWER -> "%";
            case SPELL_RESIST -> "%";
            case SPELL_LEVEL -> "";
            case ENRAGED -> " stacks";
            case BLOCKING -> "";
        };
    }
}