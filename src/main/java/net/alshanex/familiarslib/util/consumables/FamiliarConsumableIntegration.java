package net.alshanex.familiarslib.util.consumables;

import net.alshanex.familiarslib.item.FamiliarConsumableItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem.ConsumableType;
import net.alshanex.familiarslib.FamiliarsLib;

/**
 * Integration helper for the consumable system with AbstractSpellCastingPet
 * This class handles the interaction logic and NBT integration
 */
public class FamiliarConsumableIntegration {

    // Cache for consumable data to avoid NBT operations during gameplay
    private static final java.util.Map<java.util.UUID, FamiliarConsumableSystem.ConsumableData> consumableDataCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Gets the consumable data for a familiar, using cache for performance
     */
    public static FamiliarConsumableSystem.ConsumableData getConsumableData(AbstractSpellCastingPet familiar) {
        return consumableDataCache.computeIfAbsent(familiar.getUUID(),
                uuid -> new FamiliarConsumableSystem.ConsumableData());
    }

    /**
     * Updates the cached consumable data
     */
    private static void updateConsumableData(AbstractSpellCastingPet familiar, FamiliarConsumableSystem.ConsumableData data) {
        consumableDataCache.put(familiar.getUUID(), data);
    }

    /**
     * Removes cached data when familiar is removed
     */
    public static void clearCachedData(AbstractSpellCastingPet familiar) {
        consumableDataCache.remove(familiar.getUUID());
    }

    /**
     * Handles the interaction when a player tries to feed a consumable to a familiar
     * Call this from your mobInteract method in AbstractSpellCastingPet
     */
    public static InteractionResult handleConsumableInteraction(AbstractSpellCastingPet familiar, Player player, ItemStack itemStack) {
        ConsumableItemInfo info = getConsumableInfo(itemStack);
        if (info == null) {
            return InteractionResult.PASS; // Not a consumable item
        }

        if (!familiar.level().isClientSide) {
            FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);

            // Check if the familiar can use this consumable
            int currentValue = data.getValue(info.type);
            int maxAllowed = info.type.getTierLimit(info.tier);

            if (currentValue >= maxAllowed) {
                // Already at maximum for this tier
                if(player instanceof ServerPlayer serverPlayer){
                    serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.familiarslib.consumable.max_reached", Component.translatable(getTypeTranslationKey(info.type))).withStyle(ChatFormatting.RED)));
                }
                return InteractionResult.FAIL;
            }

            // Check if this tier is appropriate for current progress
            int maxUsableTier = getMaxUsableTier(data, info.type);
            if (info.tier > maxUsableTier) {
                // This tier is too high for current progress
                if(player instanceof ServerPlayer serverPlayer){
                    serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.familiarslib.consumable.tier_too_high", info.tier, maxUsableTier).withStyle(ChatFormatting.RED)));
                }
                return InteractionResult.FAIL;
            }

            // Apply the consumable
            int bonus = info.type.getTierBonus(info.tier);
            int newValue = Math.min(currentValue + bonus, maxAllowed);
            data.setValue(info.type, newValue);

            // Update cache and apply modifiers
            updateConsumableData(familiar, data);
            FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);

            // Consume the item
            itemStack.shrink(1);

            // Show success message with current progress
            String unit = getUnitSuffix(info.type);
            if(player instanceof ServerPlayer serverPlayer){
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.consumable.success", Component.translatable(getTypeTranslationKey(info.type)), newValue + unit).withStyle(ChatFormatting.GREEN)));
            }

            // Trigger eating particles and sound
            spawnConsumableEffects(familiar);

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Gets the maximum tier that can still be used for a consumable type
     */
    private static int getMaxUsableTier(FamiliarConsumableSystem.ConsumableData data, ConsumableType type) {
        int currentValue = data.getValue(type);

        // Check which tier limits we haven't reached yet
        for (int tier = type.getMaxTier(); tier >= 1; tier--) {
            if (currentValue < type.getTierLimit(tier)) {
                return tier;
            }
        }
        return 0; // No tier can be used (at max)
    }

    /**
     * Gets consumable information from an item stack
     * Uses the FamiliarConsumableItem class to detect consumables
     */
    private static ConsumableItemInfo getConsumableInfo(ItemStack itemStack) {
        // Check if it's a FamiliarConsumableItem first
        if (itemStack.getItem() instanceof FamiliarConsumableItem consumableItem) {
            return new ConsumableItemInfo(consumableItem.getConsumableType(), consumableItem.getTier());
        }
        return null; // Not a consumable
    }

    private static String getTypeTranslationKey(ConsumableType type) {
        return switch (type) {
            case ARMOR -> "consumable.type.armor";
            case HEALTH -> "consumable.type.health";
            case SPELL_POWER -> "consumable.type.spell_power";
            case SPELL_RESIST -> "consumable.type.spell_resist";
            case SPELL_LEVEL -> "consumable.type.spell_level";
            case ENRAGED -> "consumable.type.enraged";
            case BLOCKING -> "consumable.type.blocking";
        };
    }

    private static String getUnitSuffix(ConsumableType type) {
        return switch (type) {
            case ARMOR -> " armor";
            case HEALTH -> "%";
            case SPELL_POWER -> "%";
            case SPELL_RESIST -> "%";
            case SPELL_LEVEL -> "";
            case ENRAGED -> " stacks";
            case BLOCKING -> "";
        };
    }

    private static void spawnConsumableEffects(AbstractSpellCastingPet familiar) {
        // Spawn eating particles
        // FamiliarHelper.spawnEatingParticles(familiar);
    }

    /**
     * Save consumable data to NBT - call this from addAdditionalSaveData
     */
    public static void saveConsumableData(AbstractSpellCastingPet familiar, CompoundTag compound) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        FamiliarConsumableSystem.saveConsumableDataToNBT(data, compound);
    }

    /**
     * Load consumable data from NBT - call this from readAdditionalSaveData
     */
    public static void loadConsumableData(AbstractSpellCastingPet familiar, CompoundTag compound) {
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableSystem.loadConsumableDataFromNBT(compound);
        updateConsumableData(familiar, data);

        // Apply the attribute modifiers
        if (!familiar.level().isClientSide) {
            FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);
        }
    }

    /**
     * Gets the effective spell level for spell casting, including consumable bonuses
     * Call this when determining spell levels for familiar casting
     */
    public static float getEffectiveSpellLevel(AbstractSpellCastingPet familiar, float baseSpellLevel) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        float bonus = data.getValue(ConsumableType.SPELL_LEVEL) / 10.0f; // Convert back from integer storage
        return Math.min(baseSpellLevel + bonus, 0.8f); // Cap at 0.8
    }

    /**
     * Remove all consumable modifiers - call this when familiar is dismissed/dies
     */
    public static void removeConsumableModifiers(AbstractSpellCastingPet familiar) {
        FamiliarConsumableSystem.removeAllConsumableModifiers(familiar);
        clearCachedData(familiar);
    }

    /**
     * Apply consumable modifiers - call this when familiar is summoned
     */
    public static void applyConsumableModifiers(AbstractSpellCastingPet familiar) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);
    }

    /**
     * Gets the current consumable bonus for a specific type
     */
    public static int getCurrentBonus(AbstractSpellCastingPet familiar, ConsumableType type) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        return data.getValue(type);
    }

    /**
     * Gets the maximum tier that can still be used for a consumable type
     */
    public static int getMaxUsableTier(AbstractSpellCastingPet familiar, ConsumableType type) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        return getMaxUsableTier(data, type);
    }

    /**
     * Migrates legacy health and armor stacks to the new consumable system
     */
    public static void migrateLegacyData(AbstractSpellCastingPet familiar, int legacyHealthStacks, int legacyArmorStacks, int legacyEnragedStacks, boolean legacyCanBlock) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);

        // Remove legacy modifiers first
        FamiliarConsumableSystem.removeLegacyModifiers(familiar);

        // Convert legacy health stacks to health percentage
        // Old system: 10% per stack, new system: percentage values
        if (legacyHealthStacks > 0) {
            int healthPercentage = legacyHealthStacks * 10; // 10% per stack
            // Clamp to max tier 3 limit (150%)
            healthPercentage = Math.min(healthPercentage, ConsumableType.HEALTH.getMaxLimit());
            data.setValue(ConsumableType.HEALTH, healthPercentage);
            FamiliarsLib.LOGGER.debug("Migrated {} legacy health stacks to {}% health bonus", legacyHealthStacks, healthPercentage);
        }

        // Convert legacy armor stacks to armor points
        // Old system: 1 armor per stack, new system: direct armor values
        if (legacyArmorStacks > 0) {
            int armorPoints = legacyArmorStacks;
            // Clamp to max tier 3 limit (20)
            armorPoints = Math.min(armorPoints, ConsumableType.ARMOR.getMaxLimit());
            data.setValue(ConsumableType.ARMOR, armorPoints);
            FamiliarsLib.LOGGER.debug("Migrated {} legacy armor stacks to {} armor points", legacyArmorStacks, armorPoints);
        }

        // Migrate enraged stacks directly
        if (legacyEnragedStacks > 0) {
            int enragedStacks = Math.min(legacyEnragedStacks, ConsumableType.ENRAGED.getMaxLimit());
            data.setValue(ConsumableType.ENRAGED, enragedStacks);
            FamiliarsLib.LOGGER.debug("Migrated {} legacy enraged stacks", enragedStacks);
        }

        // Migrate blocking ability
        if (legacyCanBlock) {
            data.setValue(ConsumableType.BLOCKING, 1);
            FamiliarsLib.LOGGER.debug("Migrated legacy blocking ability");
        }

        // Update cache and apply new modifiers
        updateConsumableData(familiar, data);
        FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);

        FamiliarsLib.LOGGER.debug("Completed legacy data migration for familiar {}", familiar.getUUID());
    }

    /**
     * Checks if the familiar can block (for game logic)
     */
    public static boolean canFamiliarBlock(AbstractSpellCastingPet familiar) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        return data.getValue(ConsumableType.BLOCKING) > 0;
    }

    /**
     * Gets the current enraged stacks for attack damage calculation
     */
    public static int getEnragedStacks(AbstractSpellCastingPet familiar) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        return data.getValue(ConsumableType.ENRAGED);
    }

    private static class ConsumableItemInfo {
        final ConsumableType type;
        final int tier;

        ConsumableItemInfo(ConsumableType type, int tier) {
            this.type = type;
            this.tier = tier;
        }
    }
}