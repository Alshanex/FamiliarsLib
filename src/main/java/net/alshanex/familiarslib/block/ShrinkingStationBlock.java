package net.alshanex.familiarslib.block;

import net.alshanex.familiarslib.block.entity.ShrinkingStationBlockEntity;
import net.alshanex.familiarslib.registry.FBlockEntityRegistry;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED;

public class ShrinkingStationBlock extends BaseEntityBlock {
    public static final MapCodec<ShrinkingStationBlock> CODEC = simpleCodec(ShrinkingStationBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public static final VoxelShape SHAPE_LOWER_NORTH = Shapes.or(
            Block.box(0, 6, 0, 16, 16, 16),
            Block.box(1, 0, 1, 15, 6, 15)
    );

    public static final VoxelShape SHAPE_UPPER_NORTH = Shapes.or(
            Block.box(5, 0, 5, 11, 2, 11),       // Was 16-18
            Block.box(3.5, 2, 3.5, 12.5, 11, 12.5) // Was 18-27
    );

    public ShrinkingStationBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Only the LOWER half gets the Block Entity and Inventory
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return new ShrinkingStationBlockEntity(pos, state);
        }
        return null;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            // Only drop items if the LOWER half is broken (since that's where the BE is)
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof ShrinkingStationBlockEntity shrinkingStationBlockEntity) {
                    shrinkingStationBlockEntity.drops();
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        // Only the LOWER half should drop the item
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return List.of();
        }
        return super.getDrops(state, builder);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (facing.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (facing == Direction.UP)) {
            // If we are Lower and Up is not us, OR we are Upper and Down is not us -> Destroy
            return (facingState.is(this) && facingState.getValue(HALF) != half)
                    ? state.setValue(FACING, facingState.getValue(FACING)) // Sync rotation just in case
                    : Blocks.AIR.defaultBlockState();
        }

        if (half == DoubleBlockHalf.LOWER && facing == Direction.DOWN && !state.canSurvive(level, currentPos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack pStack, BlockState state, Level pLevel, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lowerState = pLevel.getBlockState(lowerPos);
            if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                return useItemOn(pStack, lowerState, pLevel, lowerPos, player, hand, hit);
            }
            return ItemInteractionResult.FAIL;
        }

        if (!pLevel.isClientSide()) {
            BlockEntity entity = pLevel.getBlockEntity(pos);
            if (entity instanceof ShrinkingStationBlockEntity shrinkingStationBlockEntity) {

                ItemStack handItem = player.getItemInHand(hand);

                // Handle input slot interaction
                if (!handItem.isEmpty()) {
                    ItemStack inputItem = shrinkingStationBlockEntity.getInputItem();
                    ItemStack outputItem = shrinkingStationBlockEntity.getOutputItem();

                    if (inputItem.isEmpty() && outputItem.isEmpty()) {
                        ItemStack toInsert = handItem.copy();
                        toInsert.setCount(1);
                        shrinkingStationBlockEntity.setInputItem(toInsert);
                        handItem.shrink(1);
                        pLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                        return ItemInteractionResult.SUCCESS;
                    }
                } else {
                    ItemStack outputItem = shrinkingStationBlockEntity.getOutputItem();
                    ItemStack inputItem = shrinkingStationBlockEntity.getInputItem();

                    if (!outputItem.isEmpty()) {
                        player.setItemInHand(hand, outputItem.copy());
                        shrinkingStationBlockEntity.setOutputItem(ItemStack.EMPTY);
                        pLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                        return ItemInteractionResult.SUCCESS;
                    } else if (!inputItem.isEmpty()) {
                        player.setItemInHand(hand, inputItem.copy());
                        shrinkingStationBlockEntity.setInputItem(ItemStack.EMPTY);
                        pLevel.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
                        return ItemInteractionResult.SUCCESS;
                    }
                }
            }
        }

        return ItemInteractionResult.sidedSuccess(pLevel.isClientSide());
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if(level.isClientSide() || state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null; // Don't tick the upper block (it has no BE)
        }

        return createTickerHelper(blockEntityType, FBlockEntityRegistry.SHRINKING_STATION.get(),
                (level1, blockPos, blockState, blockEntity) -> blockEntity.tick(level1, blockPos, blockState));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER)
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    // Prevent creative mode dropping items when breaking the top half
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                BlockPos blockpos = pos.below();
                BlockState blockstate = level.getBlockState(blockpos);
                if (blockstate.is(state.getBlock()) && blockstate.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, blockpos, Block.getId(blockstate));
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape baseShape = (state.getValue(HALF) == DoubleBlockHalf.LOWER) ? SHAPE_LOWER_NORTH : SHAPE_UPPER_NORTH;

        switch (state.getValue(FACING)) {
            case SOUTH:
                return rotateBox(baseShape, Rotation.CLOCKWISE_180);
            case EAST:
                return rotateBox(baseShape, Rotation.CLOCKWISE_90);
            case WEST:
                return rotateBox(baseShape, Rotation.COUNTERCLOCKWISE_90);
            default:
                return baseShape;
        }
    }

    private static VoxelShape rotateBox(VoxelShape shape, Rotation rotation) {
        double x1, y1, z1, x2, y2, z2;
        List<AABB> boxes = shape.toAabbs();

        VoxelShape rotatedShape = Shapes.empty();

        for (AABB box : boxes) {
            x1 = box.minX;
            y1 = box.minY;
            z1 = box.minZ;
            x2 = box.maxX;
            y2 = box.maxY;
            z2 = box.maxZ;

            VoxelShape rotatedBox;
            switch (rotation) {
                case CLOCKWISE_90:
                    rotatedBox = Shapes.box(1 - z2, y1, x1, 1 - z1, y2, x2);
                    break;
                case CLOCKWISE_180:
                    rotatedBox = Shapes.box(1 - x2, y1, 1 - z2, 1 - x1, y2, 1 - z1);
                    break;
                case COUNTERCLOCKWISE_90:
                    rotatedBox = Shapes.box(z1, y1, 1 - x2, z2, y2, 1 - x1);
                    break;
                default:
                    rotatedBox = Shapes.box(x1, y1, z1, x2, y2, z2);
                    break;
            }

            rotatedShape = Shapes.or(rotatedShape, rotatedBox);
        }

        return rotatedShape;
    }
}