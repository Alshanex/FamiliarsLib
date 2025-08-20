package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.util.Utils;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.*;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.screen.FamiliarStorageScreen;
import net.alshanex.familiarslib.screen.FamiliarWanderScreen;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Helper class for the entire familiars mod
 */
public class FamiliarManager {

    private static final Set<UUID> deadFamiliars = new HashSet<>();

    public static void markFamiliarAsDead(UUID familiarId) {
        deadFamiliars.add(familiarId);
        FamiliarsLib.LOGGER.debug("Marked familiar {} as dead", familiarId);
    }

    public static boolean isFamiliarDead(UUID familiarId) {
        return deadFamiliars.contains(familiarId);
    }

    public static void unmarkFamiliarAsDead(UUID familiarId) {
        deadFamiliars.remove(familiarId);
        FamiliarsLib.LOGGER.debug("Unmarked familiar {} as dead", familiarId);
    }

    public static boolean handleFamiliarTaming(AbstractSpellCastingPet familiar, ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        if (!familiarData.canTameMoreFamiliars()) {
            FamiliarsLib.LOGGER.debug("Player {} tried to tame familiar but is at max capacity ({}/{})",
                    player.getName().getString(),
                    familiarData.getFamiliarCount(),
                    PlayerFamiliarData.MAX_FAMILIAR_LIMIT);
            return false;
        }

        CompoundTag familiarNBT = createFamiliarNBT(familiar);
        UUID familiarId = familiar.getUUID();

        boolean success = familiarData.tryAddTamedFamiliar(familiarId, familiarNBT);

        if (success) {
            if (familiarData.getSelectedFamiliarId() == null) {
                familiarData.setSelectedFamiliarId(familiarId);
            }

            syncFamiliarData(player, familiarData);

            FamiliarsLib.LOGGER.debug("Player {} successfully tamed familiar {}. ({}/{})",
                    player.getName().getString(),
                    familiarId,
                    familiarData.getFamiliarCount(),
                    PlayerFamiliarData.MAX_FAMILIAR_LIMIT);

            return true;
        } else {
            FamiliarsLib.LOGGER.error("Failed to add familiar to player data despite limit check");
            return false;
        }
    }

    public static boolean canPlayerTameMoreFamiliars(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        return familiarData.canTameMoreFamiliars();
    }

    public static String getFamiliarCapacityInfo(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        return familiarData.getFamiliarCount() + "/" + PlayerFamiliarData.MAX_FAMILIAR_LIMIT;
    }

    public static void handleFamiliarSummoning(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        UUID selectedFamiliarId = familiarData.getSelectedFamiliarId();

        FamiliarsLib.LOGGER.debug("Handling familiar summoning for player: {}", player.getName().getString());
        FamiliarsLib.LOGGER.debug("Selected familiar ID: {}", selectedFamiliarId);

        if (selectedFamiliarId == null) {
            FamiliarsLib.LOGGER.debug("No familiar selected, aborting summoning");
            return;
        }

        ServerLevel level = player.serverLevel();
        Entity existingEntity = level.getEntity(selectedFamiliarId);
        boolean familiarExistsInWorld = existingEntity instanceof AbstractSpellCastingPet;

        FamiliarsLib.LOGGER.debug("Familiar exists in world: {}", familiarExistsInWorld);

        if (familiarExistsInWorld) {
            FamiliarsLib.LOGGER.debug("Dessummoning familiar: {}", selectedFamiliarId);
            desummonFamiliar(player, selectedFamiliarId);
        } else {
            FamiliarsLib.LOGGER.debug("Summoning familiar: {}", selectedFamiliarId);
            summonFamiliar(player, selectedFamiliarId);
        }
    }

    public static void summonFamiliar(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        CompoundTag familiarNBT = familiarData.getFamiliarData(familiarId);

        FamiliarsLib.LOGGER.debug("Attempting to summon familiar: {}", familiarId);

        if (familiarNBT == null) {
            FamiliarsLib.LOGGER.debug("No NBT data found for familiar: {}", familiarId);
            return;
        }

        ServerLevel level = player.serverLevel();
        String entityTypeString = familiarNBT.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType == null) {
            FamiliarsLib.LOGGER.debug("Unknown entity type: {}", entityTypeString);
            return;
        }

        Entity entity = entityType.create(level);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.debug("Entity is not a familiar: {}", entity);
            return;
        }

        familiar.load(familiarNBT);
        familiar.setUUID(familiarId);

        // Apply health correctly using the helper method
        applyHealthFromNBT(familiar, familiarNBT);

        Vec3 spawnPos = findSafeSpawnPosition(player, level);
        familiar.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        familiar.setYRot(player.getYRot());
        familiar.setOldPosAndRot();

        level.addFreshEntity(familiar);
        FamiliarAttributesHelper.handleFamiliarSummoned(player, familiar);
        level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.0F);


        familiarData.setCurrentSummonedFamiliarId(familiarId);
        familiarData.addSummonedFamiliar(familiarId);
        syncFamiliarData(player, familiarData);
    }

    public static void desummonFamiliar(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        ServerLevel level = player.serverLevel();

        FamiliarsLib.LOGGER.debug("Attempting to desummon familiar: {}", familiarId);

        Entity entity = level.getEntity(familiarId);
        if (entity instanceof AbstractSpellCastingPet familiar) {
            if (familiar.getSummoner() != null && familiar.getSummoner().is(player) &&
                    familiar.getUUID().equals(familiarId)) {

                if (familiar.isCasting()) {
                    familiar.cancelCast();
                    familiar.getMagicData().resetCastingState();
                }

                familiar.setTarget(null);

                familiar.setSitting(false);

                familiar.setHasUsedSingleAttack(false);

                CompoundTag updatedNBT = createFamiliarNBT(familiar);

                familiarData.addTamedFamiliar(familiarId, updatedNBT);

                familiar.remove(Entity.RemovalReason.DISCARDED);
                FamiliarAttributesHelper.handleFamiliarDismissed(player, familiar);
                level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                        SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                FamiliarsLib.LOGGER.debug("Familiar desummoned successfully: {}", familiarId);

                if (familiarId.equals(familiarData.getCurrentSummonedFamiliarId())) {
                    familiarData.setCurrentSummonedFamiliarId(null);
                }
                familiarData.removeSummonedFamiliar(familiarId);
            } else {
                FamiliarsLib.LOGGER.debug("Familiar found but doesn't belong to player: {}", familiarId);
            }
        } else {
            FamiliarsLib.LOGGER.debug("Familiar not found in world: {}", familiarId);
            if (familiarId.equals(familiarData.getCurrentSummonedFamiliarId())) {
                familiarData.setCurrentSummonedFamiliarId(null);
            }
            familiarData.removeSummonedFamiliar(familiarId);
        }

        syncFamiliarData(player, familiarData);
    }

    public static void updateFamiliarData(AbstractSpellCastingPet familiar) {
        if (familiar.getSummoner() instanceof ServerPlayer player) {
            PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            UUID familiarId = familiar.getUUID();

            if (isFamiliarDead(familiarId)) {
                FamiliarsLib.LOGGER.debug("Skipping update for marked dead familiar {}", familiarId);
                return;
            }

            if (familiar.getHealth() <= 0) {
                FamiliarsLib.LOGGER.debug("Skipping update for dead/dying familiar {}", familiarId);
                return;
            }

            if (!familiarData.hasFamiliar(familiarId)) {
                FamiliarsLib.LOGGER.debug("Skipping update for familiar {} - not in player data (probably dead)", familiarId);
                return;
            }

            try {
                CompoundTag updatedNBT = createFamiliarNBT(familiar);
                familiarData.addTamedFamiliar(familiarId, updatedNBT);
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error updating familiar data for {}: ", familiarId, e);
            }
        }
    }

    public static CompoundTag createFamiliarNBT(AbstractSpellCastingPet familiar) {
        CompoundTag nbt = new CompoundTag();
        familiar.saveWithoutId(nbt);

        // Simply save the current health
        float currentHealth = familiar.getHealth();
        nbt.putFloat("currentHealth", currentHealth);
        nbt.putFloat("baseMaxHealth", familiar.getBaseMaxHealth());

        String entityTypeId = EntityType.getKey(familiar.getType()).toString();
        nbt.putString("id", entityTypeId);

        if (familiar.hasCustomName()) {
            nbt.putString("customName", familiar.getCustomName().getString());
        }

        FamiliarConsumableIntegration.saveConsumableData(familiar, nbt);

        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(familiar);
        FamiliarsLib.LOGGER.debug("Saving familiar {}: current health={}, max health={}, base max health={}, consumable data = {}",
                familiar.getUUID(), currentHealth, familiar.getMaxHealth(), familiar.getBaseMaxHealth(), data.toString());

        return nbt;
    }

    private static void applyHealthFromNBT(AbstractSpellCastingPet familiar, CompoundTag familiarNBT) {
        float savedHealth = familiarNBT.getFloat("currentHealth");

        FamiliarsLib.LOGGER.debug("Loading health for familiar {}: saved health = {}",
                familiar.getUUID(), savedHealth);

        // Load consumable data (this stores the data but doesn't apply modifiers yet)
        FamiliarConsumableIntegration.loadConsumableData(familiar, familiarNBT);

        if (!familiar.level().isClientSide) {
            // Apply modifiers immediately for summoning
            FamiliarConsumableIntegration.applyConsumableModifiers(familiar);

            // Get the new max health and clamp the saved health to it
            float newMaxHealth = familiar.getMaxHealth();
            float restoredHealth = Math.min(savedHealth, newMaxHealth);

            FamiliarsLib.LOGGER.debug("Applying health for summoned familiar {}: saved={}, newMax={}, restored={}",
                    familiar.getUUID(), savedHealth, newMaxHealth, restoredHealth);

            familiar.setHealth(restoredHealth);
        }

        FamiliarsLib.LOGGER.debug("Applied health to familiar {}: {}/{} ({}%)",
                familiar.getUUID(), familiar.getHealth(), familiar.getMaxHealth(),
                (familiar.getHealth()/familiar.getMaxHealth()*100));
    }

    private static Vec3 findSafeSpawnPosition(ServerPlayer player, ServerLevel level) {
        float yrot = 6.281f + player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 spawn = Utils.moveToRelativeGroundLevel(level,
                player.getEyePosition().add(new Vec3(3 * Mth.cos(yrot), 0, 3 * Mth.sin(yrot))), 10);
        return spawn;
    }

    public static void syncFamiliarData(ServerPlayer player, PlayerFamiliarData familiarData) {
        try {
            Map<UUID, CompoundTag> familiarsData = familiarData.getAllFamiliars();
            UUID selectedId = familiarData.getSelectedFamiliarId();
            UUID summonedId = familiarData.getCurrentSummonedFamiliarId();
            Set<UUID> summonedIds = familiarData.getSummonedFamiliarIds();

            FamiliarsLib.LOGGER.debug("Syncing familiar data - Familiars: {}, Selected: {}, Summoned: {}, All Summoned: {}",
                    familiarsData.size(), selectedId, summonedId, summonedIds.size());

            PacketDistributor.sendToPlayer(player, new FamiliarDataPacket(familiarsData, selectedId, summonedId, summonedIds));

            CompoundTag syncData = familiarData.serializeNBT(player.registryAccess());
            PacketDistributor.sendToPlayer(player, new SyncFamiliarDataPacket(syncData));

            FamiliarsLib.LOGGER.debug("All data synced to client successfully");

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error syncing familiar data: ", e);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleFamiliarDataPacket(Map<UUID, CompoundTag> familiars, UUID selectedFamiliarId, UUID currentSummonedFamiliarId, Set<UUID> summonedFamiliarIds){
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerFamiliarData data = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

            Map<UUID, CompoundTag> currentFamiliars = data.getAllFamiliars();
            for (UUID id : new HashSet<>(currentFamiliars.keySet())) {
                data.removeTamedFamiliar(id);
            }

            data.clearAllSummoned();

            for (Map.Entry<UUID, CompoundTag> entry : familiars.entrySet()) {
                data.addTamedFamiliar(entry.getKey(), entry.getValue());
            }

            data.setSelectedFamiliarId(selectedFamiliarId);
            data.setCurrentSummonedFamiliarId(currentSummonedFamiliarId);

            if (summonedFamiliarIds != null) {
                for (UUID summonedId : summonedFamiliarIds) {
                    data.addSummonedFamiliar(summonedId);
                }
            }

            FamiliarsLib.LOGGER.debug("Client received familiar data - Count: {}, Selected: {}, Summoned: {}, All Summoned: {}",
                    familiars.size(), selectedFamiliarId, currentSummonedFamiliarId, summonedFamiliarIds != null ? summonedFamiliarIds.size() : 0);
        }
    }

    public static boolean hasSelectedFamiliar(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        return familiarData.getSelectedFamiliarId() != null;
    }

    public static boolean isFamiliarSummoned(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        return familiarData.getCurrentSummonedFamiliarId() != null;
    }

    public static void syncFamiliarDataForPlayer(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        syncFamiliarData(player, familiarData);
    }

    public static void handleFamiliarSelection(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        if (familiarData.hasFamiliar(familiarId)) {
            familiarData.setSelectedFamiliarId(familiarId);
            syncFamiliarData(player, familiarData);
        }
    }

    public static void summonSpecificFamiliarAtPosition(ServerPlayer player, UUID familiarId, int positionIndex) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        CompoundTag familiarNBT = familiarData.getFamiliarData(familiarId);

        FamiliarsLib.LOGGER.debug("Attempting to summon specific familiar: {} at position {}", familiarId, positionIndex);

        if (familiarNBT == null) {
            FamiliarsLib.LOGGER.debug("No NBT data found for familiar: {}", familiarId);
            return;
        }

        if (familiarData.isFamiliarSummoned(familiarId)) {
            FamiliarsLib.LOGGER.debug("Familiar {} is already summoned", familiarId);
            return;
        }

        String entityTypeString = familiarNBT.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType == null) {
            FamiliarsLib.LOGGER.debug("Unknown entity type: {}", entityTypeString);
            return;
        }

        ServerLevel level = player.serverLevel();
        Entity entity = entityType.create(level);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.debug("Entity is not a familiar: {}", entity);
            return;
        }

        familiar.load(familiarNBT);
        familiar.setUUID(familiarId);

        applyHealthFromNBT(familiar, familiarNBT);

        Vec3 spawnPos = findSafeSpawnPositionWithIndex(player, level, positionIndex);
        familiar.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        familiar.setYRot(player.getYRot());
        familiar.setOldPosAndRot();

        level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.0F);

        level.addFreshEntity(familiar);
        FamiliarAttributesHelper.handleFamiliarSummoned(player, familiar);

        familiarData.addSummonedFamiliar(familiarId);
        syncFamiliarData(player, familiarData);
    }

    private static Vec3 findSafeSpawnPositionWithIndex(ServerPlayer player, ServerLevel level, int positionIndex) {
        double[][] spawnOffsets = {
                {3.0, 0.0},
                {-3.0, 0.0},
                {0.0, 3.0},
                {0.0, -3.0},
                {2.1, 2.1},
                {-2.1, 2.1},
                {2.1, -2.1},
                {-2.1, -2.1},
                {4.0, 0.0},
                {0.0, 4.0}
        };

        int offsetIndex = positionIndex % spawnOffsets.length;
        double[] offset = spawnOffsets[offsetIndex];

        float yrot = player.getYRot() * Mth.DEG_TO_RAD;
        double cos = Math.cos(yrot);
        double sin = Math.sin(yrot);

        double worldX = offset[0] * cos - offset[1] * sin;
        double worldZ = offset[0] * sin + offset[1] * cos;

        Vec3 playerPos = player.getEyePosition();
        Vec3 targetPos = playerPos.add(worldX, 0, worldZ);

        Vec3 spawnPos = Utils.moveToRelativeGroundLevel(level, targetPos, 10);

        for (int attempt = 0; attempt < 3; attempt++) {
            if (isPositionSafe(level, spawnPos)) {
                return spawnPos;
            }

            double randomX = (level.random.nextDouble() - 0.5) * 2;
            double randomZ = (level.random.nextDouble() - 0.5) * 2;
            Vec3 alternativePos = targetPos.add(randomX, 0, randomZ);
            spawnPos = Utils.moveToRelativeGroundLevel(level, alternativePos, 10);
        }

        return findSafeSpawnPosition(player, level);
    }

    private static boolean isPositionSafe(ServerLevel level, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);

        boolean groundSolid = level.getBlockState(blockPos.below()).isSolid();
        boolean spaceEmpty = level.getBlockState(blockPos).isAir() && level.getBlockState(blockPos.above()).isAir();

        return groundSolid && spaceEmpty;
    }

    public static void desummonSpecificFamiliar(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        ServerLevel level = player.serverLevel();

        FamiliarsLib.LOGGER.debug("Attempting to desummon specific familiar: {}", familiarId);

        Entity entity = level.getEntity(familiarId);
        if (entity instanceof AbstractSpellCastingPet familiar) {
            if (familiar.getSummoner() != null && familiar.getSummoner().is(player) &&
                    familiar.getUUID().equals(familiarId)) {

                if (familiar.isCasting()) {
                    familiar.cancelCast();
                    familiar.getMagicData().resetCastingState();
                }

                familiar.setTarget(null);

                familiar.setSitting(false);

                familiar.setHasUsedSingleAttack(false);

                CompoundTag updatedNBT = createFamiliarNBT(familiar);
                familiarData.addTamedFamiliar(familiarId, updatedNBT);

                familiar.remove(Entity.RemovalReason.DISCARDED);
                FamiliarAttributesHelper.handleFamiliarDismissed(player, familiar);
                level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                        SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                familiarData.removeSummonedFamiliar(familiarId);

                FamiliarsLib.LOGGER.debug("Specific familiar desummoned successfully: {}", familiarId);
            } else {
                FamiliarsLib.LOGGER.debug("Familiar found but doesn't belong to player: {}", familiarId);
            }
        } else {
            FamiliarsLib.LOGGER.debug("Familiar not found in world: {}", familiarId);
            familiarData.removeSummonedFamiliar(familiarId);
        }

        syncFamiliarData(player, familiarData);
    }

    @OnlyIn(Dist.CLIENT)
    public static void syncFamiliarData(CompoundTag familiarData){
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerFamiliarData data = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            data.deserializeNBT(player.registryAccess(), familiarData);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void openStorageScreen(BlockPos blockPos){
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                if (storageEntity.isStoreMode()) {
                    minecraft.setScreen(new FamiliarStorageScreen(blockPos));
                } else {
                    minecraft.setScreen(new FamiliarWanderScreen(blockPos));
                }
            }
        }
    }

    public static void updateSummonedFamiliarsData(ServerPlayer player) {
        try {
            PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            ServerLevel level = player.serverLevel();

            Set<UUID> actualSummonedFamiliars = new HashSet<>();
            int updatedCount = 0;

            for (Map.Entry<UUID, ?> entry : familiarData.getAllFamiliars().entrySet()) {
                UUID familiarId = entry.getKey();

                Entity entity = level.getEntity(familiarId);
                if (entity instanceof AbstractSpellCastingPet familiar) {
                    if (familiar.getSummoner() != null && familiar.getSummoner().is(player)) {
                        updateFamiliarData(familiar);
                        actualSummonedFamiliars.add(familiarId);
                        updatedCount++;
                        FamiliarsLib.LOGGER.debug("Updated data for summoned familiar {}", familiarId);
                    }
                }
            }

            familiarData.getSummonedFamiliarIds().clear();
            for (UUID summonedId : actualSummonedFamiliars) {
                familiarData.addSummonedFamiliar(summonedId);
            }

            if (updatedCount > 0) {
                FamiliarsLib.LOGGER.debug("Updated data for {} summoned familiars before opening screen", updatedCount);
            }

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error updating summoned familiars data: ", e);
        }
    }

    public static void cleanupSummonedFamiliarsOnDimensionChange(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        familiarData.clearAllSummoned();

        FamiliarsLib.LOGGER.debug("Cleared summoned familiars for player {} on dimension change",
                player.getName().getString());
    }

    public static Set<UUID> getSummonedFamiliarIds(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        return familiarData.getSummonedFamiliarIds();
    }

    public static boolean isFamiliarSummoned(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        return familiarData.isFamiliarSummoned(familiarId);
    }

    public static void handleMoveFamiliar(ServerPlayer player, BlockPos blockPos, UUID familiarId, boolean toStorage) {
        BlockEntity blockEntity = player.level().getBlockEntity(blockPos);
        if (!(blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            return;
        }

        if (!storageEntity.isOwner(player)) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("message.familiarslib.not_storage_owner").withStyle(ChatFormatting.RED)));
            return;
        }

        if (!storageEntity.isStoreMode()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("message.familiarslib.only_store_mode").withStyle(ChatFormatting.RED)));
            return;
        }

        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        if (toStorage) {
            // Player to storage
            if (!familiarData.hasFamiliar(familiarId)) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.familiar_not_found").withStyle(ChatFormatting.RED)));
                return;
            }

            CompoundTag familiarNBT = familiarData.getFamiliarData(familiarId);
            if (familiarNBT == null) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.familiar_data_error").withStyle(ChatFormatting.RED)));
                return;
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                Entity entity = serverLevel.getEntity(familiarId);
                if (entity instanceof AbstractSpellCastingPet familiar) {
                    desummonSpecificFamiliar(player, familiarId);
                }
            }

            boolean success = storageEntity.storeFamiliar(familiarId, familiarNBT, player);
            if (success) {
                if(FamiliarsLib.isModLoaded("alshanex_familiars")){
                    try {
                        Class<?> clazz = Class.forName("net.alshanex.alshanex_familiars.util.familiars.AlshanexFamiliarsManager");
                        Method method = clazz.getMethod("cleanFamiliarFromPandoraBox", ServerPlayer.class, UUID.class);
                        method.invoke(null, player, familiarId);
                    } catch (Exception e) {
                        FamiliarsLib.LOGGER.error("Error calling cleanFamiliarFromPandoraBox: ", e);
                    }
                }
                String familiarName = getFamiliarName(familiarNBT);
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.familiar_stored", familiarName).withStyle(ChatFormatting.GREEN)));

                syncFamiliarDataForPlayer(player);
            } else {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.storage_full").withStyle(ChatFormatting.RED)));
            }

        } else {
            // Storage to player
            Map<UUID, CompoundTag> storedFamiliars = storageEntity.getStoredFamiliars();

            if (!storedFamiliars.containsKey(familiarId)) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.familiar_not_in_storage").withStyle(ChatFormatting.RED)));
                return;
            }

            if (!familiarData.canTameMoreFamiliars()) {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.player_familiar_limit").withStyle(ChatFormatting.RED)));
                return;
            }

            CompoundTag familiarNBT = storedFamiliars.get(familiarId);
            boolean success = storageEntity.retrieveFamiliar(familiarId, player);
            if (success) {
                String familiarName = getFamiliarName(familiarNBT);
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.familiar_retrieved", familiarName).withStyle(ChatFormatting.GREEN)));

                syncFamiliarDataForPlayer(player);
            } else {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.retrieval_failed").withStyle(ChatFormatting.RED)));
            }
        }

        Map<UUID, CompoundTag> updatedStoredData = storageEntity.getStoredFamiliars();
        PacketDistributor.sendToPlayer(player, new UpdateFamiliarStoragePacket(
                blockPos,
                updatedStoredData,
                storageEntity.isStoreMode(),
                storageEntity.canFamiliarsUseGoals(),
                storageEntity.getMaxDistance()
        ));
    }

    private static String getFamiliarName(CompoundTag familiarNBT) {
        if (familiarNBT.contains("customName")) {
            return familiarNBT.getString("customName");
        }

        String entityTypeString = familiarNBT.getString("id");
        String[] parts = entityTypeString.split(":");
        if (parts.length > 1) {
            return parts[1].replace("_", " ");
        }
        return entityTypeString.substring(entityTypeString.lastIndexOf(':') + 1);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleStorageUpdate(BlockPos blockPos, Map<UUID, CompoundTag> storedFamiliars, boolean storeMode, boolean canFamiliarsUseGoals, int maxDistance) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                storageEntity.storedFamiliars.clear();
                for (Map.Entry<UUID, CompoundTag> entry : storedFamiliars.entrySet()) {
                    storageEntity.storedFamiliars.put(entry.getKey(),
                            new AbstractFamiliarStorageBlockEntity.FamiliarData(entry.getValue(), 0));
                }

                storageEntity.setClientStoreMode(storeMode);
                storageEntity.setClientCanFamiliarsUseGoals(canFamiliarsUseGoals);
                storageEntity.setClientMaxDistance(maxDistance);

                FamiliarsLib.LOGGER.debug("Received storage update for position {} with {} stored familiars, mode: {}, goals: {}, distance: {}",
                        blockPos, storedFamiliars.size(), storeMode ? "Store" : "Wander", canFamiliarsUseGoals, maxDistance);

                if (minecraft.screen instanceof FamiliarStorageScreen storageScreen) {
                    storageScreen.reloadFamiliarData();
                }
            }
        }
    }

    public static boolean storeFamiliarInHouse(UUID familiarId, CompoundTag familiarData, ServerPlayer player, BlockPos storagePos) {
        if (!(player.level().getBlockEntity(storagePos) instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            return false;
        }

        if (!storageEntity.isOwner(player)) {
            return false;
        }

        PlayerFamiliarData playerData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        AbstractFamiliarStorageBlockEntity.FamiliarData data = new AbstractFamiliarStorageBlockEntity.FamiliarData(familiarData, 0);
        storageEntity.storedFamiliars.put(familiarId, data);

        storageEntity.outsideFamiliars.remove(familiarId);

        if (player.level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                familiar.setIsInHouse(true, storagePos);
                desummonSpecificFamiliar(player, familiarId);
            }
        }

        playerData.removeTamedFamiliar(familiarId);

        storageEntity.setChanged();
        storageEntity.syncToClient();

        FamiliarsLib.LOGGER.debug("Stored familiar {} in house at {}", familiarId, storagePos);
        return true;
    }

    public static boolean retrieveFamiliarFromHouse(UUID familiarId, ServerPlayer player, BlockPos storagePos) {
        if (!(player.level().getBlockEntity(storagePos) instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            return false;
        }

        if (!storageEntity.isOwner(player)) {
            return false;
        }

        PlayerFamiliarData playerData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        if (!playerData.canTameMoreFamiliars()) {
            return false;
        }

        AbstractFamiliarStorageBlockEntity.FamiliarData familiarData = storageEntity.storedFamiliars.remove(familiarId);
        storageEntity.outsideFamiliars.remove(familiarId);

        CompoundTag nbtData = familiarData.nbtData.copy();
        nbtData.putBoolean("isInHouse", false);

        playerData.addTamedFamiliar(familiarId, nbtData);

        if (playerData.getSelectedFamiliarId() == null) {
            playerData.setSelectedFamiliarId(familiarId);
        }

        if (player.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                familiar.setIsInHouse(false, null);
                familiar.remove(Entity.RemovalReason.DISCARDED);
                FamiliarsLib.LOGGER.debug("Removed familiar {} from world as it was retrieved", familiarId);
            }
        }

        storageEntity.setChanged();
        storageEntity.syncToClient();

        FamiliarsLib.LOGGER.debug("Retrieved familiar {} from house at {}", familiarId, storagePos);
        return true;
    }

    public static void handleSetStorageMode(ServerPlayer player, BlockPos blockPos, boolean storeMode) {
        BlockEntity blockEntity = player.level().getBlockEntity(blockPos);
        if (!(blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            return;
        }

        if (!storageEntity.isOwner(player)) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("message.familiarslib.not_storage_owner").withStyle(ChatFormatting.RED)));
            return;
        }

        storageEntity.setStoreMode(storeMode);

        String modeDescription = storeMode ?
                "ui.familiarslib.familiar_storage_screen.store_mode_message" :
                "ui.familiarslib.familiar_storage_screen.wander_mode_message";

        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.translatable(modeDescription).withStyle(
                        storeMode ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));

        Map<UUID, CompoundTag> storedData = storageEntity.getStoredFamiliars();
        PacketDistributor.sendToPlayer(player, new UpdateFamiliarStoragePacket(blockPos, storedData,
                storeMode, storageEntity.canFamiliarsUseGoals(), storageEntity.getMaxDistance()));
    }

    public static void handleUpdateStorageSettings(ServerPlayer player, BlockPos blockPos, boolean canFamiliarsUseGoals, int maxDistance) {
        BlockEntity blockEntity = player.level().getBlockEntity(blockPos);
        if (!(blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            return;
        }

        if (!storageEntity.isOwner(player)) {
            player.connection.send(new ClientboundSetActionBarTextPacket(
                    Component.translatable("message.familiarslib.not_storage_owner").withStyle(ChatFormatting.RED)));
            return;
        }

        storageEntity.setCanFamiliarsUseGoals(canFamiliarsUseGoals);
        storageEntity.setMaxDistance(maxDistance);

        FamiliarsLib.LOGGER.debug("Updated storage settings for player {}: goals={}, distance={}",
                player.getName().getString(), canFamiliarsUseGoals, maxDistance);

        // Sync to client
        Map<UUID, CompoundTag> storedData = storageEntity.getStoredFamiliars();
        PacketDistributor.sendToPlayer(player, new UpdateFamiliarStoragePacket(blockPos, storedData,
                storageEntity.isStoreMode(), canFamiliarsUseGoals, maxDistance));
    }
}