package net.alshanex.familiarslib.block;

import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.network.OpenFamiliarStoragePacket;
import net.alshanex.familiarslib.network.UpdateFamiliarStoragePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
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
                storageEntity.setOwner(serverPlayer);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                if (!storageEntity.isOwner(serverPlayer)) {
                    return InteractionResult.FAIL;
                }

                Map<UUID, CompoundTag> storedData = storageEntity.getStoredFamiliars();
                PacketDistributor.sendToPlayer(serverPlayer, new UpdateFamiliarStoragePacket(pos, storedData, storageEntity.isStoreMode()));

                PacketDistributor.sendToPlayer(serverPlayer, new OpenFamiliarStoragePacket(pos));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                storageEntity.returnFamiliarsToOwner();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    protected ParticleOptions getParticlesForAnimation(){
        return ParticleTypes.HAPPY_VILLAGER;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            if (storageEntity.getStoredFamiliarCount() > 0 && random.nextFloat() < 0.1F) {
                double d0 = (double)pos.getX() + random.nextDouble();
                double d1 = (double)pos.getY() + random.nextDouble() * 0.5D + 0.5D;
                double d2 = (double)pos.getZ() + random.nextDouble();
                level.addParticle(getParticlesForAnimation(), d0, d1, d2, 0.0D, 0.0D, 0.0D);
            }
        }
    }
}
