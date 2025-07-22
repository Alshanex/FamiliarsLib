package net.alshanex.familiarslib.block;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.OpenBedLinkSelectionPacket;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public abstract class AbstractFamiliarBedBlock extends BaseEntityBlock {
    public AbstractFamiliarBedBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarBedBlockEntity petBed) {
                //Sets the player that placed the block as the owner
                petBed.setOwnerUUID(player.getUUID());
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarBedBlockEntity petBed) {
                petBed.onBedBroken();
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, BlockHitResult pHitResult) {
        if (pLevel.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
        if (!(blockEntity instanceof AbstractFamiliarBedBlockEntity petBed)) {
            return InteractionResult.FAIL;
        }

        // Check if player is the owner
        if (!petBed.isOwner(pPlayer)) {
            if(pPlayer instanceof ServerPlayer serverPlayer){
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.not_bed_owner").withStyle(ChatFormatting.RED)));
            }
            return InteractionResult.FAIL;
        }

        // Check if player has any familiars
        if (pPlayer instanceof ServerPlayer serverPlayer) {
            updateSummonedFamiliarsData(serverPlayer);

            PlayerFamiliarData familiarData = serverPlayer.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            if (familiarData.isEmpty()) {
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.no_familiars").withStyle(ChatFormatting.RED)));
                return InteractionResult.FAIL;
            }

            FamiliarManager.syncFamiliarDataForPlayer(serverPlayer);

            // Open bed link selection screen
            serverPlayer.level().getServer().execute(() -> {
                PacketDistributor.sendToPlayer(serverPlayer, new OpenBedLinkSelectionPacket(pPos));
            });
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.FAIL;
    }

    //Updates familiars data before bed link screen
    private static void updateSummonedFamiliarsData(ServerPlayer player) {
        try {
            PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            ServerLevel level = player.serverLevel();

            int updatedCount = 0;

            for (Map.Entry<UUID, ?> entry : familiarData.getAllFamiliars().entrySet()) {
                UUID familiarId = entry.getKey();

                Entity entity = level.getEntity(familiarId);
                if (entity instanceof AbstractSpellCastingPet familiar) {
                    if (familiar.getSummoner() != null && familiar.getSummoner().is(player)) {
                        FamiliarManager.updateFamiliarData(familiar);
                        updatedCount++;
                        FamiliarsLib.LOGGER.debug("Updated data for summoned familiar {}", familiarId);
                    }
                }
            }

            if (updatedCount > 0) {
                FamiliarsLib.LOGGER.debug("Updated data for {} summoned familiars before opening bed link screen", updatedCount);
            }

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error updating summoned familiars data for bed interaction: ", e);
        }
    }
}
