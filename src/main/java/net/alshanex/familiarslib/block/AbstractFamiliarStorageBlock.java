package net.alshanex.familiarslib.block;

import io.redspace.ironsspellbooks.registries.ParticleRegistry;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.network.OpenFamiliarStoragePacket;
import net.alshanex.familiarslib.network.UpdateFamiliarStoragePacket;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractFamiliarStorageBlock extends BaseEntityBlock implements EntityBlock {
    protected AbstractFamiliarStorageBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                //Sets the owner of the storage block
                storageEntity.setOwner(serverPlayer);
            }
        }
    }

    protected int getAmountPerChunk(){
        return 1;
    }

    //Opens the storage block screen after updating familiars data from the player
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                if (countStorageBlocksInRadius(level, pos, 16) > getAmountPerChunk()) {
                    serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.familiarslib.storage_block_chunk_limit")
                                    .withStyle(ChatFormatting.RED)));
                    return InteractionResult.FAIL;
                }

                if (!storageEntity.isOwner(serverPlayer)) {
                    return InteractionResult.FAIL;
                }

                Map<UUID, CompoundTag> storedData = storageEntity.getStoredFamiliars();
                PacketDistributor.sendToPlayer(serverPlayer, new UpdateFamiliarStoragePacket(
                        pos,
                        storedData,
                        storageEntity.isStoreMode(),
                        storageEntity.canFamiliarsUseGoals(),
                        storageEntity.getMaxDistance()
                ));

                PacketDistributor.sendToPlayer(serverPlayer, new OpenFamiliarStoragePacket(pos));
                return InteractionResult.SUCCESS;
            }
        }
        if(player instanceof ServerPlayer serverPlayer && CurioUtils.isWearingMultiSelectionCurio(serverPlayer)){
            CurioUtils.updateSelectedFamiliarsInEquipped(serverPlayer, Set.of());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private int countStorageBlocksInRadius(Level level, BlockPos centerPos, int radius) {
        int count = 0;

        int startX = centerPos.getX() - radius;
        int endX = centerPos.getX() + radius;
        int startZ = centerPos.getZ() - radius;
        int endZ = centerPos.getZ() + radius;

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = minY; y < maxY; y++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    BlockState blockState = level.getBlockState(checkPos);
                    if (blockState.getBlock() instanceof AbstractFamiliarStorageBlock) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                //You can't break the block with familiars inside, but just in case
                storageEntity.returnFamiliarsToOwner();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    //Prevents the block to break in wander mode or in store mode with familiars inside
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            if (player instanceof ServerPlayer serverPlayer && !storageEntity.isOwner(serverPlayer)) {
                if (!serverPlayer.getAbilities().instabuild) {
                    return 0.0F;
                }
            }

            if (player.getAbilities().instabuild) {
                return super.getDestroyProgress(state, player, level, pos);
            }

            if (!storageEntity.isStoreMode()) {
                if (!player.level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.familiarslib.cannot_break_wander_mode")
                                    .withStyle(ChatFormatting.RED), true);
                }
                return 0.0F;
            }

            // En modo Store, solo se puede romper si no hay familiares dentro
            if (storageEntity.getStoredFamiliarCount() > 0 || storageEntity.getOutsideFamiliarCount() > 0) {
                if (!player.level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.familiarslib.cannot_break_has_familiars")
                                    .withStyle(ChatFormatting.RED), true);
                }
                return 0.0F;
            }
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    //Prevents the block to break in wander mode or in store mode with familiars inside
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            if (player instanceof ServerPlayer serverPlayer && !storageEntity.isOwner(serverPlayer)) {
                if (!serverPlayer.getAbilities().instabuild) {
                    return false;
                }
            }

            if (player.getAbilities().instabuild) {
                return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
            }

            if (!storageEntity.isStoreMode()) {
                return false;
            }

            if (storageEntity.getStoredFamiliarCount() > 0 || storageEntity.getOutsideFamiliarCount() > 0) {
                return false;
            }
        }

        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    protected ParticleOptions getParticlesForAnimation(){
        return ParticleTypes.HAPPY_VILLAGER;
    }

    //Spawns particles when familiars inside, sleep particles at night
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            if (storageEntity.getStoredFamiliarCount() > 0 && random.nextFloat() < 0.1F) {
                if (!isDaytime(level) && !storageEntity.isStoreMode()) {
                    double d0 = (double)pos.getX() + random.nextDouble();
                    double d1 = (double)pos.getY() + random.nextDouble() * 0.5D + 0.5D;
                    double d2 = (double)pos.getZ() + random.nextDouble();

                    for (int i = 0; i < 2; i++) {
                        double offsetX = random.nextDouble() * 0.6D - 0.3D;
                        double offsetY = random.nextDouble() * 0.3D;
                        double offsetZ = random.nextDouble() * 0.6D - 0.3D;
                        level.addParticle(FParticleRegistry.SLEEP_PARTICLE.get(),
                                d0 + offsetX, d1 + offsetY, d2 + offsetZ,
                                offsetX * 0.01D, 0.05D, offsetZ * 0.01D);
                    }
                } else {
                    double d0 = (double)pos.getX() + random.nextDouble();
                    double d1 = (double)pos.getY() + random.nextDouble() * 0.5D + 0.5D;
                    double d2 = (double)pos.getZ() + random.nextDouble();
                    level.addParticle(getParticlesForAnimation(), d0, d1, d2, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    private boolean isDaytime(Level level) {
        long time = level.getDayTime() % 24000;
        return time >= 1000 && time <= 13000;
    }
}
