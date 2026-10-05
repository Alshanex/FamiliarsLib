package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.FamiliarRoster;
import net.alshanex.familiarslib.data.FamiliarSavedData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.alshanex.familiarslib.util.familiars.FamiliarSync;
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

/**
 * Familiar storage block.
 *
 * Only familiar UUIDs are kept here. The snapshots live in the owner's FamiliarSavedData.
 */
public abstract class AbstractFamiliarStorageBlockEntity extends BlockEntity {
    private static final int MAX_STORED_FAMILIARS = 10;
    private static final int DEFAULT_MAX_DISTANCE = 25;

    private UUID ownerUUID;

    // Familiars inside the house (store mode)
    private final Set<UUID> storedFamiliars = new LinkedHashSet<>();
    // Familiars wandering around the house (wander mode)
    public final Set<UUID> outsideFamiliars = new HashSet<>();

    // Client only: snapshots received through UpdateFamiliarStoragePacket, for the screen
    private final Map<UUID, CompoundTag> clientSnapshots = new LinkedHashMap<>();

    // Houses saved by the old version kept full snapshots here; moved to SavedData in onLoad()
    private final Map<UUID, CompoundTag> legacySnapshots = new LinkedHashMap<>();

    private boolean storeMode = true; // Default to store mode
    private boolean canFamiliarsUseGoals = true;
    private int maxDistance = DEFAULT_MAX_DISTANCE;

    public AbstractFamiliarStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    /** The owner's familiar data (works while the owner is offline). Server only. */
    @Nullable
    protected FamiliarSavedData ownerData() {
        if (ownerUUID == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return FamiliarSavedData.get(serverLevel.getServer(), ownerUUID);
    }

    // Loading / migration

    @Override
    public void onLoad() {
        super.onLoad();
        if (!(level instanceof ServerLevel serverLevel)) return;

        FamiliarSavedData data = ownerData();
        if (data == null) return;

        boolean changed = false;

        // 1. Houses saved by the old version: move their snapshots into the owner's SavedData
        if (!legacySnapshots.isEmpty()) {
            int moved = 0;
            for (Map.Entry<UUID, CompoundTag> e : legacySnapshots.entrySet()) {
                if (data.putHousedIfAbsent(e.getKey(), level.dimension(), worldPosition, false, e.getValue())) {
                    moved++;
                }
            }
            // Write the SavedData before this block entity stops carrying the legacy copy
            serverLevel.getServer().overworld().getDataStorage().save();
            legacySnapshots.clear();
            changed = true;
            FamiliarsLib.LOGGER.info("Migrated {} familiar(s) from storage block at {} to SavedData", moved, worldPosition);
        }

        // 2. Drop IDs whose familiar no longer belongs to this house
        if (storedFamiliars.removeIf(id -> !data.isHousedAt(id, level.dimension(), worldPosition))) {
            changed = true;
        }

        if (changed) {
            setChanged();
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractFamiliarStorageBlockEntity storageEntity) {
        storageEntity.tick();
    }

    protected void tick() {
        if (level == null || level.isClientSide) return;

        // In store mode, nothing to do - familiars are stored in the owner's SavedData
        if (storeMode) {
            return;
        }

        // Every 2 seconds, enforce distance limits on wandering familiars
        if (level.getGameTime() % 40 == 0) {
            enforceDistanceLimits();
        }

        // Every 5 seconds, clean up dead familiars and refresh the snapshots of wandering ones
        if (level.getGameTime() % 100 == 0) {
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
                    Vec3 direction = familiar.position().subtract(houseCenter).normalize();
                    Vec3 targetPos = houseCenter.add(direction.scale(maxDistance * 0.6));

                    BlockPos targetBlock = BlockPos.containing(targetPos);
                    BlockPos safePos = findSafePositionNear(targetBlock);

                    if (safePos != null) {
                        familiar.teleportTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5);
                        familiar.getNavigation().stop();
                    } else {
                        Vec3 releasePos = findSafeReleasePosition();
                        if (releasePos != null) {
                            familiar.teleportTo(releasePos.x, releasePos.y, releasePos.z);
                            familiar.getNavigation().stop();
                        }
                    }
                }
            }
        }
    }

    // Finds a safe position near the given block position
    private BlockPos findSafePositionNear(BlockPos center) {
        if (isSafeSpawnPosition(center)) {
            return center;
        }

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

    protected void cleanupDeadFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        FamiliarSavedData data = ownerData();
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
                } else if (data != null) {
                    // Upsert: also registers wandering familiars from houses saved by the old version
                    data.putHoused(familiarId, level.dimension(), worldPosition, true,
                            FamiliarManager.createFamiliarNBT(familiar));
                }
            } else {
                // UUID points to something that isn't a familiar
                familiarsToRemove.add(familiarId);
            }
        }

        if (!familiarsToRemove.isEmpty()) {
            outsideFamiliars.removeAll(familiarsToRemove);
            setChanged();
            syncToClient();
        }
    }

    public boolean isStoreMode() {
        return storeMode;
    }

    public void setStoreMode(boolean storeMode) {
        if (this.storeMode != storeMode) {
            this.storeMode = storeMode;

            if (storeMode) {
                recallAllOutsideFamiliars();
            } else {
                releaseAllStoredFamiliars();
            }

            setChanged();
            syncToClient();
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
        }
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(int maxDistance) {
        this.maxDistance = Math.max(3, Math.min(25, maxDistance));
        setChanged();
        syncToClient();
    }

    // Wander mode: release / recall

    // Recalls all outside familiars back into storage (used when switching to store mode)
    protected void recallAllOutsideFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        FamiliarSavedData data = ownerData();
        if (data == null) return;

        for (UUID familiarId : new HashSet<>(outsideFamiliars)) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                // Loaded: take a fresh snapshot and remove the entity
                data.putHoused(familiarId, level.dimension(), worldPosition, false,
                        FamiliarManager.createFamiliarNBT(familiar));
                storedFamiliars.add(familiarId);
                familiar.remove(Entity.RemovalReason.DISCARDED);
            } else if (data.isHousedAt(familiarId, level.dimension(), worldPosition)) {
                // Not loaded, but we have a recent snapshot: store it. The entity still in the
                // unloaded chunk is discarded by the familiar's house check when it loads.
                data.setHousedOutside(familiarId, false);
                storedFamiliars.add(familiarId);
            }
            // Not loaded and never snapshotted (old-version wandering familiar): nothing we can
            // store, it keeps living in the world as before.
        }

        outsideFamiliars.clear();
        setChanged();
        syncToClient();
    }

    // Releases all stored familiars into the world (used when switching to wander mode)
    protected void releaseAllStoredFamiliars() {
        for (UUID familiarId : new ArrayList<>(storedFamiliars)) {
            releaseFamiliar(familiarId);
        }
    }

    // Releases a specific familiar from storage into the world
    protected void releaseFamiliar(UUID familiarId) {
        if (!storedFamiliars.contains(familiarId)) return;

        FamiliarSavedData data = ownerData();
        if (data == null) return;

        CompoundTag nbtData = data.getHousedData(familiarId);
        if (nbtData == null) {
            // Not in the owner's data anymore: stale ID
            storedFamiliars.remove(familiarId);
            setChanged();
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        EntityType<?> entityType = EntityType.byString(nbtData.getString("id")).orElse(null);
        if (entityType == null) {
            return;
        }

        Entity entity = entityType.create(serverLevel);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            return;
        }

        familiar.load(nbtData);
        familiar.setUUID(familiarId);

        float savedHealth = nbtData.getFloat("currentHealth");
        familiar.setHealth(Math.min(savedHealth, familiar.getMaxHealth()));

        Vec3 spawnPos = findSafeReleasePosition();
        if (spawnPos == null) {
            return;
        }

        familiar.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        familiar.setYRot(level.random.nextFloat() * 360F);
        familiar.setOldPosAndRot();
        familiar.setIsInHouse(true, getBlockPos());

        serverLevel.addFreshEntity(familiar);
        serverLevel.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z,
                SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
        serverLevel.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(familiar, getBlockState()));

        // Still housed (the snapshot stays as a backup), but now wandering
        data.setHousedOutside(familiarId, true);
        storedFamiliars.remove(familiarId);
        outsideFamiliars.add(familiarId);

        setChanged();
        syncToClient();
    }

    // Method to manually recall a familiar (e.g., from the familiar's AI returning to house)
    public boolean tryRecallFamiliar(AbstractSpellCastingPet familiar) {
        if (!canStoreFamiliar()) {
            return false;
        }

        UUID familiarId = familiar.getUUID();

        if (outsideFamiliars.contains(familiarId) &&
                familiar.getIsInHouse() &&
                getBlockPos().equals(familiar.housePosition)) {

            FamiliarSavedData data = ownerData();
            if (data == null) return false;

            data.putHoused(familiarId, level.dimension(), worldPosition, false,
                    FamiliarManager.createFamiliarNBT(familiar));
            storedFamiliars.add(familiarId);
            outsideFamiliars.remove(familiarId);

            familiar.remove(Entity.RemovalReason.DISCARDED);

            level.playSound(null, getBlockPos(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(familiar, getBlockState()));

            setChanged();
            syncToClient();
            return true;
        }

        return false;
    }

    protected abstract Direction getFacingDirection();

    // Finds a safe position to spawn in the set direction
    protected Vec3 findSafeReleasePosition() {
        BlockPos frontPos = getBlockPos().relative(getFacingDirection());
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

    /** Snapshots of the familiars inside the house (copies). Server: from SavedData. Client: last packet. */
    public Map<UUID, CompoundTag> getStoredFamiliars() {
        if (level != null && level.isClientSide) {
            return new LinkedHashMap<>(clientSnapshots);
        }

        Map<UUID, CompoundTag> result = new LinkedHashMap<>();
        FamiliarSavedData data = ownerData();
        if (data == null) return result;

        for (UUID id : storedFamiliars) {
            CompoundTag snapshot = data.getHousedData(id);
            if (snapshot != null) {
                result.put(id, snapshot);
            }
        }
        return result;
    }

    public Map<UUID, CompoundTag> getPhysicallyStoredFamiliars() {
        return getStoredFamiliars();
    }

    public boolean isFamiliarPhysicallyStored(UUID familiarId) {
        return storedFamiliars.contains(familiarId);
    }

    public Set<UUID> getStoredFamiliarIds() {
        return Collections.unmodifiableSet(storedFamiliars);
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

    public boolean ownsFamiliar(UUID familiarId) {
        return storedFamiliars.contains(familiarId) || outsideFamiliars.contains(familiarId);
    }

    // Store / retrieve

    /** Moves a carried familiar into this house. */
    public boolean storeFamiliar(UUID familiarId, ServerPlayer player) {
        if (!isOwner(player) || !canStoreFamiliar() || level == null) {
            return false;
        }

        FamiliarRoster roster = FamiliarRoster.of(player);
        if (!roster.hasFamiliar(familiarId)) {
            return false;
        }

        // Desummon first, so the roster holds an up-to-date snapshot
        if (player.serverLevel().getEntity(familiarId) instanceof AbstractSpellCastingPet) {
            FamiliarManager.desummonSpecificFamiliar(player, familiarId);
        }

        // One operation: carried -> housed
        if (!FamiliarSavedData.get(player).moveToHouse(familiarId, level.dimension(), worldPosition)) {
            return false;
        }

        // Clears selection/summon references (the carried entry is already gone) and reselects
        roster.removeTamedFamiliar(familiarId);

        storedFamiliars.add(familiarId);
        outsideFamiliars.remove(familiarId);

        setChanged();
        syncToClient();
        FamiliarSync.state(player);
        return true;
    }

    /** Moves a familiar from inside this house back to the owner. */
    public boolean retrieveFamiliar(UUID familiarId, ServerPlayer player) {
        if (!isOwner(player) || !storedFamiliars.contains(familiarId)) {
            return false;
        }

        FamiliarRoster roster = FamiliarRoster.of(player);
        if (!roster.canTameMoreFamiliars()) {
            return false;
        }

        // One operation: housed -> carried
        if (!FamiliarSavedData.get(player).moveToCarried(familiarId)) {
            // Stale ID: the familiar isn't in the owner's data anymore
            storedFamiliars.remove(familiarId);
            setChanged();
            syncToClient();
            return false;
        }

        storedFamiliars.remove(familiarId);

        if (roster.getSelectedFamiliarId() == null) {
            roster.setSelectedFamiliarId(familiarId);
        }

        setChanged();
        syncToClient();
        FamiliarSync.snapshot(player, familiarId);
        return true;
    }

    // Returns every familiar of this house to the owner (house broken or blown up)
    public void returnFamiliarsToOwner() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        FamiliarSavedData data = ownerData(); // online or offline
        if (data == null) {
            return;
        }

        // Wandering familiars that are loaded: fresh snapshot, then remove the entity.
        // Unloaded ones are returned from their last snapshot; their entity is discarded by the familiar's house check when its chunk loads.
        for (UUID familiarId : outsideFamiliars) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                data.putHoused(familiarId, level.dimension(), worldPosition, true,
                        FamiliarManager.createFamiliarNBT(familiar));
                familiar.setIsInHouse(false, null);
                familiar.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        // One operation per familiar: housed -> carried. No cap check: never destroy a familiar.
        data.returnAllFromHouse(level.dimension(), worldPosition);

        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
        if (owner != null) {
            FamiliarRoster.of(owner).validate();
            FamiliarSync.state(owner);
        }
        // Offline owners get their selection fixed by validate() on next login

        storedFamiliars.clear();
        outsideFamiliars.clear();
        setChanged();
    }

    // Handles familiar death
    public void handleFamiliarDeath(UUID familiarId) {
        if (level == null || level.isClientSide) return;

        boolean wasTracked = outsideFamiliars.remove(familiarId);
        if (storedFamiliars.remove(familiarId)) {
            wasTracked = true;
        }

        if (wasTracked) {
            FamiliarSavedData data = ownerData();
            if (data != null) {
                data.removeHoused(familiarId);
            }

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

    public void setClientStoredFamiliars(Map<UUID, CompoundTag> snapshots) {
        if (level != null && level.isClientSide) {
            clientSnapshots.clear();
            clientSnapshots.putAll(snapshots);
            storedFamiliars.clear();
            storedFamiliars.addAll(snapshots.keySet());
        }
    }

    public void setClientStoreMode(boolean storeMode) {
        if (level != null && level.isClientSide) {
            this.storeMode = storeMode;
        }
    }

    public void setClientCanFamiliarsUseGoals(boolean canFamiliarsUseGoals) {
        if (level != null && level.isClientSide) {
            this.canFamiliarsUseGoals = canFamiliarsUseGoals;
        }
    }

    public void setClientMaxDistance(int maxDistance) {
        if (level != null && level.isClientSide) {
            this.maxDistance = maxDistance;
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

        // IDs only. Legacy snapshots not migrated yet are written back so nothing is lost.
        ListTag storedList = new ListTag();
        for (UUID id : storedFamiliars) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", id);
            CompoundTag legacy = legacySnapshots.get(id);
            if (legacy != null) {
                entry.put("data", legacy);
            }
            storedList.add(entry);
        }
        tag.put("storedFamiliars", storedList);

        ListTag outsideList = new ListTag();
        for (UUID familiarId : outsideFamiliars) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", familiarId);
            outsideList.add(entry);
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

        storedFamiliars.clear();
        legacySnapshots.clear();
        if (tag.contains("storedFamiliars", Tag.TAG_LIST)) {
            ListTag storedList = tag.getList("storedFamiliars", Tag.TAG_COMPOUND);
            for (int i = 0; i < storedList.size(); i++) {
                CompoundTag entry = storedList.getCompound(i);
                if (entry.hasUUID("id")) {
                    UUID id = entry.getUUID("id");
                    storedFamiliars.add(id);
                    // Old format: full snapshot inside the block entity
                    if (entry.contains("data", Tag.TAG_COMPOUND) && !entry.getCompound("data").isEmpty()) {
                        legacySnapshots.put(id, entry.getCompound("data"));
                    }
                }
            }
        }

        outsideFamiliars.clear();
        if (tag.contains("outsideFamiliars", Tag.TAG_LIST)) {
            ListTag outsideList = tag.getList("outsideFamiliars", Tag.TAG_COMPOUND);
            for (int i = 0; i < outsideList.size(); i++) {
                CompoundTag entry = outsideList.getCompound(i);
                if (entry.hasUUID("id")) {
                    outsideFamiliars.add(entry.getUUID("id"));
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
}