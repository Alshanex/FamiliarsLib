package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.registry.CriteriaTriggersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Generic class for familiar shards
 */
public abstract class AbstractRuneItem extends Item {
    public AbstractRuneItem() {
        super(new Properties().rarity(Rarity.EPIC).stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level worldIn = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        stack.shrink(1);

        spawnFamiliar(worldIn, player, pos);

        if(player instanceof ServerPlayer serverPlayer){
            triggerShardAdvancement(serverPlayer);
        }

        return InteractionResult.SUCCESS;
    }

    protected void spawnFamiliar(Level worldIn, Player player, BlockPos pos){

    }

    protected void triggerShardAdvancement(ServerPlayer player){
        CriteriaTriggersRegistry.SHARD_TRIGGER.trigger(player);
    }
}
