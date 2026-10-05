package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.block.FamiliarStorageBlock;
import net.alshanex.familiarslib.registry.FBlockEntityRegistry;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FamiliarStorageBlockEntity extends AbstractFamiliarStorageBlockEntity {
    public FamiliarStorageBlockEntity(BlockPos pos, BlockState blockState) {
        super(FBlockEntityRegistry.FAMILIAR_STORAGE.get(), pos, blockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return super.getUpdateTag(registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected Direction getFacingDirection() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof FamiliarStorageBlock) {
            return state.getValue(FamiliarStorageBlock.FACING).getOpposite();
        }
        return Direction.NORTH;
    }
}