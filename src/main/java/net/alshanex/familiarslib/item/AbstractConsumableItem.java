package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.registry.CriteriaTriggersRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class AbstractConsumableItem extends Item {
    public AbstractConsumableItem() {
        super(new Properties().stacksTo(16).rarity(Rarity.UNCOMMON));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand) {
        if(player instanceof ServerPlayer serverPlayer){
            triggerConsumableAdvancement(serverPlayer);
        }
        return applyConsumableEffect(stack, player, interactionTarget, usedHand);
    }

    protected void triggerConsumableAdvancement(ServerPlayer player){
        CriteriaTriggersRegistry.CONSUMABLE_TRIGGER.get().trigger(player);
    }

    protected InteractionResult applyConsumableEffect(ItemStack stack, Player player, LivingEntity interactionTarget, InteractionHand usedHand){
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemStack, TooltipContext context, @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        super.appendHoverText(itemStack, context, lines, flag);
        lines.add(Component.translatable("ui.familiarslib.consumable_item").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("ui.familiarslib.consumable_item_desc").withStyle(ChatFormatting.WHITE));
    }
}
