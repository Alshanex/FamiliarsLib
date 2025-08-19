package net.alshanex.familiarslib.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem.ConsumableType;

import java.util.List;

/**
 * Base class for consumable items
 */
public class FamiliarConsumableItem extends Item {
    private final ConsumableType consumableType;
    private final int tier;

    public FamiliarConsumableItem(Properties properties, ConsumableType consumableType, int tier) {
        super(properties);
        this.consumableType = consumableType;
        this.tier = tier;
    }

    public ConsumableType getConsumableType() {
        return consumableType;
    }

    public int getTier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        // Add consumable information to tooltip
        String typeKey = getTypeTranslationKey(consumableType);
        int bonus = consumableType.getTierBonus(tier);
        int limit = consumableType.getTierLimit(tier);
        String unit = getUnitSuffix(consumableType);

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.type",
                Component.translatable(typeKey)).withStyle(ChatFormatting.GOLD));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.bonus",
                bonus + unit).withStyle(ChatFormatting.GREEN));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.limit",
                limit + unit).withStyle(ChatFormatting.BLUE));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.tier",
                tier).withStyle(ChatFormatting.YELLOW));
    }

    private String getTypeTranslationKey(ConsumableType type) {
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

    private String getUnitSuffix(ConsumableType type) {
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