package net.alshanex.familiarslib.block;

import net.alshanex.familiarslib.block.entity.PetBedBlockEntity;
import com.mojang.serialization.MapCodec;
import net.alshanex.familiarslib.block.AbstractFamiliarBedBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PetBedBlock extends AbstractFamiliarBedBlock {

    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape PART_1 = box(14, 0, 0, 16, 1, 2);
    private static final VoxelShape PART_2 = box(0, 5, 13, 16, 7, 16);
    private static final VoxelShape PART_3 = box(0, 1, 0, 16, 5, 16);
    private static final VoxelShape PART_4 = box(13, 5, 3, 16, 7, 13);
    private static final VoxelShape PART_5 = box(0, 5, 3, 3, 7, 13);
    private static final VoxelShape PART_6 = box(14, 0, 14, 16, 1, 16);
    private static final VoxelShape PART_7 = box(0, 0, 14, 2, 1, 16);
    private static final VoxelShape PART_8 = box(0, 0, 0, 2, 1, 2);

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            PART_1, PART_2, PART_3, PART_4, PART_5, PART_6, PART_7, PART_8
    );

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            rotateBox(PART_1, Rotation.CLOCKWISE_180),
            rotateBox(PART_2, Rotation.CLOCKWISE_180),
            rotateBox(PART_3, Rotation.CLOCKWISE_180),
            rotateBox(PART_4, Rotation.CLOCKWISE_180),
            rotateBox(PART_5, Rotation.CLOCKWISE_180),
            rotateBox(PART_6, Rotation.CLOCKWISE_180),
            rotateBox(PART_7, Rotation.CLOCKWISE_180),
            rotateBox(PART_8, Rotation.CLOCKWISE_180)
    );

    private static final VoxelShape SHAPE_EAST = Shapes.or(
            rotateBox(PART_1, Rotation.CLOCKWISE_90),
            rotateBox(PART_2, Rotation.CLOCKWISE_90),
            rotateBox(PART_3, Rotation.CLOCKWISE_90),
            rotateBox(PART_4, Rotation.CLOCKWISE_90),
            rotateBox(PART_5, Rotation.CLOCKWISE_90),
            rotateBox(PART_6, Rotation.CLOCKWISE_90),
            rotateBox(PART_7, Rotation.CLOCKWISE_90),
            rotateBox(PART_8, Rotation.CLOCKWISE_90)
    );

    private static final VoxelShape SHAPE_WEST = Shapes.or(
            rotateBox(PART_1, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_2, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_3, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_4, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_5, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_6, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_7, Rotation.COUNTERCLOCKWISE_90),
            rotateBox(PART_8, Rotation.COUNTERCLOCKWISE_90)
    );

    public static final MapCodec<PetBedBlock> CODEC = simpleCodec(PetBedBlock::new);

    public PetBedBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(COLOR, DyeColor.WHITE);
    }

    public BlockState setColor(BlockState state, DyeColor color) {
        return state.setValue(COLOR, color);
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

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(FACING)) {
            case SOUTH:
                return SHAPE_SOUTH;
            case EAST:
                return SHAPE_EAST;
            case WEST:
                return SHAPE_WEST;
            default:
                return SHAPE_NORTH;
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public MapCodec<PetBedBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PetBedBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof DyeItem) {
            DyeColor dyeColor = ((DyeItem) stack.getItem()).getDyeColor();
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (blockEntity instanceof PetBedBlockEntity) {
                PetBedBlockEntity petBed = (PetBedBlockEntity) blockEntity;

                if (state.getValue(COLOR) == dyeColor) {
                    return ItemInteractionResult.FAIL;
                }

                if (!level.isClientSide) {
                    level.setBlock(pos, state.setValue(COLOR, dyeColor), 3);
                    petBed.setColor(dyeColor);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }
}
