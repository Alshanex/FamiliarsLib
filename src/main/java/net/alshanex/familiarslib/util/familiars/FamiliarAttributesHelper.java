package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.CapabilityRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.*;

/**
 * Helper class for the Familiar Spellbook attribute sharing
 * Legacy consumable attribute methods have been removed and migrated to FamiliarConsumableSystem
 */
public class FamiliarAttributesHelper {
    private static final UUID SHARED_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000001");
    private static final UUID SHARED_SPELL_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000002");

    private static final UUID SHARED_FIRE_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000003");
    private static final UUID SHARED_ICE_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000004");
    private static final UUID SHARED_LIGHTNING_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000005");
    private static final UUID SHARED_HOLY_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000006");
    private static final UUID SHARED_ENDER_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000007");
    private static final UUID SHARED_BLOOD_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000008");
    private static final UUID SHARED_EVOCATION_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000009");
    private static final UUID SHARED_NATURE_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-00000000000a");
    private static final UUID SHARED_ELDRITCH_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-00000000000b");
    private static final UUID SHARED_SOUND_SPELL_POWER_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-00000000000c");

    private static final UUID SHARED_FIRE_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-00000000000d");
    private static final UUID SHARED_ICE_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-00000000000e");
    private static final UUID SHARED_LIGHTNING_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-00000000000f");
    private static final UUID SHARED_HOLY_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000010");
    private static final UUID SHARED_ENDER_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000011");
    private static final UUID SHARED_BLOOD_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000012");
    private static final UUID SHARED_EVOCATION_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000013");
    private static final UUID SHARED_NATURE_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000014");
    private static final UUID SHARED_ELDRITCH_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000015");
    private static final UUID SHARED_SOUND_MAGIC_RESIST_ID = UUID.fromString("a1b2c3d4-1111-4000-8000-000000000016");

    private static final Map<Attribute, UUID> SHARED_ATTRIBUTES = createSharedAttributesMap();

    private static Map<Attribute, UUID> createSharedAttributesMap() {
        Map<Attribute, UUID> map = new HashMap<>();

        map.put(AttributeRegistry.SPELL_POWER.get(), SHARED_SPELL_POWER_ID);
        map.put(AttributeRegistry.SPELL_RESIST.get(), SHARED_SPELL_RESIST_ID);

        map.put(AttributeRegistry.FIRE_SPELL_POWER.get(), SHARED_FIRE_SPELL_POWER_ID);
        map.put(AttributeRegistry.ICE_SPELL_POWER.get(), SHARED_ICE_SPELL_POWER_ID);
        map.put(AttributeRegistry.LIGHTNING_SPELL_POWER.get(), SHARED_LIGHTNING_SPELL_POWER_ID);
        map.put(AttributeRegistry.HOLY_SPELL_POWER.get(), SHARED_HOLY_SPELL_POWER_ID);
        map.put(AttributeRegistry.ENDER_SPELL_POWER.get(), SHARED_ENDER_SPELL_POWER_ID);
        map.put(AttributeRegistry.BLOOD_SPELL_POWER.get(), SHARED_BLOOD_SPELL_POWER_ID);
        map.put(AttributeRegistry.EVOCATION_SPELL_POWER.get(), SHARED_EVOCATION_SPELL_POWER_ID);
        map.put(AttributeRegistry.NATURE_SPELL_POWER.get(), SHARED_NATURE_SPELL_POWER_ID);
        map.put(AttributeRegistry.ELDRITCH_SPELL_POWER.get(), SHARED_ELDRITCH_SPELL_POWER_ID);
        map.put(net.alshanex.familiarslib.registry.AttributeRegistry.SOUND_SPELL_POWER.get(), SHARED_SOUND_SPELL_POWER_ID);

        map.put(AttributeRegistry.FIRE_MAGIC_RESIST.get(), SHARED_FIRE_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.ICE_MAGIC_RESIST.get(), SHARED_ICE_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.LIGHTNING_MAGIC_RESIST.get(), SHARED_LIGHTNING_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.HOLY_MAGIC_RESIST.get(), SHARED_HOLY_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.ENDER_MAGIC_RESIST.get(), SHARED_ENDER_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.BLOOD_MAGIC_RESIST.get(), SHARED_BLOOD_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.EVOCATION_MAGIC_RESIST.get(), SHARED_EVOCATION_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.NATURE_MAGIC_RESIST.get(), SHARED_NATURE_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.ELDRITCH_MAGIC_RESIST.get(), SHARED_ELDRITCH_MAGIC_RESIST_ID);
        map.put(net.alshanex.familiarslib.registry.AttributeRegistry.SOUND_MAGIC_RESIST.get(), SHARED_SOUND_MAGIC_RESIST_ID);

        return map;
    }

    public static void applyAttributes(AbstractSpellCastingPet familiar) {
        LivingEntity owner = familiar.getSummoner();
        if (!(owner instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!CurioUtils.isWearingFamiliarSpellbook(serverPlayer)) {
            removeAttributes(familiar);
            return;
        }

        List<AbstractSpellCastingPet> summonedFamiliars = getSummonedFamiliars(serverPlayer);

        if (summonedFamiliars.isEmpty()) {
            return;
        }

        applySharedAttributeModifiers(familiar, serverPlayer, summonedFamiliars.size());
    }

    public static void removeAttributes(AbstractSpellCastingPet familiar) {
        for (Map.Entry<Attribute, UUID> entry : SHARED_ATTRIBUTES.entrySet()) {
            AttributeInstance attributeInstance = familiar.getAttribute(entry.getKey());
            if (attributeInstance != null) {
                attributeInstance.removeModifier(entry.getValue());
            }
        }
    }

    public static void recalculateAllFamiliarAttributes(ServerPlayer player) {
        if (!CurioUtils.isWearingFamiliarSpellbook(player)) {
            removeAllFamiliarAttributes(player);
            return;
        }

        List<AbstractSpellCastingPet> summonedFamiliars = getSummonedFamiliars(player);

        if (summonedFamiliars.isEmpty()) {
            return;
        }

        for (AbstractSpellCastingPet familiar : summonedFamiliars) {
            applySharedAttributeModifiers(familiar, player, summonedFamiliars.size());
        }
    }

    public static void removeAllFamiliarAttributes(ServerPlayer player) {
        List<AbstractSpellCastingPet> summonedFamiliars = getSummonedFamiliars(player);

        for (AbstractSpellCastingPet familiar : summonedFamiliars) {
            removeAttributes(familiar);
        }
    }

    private static void applySharedAttributeModifiers(AbstractSpellCastingPet familiar, ServerPlayer player, int totalFamiliars) {
        for (Map.Entry<Attribute, UUID> entry : SHARED_ATTRIBUTES.entrySet()) {
            Attribute attribute = entry.getKey();
            UUID modifierId = entry.getValue();

            AttributeInstance playerAttribute = player.getAttribute(attribute);
            if (playerAttribute == null) continue;

            double playerValue = playerAttribute.getValue();
            double baseValue = playerAttribute.getBaseValue();

            double additionalValue = (playerValue - baseValue) / 2;
            additionalValue = Math.round(additionalValue * 100.0) / 100.0;

            if (additionalValue <= 0) continue;

            double familiarShare = additionalValue / totalFamiliars;
            familiarShare = Math.round(familiarShare * 100.0) / 100.0;

            AttributeInstance familiarAttribute = familiar.getAttribute(attribute);
            if (familiarAttribute != null) {
                familiarAttribute.removeModifier(modifierId);

                AttributeModifier modifier = new AttributeModifier(
                        modifierId,
                        "shared_familiar_attribute",
                        familiarShare,
                        AttributeModifier.Operation.ADDITION
                );
                familiarAttribute.addTransientModifier(modifier);
            }
        }
    }

    private static List<AbstractSpellCastingPet> getSummonedFamiliars(ServerPlayer player) {
        List<AbstractSpellCastingPet> summonedFamiliars = new ArrayList<>();

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return summonedFamiliars;
        }

        player.getCapability(CapabilityRegistry.PLAYER_FAMILIAR_DATA).ifPresent(familiarData -> {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof AbstractSpellCastingPet familiar) {
                    UUID ownerUUID = familiar.getOwnerUUID();
                    if (ownerUUID != null && ownerUUID.equals(player.getUUID())) {
                        if (familiarData.hasFamiliar(familiar.getUUID())) {
                            if (!familiar.getIsInHouse()) {
                                summonedFamiliars.add(familiar);
                            }
                        }
                    }
                }
            }
        });

        return summonedFamiliars;
    }

    public static void handleSpellbookEquipChange(ServerPlayer player, boolean equipped) {
        if (equipped) {
            recalculateAllFamiliarAttributes(player);
        } else {
            removeAllFamiliarAttributes(player);
        }
    }

    public static void handleFamiliarSummoned(ServerPlayer player, AbstractSpellCastingPet familiar) {
        if (CurioUtils.isWearingFamiliarSpellbook(player)) {
            recalculateAllFamiliarAttributes(player);
        }
    }

    public static void handleFamiliarDismissed(ServerPlayer player, AbstractSpellCastingPet familiar) {
        removeAttributes(familiar);

        if (CurioUtils.isWearingFamiliarSpellbook(player)) {
            recalculateAllFamiliarAttributes(player);
        }
    }

    public static void handlePlayerAttributeChange(ServerPlayer player) {
        if (CurioUtils.isWearingFamiliarSpellbook(player)) {
            recalculateAllFamiliarAttributes(player);
        }
    }

    public static void debugFamiliarAttributes(AbstractSpellCastingPet familiar) {
        FamiliarsLib.LOGGER.debug("Familiar {} attributes:", familiar.getUUID());
        for (Attribute attribute : SHARED_ATTRIBUTES.keySet()) {
            AttributeInstance instance = familiar.getAttribute(attribute);
            if (instance != null) {
                FamiliarsLib.LOGGER.debug("  {}: {} (base: {})",
                        attribute.getDescriptionId(),
                        instance.getValue(),
                        instance.getBaseValue());
            }
        }
    }

    /**
     * Removes legacy attribute modifiers from the old consumable system
     * This should be called during migration to clean up old modifiers
     */
    public static void removeLegacyConsumableModifiers(AbstractSpellCastingPet familiar) {
        UUID legacyHealthModifierId = UUID.fromString("b1b2c3d4-2222-4000-8000-000000000001");
        UUID legacyArmorModifierId = UUID.fromString("b1b2c3d4-2222-4000-8000-000000000002");

        // Remove legacy health modifier
        AttributeInstance health = familiar.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            AttributeModifier legacyHealthModifier = health.getModifier(legacyHealthModifierId);
            if (legacyHealthModifier != null) {
                health.removeModifier(legacyHealthModifier);
                FamiliarsLib.LOGGER.debug("Removed legacy health modifier from familiar {}", familiar.getUUID());
            }
        }

        // Remove legacy armor modifier
        AttributeInstance armor = familiar.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            AttributeModifier legacyArmorModifier = armor.getModifier(legacyArmorModifierId);
            if (legacyArmorModifier != null) {
                armor.removeModifier(legacyArmorModifier);
                FamiliarsLib.LOGGER.debug("Removed legacy armor modifier from familiar {}", familiar.getUUID());
            }
        }
    }
}