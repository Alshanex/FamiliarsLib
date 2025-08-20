package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Helper methods for the AbstractSpellCastingPet class
 */
public class FamiliarHelper {

    public static void attemptLegacyMigration(ServerPlayer player, AbstractSpellCastingPet familiar) {
        try {
            PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
            UUID familiarId = familiar.getUUID();

            if (!familiarData.hasFamiliar(familiarId)) {
                FamiliarsLib.LOGGER.debug("Migrating legacy familiar {} for player {}",
                        familiarId, player.getName().getString());

                CompoundTag familiarNBT = createFamiliarNBT(familiar);

                if (familiarData.tryAddTamedFamiliar(familiarId, familiarNBT)) {
                    if (familiarData.getSelectedFamiliarId() == null) {
                        familiarData.setSelectedFamiliarId(familiarId);
                        FamiliarsLib.LOGGER.debug("Set migrated familiar {} as selected", familiarId);
                    }

                    familiarData.setCurrentSummonedFamiliarId(familiarId);

                    FamiliarManager.syncFamiliarDataForPlayer(player);

                    FamiliarsLib.LOGGER.debug("Successfully migrated legacy familiar {} to data attachment", familiarId);
                } else {
                    FamiliarsLib.LOGGER.debug("Failed to migrate legacy familiar {} - player {} at max capacity ({}/{})",
                            familiarId, player.getName().getString(),
                            familiarData.getFamiliarCount(), PlayerFamiliarData.MAX_FAMILIAR_LIMIT);
                }
            } else {
                if (!familiarId.equals(familiarData.getCurrentSummonedFamiliarId())) {
                    familiarData.setCurrentSummonedFamiliarId(familiarId);
                    FamiliarManager.syncFamiliarDataForPlayer(player);
                }
            }
        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error during legacy familiar migration for {}: ", familiar.getUUID(), e);
        }
    }

    private static CompoundTag createFamiliarNBT(AbstractSpellCastingPet familiar) {
        CompoundTag nbt = new CompoundTag();
        familiar.saveWithoutId(nbt);

        float currentHealth = familiar.getHealth();
        nbt.putFloat("currentHealth", currentHealth);
        nbt.putFloat("baseMaxHealth", familiar.getBaseMaxHealth());

        FamiliarsLib.LOGGER.debug("Creating NBT for familiar {}: current health = {}, max = {}, base = {}",
                familiar.getUUID(), currentHealth, familiar.getMaxHealth(), familiar.getBaseMaxHealth());

        String entityTypeId = EntityType.getKey(familiar.getType()).toString();
        nbt.putString("id", entityTypeId);

        if (familiar.hasCustomName()) {
            nbt.putString("customName", familiar.getCustomName().getString());
        }

        FamiliarConsumableIntegration.saveConsumableData(familiar, nbt);

        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(familiar);
        FamiliarsLib.LOGGER.debug("Saving familiar {}: current health={}, base max health={}, consumable data = {}",
                familiar.getUUID(), currentHealth, familiar.getBaseMaxHealth(), data.toString());

        return nbt;
    }

    public static void spawnTamingParticles(boolean tamed, AbstractSpellCastingPet familiar) {
        ParticleOptions particleoptions = ParticleTypes.HEART;
        if (!tamed) {
            particleoptions = ParticleTypes.SMOKE;
        }

        int count = 16;
        float radius = 1.25f;
        for (int i = 0; i < count; i++) {
            double x, z;
            double theta = Math.toRadians(360 / count) * i;
            x = Math.cos(theta) * radius;
            z = Math.sin(theta) * radius;
            MagicManager.spawnParticles(familiar.level(), particleoptions, familiar.position().x + x, familiar.position().y, familiar.position().z + z, 1, 0, 0, 0, 0.1, false);
        }
    }

    public static void spawnEatingParticles(AbstractSpellCastingPet familiar) {
        ParticleOptions particleoptions = ParticleTypes.HEART;

        int count = 3;
        float radius = .5f;
        for (int i = 0; i < count; i++) {
            double x, z;
            double theta = Math.toRadians(360 / count) * i;
            x = Math.cos(theta) * radius;
            z = Math.sin(theta) * radius;
            MagicManager.spawnParticles(familiar.level(), particleoptions, familiar.position().x + x, familiar.position().y, familiar.position().z + z, 1, 0, 0, 0, 0.1, false);
        }
    }

    public static boolean canDropItem(ServerLevel level, Entity entity, Item searchItem) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return false;
        }

        ResourceKey<LootTable> lootTableId = livingEntity.getLootTable();
        if (lootTableId == null) {
            return false;
        }

        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableId);

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);

        List<ItemStack> generatedItems = lootTable.getRandomItems(params);

        return generatedItems.stream().anyMatch(stack -> stack.getItem() == searchItem);
    }

    public static void handleHouseBehavior(AbstractSpellCastingPet familiar) {
        if (familiar.housePosition == null) return;

        if (!(familiar.level().getBlockEntity(familiar.housePosition) instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            familiar.setIsInHouse(false, null);
            familiar.remove(Entity.RemovalReason.DISCARDED);
            FamiliarsLib.LOGGER.debug("House destroyed, removing familiar {}", familiar.getUUID());
            return;
        }

        double distanceToHouse = familiar.position().distanceTo(Vec3.atCenterOf(familiar.housePosition));
        if (distanceToHouse > 35.0) {
            if (familiar.tickCount % 20 == 0) {
                FamiliarsLib.LOGGER.debug("Familiar {} too far from house, attempting to return", familiar.getUUID());
                storageEntity.tryRecallFamiliar(familiar);
            }
        }

        if (familiar.tickCount % 400 == 0 && familiar.level().random.nextFloat() < 0.1F) {
            storageEntity.tryRecallFamiliar(familiar);
        }

        if (familiar.getHealth() < familiar.getMaxHealth() * 0.3F) {
            storageEntity.tryRecallFamiliar(familiar);
        }

        if ((familiar.level().isNight() || familiar.level().isRaining()) && familiar.level().random.nextFloat() < 0.2F) {
            storageEntity.tryRecallFamiliar(familiar);
        }
    }

}
