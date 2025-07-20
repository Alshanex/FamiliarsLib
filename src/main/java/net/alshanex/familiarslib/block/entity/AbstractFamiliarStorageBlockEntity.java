package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    private static final int MIN_OCCUPATION_TICKS = 100; // 5 segundos
    private static final int RELEASE_CHANCE_PER_TICK = 40; // 1/40 chance
    private static final double MAX_DISTANCE_FROM_HOUSE = 25.0; // Zona amplia alrededor de la casa
    private int ticksSinceLastRelease = 0;
    private static final int MAX_TICKS_WITHOUT_RELEASE = 400; // 20 segundos máximo sin actividad

    private UUID ownerUUID;
    public final Map<UUID, FamiliarData> storedFamiliars = new HashMap<>();
    public final Set<UUID> outsideFamiliars = new HashSet<>();

    private boolean storeMode = false;

    public AbstractFamiliarStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractFamiliarStorageBlockEntity storageEntity) {
        storageEntity.tick();
    }

    private void tick() {
        if (level == null || level.isClientSide) return;

        // En Store Mode: NO liberar familiares
        if (storeMode) {
            // Solo limpiar familiares afuera que ya no existen (no debería haber, pero por seguridad)
            cleanupMissingFamiliars();
            return;
        }

        // En Wander Mode: comportamiento normal
        if (!storedFamiliars.isEmpty()) {
            ticksSinceLastRelease++;

            // Chance normal cada tick
            if (level.random.nextInt(RELEASE_CHANCE_PER_TICK) == 0) {
                if (releaseRandomFamiliar()) {
                    ticksSinceLastRelease = 0;
                }
            }

            // Garantizar actividad cada 20 segundos
            if (ticksSinceLastRelease >= MAX_TICKS_WITHOUT_RELEASE) {
                if (releaseRandomFamiliar()) {
                    ticksSinceLastRelease = 0;
                }
            }

            // Extra actividad con múltiples familiares
            if (storedFamiliars.size() >= 3 && level.random.nextInt(80) == 0) {
                releaseRandomFamiliar();
            }

            // Actividad extra durante el día
            if (isDaytime() && level.random.nextInt(60) == 0) {
                releaseRandomFamiliar();
            }
        } else {
            ticksSinceLastRelease = 0;
        }

        // Verificar familiares afuera
        cleanupMissingFamiliars();
        handleDistantFamiliars();
    }

    public boolean isStoreMode() {
        return storeMode;
    }

    public void setStoreMode(boolean storeMode) {
        if (this.storeMode != storeMode) {
            this.storeMode = storeMode;

            if (storeMode) {
                recallAllOutsideFamiliars();
            }

            setChanged();
            syncToClient();

            FamiliarsLib.LOGGER.info("Storage mode changed to: {}", storeMode ? "Store" : "Wander");
        }
    }

    private void recallAllOutsideFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Set<UUID> familiarsToRecall = new HashSet<>(outsideFamiliars);

        for (UUID familiarId : familiarsToRecall) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                // Crear NBT del familiar actual
                CompoundTag nbtData = FamiliarManager.createFamiliarNBT(familiar);

                // Mover a storage
                FamiliarData data = new FamiliarData(nbtData, 0);
                storedFamiliars.put(familiarId, data);

                // Despawnear del mundo
                familiar.remove(Entity.RemovalReason.DISCARDED);

                FamiliarsLib.LOGGER.info("Recalled familiar {} due to Store Mode activation", familiarId);
            }
        }

        // Limpiar lista de afuera
        outsideFamiliars.clear();
    }

    private boolean releaseRandomFamiliar() {
        if (storeMode || storedFamiliars.isEmpty()) return false;

        List<UUID> availableFamiliars = new ArrayList<>();
        for (Map.Entry<UUID, FamiliarData> entry : storedFamiliars.entrySet()) {
            if (entry.getValue().canBeReleased()) {
                availableFamiliars.add(entry.getKey());
            }
        }

        if (availableFamiliars.isEmpty()) {
            for (Map.Entry<UUID, FamiliarData> entry : storedFamiliars.entrySet()) {
                if (entry.getValue().occupationTime >= 100) {
                    availableFamiliars.add(entry.getKey());
                }
            }
        }

        if (availableFamiliars.isEmpty() && !storedFamiliars.isEmpty()) {
            availableFamiliars.addAll(storedFamiliars.keySet());
        }

        if (!availableFamiliars.isEmpty()) {
            UUID familiarToRelease = availableFamiliars.get(level.random.nextInt(availableFamiliars.size()));
            releaseFamiliar(familiarToRelease);
            return true;
        }

        return false;
    }

    private void releaseFamiliar(UUID familiarId) {
        FamiliarData familiarData = storedFamiliars.get(familiarId);
        if (familiarData == null) return;

        ServerLevel serverLevel = (ServerLevel) level;
        String entityTypeString = familiarData.nbtData.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType == null) {
            FamiliarsLib.LOGGER.warn("Unknown entity type: {}", entityTypeString);
            return;
        }

        Entity entity = entityType.create(serverLevel);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.warn("Entity is not a familiar: {}", entity);
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

        // Move from stored to outside - solo tracking simple
        storedFamiliars.remove(familiarId);
        outsideFamiliars.add(familiarId);

        setChanged();
        syncToClient();

        FamiliarsLib.LOGGER.info("Released familiar {} from storage at {}", familiarId, getBlockPos());
    }

    private void cleanupMissingFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        Set<UUID> familiarsToRemove = new HashSet<>();

        for (UUID familiarId : outsideFamiliars) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (!(entity instanceof AbstractSpellCastingPet)) {
                familiarsToRemove.add(familiarId);
                FamiliarsLib.LOGGER.info("Familiar {} no longer exists, removed from tracking", familiarId);
            }
        }

        outsideFamiliars.removeAll(familiarsToRemove);
    }

    private void handleDistantFamiliars() {
        if (outsideFamiliars.isEmpty()) return;

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos storagePos = getBlockPos();
        Set<UUID> familiarsToRecall = new HashSet<>();

        for (UUID familiarId : outsideFamiliars) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                double distance = familiar.position().distanceTo(Vec3.atCenterOf(storagePos));

                // Solo forzar entrada si están REALMENTE lejos (para evitar que se pierdan)
                if (distance > MAX_DISTANCE_FROM_HOUSE) {
                    FamiliarsLib.LOGGER.info("Familiar {} too far from house ({}), forcing recall", familiarId, distance);
                    if (tryRecallFamiliar(familiar)) {
                        familiarsToRecall.add(familiarId);
                    }
                }
            }
        }

        outsideFamiliars.removeAll(familiarsToRecall);
    }

    protected abstract Direction getFacingDirection();

    private Vec3 findSafeReleasePosition() {
        BlockPos storagePos = getBlockPos();

        Direction facing = getFacingDirection();

        BlockPos frontPos = storagePos.relative(facing);
        if (isSafeSpawnPosition(frontPos)) {
            return Vec3.atBottomCenterOf(frontPos);
        }

        return null;
    }

    private boolean isSafeSpawnPosition(BlockPos pos) {
        return level.getBlockState(pos).isAir() &&
                level.getBlockState(pos.above()).isAir() &&
                level.getBlockState(pos.below()).isSolid();
    }

    private boolean isDaytime() {
        long time = level.getDayTime() % 24000;
        return time >= 1000 && time <= 13000;
    }

    // Method to manually recall a familiar (called when familiar wants to enter)
    public boolean tryRecallFamiliar(AbstractSpellCastingPet familiar) {
        if (!canStoreFamiliar()) {
            return false;
        }

        UUID familiarId = familiar.getUUID();

        // Solo recall si pertenece a esta casa
        if (outsideFamiliars.contains(familiarId) &&
                familiar.getIsInHouse() &&
                getBlockPos().equals(familiar.housePosition)) {

            // Create NBT data
            CompoundTag nbtData = FamiliarManager.createFamiliarNBT(familiar);

            // Store familiar
            FamiliarData familiarData = new FamiliarData(nbtData, 0);
            storedFamiliars.put(familiarId, familiarData);
            outsideFamiliars.remove(familiarId);

            // Remove from world
            familiar.remove(Entity.RemovalReason.DISCARDED);

            // Play sound and effects
            level.playSound(null, getBlockPos(), SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, getBlockPos(), GameEvent.Context.of(familiar, getBlockState()));

            setChanged();
            syncToClient();

            FamiliarsLib.LOGGER.info("Recalled familiar {} to storage", familiarId);
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

    public boolean storeFamiliar(UUID familiarId, CompoundTag familiarData, ServerPlayer player) {
        if (!isOwner(player)) {
            return false;
        }

        if (!canStoreFamiliar()) {
            return false;
        }

        return FamiliarManager.storeFamiliarInHouse(familiarId, familiarData, player, getBlockPos());
    }

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

                        FamiliarsLib.LOGGER.info("Returned familiar {} to owner {}", familiarId, owner.getName().getString());
                    }
                }

                // Remove outside familiars from world
                for (UUID familiarId : outsideFamiliars) {
                    Entity entity = serverLevel.getEntity(familiarId);
                    if (entity instanceof AbstractSpellCastingPet familiar) {
                        familiar.setIsInHouse(false, null);
                        familiar.remove(Entity.RemovalReason.DISCARDED);
                        FamiliarsLib.LOGGER.info("Removed outside familiar {} from world due to house destruction", familiarId);
                    }
                }

                FamiliarManager.syncFamiliarDataForPlayer(owner);
            }
        }

        storedFamiliars.clear();
        outsideFamiliars.clear();
    }

    public void setClientStoreMode(boolean storeMode) {
        if (level != null && level.isClientSide) {
            this.storeMode = storeMode;
            FamiliarsLib.LOGGER.info("Client updated storage mode to: {}", storeMode ? "Store" : "Wander");
        }
    }

    public boolean ownsFamiliar(UUID familiarId) {
        return storedFamiliars.containsKey(familiarId) || outsideFamiliars.contains(familiarId);
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

        tag.putInt("ticksSinceLastRelease", ticksSinceLastRelease);
        tag.putBoolean("storeMode", storeMode);

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

        ticksSinceLastRelease = tag.getInt("ticksSinceLastRelease");
        storeMode = tag.getBoolean("storeMode");

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
            return occupationTime >= MIN_OCCUPATION_TICKS;
        }
    }
}
