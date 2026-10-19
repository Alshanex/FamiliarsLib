package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.data.BoundFamiliar;
import net.alshanex.familiarslib.util.familiars.BedCleanupHelper;
import net.alshanex.familiarslib.util.familiars.FamiliarEggHatching;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class AbstractFamiliarBedBlockEntity extends BlockEntity {
    protected boolean isBedTaken;
    private int safetyCheckTimer = 0;
    private static final int SAFETY_CHECK_INTERVAL = 100;

    // Familiar Egg

    /** Ticks from placing an egg until it's ready to hatch (5 minutes). */
    public static final int EGG_HATCH_TICKS = 6000;
    /** Progress at which the egg shows its first and second cracks. */
    public static final float EGG_CRACK_STAGE_1 = 0.5F;
    public static final float EGG_CRACK_STAGE_2 = 0.85F;
    public static final int EGG_CHECK_INTERVAL = 20;

    @Nullable
    private BoundFamiliar egg;
    private boolean hasEgg;
    private long eggPlacedTime;
    private int eggTimer;

    public AbstractFamiliarBedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.isBedTaken = false;
    }

    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }

        if (egg != null) {
            eggTimer++;
            if (eggTimer % EGG_CHECK_INTERVAL == 0) {
                FamiliarEggHatching.tick(this, (ServerLevel) level, eggTimer / EGG_CHECK_INTERVAL);
            }
            return;
        }

        safetyCheckTimer++;

        // Perform safety check every 5 seconds
        if (safetyCheckTimer >= SAFETY_CHECK_INTERVAL) {
            safetyCheckTimer = 0;

            if (isBedTaken) {
                BedCleanupHelper.performSafetyCheck(this, worldPosition, (ServerLevel) level);
            }
        }
    }

    /** Taken by a sleeping familiar, or holding an egg. */
    public boolean isBedTaken() {
        return this.isBedTaken || this.hasEgg;
    }

    public void setBedTaken(boolean bool){
        this.isBedTaken = bool;
        setChanged();

        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // Egg

    public boolean hasEgg() {
        return hasEgg;
    }

    /** Server only. */
    @Nullable
    public BoundFamiliar getEgg() {
        return egg;
    }

    public void placeEgg(BoundFamiliar familiar, long gameTime) {
        this.egg = familiar;
        this.hasEgg = true;
        this.eggPlacedTime = gameTime;
        this.eggTimer = 0;
        markUpdated();
    }

    /** Removes the egg and returns its familiar (null if there was none). */
    @Nullable
    public BoundFamiliar takeEgg() {
        BoundFamiliar taken = this.egg;
        this.egg = null;
        this.hasEgg = false;
        this.eggTimer = 0;
        markUpdated();
        return taken;
    }

    /** 0 when placed, 1 when ready to hatch. */
    public float getEggProgress(long gameTime) {
        return Mth.clamp((gameTime - eggPlacedTime) / (float) EGG_HATCH_TICKS, 0F, 1F);
    }

    /** Where the bottom center of the egg sits, relative to the block's corner. Override for beds of other shapes. */
    public Vec3 getEggRenderOffset() {
        return new Vec3(0.5, 0.3125, 0.5);
    }

    /** Size of the egg on the bed (1 = a full block tall). */
    public float getEggRenderScale() {
        return 0.5F;
    }

    private void markUpdated() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        if (pTag.contains("isTaken")) {
            this.isBedTaken = pTag.getBoolean("isTaken");
        }

        // Disk has the full egg; client updates only say whether there is one
        this.egg = pTag.contains("Egg") ? BoundFamiliar.fromTag(pTag.getCompound("Egg")) : null;
        this.hasEgg = this.egg != null || pTag.getBoolean("HasEgg");
        this.eggPlacedTime = pTag.getLong("EggPlaced");
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registryAccess) {
        super.saveAdditional(tag, registryAccess);
        tag.putBoolean("isTaken", this.isBedTaken);
        if (egg != null) {
            tag.put("Egg", egg.toTag());
            tag.putBoolean("HasEgg", true);
            tag.putLong("EggPlaced", eggPlacedTime);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("isTaken", this.isBedTaken);
        tag.putBoolean("HasEgg", this.hasEgg);
        tag.putLong("EggPlaced", this.eggPlacedTime);
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