package net.alshanex.familiarslib.util.consumables;

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
        FamiliarConsumableComponent consumableComponent = ComponentRegistry.FAMILIAR_CONSUMABLE.get(stack);

        if (consumableComponent != null) {
            String typeKey = getTypeTranslationKey(consumableComponent.type());
            int bonus = consumableComponent.getBonus();
            int limit = consumableComponent.getLimit();
            String unit = getUnitSuffix(consumableComponent.type());

            tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.type",
                    Component.translatable(typeKey)).withStyle(ChatFormatting.GOLD));

            tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.bonus",
                    bonus + unit).withStyle(ChatFormatting.GREEN));

            tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.limit",
                    limit + unit).withStyle(ChatFormatting.BLUE));

            tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.tier",
                    consumableComponent.tier()).withStyle(ChatFormatting.YELLOW));
        }

        FamiliarFoodComponent foodComponent = ComponentRegistry.FAMILIAR_FOOD.get(stack);

        if (foodComponent != null) {
            int healingValue = foodComponent.healing();
            tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.food",
                    healingValue + " hearts").withStyle(ChatFormatting.RED));
        }
    }

    private static String getTypeTranslationKey(ConsumableType type) {
        return switch (type) {
            case ARMOR -> "consumable.type.armor";
            case HEALTH -> "consumable.type.health";
            case SPELL_POWER -> "consumable.type.spell_power";
            case SPELL_RESIST -> "consumable.type.spell_resist";
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
            case ENRAGED -> " stacks";
            case BLOCKING -> "";
        };
    }
}