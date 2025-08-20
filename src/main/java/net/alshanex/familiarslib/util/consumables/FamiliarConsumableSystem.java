package net.alshanex.familiarslib.util.consumables;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.ResourceLocation;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;

/**
 * Handles consumable items for familiars that boost their attributes
 * Uses bit-packed storage for efficient NBT usage
 */
public class FamiliarConsumableSystem {

    // ResourceLocation IDs for each stat modifier
    private static final ResourceLocation CONSUMABLE_ARMOR_ID = ResourceLocation.fromNamespaceAndPath("familiarslib", "consumable_armor");
    private static final ResourceLocation CONSUMABLE_HEALTH_ID = ResourceLocation.fromNamespaceAndPath("familiarslib", "consumable_health");
    private static final ResourceLocation CONSUMABLE_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath("familiarslib", "consumable_spell_power");
    private static final ResourceLocation CONSUMABLE_SPELL_RESIST_ID = ResourceLocation.fromNamespaceAndPath("familiarslib", "consumable_spell_resist");
    private static final ResourceLocation CONSUMABLE_ENRAGED_ID = ResourceLocation.fromNamespaceAndPath("familiarslib", "consumable_enraged");
    private static final ResourceLocation CONSUMABLE_BLOCKING_ID = ResourceLocation.fromNamespaceAndPath("familiarslib", "consumable_blocking");

    public enum ConsumableType implements StringRepresentable {
        ARMOR(0, new int[]{1, 2, 3}, new int[]{10, 15, 20}, "armor"),
        HEALTH(1, new int[]{5, 10, 15}, new int[]{50, 100, 150}, "health"),
        SPELL_POWER(2, new int[]{1, 3, 5}, new int[]{15, 30, 45}, "spell_power"),
        SPELL_RESIST(3, new int[]{1, 3, 5}, new int[]{15, 30, 45}, "spell_resist"),
        SPELL_LEVEL(4, new int[]{1, 1, 2}, new int[]{3, 5, 7}, "spell_level"), // Values * 10 for integer storage
        ENRAGED(5, new int[]{1}, new int[]{10}, "enraged"), // Only tier 1, +1 per use, max 10
        BLOCKING(6, new int[]{1}, new int[]{1}, "blocking"); // Only tier 1, boolean toggle

        public static final Codec<ConsumableType> CODEC = StringRepresentable.fromEnum(ConsumableType::values);
        public static final StreamCodec<RegistryFriendlyByteBuf, ConsumableType> STREAM_CODEC =
                ByteBufCodecs.idMapper(
                        id -> {
                            for (ConsumableType type : values()) {
                                if (type.getId() == id) return type;
                            }
                            return ARMOR; // fallback
                        },
                        ConsumableType::getId
                ).cast();

        private final int id;
        private final int[] tierBonuses;
        private final int[] tierLimits;
        private final String serializedName;

        ConsumableType(int id, int[] tierBonuses, int[] tierLimits, String serializedName) {
            this.id = id;
            this.tierBonuses = tierBonuses;
            this.tierLimits = tierLimits;
            this.serializedName = serializedName;
        }

        public int getId() { return id; }
        public int getTierBonus(int tier) { return tierBonuses[tier - 1]; }
        public int getTierLimit(int tier) { return tierLimits[tier - 1]; }
        public int getMaxLimit() { return tierLimits[tierLimits.length - 1]; }
        public int getMaxTier() { return tierBonuses.length; }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    /**
     * Compact storage class
     */
    public static class ConsumableData {
        private byte armor;      // 0-20 armor points
        private byte health;     // 0-150 health percentage
        private byte spellPower; // 0-45 spell power percentage
        private byte spellResist;// 0-45 spell resist percentage
        private byte spellLevel; // 0-8 spell level * 10 (0.0-0.8)
        private byte enraged;    // 0-10 enraged stacks
        private byte blocking;   // 0-1 blocking ability

        public ConsumableData() {}

        public ConsumableData(CompoundTag tag) {
            loadFromNBT(tag);
        }

        public void saveToNBT(CompoundTag tag) {
            // Use individual keys for better debugging and compatibility
            tag.putByte("consumableArmor", armor);
            tag.putByte("consumableHealth", health);
            tag.putByte("consumableSpellPower", spellPower);
            tag.putByte("consumableSpellResist", spellResist);
            tag.putByte("consumableSpellLevel", spellLevel);
            tag.putByte("consumableEnraged", enraged);
            tag.putByte("consumableBlocking", blocking);
        }

        public void loadFromNBT(CompoundTag tag) {
            if (tag.contains("consumableArmor")) {
                armor = tag.getByte("consumableArmor");
                health = tag.getByte("consumableHealth");
                spellPower = tag.getByte("consumableSpellPower");
                spellResist = tag.getByte("consumableSpellResist");
                spellLevel = tag.getByte("consumableSpellLevel");
                enraged = tag.getByte("consumableEnraged");
                blocking = tag.getByte("consumableBlocking");
            }
        }

        public int getValue(ConsumableType type) {
            return switch (type) {
                case ARMOR -> armor & 0xFF;
                case HEALTH -> health & 0xFF;
                case SPELL_POWER -> spellPower & 0xFF;
                case SPELL_RESIST -> spellResist & 0xFF;
                case SPELL_LEVEL -> spellLevel & 0xFF;
                case ENRAGED -> enraged & 0xFF;
                case BLOCKING -> blocking & 0xFF;
            };
        }

        public void setValue(ConsumableType type, int value) {
            value = Math.max(0, Math.min(255, value)); // Clamp to byte range
            switch (type) {
                case ARMOR -> armor = (byte)value;
                case HEALTH -> health = (byte)value;
                case SPELL_POWER -> spellPower = (byte)value;
                case SPELL_RESIST -> spellResist = (byte)value;
                case SPELL_LEVEL -> spellLevel = (byte)value;
                case ENRAGED -> enraged = (byte)value;
                case BLOCKING -> blocking = (byte)value;
            }
        }

        // Add debugging method
        public boolean hasAnyData() {
            return armor != 0 || health != 0 || spellPower != 0 || spellResist != 0 ||
                    spellLevel != 0 || enraged != 0 || blocking != 0;
        }

        @Override
        public String toString() {
            return String.format("ConsumableData{armor=%d, health=%d, spellPower=%d, spellResist=%d, spellLevel=%d, enraged=%d, blocking=%d}",
                    armor & 0xFF, health & 0xFF, spellPower & 0xFF, spellResist & 0xFF, spellLevel & 0xFF, enraged & 0xFF, blocking & 0xFF);
        }
    }

    /**
     * Saves the consumable data to NBT
     */
    public static void saveConsumableDataToNBT(ConsumableData data, CompoundTag tag) {
        data.saveToNBT(tag);
    }

    /**
     * Loads the consumable data from NBT
     */
    public static ConsumableData loadConsumableDataFromNBT(CompoundTag tag) {
        return new ConsumableData(tag);
    }

    /**
     * Applies all attribute modifiers based on consumable data
     */
    public static void applyAttributeModifiers(AbstractSpellCastingPet familiar, ConsumableData data) {
        // Remove existing modifiers first
        removeAllConsumableModifiers(familiar);

        // Apply armor bonus
        int armorBonus = data.getValue(ConsumableType.ARMOR);
        if (armorBonus > 0) {
            AttributeInstance armorAttribute = familiar.getAttribute(Attributes.ARMOR);
            if (armorAttribute != null) {
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_ARMOR_ID,
                        armorBonus,
                        AttributeModifier.Operation.ADD_VALUE);
                armorAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply health bonus (percentage)
        int healthBonus = data.getValue(ConsumableType.HEALTH);
        if (healthBonus > 0) {
            AttributeInstance healthAttribute = familiar.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttribute != null) {
                double multiplier = healthBonus / 100.0; // Convert percentage to multiplier
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_HEALTH_ID,
                        multiplier,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                healthAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply spell power bonus (percentage)
        int spellPowerBonus = data.getValue(ConsumableType.SPELL_POWER);
        if (spellPowerBonus > 0) {
            AttributeInstance spellPowerAttribute = familiar.getAttribute(AttributeRegistry.SPELL_POWER);
            if (spellPowerAttribute != null) {
                double multiplier = spellPowerBonus / 100.0;
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_SPELL_POWER_ID,
                        multiplier,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                spellPowerAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply spell resist bonus (percentage)
        int spellResistBonus = data.getValue(ConsumableType.SPELL_RESIST);
        if (spellResistBonus > 0) {
            AttributeInstance spellResistAttribute = familiar.getAttribute(AttributeRegistry.SPELL_RESIST);
            if (spellResistAttribute != null) {
                double multiplier = spellResistBonus / 100.0;
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_SPELL_RESIST_ID,
                        multiplier,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                spellResistAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply enraged bonus (attack damage)
        int enragedStacks = data.getValue(ConsumableType.ENRAGED);
        if (enragedStacks > 0) {
            AttributeInstance attackDamageAttribute = familiar.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamageAttribute != null) {
                double bonus = enragedStacks * 0.5; // +0.5 attack damage per stack
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_ENRAGED_ID,
                        bonus,
                        AttributeModifier.Operation.ADD_VALUE);
                attackDamageAttribute.addPermanentModifier(modifier);
            }
        }

        // Blocking is handled separately as it's not an attribute
        // Spell level is handled separately as it's not an attribute
    }

    /**
     * Removes all consumable-related attribute modifiers
     */
    public static void removeAllConsumableModifiers(AbstractSpellCastingPet familiar) {
        // Remove armor modifier
        AttributeInstance armor = familiar.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            AttributeModifier armorModifier = armor.getModifier(CONSUMABLE_ARMOR_ID);
            if (armorModifier != null) {
                armor.removeModifier(armorModifier);
            }
        }

        // Remove health modifier
        AttributeInstance health = familiar.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            AttributeModifier healthModifier = health.getModifier(CONSUMABLE_HEALTH_ID);
            if (healthModifier != null) {
                health.removeModifier(healthModifier);
            }
        }

        // Remove spell power modifier
        AttributeInstance spellPower = familiar.getAttribute(AttributeRegistry.SPELL_POWER);
        if (spellPower != null) {
            AttributeModifier spellPowerModifier = spellPower.getModifier(CONSUMABLE_SPELL_POWER_ID);
            if (spellPowerModifier != null) {
                spellPower.removeModifier(spellPowerModifier);
            }
        }

        // Remove spell resist modifier
        AttributeInstance spellResist = familiar.getAttribute(AttributeRegistry.SPELL_RESIST);
        if (spellResist != null) {
            AttributeModifier spellResistModifier = spellResist.getModifier(CONSUMABLE_SPELL_RESIST_ID);
            if (spellResistModifier != null) {
                spellResist.removeModifier(spellResistModifier);
            }
        }

        // Remove enraged modifier
        AttributeInstance attackDamage = familiar.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage != null) {
            AttributeModifier enragedModifier = attackDamage.getModifier(CONSUMABLE_ENRAGED_ID);
            if (enragedModifier != null) {
                attackDamage.removeModifier(enragedModifier);
            }
        }
    }

    /**
     * Removes legacy attribute modifiers from the old system
     */
    public static void removeLegacyModifiers(AbstractSpellCastingPet familiar) {
        ResourceLocation legacyHealthModifierId = ResourceLocation.fromNamespaceAndPath("familiarslib", "familiar_health_modifier");
        ResourceLocation legacyArmorModifierId = ResourceLocation.fromNamespaceAndPath("familiarslib", "familiar_armor_modifier");

        // Remove legacy health modifier
        AttributeInstance health = familiar.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            AttributeModifier legacyHealthModifier = health.getModifier(legacyHealthModifierId);
            if (legacyHealthModifier != null) {
                health.removeModifier(legacyHealthModifier);
            }
        }

        // Remove legacy armor modifier
        AttributeInstance armor = familiar.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            AttributeModifier legacyArmorModifier = armor.getModifier(legacyArmorModifierId);
            if (legacyArmorModifier != null) {
                armor.removeModifier(legacyArmorModifier);
            }
        }
    }
}