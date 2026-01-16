package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.CapabilityRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID)
public class PlayerLoginHandler {
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
                FamiliarsLib.LOGGER.debug("Player {} died, desummoning all familiars",
                        serverPlayer.getName().getString());

                ServerLevel level = serverPlayer.serverLevel();
                Map<UUID, CompoundTag> familiars = familiarData.getAllFamiliars();
                Set<UUID> familiarUUIDs = familiars.keySet();
                familiarData.clearAllSummoned();

                for (UUID familiarUUID : familiarUUIDs) {
                    Entity existingEntity = level.getEntity(familiarUUID);
                    boolean familiarExistsInWorld = existingEntity instanceof AbstractSpellCastingPet;

                    if (familiarExistsInWorld) {
                        if (existingEntity instanceof AbstractSpellCastingPet familiar) {

                            if (familiar.isCasting()) {
                                familiar.cancelCast();
                                familiar.getMagicData().resetCastingState();
                            }

                            familiar.setTarget(null);

                            familiar.setHasUsedSingleAttack(false);

                            CompoundTag updatedNBT = FamiliarManager.createFamiliarNBT(familiar);
                            familiarData.addTamedFamiliar(familiarUUID, updatedNBT);

                            familiar.remove(Entity.RemovalReason.DISCARDED);
                            level.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                                    SoundEvents.BEACON_DEACTIVATE,
                                    SoundSource.BLOCKS, 1.0F, 1.0F);
                            FamiliarsLib.LOGGER.debug("Familiar desummoned due to player death: {}", familiarUUID);
                        } else {
                            FamiliarsLib.LOGGER.debug("Familiar not found in world: {}", familiarUUID);
                        }
                    }
                }
                FamiliarManager.syncFamiliarDataForPlayer(serverPlayer);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
                FamiliarsLib.LOGGER.debug("Player {} changed dimension, syncing familiar and bed link data", serverPlayer.getName().getString());

                if(!serverPlayer.level().isClientSide){
                    ServerLevel originalLevel = serverPlayer.server.getLevel(event.getFrom());
                    Map<UUID, CompoundTag> familiars = familiarData.getAllFamiliars();
                    Set<UUID> familiarUUIDS = familiars.keySet();
                    familiarData.clearAllSummoned();

                    for(UUID familiarUUID : familiarUUIDS){
                        Entity existingEntity = originalLevel.getEntity(familiarUUID);
                        boolean familiarExistsInWorld = existingEntity instanceof AbstractSpellCastingPet;

                        if (familiarExistsInWorld) {
                            if (existingEntity instanceof AbstractSpellCastingPet familiar) {

                                if (familiar.isCasting()) {
                                    familiar.cancelCast();
                                    familiar.getMagicData().resetCastingState();
                                }

                                familiar.setTarget(null);

                                familiar.setHasUsedSingleAttack(false);

                                CompoundTag updatedNBT = FamiliarManager.createFamiliarNBT(familiar);
                                familiarData.addTamedFamiliar(familiarUUID, updatedNBT);

                                familiar.remove(Entity.RemovalReason.DISCARDED);
                                originalLevel.playSound(null, familiar.getX(), familiar.getY(), familiar.getZ(),
                                        SoundEvents.BEACON_DEACTIVATE,
                                        SoundSource.BLOCKS, 1.0F, 1.0F);
                                FamiliarsLib.LOGGER.debug("Specific familiar desummoned successfully: {}", familiarUUID);
                            } else {
                                FamiliarsLib.LOGGER.debug("Familiar not found in world: {}", familiarUUID);
                            }
                        }
                    }
                    FamiliarManager.syncFamiliarDataForPlayer(serverPlayer);
                }
            });
        }
    }
}
