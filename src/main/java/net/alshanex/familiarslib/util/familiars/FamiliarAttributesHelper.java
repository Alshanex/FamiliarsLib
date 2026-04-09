package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.mixin.SchoolTypeAccessor;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
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

    /**
     * Cache of attribute holder -> modifier ResourceLocation.
     * Built lazily on first access from the SchoolRegistry + global spell power/resist.
     */
    private static volatile Map<Holder<Attribute>, ResourceLocation> sharedAttributes;

    /**
     * Lazily builds and returns the shared attributes map.
     * Iterates over all registered SchoolTypes and extracts their power and resistance attributes, plus the global SPELL_POWER and SPELL_RESIST.
     */
    private static Map<Holder<Attribute>, ResourceLocation> getSharedAttributes() {
        if (sharedAttributes == null) {
            synchronized (FamiliarAttributesHelper.class) {
                if (sharedAttributes == null) {
                    sharedAttributes = buildSharedAttributesMap();
                }
            }
        }
        return sharedAttributes;
    }

    private static Map<Holder<Attribute>, ResourceLocation> buildSharedAttributesMap() {
        Map<Holder<Attribute>, ResourceLocation> map = new LinkedHashMap<>();

        // Global spell power and resistance
        map.put(AttributeRegistry.SPELL_POWER, modifierId("shared_spell_power"));
        map.put(AttributeRegistry.SPELL_RESIST, modifierId("shared_spell_resist"));

        // Dynamically discover all schools from the registry
        for (SchoolType school : SchoolRegistry.REGISTRY) {
            String schoolName = school.getId().getPath();

            Holder<Attribute> powerAttr = ((SchoolTypeAccessor) school).getPowerAttribute();
            Holder<Attribute> resistAttr = ((SchoolTypeAccessor) school).getResistanceAttribute();

            if (powerAttr != null) {
                map.put(powerAttr, modifierId("shared_" + schoolName + "_spell_power"));
            }
            if (resistAttr != null) {
                map.put(resistAttr, modifierId("shared_" + schoolName + "_magic_resist"));
            }
        }

        //FamiliarsLib.LOGGER.debug("FamiliarAttributesHelper: Registered {} shared attributes from {} schools", map.size(), SchoolRegistry.REGISTRY.size());

        return Collections.unmodifiableMap(map);
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, path);
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
        for (Map.Entry<Holder<Attribute>, ResourceLocation> entry : getSharedAttributes().entrySet()) {
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
        for (Map.Entry<Holder<Attribute>, ResourceLocation> entry : getSharedAttributes().entrySet()) {
            Holder<Attribute> attributeHolder = entry.getKey();
            ResourceLocation modifierLocation = entry.getValue();

            AttributeInstance playerAttribute = player.getAttribute(attributeHolder);
            if (playerAttribute == null) continue;

            double playerValue = playerAttribute.getValue();
            double baseValue = playerAttribute.getBaseValue();

            double additionalValue = (playerValue - baseValue) / 2;
            additionalValue = Math.round(additionalValue * 100.0) / 100.0;

            if (additionalValue <= 0) continue;

            double familiarShare = additionalValue / totalFamiliars;
            familiarShare = Math.round(familiarShare * 100.0) / 100.0;

            AttributeInstance familiarAttribute = familiar.getAttribute(attributeHolder);
            if (familiarAttribute != null) {
                familiarAttribute.removeModifier(modifierLocation);

                AttributeModifier modifier = new AttributeModifier(
                        modifierLocation,
                        familiarShare,
                        AttributeModifier.Operation.ADD_VALUE
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

        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

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
        for (Holder<Attribute> attributeHolder : getSharedAttributes().keySet()) {
            AttributeInstance instance = familiar.getAttribute(attributeHolder);
            if (instance != null) {
                /*
                FamiliarsLib.LOGGER.debug("  {}: {} (base: {})",
                        attributeHolder.value().getDescriptionId(),
                        instance.getValue(),
                        instance.getBaseValue());
                */
            }
        }
    }

    /**
     * Removes legacy attribute modifiers from the old consumable system.
     * This should be called during migration to clean up old modifiers.
     */
    public static void removeLegacyConsumableModifiers(AbstractSpellCastingPet familiar) {
        ResourceLocation legacyHealthModifierId = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_health_modifier");
        ResourceLocation legacyArmorModifierId = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_armor_modifier");

        AttributeInstance health = familiar.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            AttributeModifier legacyHealthModifier = health.getModifier(legacyHealthModifierId);
            if (legacyHealthModifier != null) {
                health.removeModifier(legacyHealthModifier);
            }
        }

        AttributeInstance armor = familiar.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            AttributeModifier legacyArmorModifier = armor.getModifier(legacyArmorModifierId);
            if (legacyArmorModifier != null) {
                armor.removeModifier(legacyArmorModifier);
            }
        }
    }

    @Deprecated(forRemoval = true)
    public static void applyHealthAttribute(AbstractSpellCastingPet pet) {
        FamiliarsLib.LOGGER.warn("applyHealthAttribute is deprecated. Use FamiliarConsumableSystem instead.");
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(pet);
        FamiliarConsumableSystem.applyAttributeModifiers(pet, data);
    }

    @Deprecated(forRemoval = true)
    public static void applyArmorAttribute(AbstractSpellCastingPet pet) {
        FamiliarsLib.LOGGER.warn("applyArmorAttribute is deprecated. Use FamiliarConsumableSystem instead.");
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(pet);
        FamiliarConsumableSystem.applyAttributeModifiers(pet, data);
    }

    @Deprecated(forRemoval = true)
    public static void applyAllConsumableAttributes(AbstractSpellCastingPet pet) {
        FamiliarsLib.LOGGER.warn("applyAllConsumableAttributes is deprecated. Use FamiliarConsumableSystem instead.");
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(pet);
        FamiliarConsumableSystem.applyAttributeModifiers(pet, data);
    }
}