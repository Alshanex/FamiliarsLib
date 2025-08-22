package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.util.familiars.BedCleanupHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;

public abstract class AbstractFamiliarBedBlockEntity extends BlockEntity {
    protected boolean isBedTaken;
    private int safetyCheckTimer = 0;
    private static final int SAFETY_CHECK_INTERVAL = 100;

    public AbstractFamiliarBedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.isBedTaken = false;
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        safetyCheckTimer++;

        // Perform safety check every 5 seconds
        if (safetyCheckTimer >= SAFETY_CHECK_INTERVAL) {
            safetyCheckTimer = 0;

            if (isBedTaken()) {
                BedCleanupHelper.performSafetyCheck(this, worldPosition, (ServerLevel) level);
            }
        }
    }

    public boolean isBedTaken() {
        return this.isBedTaken;
    }

    public void setBedTaken(boolean bool){
        this.isBedTaken = bool;
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        if (pTag.hasUUID("isTaken")) {
            this.isBedTaken = pTag.getBoolean("isTaken");
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registryAccess) {
        super.saveAdditional(tag, registryAccess);
        tag.putBoolean("isTaken", this.isBedTaken);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isTaken", this.isBedTaken);
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

    public Vec3 getSleepPosition() {
        return new Vec3(
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.125,
                worldPosition.getZ() + 0.5
        );
    }

    public boolean isPositionCorrectForSleeping(Vec3 entityPos) {
        Vec3 sleepPos = getSleepPosition();
        double distance = entityPos.distanceTo(sleepPos);
        return distance <= 1.0;
    }
}
