package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.BedLinkData;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import java.util.UUID;

public abstract class AbstractFamiliarBedBlockEntity extends BlockEntity {
    private UUID ownerUUID = null;

    public AbstractFamiliarBedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
        setChanged();
    }

    public boolean isOwner(Player player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    //Gets bed's linked familiar
    public UUID getLinkedFamiliar() {
        if (ownerUUID == null || level == null) return null;

        Player owner = level.getPlayerByUUID(ownerUUID);
        if (!(owner instanceof ServerPlayer serverPlayer)) return null;

        BedLinkData linkData = serverPlayer.getData(AttachmentRegistry.BED_LINK_DATA);
        return linkData.getLinkedFamiliar(worldPosition);
    }

    public boolean hasLinkedFamiliar() {
        return getLinkedFamiliar() != null;
    }

    public boolean isLinkedToPet(UUID petUUID) {
        if (ownerUUID == null || level == null || petUUID == null) return false;

        Player owner = level.getPlayerByUUID(ownerUUID);
        if (owner == null) {
            return false;
        }

        BedLinkData linkData = owner.getData(AttachmentRegistry.BED_LINK_DATA);
        UUID linkedFamiliar = linkData.getLinkedFamiliar(worldPosition);

        return linkedFamiliar != null && linkedFamiliar.equals(petUUID);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        if (pTag.hasUUID("ownerUUID")) {
            this.ownerUUID = pTag.getUUID("ownerUUID");
        }
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registryAccess) {
        if (ownerUUID != null) {
            tag.putUUID("ownerUUID", ownerUUID);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        if (ownerUUID != null) {
            tag.putUUID("ownerUUID", ownerUUID);
        }
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

    public void onBedBroken() {
        if (ownerUUID != null && level != null && !level.isClientSide) {
            Player owner = level.getPlayerByUUID(ownerUUID);
            if (owner instanceof ServerPlayer serverPlayer) {
                BedLinkData linkData = serverPlayer.getData(AttachmentRegistry.BED_LINK_DATA);
                linkData.unlinkBed(worldPosition);
            }
        }
    }
}
