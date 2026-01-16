package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class FamiliarConsumableItem extends Item {
    private final FamiliarConsumableSystem.ConsumableType consumableType;
    private final int tier;

    public FamiliarConsumableItem(Properties properties, FamiliarConsumableSystem.ConsumableType consumableType, int tier) {
        super(properties);
        this.consumableType = consumableType;
        this.tier = tier;
    }

    public FamiliarConsumableSystem.ConsumableType getConsumableType() {
        return consumableType;
    }

    public int getTier() {
        return tier;
    }

    public int getBonus() {
        return consumableType.getTierBonus(tier);
    }

    public int getLimit() {
        return consumableType.getTierLimit(tier);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltipComponents, flag);

        String typeKey = getTypeTranslationKey(consumableType);
        String unit = getUnitSuffix(consumableType);

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.type",
                Component.translatable(typeKey)).withStyle(ChatFormatting.GOLD));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.bonus",
                getBonus() + unit).withStyle(ChatFormatting.GREEN));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.limit",
                getLimit() + unit).withStyle(ChatFormatting.BLUE));

        tooltipComponents.add(Component.translatable("item.familiarslib.consumable.tooltip.tier",
                tier).withStyle(ChatFormatting.YELLOW));
    }

    private static String getTypeTranslationKey(FamiliarConsumableSystem.ConsumableType type) {
        return switch (type) {
            case ARMOR -> "consumable.type.armor";
            case HEALTH -> "consumable.type.health";
            case SPELL_POWER -> "consumable.type.spell_power";
            case SPELL_RESIST -> "consumable.type.spell_resist";
            case ENRAGED -> "consumable.type.enraged";
            case BLOCKING -> "consumable.type.blocking";
        };
    }

    private static String getUnitSuffix(FamiliarConsumableSystem.ConsumableType type) {
        return switch (type) {
            case ARMOR -> "";
            case HEALTH, SPELL_POWER, SPELL_RESIST -> "%";
            case ENRAGED -> " stacks";
            case BLOCKING -> "";
        };
    }
}
