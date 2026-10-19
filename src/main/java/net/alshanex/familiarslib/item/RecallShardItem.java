package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.util.familiars.FamiliarRecall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Brings back a familiar you released or lost in this dimension. Consumed when a familiar is recalled. */
public class RecallShardItem extends Item {

    public RecallShardItem() {
        super(new Properties().stacksTo(16).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            FamiliarRecall.openRecallList(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.familiarslib.recall_shard.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.familiarslib.recall_shard.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}