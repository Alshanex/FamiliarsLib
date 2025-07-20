package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.util.Utils;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.BedLinkData;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.network.FamiliarDataPacket;
import net.alshanex.familiarslib.network.SyncBedLinkDataPacket;
import net.alshanex.familiarslib.network.SyncFamiliarDataPacket;
import net.alshanex.familiarslib.network.UpdateFamiliarStoragePacket;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.screen.BedLinkSelectionScreen;
import net.alshanex.familiarslib.screen.FamiliarStorageScreen;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
            FamiliarsLib.LOGGER.warn("Player {} tried to tame familiar but is at max capacity ({}/{})",
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

            FamiliarsLib.LOGGER.info("Player {} successfully tamed familiar {}. ({}/{})",
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

        // Debug log
        FamiliarsLib.LOGGER.info("Handling familiar summoning for player: {}", player.getName().getString());
        FamiliarsLib.LOGGER.info("Selected familiar ID: {}", selectedFamiliarId);

        if (selectedFamiliarId == null) {
            FamiliarsLib.LOGGER.info("No familiar selected, aborting summoning");
            return;
        }

        // Verificar si el familiar seleccionado ya está en el mundo
        ServerLevel level = player.serverLevel();
        Entity existingEntity = level.getEntity(selectedFamiliarId);
        boolean familiarExistsInWorld = existingEntity instanceof AbstractSpellCastingPet;

        FamiliarsLib.LOGGER.info("Familiar exists in world: {}", familiarExistsInWorld);

        if (familiarExistsInWorld) {
            // El familiar seleccionado ya está en el mundo, dessummonearlo
            FamiliarsLib.LOGGER.info("Dessummoning familiar: {}", selectedFamiliarId);
            desummonFamiliar(player, selectedFamiliarId);
        } else {
            // El familiar seleccionado no está en el mundo, summonearlo
            FamiliarsLib.LOGGER.info("Summoning familiar: {}", selectedFamiliarId);
            summonFamiliar(player, selectedFamiliarId);
        }
    }

    private static void summonFamiliar(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        CompoundTag familiarNBT = familiarData.getFamiliarData(familiarId);

        FamiliarsLib.LOGGER.info("Attempting to summon familiar: {}", familiarId);

        if (familiarNBT == null) {
            FamiliarsLib.LOGGER.warn("No NBT data found for familiar: {}", familiarId);
            return;
        }

        ServerLevel level = player.serverLevel();
        String entityTypeString = familiarNBT.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType == null) {
            FamiliarsLib.LOGGER.warn("Unknown entity type: {}", entityTypeString);
            return;
        }

        Entity entity = entityType.create(level);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.warn("Entity is not a familiar: {}", entity);
            return;
        }

        // Cargar NBT (esto aplicará atributos automáticamente)
        familiar.load(familiarNBT);
        familiar.setUUID(familiarId);

        // Verificar y ajustar vida
        float savedHealth = familiarNBT.getFloat("currentHealth");
        float actualMaxHealth = familiar.getMaxHealth();
        familiar.setHealth(Math.min(savedHealth, actualMaxHealth));

        Vec3 spawnPos = findSafeSpawnPosition(player, level);
        familiar.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
        familiar.setYRot(player.getYRot());
        familiar.setOldPosAndRot();

        level.addFreshEntity(familiar);
        FamiliarAttributesHelper.handleFamiliarSummoned(player, familiar);
        level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.0F);

        FamiliarsLib.LOGGER.info("Familiar summoned: {} with health {}/{}",
                familiarId, familiar.getHealth(), familiar.getMaxHealth());

        familiarData.setCurrentSummonedFamiliarId(familiarId);
        familiarData.addSummonedFamiliar(familiarId);
        syncFamiliarData(player, familiarData);
    }

    private static void desummonFamiliar(ServerPlayer player, UUID familiarId) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
        ServerLevel level = player.serverLevel();

        FamiliarsLib.LOGGER.info("Attempting to desummon familiar: {}", familiarId);

        Entity entity = level.getEntity(familiarId);
        if (entity instanceof AbstractSpellCastingPet familiar) {
            if (familiar.getSummoner() != null && familiar.getSummoner().is(player) &&
                    familiar.getUUID().equals(familiarId)) {

                CompoundTag updatedNBT = createFamiliarNBT(familiar);

                familiarData.addTamedFamiliar(familiarId, updatedNBT);

                familiar.setTarget(null);
                familiar.cancelCast();

                familiar.remove(Entity.RemovalReason.DISCARDED);
                FamiliarAttributesHelper.handleFamiliarDismissed(player, familiar);
                level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                        SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                FamiliarsLib.LOGGER.info("Familiar desummoned successfully: {}", familiarId);

                if (familiarId.equals(familiarData.getCurrentSummonedFamiliarId())) {
                    familiarData.setCurrentSummonedFamiliarId(null);
                }
                familiarData.removeSummonedFamiliar(familiarId);
            } else {
                FamiliarsLib.LOGGER.warn("Familiar found but doesn't belong to player: {}", familiarId);
            }
        } else {
            FamiliarsLib.LOGGER.warn("Familiar not found in world: {}", familiarId);
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

                //AlshanexFamiliarsMod.LOGGER.debug("Updated familiar data for living familiar {}", familiarId);
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error updating familiar data for {}: ", familiarId, e);
            }
        }
    }

    public static CompoundTag createFamiliarNBT(AbstractSpellCastingPet familiar) {
        CompoundTag nbt = new CompoundTag();
        familiar.saveWithoutId(nbt);

        float currentHealth = familiar.getHealth();
        float maxHealth = familiar.getMaxHealth();

        nbt.putFloat("currentHealth", currentHealth);
        nbt.putFloat("maxHealthDebug", maxHealth);

        String entityTypeId = EntityType.getKey(familiar.getType()).toString();
        nbt.putString("id", entityTypeId);

        nbt.putInt("armorStacks", familiar.getArmorStacks());
        nbt.putInt("enragedStacks", familiar.getEnragedStacks());
        nbt.putBoolean("canBlock", familiar.getIsBlocking());

        if (familiar.hasCustomName()) {
            nbt.putString("customName", familiar.getCustomName().getString());
        }

        FamiliarsLib.LOGGER.debug("Saving familiar {}: health stacks={}, current health={}/{}, armor stacks={}",
                familiar.getUUID(), familiar.getHealthStacks(), currentHealth, maxHealth, familiar.getArmorStacks());

        return nbt;
    }

    private static Vec3 findSafeSpawnPosition(ServerPlayer player, ServerLevel level) {
        float yrot = 6.281f + player.getYRot() * Mth.DEG_TO_RAD;
        Vec3 spawn = Utils.moveToRelativeGroundLevel(level,
                player.getEyePosition().add(new Vec3(3 * Mth.cos(yrot), 0, 3 * Mth.sin(yrot))), 10);
        return spawn;
    }

    private static void syncFamiliarData(ServerPlayer player, PlayerFamiliarData familiarData) {
        try {
            Map<UUID, CompoundTag> familiarsData = familiarData.getAllFamiliars();
            UUID selectedId = familiarData.getSelectedFamiliarId();
            UUID summonedId = familiarData.getCurrentSummonedFamiliarId();
            Set<UUID> summonedIds = familiarData.getSummonedFamiliarIds();

            FamiliarsLib.LOGGER.info("Syncing familiar data - Familiars: {}, Selected: {}, Summoned: {}, All Summoned: {}",
                    familiarsData.size(), selectedId, summonedId, summonedIds.size());

            PacketDistributor.sendToPlayer(player, new FamiliarDataPacket(familiarsData, selectedId, summonedId, summonedIds));

            // Send sync packets for both data types
            CompoundTag syncData = familiarData.serializeNBT(player.registryAccess());
            PacketDistributor.sendToPlayer(player, new SyncFamiliarDataPacket(syncData));

            // Also sync bed link data
            BedLinkData linkData = player.getData(AttachmentRegistry.BED_LINK_DATA);
            CompoundTag linkSyncData = linkData.serializeNBT(player.registryAccess());
            PacketDistributor.sendToPlayer(player, new SyncBedLinkDataPacket(linkSyncData));

            FamiliarsLib.LOGGER.info("All data synced to client successfully");

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error syncing familiar data: ", e);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleFamiliarDataPacket(Map<UUID, CompoundTag> familiars, UUID selectedFamiliarId, UUID currentSummonedFamiliarId, Set<UUID> summonedFamiliarIds){
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerFamiliarData data = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

            // Limpiar datos existentes completamente
            Map<UUID, CompoundTag> currentFamiliars = data.getAllFamiliars();
            for (UUID id : new HashSet<>(currentFamiliars.keySet())) {
                data.removeTamedFamiliar(id);
            }

            // Limpiar datos de summoneados
            data.clearAllSummoned();

            // Cargar nuevos datos
            for (Map.Entry<UUID, CompoundTag> entry : familiars.entrySet()) {
                data.addTamedFamiliar(entry.getKey(), entry.getValue());
            }

            // Establecer familiar seleccionado
            data.setSelectedFamiliarId(selectedFamiliarId);
            data.setCurrentSummonedFamiliarId(currentSummonedFamiliarId);

            if (summonedFamiliarIds != null) {
                for (UUID summonedId : summonedFamiliarIds) {
                    data.addSummonedFamiliar(summonedId);
                }
            }

            // Log para debugging
            FamiliarsLib.LOGGER.info("Client received familiar data - Count: {}, Selected: {}, Summoned: {}, All Summoned: {}",
                    familiars.size(), selectedFamiliarId, currentSummonedFamiliarId, summonedFamiliarIds != null ? summonedFamiliarIds.size() : 0);

            if (Minecraft.getInstance().screen instanceof BedLinkSelectionScreen bedLinkScreen) {
                bedLinkScreen.reloadFamiliarData();
            }
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

        FamiliarsLib.LOGGER.info("Attempting to summon specific familiar: {} at position {}", familiarId, positionIndex);

        if (familiarNBT == null) {
            FamiliarsLib.LOGGER.warn("No NBT data found for familiar: {}", familiarId);
            return;
        }

        if (familiarData.isFamiliarSummoned(familiarId)) {
            FamiliarsLib.LOGGER.info("Familiar {} is already summoned", familiarId);
            return;
        }

        String entityTypeString = familiarNBT.getString("id");
        EntityType<?> entityType = EntityType.byString(entityTypeString).orElse(null);

        if (entityType == null) {
            FamiliarsLib.LOGGER.warn("Unknown entity type: {}", entityTypeString);
            return;
        }

        ServerLevel level = player.serverLevel();
        Entity entity = entityType.create(level);
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.warn("Entity is not a familiar: {}", entity);
            return;
        }

        // Cargar NBT (esto aplicará atributos automáticamente)
        familiar.load(familiarNBT);
        familiar.setUUID(familiarId);

        // Verificar y ajustar vida
        float savedHealth = familiarNBT.getFloat("currentHealth");
        float actualMaxHealth = familiar.getMaxHealth();
        familiar.setHealth(Math.min(savedHealth, actualMaxHealth));

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

        FamiliarsLib.LOGGER.info("Specific familiar summoned: {} at position {} with health {}/{}",
                familiarId, spawnPos, familiar.getHealth(), familiar.getMaxHealth());
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

        // Convertir offset relativo a la rotación del jugador
        float yrot = player.getYRot() * Mth.DEG_TO_RAD;
        double cos = Math.cos(yrot);
        double sin = Math.sin(yrot);

        // Rotar el offset según la dirección del jugador
        double worldX = offset[0] * cos - offset[1] * sin;
        double worldZ = offset[0] * sin + offset[1] * cos;

        Vec3 playerPos = player.getEyePosition();
        Vec3 targetPos = playerPos.add(worldX, 0, worldZ);

        // Buscar posición segura en el suelo
        Vec3 spawnPos = Utils.moveToRelativeGroundLevel(level, targetPos, 10);

        // Si la posición no es segura, probar con un offset ligeramente diferente
        for (int attempt = 0; attempt < 3; attempt++) {
            if (isPositionSafe(level, spawnPos)) {
                return spawnPos;
            }

            // Intentar con un pequeño offset aleatorio
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

        FamiliarsLib.LOGGER.info("Attempting to desummon specific familiar: {}", familiarId);

        Entity entity = level.getEntity(familiarId);
        if (entity instanceof AbstractSpellCastingPet familiar) {
            if (familiar.getSummoner() != null && familiar.getSummoner().is(player) &&
                    familiar.getUUID().equals(familiarId)) {

                CompoundTag updatedNBT = createFamiliarNBT(familiar);
                familiarData.addTamedFamiliar(familiarId, updatedNBT);

                familiar.setTarget(null);
                familiar.cancelCast();

                familiar.remove(Entity.RemovalReason.DISCARDED);
                FamiliarAttributesHelper.handleFamiliarDismissed(player, familiar);
                level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                        SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                familiarData.removeSummonedFamiliar(familiarId);

                FamiliarsLib.LOGGER.info("Specific familiar desummoned successfully: {}", familiarId);
            } else {
                FamiliarsLib.LOGGER.warn("Familiar found but doesn't belong to player: {}", familiarId);
            }
        } else {
            FamiliarsLib.LOGGER.warn("Familiar not found in world: {}", familiarId);
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
    public static void openBedScreen(BlockPos bedPos){
        Minecraft.getInstance().setScreen(new BedLinkSelectionScreen(bedPos));
    }

    @OnlyIn(Dist.CLIENT)
    public static void openStorageScreen(BlockPos blockPos){
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity) {
                minecraft.setScreen(new FamiliarStorageScreen(blockPos));
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void syncBedData(CompoundTag bedLinkData){
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            BedLinkData data = player.getData(AttachmentRegistry.BED_LINK_DATA);
            data.deserializeNBT(player.registryAccess(), bedLinkData);
            FamiliarsLib.LOGGER.info("Client received bed link data sync");
        }
    }

    public static void linkFamiliarToBed(ServerPlayer serverPlayer, BlockPos bedPos, UUID familiarId){
        FamiliarsLib.LOGGER.info("Processing LinkFamiliarToBedPacket for player {} at pos {} with familiar {}",
                serverPlayer.getName().getString(), bedPos, familiarId);

        BlockEntity blockEntity = serverPlayer.level().getBlockEntity(bedPos);

        if (!(blockEntity instanceof AbstractFamiliarBedBlockEntity petBed)) {
            FamiliarsLib.LOGGER.warn("Block entity is not a PetBedBlockEntity at position {}", bedPos);
            return;
        }

        // Verify the player is the owner of the bed
        if (!petBed.isOwner(serverPlayer)) {
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.not_bed_owner").withStyle(ChatFormatting.RED)));
            FamiliarsLib.LOGGER.warn("Player {} is not the owner of bed at {}", serverPlayer.getName().getString(), bedPos);
            return;
        }

        BedLinkData linkData = serverPlayer.getData(AttachmentRegistry.BED_LINK_DATA);
        PlayerFamiliarData familiarData = serverPlayer.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        if (familiarId != null) {
            // Link familiar to bed
            if (!familiarData.hasFamiliar(familiarId)) {
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.familiar_not_found").withStyle(ChatFormatting.RED)));
                FamiliarsLib.LOGGER.warn("Familiar {} not found in player data", familiarId);
                return;
            }

            FamiliarsLib.LOGGER.info("Linking familiar {} to bed at {}", familiarId, bedPos);
            linkData.linkFamiliarToBed(familiarId, bedPos);

            // Get familiar name for message
            String familiarName = getFamiliarName(familiarData, familiarId);
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.familiar_linked_to_bed", familiarName).withStyle(ChatFormatting.GREEN)));

            FamiliarsLib.LOGGER.info("Successfully linked familiar {} to bed", familiarId);
        } else {
            // Unlink bed
            UUID previousLinked = linkData.getLinkedFamiliar(bedPos);
            if (previousLinked != null) {
                FamiliarsLib.LOGGER.info("Unlinking familiar {} from bed at {}", previousLinked, bedPos);
                linkData.unlinkBed(bedPos);

                String familiarName = getFamiliarName(familiarData, previousLinked);
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.familiar_unlinked_from_bed", familiarName).withStyle(ChatFormatting.GREEN)));

                FamiliarsLib.LOGGER.info("Successfully unlinked familiar {} from bed", previousLinked);
            } else {
                FamiliarsLib.LOGGER.info("No familiar was linked to bed at {}", bedPos);
            }
        }

        // Sync bed link data to client immediately
        PacketDistributor.sendToPlayer(serverPlayer, new SyncBedLinkDataPacket(linkData.serializeNBT(serverPlayer.registryAccess())));
        FamiliarsLib.LOGGER.info("Bed link data synced to client for player {}", serverPlayer.getName().getString());
    }

    private static String getFamiliarName(PlayerFamiliarData familiarData, UUID familiarId) {
        var familiarNBT = familiarData.getFamiliarData(familiarId);
        if (familiarNBT != null) {
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
        return "Unknown";
    }

    private static void updateSummonedFamiliarsData(ServerPlayer player) {
        try {
            PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            ServerLevel level = player.serverLevel();

            Set<UUID> actualSummonedFamiliars = new HashSet<>();
            int updatedCount = 0;

            // Recorrer todos los familiares del jugador
            for (Map.Entry<UUID, ?> entry : familiarData.getAllFamiliars().entrySet()) {
                UUID familiarId = entry.getKey();

                // Buscar el familiar en el mundo
                Entity entity = level.getEntity(familiarId);
                if (entity instanceof AbstractSpellCastingPet familiar) {
                    // Verificar que pertenece al jugador
                    if (familiar.getSummoner() != null && familiar.getSummoner().is(player)) {
                        // Actualizar sus datos
                        updateFamiliarData(familiar);
                        actualSummonedFamiliars.add(familiarId); // NUEVO: Añadir al set de summoned
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
                FamiliarsLib.LOGGER.info("Updated data for {} summoned familiars before opening screen", updatedCount);
            }

        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error updating summoned familiars data: ", e);
        }
    }

    public static void cleanupSummonedFamiliarsOnDimensionChange(ServerPlayer player) {
        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        familiarData.clearAllSummoned();

        FamiliarsLib.LOGGER.info("Cleared summoned familiars for player {} on dimension change",
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
            // Lógica de mover al storage...
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
                String familiarName = getFamiliarName(familiarNBT);
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.familiar_stored", familiarName).withStyle(ChatFormatting.GREEN)));

                syncFamiliarDataForPlayer(player);
            } else {
                player.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.storage_full").withStyle(ChatFormatting.RED)));
            }

        } else {
            // Lógica de mover al jugador...
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
        PacketDistributor.sendToPlayer(player, new UpdateFamiliarStoragePacket(blockPos, updatedStoredData, storageEntity.isStoreMode()));
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
    public static void handleStorageUpdate(BlockPos blockPos, Map<UUID, CompoundTag> storedFamiliars, boolean storeMode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            BlockEntity blockEntity = minecraft.level.getBlockEntity(blockPos);
            if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
                // Actualizar datos almacenados
                storageEntity.storedFamiliars.clear();
                for (Map.Entry<UUID, CompoundTag> entry : storedFamiliars.entrySet()) {
                    storageEntity.storedFamiliars.put(entry.getKey(),
                            new AbstractFamiliarStorageBlockEntity.FamiliarData(entry.getValue(), 0));
                }

                storageEntity.setClientStoreMode(storeMode);

                FamiliarsLib.LOGGER.info("Received storage update for position {} with {} stored familiars and mode: {}",
                        blockPos, storedFamiliars.size(), storeMode ? "Store" : "Wander");

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
        BedLinkData bedLinkData = player.getData(AttachmentRegistry.BED_LINK_DATA);

        // Add familiar to storage
        AbstractFamiliarStorageBlockEntity.FamiliarData data = new AbstractFamiliarStorageBlockEntity.FamiliarData(familiarData, 0);
        storageEntity.storedFamiliars.put(familiarId, data);

        // Remove from outside tracking if present
        storageEntity.outsideFamiliars.remove(familiarId);

        // Desummon if it's summoned
        if (player.level() instanceof ServerLevel serverLevel) {
            var entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                familiar.setIsInHouse(true, storagePos);
                desummonSpecificFamiliar(player, familiarId);
            }
        }

        playerData.removeTamedFamiliar(familiarId);
        bedLinkData.unlinkFamiliar(familiarId);

        storageEntity.setChanged();
        storageEntity.syncToClient();

        FamiliarsLib.LOGGER.info("Stored familiar {} in house at {}", familiarId, storagePos);
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

        // Cargar NBT y asegurar que los atributos estén aplicados
        CompoundTag nbtData = familiarData.nbtData.copy();
        nbtData.putBoolean("isInHouse", false);

        // IMPORTANTE: Al añadir de vuelta a player data, los atributos se aplicarán cuando se summonee
        playerData.addTamedFamiliar(familiarId, nbtData);

        if (playerData.getSelectedFamiliarId() == null) {
            playerData.setSelectedFamiliarId(familiarId);
        }

        // Remover del mundo si existe
        if (player.level() instanceof ServerLevel serverLevel) {
            Entity entity = serverLevel.getEntity(familiarId);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                familiar.setIsInHouse(false, null);
                familiar.remove(Entity.RemovalReason.DISCARDED);
                FamiliarsLib.LOGGER.info("Removed familiar {} from world as it was retrieved", familiarId);
            }
        }

        storageEntity.setChanged();
        storageEntity.syncToClient();

        FamiliarsLib.LOGGER.info("Retrieved familiar {} from house at {}", familiarId, storagePos);
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

        // Cambiar el modo
        storageEntity.setStoreMode(storeMode);

        Component modeText = storeMode ? Component.translatable("ui.familiarslib.familiar_storage_screen.store_mode") : Component.translatable("ui.familiarslib.familiar_storage_screen.wander_mode");
        Component modeDescription = storeMode ? Component.translatable("ui.familiarslib.familiar_storage_screen.store_mode_message") : Component.translatable("ui.familiarslib.familiar_storage_screen.wander_mode_message");

        player.connection.send(new ClientboundSetActionBarTextPacket(
                Component.literal(modeText + ": " + modeDescription).withStyle(
                        storeMode ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));

        Map<UUID, CompoundTag> storedData = storageEntity.getStoredFamiliars();
        PacketDistributor.sendToPlayer(player, new UpdateFamiliarStoragePacket(blockPos, storedData, storeMode));

        FamiliarsLib.LOGGER.info("Player {} set storage mode to {} at {}",
                player.getName().getString(), modeText, blockPos);
    }
}