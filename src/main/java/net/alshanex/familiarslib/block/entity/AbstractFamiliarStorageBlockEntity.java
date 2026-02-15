package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractFamiliarStorageBlockEntity extends BlockEntity {
    private static final int MAX_STORED_FAMILIARS = 10;
    private static final int DEFAULT_MAX_DISTANCE = 25;

    private UUID ownerUUID;
    public final Map<UUID, FamiliarData> storedFamiliars = new HashMap<>();
    public final Set<UUID> outsideFamiliars = new HashSet<>();

    private boolean storeMode = true; // Default to store mode
    private boolean canFamiliarsUseGoals = true;
    private int maxDistance = DEFAULT_MAX_DISTANCE;

    public AbstractFamiliarStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractFamiliarStorageBlockEntity storageEntity) {
        storageEntity.tick();
    }

    protected void tick() {
        if (level == null || level.isClientSide) return;

        // In store mode, nothing to do - familiars are stored as NBT
        if (storeMode) {
            return;
        }

        // Every 2 seconds, enforce distance limits on wandering familiars
        if (level.getGameTime() % 40 == 0) {
            enforceDistanceLimits();
        }

        // In wander mode, only do a gentle cleanup of truly dead/removed familiars
        // This runs infrequently to avoid performance issues
        if (level.getGameTime() % 100 == 0) { // Every 5 seconds
            cleanupDeadFamiliars();
        }
    }

    // Teleports familiars back near the house if they exceed the max distance
    protected void enforceDistanceLimits() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Vec3 houseCenter = Vec3.atCenterOf(getBlockPos());

        for (UUID familiarId : new HashSet<>(outsideFamiliars)) {
            Entity entity = serverLevel.getEntity(familiarId);

            // Skip null entities - they might be in unloaded chunks
            if (entity == null) {
                continue;
            }

            if (entity instanceof AbstractSpellCastingPet familiar) {
                double distance = familiar.position().distanceTo(houseCenter);

                // If familiar is beyond the max distance + a small buffer, teleport them back
                if (distance > maxDistance + 2.0) {
                    // Calculate a position near the house within the allowed range
                    Vec3 direction = familiar.position().subtract(houseCenter).normalize();
                    Vec3 targetPos = houseCenter.add(direction.scale(maxDistance * 0.6));

                    // Find safe ground position
                    BlockPos targetBlock = BlockPos.containing(targetPos);
                    BlockPos safePos = findSafePositionNear(targetBlock);

                    if (safePos != null) {
                        familiar.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                        familiar.getNavigation().stop();
                        FamiliarsLib.LOGGER.debug("Teleported familiar {} back within range (was {} blocks away, max {})",
                                familiarId, (int) distance, maxDistance);
                    } else {
                        // Fallback: teleport to house position
                        Vec3 releasePos = findSafeReleasePosition();
                        if (releasePos != null) {
                            familiar.teleportTo(releasePos.x, releasePos.y, releasePos.z);
                            familiar.getNavigation().stop();
                            FamiliarsLib.LOGGER.debug("Teleported familiar {} to house position (no safe nearby pos found)", familiarId);
                        }
                    }
                }
            }
        }
    }

    // Finds a safe position near the given block position
    private BlockPos findSafePositionNear(BlockPos center) {
        // Try the center first
        if (isSafeSpawnPosition(center)) {
            return center;
        }

        // Try nearby positions
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (isSafeSpawnPosition(check)) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    public boolean isStoreMode() {
        return storeMode;
    }

    public void setStoreMode(boolean storeMode) {
        if (this.storeMode != storeMode) {
            this.storeMode = storeMode;

            if (storeMode) {
                // Only recall when manually switching to store mode
                recallAllOutsideFamiliars();
            } else {
                // When switching to wander mode, release all stored familiars
                releaseAllStoredFamiliars();
            }

            setChanged();
            syncToClient();

            FamiliarsLib.LOGGER.debug("Storage mode changed to: {}", storeMode ? "Store" : "Wander");
        }
    }

    public boolean canFamiliarsUseGoals() {
        return canFamiliarsUseGoals;
    }

    public void setCanFamiliarsUseGoals(boolean canFamiliarsUseGoals) {
        if (this.canFamiliarsUseGoals != canFamiliarsUseGoals) {
            this.canFamiliarsUseGoals = canFamiliarsUseGoals;
            setChanged();
            syncToClient();
            FamiliarsLib.LOGGER.debug("Can familiars use goals changed to: {}", canFamiliarsUseGoals);
        }
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(int maxDistance) {
        this.maxDistance = Math.max(3, Math.min(25, maxDistance));
        setChanged();
        syncToClient();
        FamiliarsLib.LOGGER.debug("Max distance changed to: {}", this.maxDistance);
    }

    // Recalls all outside familiars back into storage (used when switching to store mode)
    protected void recallAllOutsideFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Set<UUID> familiarsToRecall = new HashSet<>(outsideFamiliars);

        for (UUID familiarId : familiarsToRecall) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                CompoundTag nbtData = FamiliarManager.createFamiliarNBT(familiar);
                FamiliarData data = new FamiliarData(nbtData, 0);
                storedFamiliars.put(familiarId, data);

                familiar.remove(Entity.RemovalReason.DISCARDED);

                FamiliarsLib.LOGGER.debug("Recalled familiar {} due to Store Mode activation", familiarId);
            }
        }

        outsideFamiliars.clear();
        setChanged();
        syncToClient();
    }

    // Releases all stored familiars into the world (used when switching to wander mode)
    protected void releaseAllStoredFamiliars() {
        if (storedFamiliars.isEmpty()) return;

        Map<UUID, FamiliarData> familiarsToRelease = new HashMap<>(storedFamiliars);

        for (Map.Entry<UUID, FamiliarData> entry : familiarsToRelease.entrySet()) {
            releaseFamiliar(entry.getKey());
        }
    }

    // Releases a specific familiar from storage into the world
    protected void releaseFamiliar(UUID familiarId) {
        FamiliarData familiarData = storedFamiliars.get(familiarId);
        if (familiarData == null) return;

        ServerLevel serverLevel = (ServerLevel) level;
        String entityTypeString = familiarData.nbtData.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType == null) {
            FamiliarsLib.LOGGER.debug("Unknown entity type: {}", entityTypeString);
            return;
        }

        Entity entity = entityType.create(serverLevel);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.debug("Entity is not a familiar: {}", entity);
            return;
        }

        // Load familiar data
        familiar.load(familiarData.nbtData);
        familiar.setUUID(familiarId);

        // Set health
        float savedHealth = familiarData.nbtData.getFloat("currentHealth");
        familiar.setHealth(Math.min(savedHealth, familiar.getMaxHealth()));

        // Set position near the storage block
        Vec3 spawnPos = findSafeReleasePosition();
        if (spawnPos == null) {
            FamiliarsLib.LOGGER.debug("No safe release position found for familiar {}", familiarId);
            return;
        }

        familiar.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        familiar.setYRot(level.random.nextFloat() * 360F);
        familiar.setOldPosAndRot();

        // Mark as being in house mode
        familiar.setIsInHouse(true, getBlockPos());

        // Add to world
        serverLevel.addFreshEntity(familiar);
        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(familiar, getBlockState()));

        // Move from stored to outside
        storedFamiliars.remove(familiarId);
        outsideFamiliars.add(familiarId);

        setChanged();
        syncToClient();

        FamiliarsLib.LOGGER.debug("Released familiar {} from storage at {}", familiarId, getBlockPos());
    }

    // Only cleans up familiars that are confirmed dead or removed from the world
    // Does NOT remove familiars that are simply null (could be in unloaded chunks)
    protected void cleanupDeadFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Set<UUID> familiarsToRemove = new HashSet<>();

        for (UUID familiarId : new HashSet<>(outsideFamiliars)) {
            Entity entity = serverLevel.getEntity(familiarId);

            // Skip null entities - they might just be in unloaded chunks
            if (entity == null) {
                continue;
            }

            if (entity instanceof AbstractSpellCastingPet familiar) {
                if (!familiar.isAlive() || familiar.isRemoved()) {
                    familiarsToRemove.add(familiarId);
                    FamiliarsLib.LOGGER.debug("Familiar {} is dead or removed, cleaning up", familiarId);
                }
            } else {
                // UUID points to something that isn't a familiar
                familiarsToRemove.add(familiarId);
                FamiliarsLib.LOGGER.debug("Entity {} is not a familiar, removing from tracking", familiarId);
            }
        }

        if (!familiarsToRemove.isEmpty()) {
            outsideFamiliars.removeAll(familiarsToRemove);
            setChanged();
            syncToClient();
        }
    }

    // Direction for the familiars to spawn, recommended to set the opposite to the block's FACING property
    protected abstract Direction getFacingDirection();

    // Finds a safe position to spawn in the set direction
    protected Vec3 findSafeReleasePosition() {
        BlockPos storagePos = getBlockPos();

        Direction facing = getFacingDirection();

        BlockPos frontPos = storagePos.relative(facing);
        if (isSafeSpawnPosition(frontPos)) {
            return Vec3.atBottomCenterOf(frontPos);
        }

        return null;
    }

    // Checks if the position is safe to spawn
    protected boolean isSafeSpawnPosition(BlockPos pos) {
        return level.getBlockState(pos).isAir() &&
                level.getBlockState(pos.above()).isAir() &&
                level.getBlockState(pos.below()).isSolid();
    }

    // Method to manually recall a familiar (e.g., from the familiar's AI returning to house)
    public boolean tryRecallFamiliar(AbstractSpellCastingPet familiar) {
        if (!canStoreFamiliar()) {
            return false;
        }

        UUID familiarId = familiar.getUUID();

        // Only recall if familiar belongs to the house
        if (outsideFamiliars.contains(familiarId) &&
                familiar.getIsInHouse() &&
                getBlockPos().equals(familiar.housePosition)) {

            CompoundTag nbtData = FamiliarManager.createFamiliarNBT(familiar);

            FamiliarData familiarDataObj = new FamiliarData(nbtData, 0);
            storedFamiliars.put(familiarId, familiarDataObj);
            outsideFamiliars.remove(familiarId);

            familiar.remove(Entity.RemovalReason.DISCARDED);

            level.playSound(null, getBlockPos(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(familiar, getBlockState()));

            setChanged();
            syncToClient();

            FamiliarsLib.LOGGER.debug("Recalled familiar {} to storage", familiarId);
            return true;
        }

        return false;
    }

    public void setOwner(ServerPlayer player) {
        this.ownerUUID = player.getUUID();
        setChanged();
    }

    public boolean isOwner(ServerPlayer player) {
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public Map<UUID, CompoundTag> getStoredFamiliars() {
        Map<UUID, CompoundTag> result = new HashMap<>();
        for (Map.Entry<UUID, FamiliarData> entry : storedFamiliars.entrySet()) {
            result.put(entry.getKey(), entry.getValue().nbtData);
        }
        return result;
    }

    public boolean isFamiliarPhysicallyStored(UUID familiarId) {
        return storedFamiliars.containsKey(familiarId);
    }

    public Map<UUID, CompoundTag> getPhysicallyStoredFamiliars() {
        Map<UUID, CompoundTag> result = new HashMap<>();
        for (Map.Entry<UUID, FamiliarData> entry : storedFamiliars.entrySet()) {
            result.put(entry.getKey(), entry.getValue().nbtData);
        }
        return result;
    }

    public boolean canStoreFamiliar() {
        return storedFamiliars.size() + outsideFamiliars.size() < MAX_STORED_FAMILIARS;
    }

    public int getStoredFamiliarCount() {
        return storedFamiliars.size();
    }

    public int getMaxStoredFamiliars() {
        return MAX_STORED_FAMILIARS;
    }

    public int getOutsideFamiliarCount() {
        return outsideFamiliars.size();
    }

    // Stores familiar in the house
    public boolean storeFamiliar(UUID familiarId, CompoundTag familiarData, ServerPlayer player) {
        if (!isOwner(player)) {
            return false;
        }

        if (!canStoreFamiliar()) {
            return false;
        }

        return FamiliarManager.storeFamiliarInHouse(familiarId, familiarData, player, getBlockPos());
    }

    // Retrieves familiar from the house
    public boolean retrieveFamiliar(UUID familiarId, ServerPlayer player) {
        if (!isOwner(player)) {
            return false;
        }

        PlayerFamiliarData playerData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        if (!playerData.canTameMoreFamiliars()) {
            return false;
        }

        if (storedFamiliars.containsKey(familiarId)) {
            return FamiliarManager.retrieveFamiliarFromHouse(familiarId, player, getBlockPos());
        }

        return false;
    }

    // Returns stored familiars to the owner
    public void returnFamiliarsToOwner() {
        if (ownerUUID == null || level == null || level.isClientSide) {
            return;
        }

        if (level instanceof ServerLevel serverLevel) {
            ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
            if (owner != null) {
                PlayerFamiliarData playerData = owner.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

                // Return stored familiars
                for (Map.Entry<UUID, FamiliarData> entry : storedFamiliars.entrySet()) {
                    UUID familiarId = entry.getKey();
                    CompoundTag familiarData = entry.getValue().nbtData;

                    if (playerData.canTameMoreFamiliars()) {
                        familiarData.putBoolean("isInHouse", false);
                        playerData.addTamedFamiliar(familiarId, familiarData);

                        if (playerData.getSelectedFamiliarId() == null) {
                            playerData.setSelectedFamiliarId(familiarId);
                        }

                        FamiliarsLib.LOGGER.debug("Returned familiar {} to owner {}", familiarId, owner.getName().getString());
                    }
                }

                // Remove outside familiars from world
                for (UUID familiarId : outsideFamiliars) {
                    Entity entity = serverLevel.getEntity(familiarId);
                    if (entity instanceof AbstractSpellCastingPet familiar) {
                        familiar.setIsInHouse(false, null);
                        familiar.remove(Entity.RemovalReason.DISCARDED);
                        FamiliarsLib.LOGGER.debug("Removed outside familiar {} from world due to house destruction", familiarId);
                    }
                }

                FamiliarManager.syncFamiliarDataForPlayer(owner);
            }
        }

        storedFamiliars.clear();
        outsideFamiliars.clear();
    }

    // Update mode in the client
    public void setClientStoreMode(boolean storeMode) {
        if (level != null && level.isClientSide) {
            this.storeMode = storeMode;
            FamiliarsLib.LOGGER.debug("Client updated storage mode to: {}", storeMode ? "Store" : "Wander");
        }
    }

    public void setClientCanFamiliarsUseGoals(boolean canFamiliarsUseGoals) {
        if (level != null && level.isClientSide) {
            this.canFamiliarsUseGoals = canFamiliarsUseGoals;
            FamiliarsLib.LOGGER.debug("Client updated can familiars use goals to: {}", canFamiliarsUseGoals);
        }
    }

    public void setClientMaxDistance(int maxDistance) {
        if (level != null && level.isClientSide) {
            this.maxDistance = maxDistance;
            FamiliarsLib.LOGGER.debug("Client updated max distance to: {}", maxDistance);
        }
    }

    public boolean ownsFamiliar(UUID familiarId) {
        return storedFamiliars.containsKey(familiarId) || outsideFamiliars.contains(familiarId);
    }

    // Handles familiar death
    public void handleFamiliarDeath(UUID familiarId) {
        if (level == null || level.isClientSide) return;

        boolean wasTracked = false;

        if (outsideFamiliars.remove(familiarId)) {
            wasTracked = true;
            FamiliarsLib.LOGGER.debug("Removed dead familiar {} from outside tracking", familiarId);
        }

        if (storedFamiliars.remove(familiarId) != null) {
            wasTracked = true;
            FamiliarsLib.LOGGER.debug("Removed dead familiar {} from stored familiars (unusual case)", familiarId);
        }

        if (wasTracked) {
            setChanged();
            syncToClient();

            if (ownerUUID != null && level instanceof ServerLevel serverLevel) {
                ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
                if (owner != null) {
                    owner.displayClientMessage(
                            Component.translatable("message.familiarslib.familiar_died_in_house")
                                    .withStyle(ChatFormatting.RED), false);
                }
            }
        }
    }

    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (ownerUUID != null) {
            tag.putUUID("ownerUUID", ownerUUID);
        }

        tag.putBoolean("storeMode", storeMode);
        tag.putBoolean("canFamiliarsUseGoals", canFamiliarsUseGoals);
        tag.putInt("maxDistance", maxDistance);

        // Save stored familiars
        ListTag storedList = new ListTag();
        for (Map.Entry<UUID, FamiliarData> entry : storedFamiliars.entrySet()) {
            CompoundTag familiarEntry = new CompoundTag();
            familiarEntry.putUUID("id", entry.getKey());
            familiarEntry.put("data", entry.getValue().nbtData);
            familiarEntry.putInt("occupationTime", entry.getValue().occupationTime);
            storedList.add(familiarEntry);
        }
        tag.put("storedFamiliars", storedList);

        // Save outside familiars
        ListTag outsideList = new ListTag();
        for (UUID familiarId : outsideFamiliars) {
            CompoundTag outsideEntry = new CompoundTag();
            outsideEntry.putUUID("id", familiarId);
            outsideList.add(outsideEntry);
        }
        tag.put("outsideFamiliars", outsideList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.hasUUID("ownerUUID")) {
            ownerUUID = tag.getUUID("ownerUUID");
        }

        storeMode = tag.getBoolean("storeMode");
        canFamiliarsUseGoals = tag.getBoolean("canFamiliarsUseGoals");
        maxDistance = tag.getInt("maxDistance");
        if (maxDistance == 0) maxDistance = DEFAULT_MAX_DISTANCE; // Fallback for old saves

        // Load stored familiars
        storedFamiliars.clear();
        if (tag.contains("storedFamiliars", Tag.TAG_LIST)) {
            ListTag storedList = tag.getList("storedFamiliars", Tag.TAG_COMPOUND);
            for (int i = 0; i < storedList.size(); i++) {
                CompoundTag familiarEntry = storedList.getCompound(i);
                if (familiarEntry.hasUUID("id")) {
                    UUID id = familiarEntry.getUUID("id");
                    CompoundTag data = familiarEntry.getCompound("data");
                    int occupationTime = familiarEntry.getInt("occupationTime");
                    storedFamiliars.put(id, new FamiliarData(data, occupationTime));
                }
            }
        }

        // Load outside familiars
        outsideFamiliars.clear();
        if (tag.contains("outsideFamiliars", Tag.TAG_LIST)) {
            ListTag outsideList = tag.getList("outsideFamiliars", Tag.TAG_COMPOUND);
            for (int i = 0; i < outsideList.size(); i++) {
                CompoundTag outsideEntry = outsideList.getCompound(i);
                if (outsideEntry.hasUUID("id")) {
                    UUID id = outsideEntry.getUUID("id");
                    outsideFamiliars.add(id);
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        loadAdditional(tag, registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Helper class to store familiar data with occupation time
    public static class FamiliarData {
        public final CompoundTag nbtData;
        public int occupationTime;

        public FamiliarData(CompoundTag nbtData, int occupationTime) {
            this.nbtData = nbtData;
            this.occupationTime = occupationTime;
        }

        public boolean canBeReleased() {
            return true; // No longer need minimum occupation time since release is manual
        }
    }
}