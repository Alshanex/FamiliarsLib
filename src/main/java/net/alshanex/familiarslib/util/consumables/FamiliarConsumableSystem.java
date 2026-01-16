package net.alshanex.familiarslib.util.consumables;

import com.mojang.serialization.Codec;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

public class FamiliarConsumableSystem {
    private static final UUID CONSUMABLE_ARMOR_UUID = UUID.fromString("a1b2c3d4-1111-1111-1111-111111111111");
    private static final UUID CONSUMABLE_HEALTH_UUID = UUID.fromString("a1b2c3d4-2222-2222-2222-222222222222");
    private static final UUID CONSUMABLE_SPELL_POWER_UUID = UUID.fromString("a1b2c3d4-3333-3333-3333-333333333333");
    private static final UUID CONSUMABLE_SPELL_RESIST_UUID = UUID.fromString("a1b2c3d4-4444-4444-4444-444444444444");
    private static final UUID CONSUMABLE_ENRAGED_UUID = UUID.fromString("a1b2c3d4-5555-5555-5555-555555555555");

    public enum ConsumableType implements StringRepresentable {
        ARMOR(0, new int[]{2, 3, 4}, new int[]{10, 16, 20}, "armor"),
        HEALTH(1, new int[]{10, 25, 50}, new int[]{50, 100, 150}, "health"),
        SPELL_POWER(2, new int[]{3, 5, 15}, new int[]{15, 30, 45}, "spell_power"),
        SPELL_RESIST(3, new int[]{3, 5, 15}, new int[]{15, 30, 45}, "spell_resist"),
        ENRAGED(5, new int[]{1}, new int[]{10}, "enraged"),
        BLOCKING(6, new int[]{1}, new int[]{1}, "blocking");

        public static final Codec<ConsumableType> CODEC = StringRepresentable.fromEnum(ConsumableType::values);

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
        public String getSerializedName() { return serializedName; }

        public static ConsumableType byId(int id) {
            for (ConsumableType type : values()) {
                if (type.id == id) return type;
            }
            return ARMOR;
        }
    }

    public static void applyAttributeModifiers(AbstractSpellCastingPet familiar, ConsumableData data) {
        removeAllConsumableModifiers(familiar);

        // Apply armor bonus
        int armorBonus = data.getValue(ConsumableType.ARMOR);
        if (armorBonus > 0) {
            AttributeInstance armorAttribute = familiar.getAttribute(Attributes.ARMOR);
            if (armorAttribute != null) {
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_ARMOR_UUID,
                        "Consumable armor bonus",
                        armorBonus,
                        AttributeModifier.Operation.ADDITION);
                armorAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply health bonus (percentage)
        int healthBonus = data.getValue(ConsumableType.HEALTH);
        if (healthBonus > 0) {
            AttributeInstance healthAttribute = familiar.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttribute != null) {
                double multiplier = healthBonus / 100.0;
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_HEALTH_UUID,
                        "Consumable health bonus",
                        multiplier,
                        AttributeModifier.Operation.MULTIPLY_BASE);
                healthAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply spell power bonus (percentage)
        int spellPowerBonus = data.getValue(ConsumableType.SPELL_POWER);
        if (spellPowerBonus > 0) {
            AttributeInstance spellPowerAttribute = familiar.getAttribute(AttributeRegistry.SPELL_POWER.get());
            if (spellPowerAttribute != null) {
                double multiplier = spellPowerBonus / 100.0;
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_SPELL_POWER_UUID,
                        "Consumable spell power bonus",
                        multiplier,
                        AttributeModifier.Operation.MULTIPLY_BASE);
                spellPowerAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply spell resist bonus (percentage)
        int spellResistBonus = data.getValue(ConsumableType.SPELL_RESIST);
        if (spellResistBonus > 0) {
            AttributeInstance spellResistAttribute = familiar.getAttribute(AttributeRegistry.SPELL_RESIST.get());
            if (spellResistAttribute != null) {
                double multiplier = spellResistBonus / 100.0;
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_SPELL_RESIST_UUID,
                        "Consumable spell resist bonus",
                        multiplier,
                        AttributeModifier.Operation.MULTIPLY_BASE);
                spellResistAttribute.addPermanentModifier(modifier);
            }
        }

        // Apply enraged bonus
        int enragedStacks = data.getValue(ConsumableType.ENRAGED);
        if (enragedStacks > 0) {
            AttributeInstance attackDamageAttribute = familiar.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamageAttribute != null) {
                double bonus = enragedStacks * 0.5;
                AttributeModifier modifier = new AttributeModifier(
                        CONSUMABLE_ENRAGED_UUID,
                        "Consumable enraged bonus",
                        bonus,
                        AttributeModifier.Operation.ADDITION);
                attackDamageAttribute.addPermanentModifier(modifier);
            }
        }
    }

    /**
     * Removes all consumable-related attribute modifiers
     */
    public static void removeAllConsumableModifiers(AbstractSpellCastingPet familiar) {
        removeModifierIfPresent(familiar.getAttribute(Attributes.ARMOR), CONSUMABLE_ARMOR_UUID);
        removeModifierIfPresent(familiar.getAttribute(Attributes.MAX_HEALTH), CONSUMABLE_HEALTH_UUID);
        removeModifierIfPresent(familiar.getAttribute(AttributeRegistry.SPELL_POWER.get()), CONSUMABLE_SPELL_POWER_UUID);
        removeModifierIfPresent(familiar.getAttribute(AttributeRegistry.SPELL_RESIST.get()), CONSUMABLE_SPELL_RESIST_UUID);
        removeModifierIfPresent(familiar.getAttribute(Attributes.ATTACK_DAMAGE), CONSUMABLE_ENRAGED_UUID);
    }

    private static void removeModifierIfPresent(AttributeInstance attribute, UUID modifierId) {
        if (attribute != null) {
            AttributeModifier modifier = attribute.getModifier(modifierId);
            if (modifier != null) {
                attribute.removeModifier(modifier);
            }
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

}
