package net.alshanex.familiarslib.block;

import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
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
}
