package net.alshanex.familiarslib.block;

import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.data.BoundFamiliar;
import net.alshanex.familiarslib.item.FamiliarEggItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class AbstractFamiliarBedBlock extends BaseEntityBlock {
    public AbstractFamiliarBedBlock(Properties properties) {
        super(properties);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : (lvl, pos, st, blockEntity) -> {
            if (blockEntity instanceof AbstractFamiliarBedBlockEntity bedEntity) {
                bedEntity.tick();
            }
        };
    }

    /** A bed broken while holding an egg drops the egg, still holding its familiar. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof AbstractFamiliarBedBlockEntity bed) {
            BoundFamiliar egg = bed.takeEgg();
            if (egg != null) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, FamiliarEggItem.boundEgg(egg));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}