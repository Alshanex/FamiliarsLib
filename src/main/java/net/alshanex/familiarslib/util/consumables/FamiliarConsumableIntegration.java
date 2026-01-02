package net.alshanex.familiarslib.util.consumables;

import net.alshanex.familiarslib.util.consumables.FamiliarConsumableComponent;
import net.alshanex.familiarslib.registry.ComponentRegistry;
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * Data component-based consumable system integration
 */
public class FamiliarConsumableIntegration {

    // Cache for performance, but NOT the source of truth
    private static final Map<UUID, FamiliarConsumableSystem.ConsumableData> consumableDataCache = new ConcurrentHashMap<>();

    // NBT key for storing consumable data directly in entity
    private static final String CONSUMABLE_NBT_KEY = "FamiliarConsumableData";

    /**
     * Gets the consumable data for a familiar, trying cache first, then entity NBT
     */
    public static FamiliarConsumableSystem.ConsumableData getConsumableData(AbstractSpellCastingPet familiar) {
        UUID familiarId = familiar.getUUID();

        // Try cache first
        FamiliarConsumableSystem.ConsumableData cachedData = consumableDataCache.get(familiarId);
        if (cachedData != null) {
            return cachedData;
        }

        // If not in cache, try to load from entity's persistent data
        FamiliarConsumableSystem.ConsumableData data = loadFromEntityNBT(familiar);

        // Cache it for performance
        consumableDataCache.put(familiarId, data);
        return data;
    }

    /**
     * Load consumable data directly from the entity's persistent NBT data
     */
    private static FamiliarConsumableSystem.ConsumableData loadFromEntityNBT(AbstractSpellCastingPet familiar) {
        // Check if the entity has stored consumable data
        if (familiar.getPersistentData().contains(CONSUMABLE_NBT_KEY)) {
            CompoundTag consumableNBT = familiar.getPersistentData().getCompound(CONSUMABLE_NBT_KEY);
            return FamiliarConsumableSystem.loadConsumableDataFromNBT(consumableNBT);
        }

        // Return empty data if none found
        return new FamiliarConsumableSystem.ConsumableData();
    }

    /**
     * Save consumable data to both cache and entity's persistent NBT
     */
    private static void saveToEntityNBT(AbstractSpellCastingPet familiar, FamiliarConsumableSystem.ConsumableData data) {
        UUID familiarId = familiar.getUUID();

        // Save to cache for performance
        consumableDataCache.put(familiarId, data);

        // Save to entity's persistent NBT for durability
        CompoundTag consumableNBT = new CompoundTag();
        FamiliarConsumableSystem.saveConsumableDataToNBT(data, consumableNBT);
        familiar.getPersistentData().put(CONSUMABLE_NBT_KEY, consumableNBT);

        FamiliarsLib.LOGGER.debug("Saved consumable data to entity NBT for familiar {}: {}",
                familiarId, data.toString());
    }

    /**
     * Updates the consumable data in both cache and entity NBT
     */
    private static void updateConsumableData(AbstractSpellCastingPet familiar, FamiliarConsumableSystem.ConsumableData data) {
        saveToEntityNBT(familiar, data);
    }

    /**
     * Gets consumable information from an item stack using data components
     */
    public static FamiliarConsumableComponent getConsumableComponent(ItemStack itemStack) {
        return itemStack.get(ComponentRegistry.FAMILIAR_CONSUMABLE.get());
    }

    /**
     * Checks if an item stack has the familiar consumable component
     */
    public static boolean isConsumableItem(ItemStack itemStack) {
        return itemStack.has(ComponentRegistry.FAMILIAR_CONSUMABLE.get());
    }

    /**
     * Creates a consumable item by adding the component to any item stack
     */
    public static ItemStack makeConsumableItem(ItemStack itemStack, ConsumableType type, int tier) {
        FamiliarConsumableComponent component = new FamiliarConsumableComponent(type, tier);
        itemStack.set(ComponentRegistry.FAMILIAR_CONSUMABLE.get(), component);
        return itemStack;
    }

    /**
     * Handles the interaction when a player tries to feed a consumable to a familiar
     */
    public static InteractionResult handleConsumableInteraction(AbstractSpellCastingPet familiar, Player player, ItemStack itemStack) {
        FamiliarConsumableComponent component = getConsumableComponent(itemStack);
        if (component == null) {
            return InteractionResult.PASS; // Not a consumable item
        }

        if (!familiar.level().isClientSide) {
            FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);

            // Check if the familiar can use this consumable
            int currentValue = data.getValue(component.type());
            int maxAllowed = component.getLimit();

            if (currentValue >= maxAllowed) {
                // Already at maximum for this tier
                if(player instanceof ServerPlayer serverPlayer){
                    serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.familiarslib.consumable.max_reached",
                                    Component.translatable(getTypeTranslationKey(component.type()))).withStyle(ChatFormatting.RED)));
                }
                return InteractionResult.FAIL;
            }

            // Check if this tier is appropriate for current progress
            int maxUsableTier = getMaxUsableTier(data, component.type());
            if (component.tier() > maxUsableTier) {
                // This tier is too high for current progress
                if(player instanceof ServerPlayer serverPlayer){
                    serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                            Component.translatable("message.familiarslib.consumable.tier_too_high",
                                    component.tier(), maxUsableTier).withStyle(ChatFormatting.RED)));
                }
                return InteractionResult.FAIL;
            }

            // Store current health before applying consumable
            float currentHealth = familiar.getHealth();
            boolean isHealthConsumable = component.type() == ConsumableType.HEALTH;

            // Apply the consumable
            int bonus = component.getBonus();
            int newValue = Math.min(currentValue + bonus, maxAllowed);
            data.setValue(component.type(), newValue);

            FamiliarsLib.LOGGER.debug("Applying consumable {} to familiar {}: {} -> {}",
                    component.type(), familiar.getUUID(), currentValue, newValue);

            // Update both cache and entity NBT
            updateConsumableData(familiar, data);
            FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);

            // Heal the familiar if it was a health consumable
            if (isHealthConsumable) {
                familiar.setHealth(familiar.getMaxHealth());
                FamiliarsLib.LOGGER.debug("Applied health consumable to familiar {}: healed to full health {}",
                        familiar.getUUID(), familiar.getMaxHealth());
            }

            // Consume the item
            itemStack.shrink(1);

            // Show success message with current progress
            String unit = getUnitSuffix(component.type());
            if(player instanceof ServerPlayer serverPlayer){
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(
                        Component.translatable("message.familiarslib.consumable.success",
                                Component.translatable(getTypeTranslationKey(component.type())),
                                newValue + unit).withStyle(ChatFormatting.GREEN)));
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

    private static String getTypeTranslationKey(ConsumableType type) {
        return switch (type) {
            case ARMOR -> "consumable.type.armor";
            case HEALTH -> "consumable.type.health";
            case SPELL_POWER -> "consumable.type.spell_power";
            case SPELL_RESIST -> "consumable.type.spell_resist";
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
            case ENRAGED -> " stacks";
            case BLOCKING -> "";
        };
    }

    private static void spawnConsumableEffects(AbstractSpellCastingPet familiar) {
        // Spawn eating particles
        // FamiliarHelper.spawnEatingParticles(familiar);
    }

    /**
     * Save consumable data to NBT - called during entity save
     */
    public static void saveConsumableData(AbstractSpellCastingPet familiar, CompoundTag compound) {
        UUID familiarId = familiar.getUUID();
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        FamiliarConsumableSystem.saveConsumableDataToNBT(data, compound);
        FamiliarsLib.LOGGER.debug("Saving consumable data to NBT for familiar {}: {}",
                familiarId, data.toString());
    }

    /**
     * Load consumable data from NBT and ensure it's stored in entity persistent data
     */
    public static void loadConsumableData(AbstractSpellCastingPet familiar, CompoundTag compound) {
        UUID familiarId = familiar.getUUID();
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableSystem.loadConsumableDataFromNBT(compound);
        saveToEntityNBT(familiar, data);
        FamiliarsLib.LOGGER.debug("Loading consumable data from NBT for familiar {}: {}",
                familiarId, data.toString());
    }

    /**
     * Remove all consumable modifiers and clear cache (but leave entity NBT)
     */
    public static void removeConsumableModifiers(AbstractSpellCastingPet familiar) {
        FamiliarConsumableSystem.removeAllConsumableModifiers(familiar);
        clearCachedData(familiar);
    }

    /**
     * Apply consumable modifiers from current data
     */
    public static void applyConsumableModifiers(AbstractSpellCastingPet familiar) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);
        FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);
        FamiliarsLib.LOGGER.debug("Applied consumable modifiers for familiar {}: {}",
                familiar.getUUID(), data.toString());
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
     * Removes cached data when familiar is removed (but NBT data persists)
     */
    public static void clearCachedData(AbstractSpellCastingPet familiar) {
        consumableDataCache.remove(familiar.getUUID());
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

    /**
     * Migrates legacy health and armor stacks to the new consumable system
     */
    public static void migrateLegacyData(AbstractSpellCastingPet familiar, int legacyHealthStacks, int legacyArmorStacks, int legacyEnragedStacks, boolean legacyCanBlock) {
        FamiliarConsumableSystem.ConsumableData data = getConsumableData(familiar);

        // Remove legacy modifiers first
        FamiliarConsumableSystem.removeLegacyModifiers(familiar);

        // Store current health before migration
        float currentHealth = familiar.getHealth();

        // Convert legacy data
        if (legacyHealthStacks > 0) {
            int healthPercentage = legacyHealthStacks * 10; // 10% per stack
            healthPercentage = Math.min(healthPercentage, ConsumableType.HEALTH.getMaxLimit());
            data.setValue(ConsumableType.HEALTH, healthPercentage);
            FamiliarsLib.LOGGER.debug("Migrated {} legacy health stacks to {}% health bonus", legacyHealthStacks, healthPercentage);
        }

        if (legacyArmorStacks > 0) {
            int armorPoints = Math.min(legacyArmorStacks, ConsumableType.ARMOR.getMaxLimit());
            data.setValue(ConsumableType.ARMOR, armorPoints);
            FamiliarsLib.LOGGER.debug("Migrated {} legacy armor stacks to {} armor points", legacyArmorStacks, armorPoints);
        }

        if (legacyEnragedStacks > 0) {
            int enragedStacks = Math.min(legacyEnragedStacks, ConsumableType.ENRAGED.getMaxLimit());
            data.setValue(ConsumableType.ENRAGED, enragedStacks);
            FamiliarsLib.LOGGER.debug("Migrated {} legacy enraged stacks", enragedStacks);
        }

        if (legacyCanBlock) {
            data.setValue(ConsumableType.BLOCKING, 1);
            FamiliarsLib.LOGGER.debug("Migrated legacy blocking ability");
        }

        // Update both cache and entity NBT
        updateConsumableData(familiar, data);
        FamiliarConsumableSystem.applyAttributeModifiers(familiar, data);

        // Restore the health after migration (clamp to new max health if needed)
        float newMaxHealth = familiar.getMaxHealth();
        float restoredHealth = Math.min(currentHealth, newMaxHealth);
        familiar.setHealth(restoredHealth);

        FamiliarsLib.LOGGER.debug("Completed legacy data migration for familiar {}: {} (health: {}/{})",
                familiar.getUUID(), data.toString(), familiar.getHealth(), familiar.getMaxHealth());
    }
}