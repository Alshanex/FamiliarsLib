package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.block.PetBedBlock;
import net.alshanex.familiarslib.registry.FBlockEntityRegistry;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;

public class PetBedBlockEntity extends AbstractFamiliarBedBlockEntity {
    private DyeColor color = DyeColor.WHITE;

    public PetBedBlockEntity(BlockPos pos, BlockState state) {
        super(FBlockEntityRegistry.PET_BED.get(), pos, state);
        if (state.hasProperty(PetBedBlock.COLOR)) {
            this.color = state.getValue(PetBedBlock.COLOR);
        }
    }

    public DyeColor getColor() {
        return this.color;
    }

    public void setColor(DyeColor color) {
        this.color = color;
        setChanged();
        if (!level.isClientSide) {
            BlockState currentState = getBlockState();
            if (currentState.hasProperty(PetBedBlock.COLOR) && currentState.getValue(PetBedBlock.COLOR) != color) {
                level.setBlock(worldPosition, currentState.setValue(PetBedBlock.COLOR, color), 3);
            }
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        String colorKey = pTag.contains("Color") ? "Color" : pTag.contains("color") ? "color" : null;
        if (colorKey != null) {
            this.color = DyeColor.byName(pTag.getString(colorKey), DyeColor.WHITE);

            if (level != null && hasLevel()) {
                BlockState state = level.getBlockState(worldPosition);
                if (state.hasProperty(PetBedBlock.COLOR) && state.getValue(PetBedBlock.COLOR) != this.color) {
                    level.setBlock(worldPosition, state.setValue(PetBedBlock.COLOR, this.color), 3);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registryAccess) {
        tag.putString("Color", this.color.getName());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Color", this.color.getName());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        loadAdditional(tag, lookupProvider);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        var packet = ClientboundBlockEntityDataPacket.create(this);
        return packet;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        handleUpdateTag(pkt.getTag(), lookupProvider);
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
}
