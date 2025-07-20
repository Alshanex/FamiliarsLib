package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
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

public class FamiliarAttributesHelper {
    private static final ResourceLocation SHARED_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_spell_power");
    private static final ResourceLocation SHARED_SPELL_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_spell_resist");

    // Atributos de poder elemental
    private static final ResourceLocation SHARED_FIRE_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_fire_spell_power");
    private static final ResourceLocation SHARED_ICE_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_ice_spell_power");
    private static final ResourceLocation SHARED_LIGHTNING_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_lightning_spell_power");
    private static final ResourceLocation SHARED_HOLY_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_holy_spell_power");
    private static final ResourceLocation SHARED_ENDER_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_ender_spell_power");
    private static final ResourceLocation SHARED_BLOOD_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_blood_spell_power");
    private static final ResourceLocation SHARED_EVOCATION_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_evocation_spell_power");
    private static final ResourceLocation SHARED_NATURE_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_nature_spell_power");
    private static final ResourceLocation SHARED_ELDRITCH_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_eldritch_spell_power");
    private static final ResourceLocation SHARED_SOUND_SPELL_POWER_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_sound_spell_power");

    // Atributos de resistencia elemental
    private static final ResourceLocation SHARED_FIRE_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_fire_magic_resist");
    private static final ResourceLocation SHARED_ICE_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_ice_magic_resist");
    private static final ResourceLocation SHARED_LIGHTNING_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_lightning_magic_resist");
    private static final ResourceLocation SHARED_HOLY_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_holy_magic_resist");
    private static final ResourceLocation SHARED_ENDER_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_ender_magic_resist");
    private static final ResourceLocation SHARED_BLOOD_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_blood_magic_resist");
    private static final ResourceLocation SHARED_EVOCATION_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_evocation_magic_resist");
    private static final ResourceLocation SHARED_NATURE_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_nature_magic_resist");
    private static final ResourceLocation SHARED_ELDRITCH_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_eldritch_magic_resist");
    private static final ResourceLocation SHARED_SOUND_MAGIC_RESIST_ID = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "shared_sound_magic_resist");

    // Mapa de atributos que se comparten usando Holders
    private static final Map<Holder<Attribute>, ResourceLocation> SHARED_ATTRIBUTES = createSharedAttributesMap();

    private static Map<Holder<Attribute>, ResourceLocation> createSharedAttributesMap() {
        Map<Holder<Attribute>, ResourceLocation> map = new HashMap<>();

        // Atributos generales
        map.put(AttributeRegistry.SPELL_POWER, SHARED_SPELL_POWER_ID);
        map.put(AttributeRegistry.SPELL_RESIST, SHARED_SPELL_RESIST_ID);

        // Atributos de poder elemental
        map.put(AttributeRegistry.FIRE_SPELL_POWER, SHARED_FIRE_SPELL_POWER_ID);
        map.put(AttributeRegistry.ICE_SPELL_POWER, SHARED_ICE_SPELL_POWER_ID);
        map.put(AttributeRegistry.LIGHTNING_SPELL_POWER, SHARED_LIGHTNING_SPELL_POWER_ID);
        map.put(AttributeRegistry.HOLY_SPELL_POWER, SHARED_HOLY_SPELL_POWER_ID);
        map.put(AttributeRegistry.ENDER_SPELL_POWER, SHARED_ENDER_SPELL_POWER_ID);
        map.put(AttributeRegistry.BLOOD_SPELL_POWER, SHARED_BLOOD_SPELL_POWER_ID);
        map.put(AttributeRegistry.EVOCATION_SPELL_POWER, SHARED_EVOCATION_SPELL_POWER_ID);
        map.put(AttributeRegistry.NATURE_SPELL_POWER, SHARED_NATURE_SPELL_POWER_ID);
        map.put(AttributeRegistry.ELDRITCH_SPELL_POWER, SHARED_ELDRITCH_SPELL_POWER_ID);
        map.put(net.alshanex.familiarslib.registry.AttributeRegistry.SOUND_SPELL_POWER, SHARED_SOUND_SPELL_POWER_ID);

        // Atributos de resistencia elemental
        map.put(AttributeRegistry.FIRE_MAGIC_RESIST, SHARED_FIRE_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.ICE_MAGIC_RESIST, SHARED_ICE_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.LIGHTNING_MAGIC_RESIST, SHARED_LIGHTNING_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.HOLY_MAGIC_RESIST, SHARED_HOLY_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.ENDER_MAGIC_RESIST, SHARED_ENDER_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.BLOOD_MAGIC_RESIST, SHARED_BLOOD_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.EVOCATION_MAGIC_RESIST, SHARED_EVOCATION_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.NATURE_MAGIC_RESIST, SHARED_NATURE_MAGIC_RESIST_ID);
        map.put(AttributeRegistry.ELDRITCH_MAGIC_RESIST, SHARED_ELDRITCH_MAGIC_RESIST_ID);
        map.put(net.alshanex.familiarslib.registry.AttributeRegistry.SOUND_MAGIC_RESIST, SHARED_SOUND_MAGIC_RESIST_ID);

        return map;
    }

    /**
     * Aplica los atributos compartidos a un familiar específico
     */
    public static void applyAttributes(AbstractSpellCastingPet familiar) {
        LivingEntity owner = familiar.getSummoner();
        if (!(owner instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // Verificar si el jugador tiene el FamiliarSpellbook equipado
        if (!CurioUtils.isWearingFamiliarSpellbook(serverPlayer)) {
            removeAttributes(familiar);
            return;
        }

        // Obtener familiares sumoneados
        List<AbstractSpellCastingPet> summonedFamiliars = getSummonedFamiliars(serverPlayer);

        if (summonedFamiliars.isEmpty()) {
            return;
        }

        // Calcular y aplicar los modificadores
        applySharedAttributeModifiers(familiar, serverPlayer, summonedFamiliars.size());
    }

    /**
     * Remueve todos los atributos compartidos de un familiar
     */
    public static void removeAttributes(AbstractSpellCastingPet familiar) {
        for (Map.Entry<Holder<Attribute>, ResourceLocation> entry : SHARED_ATTRIBUTES.entrySet()) {
            AttributeInstance attributeInstance = familiar.getAttribute(entry.getKey());
            if (attributeInstance != null) {
                attributeInstance.removeModifier(entry.getValue());
            }
        }
    }

    /**
     * Recalcula y aplica los atributos para todos los familiares sumoneados de un jugador
     */
    public static void recalculateAllFamiliarAttributes(ServerPlayer player) {
        if (!CurioUtils.isWearingFamiliarSpellbook(player)) {
            // Si no tiene el spellbook, remover todos los atributos
            removeAllFamiliarAttributes(player);
            return;
        }

        List<AbstractSpellCastingPet> summonedFamiliars = getSummonedFamiliars(player);

        if (summonedFamiliars.isEmpty()) {
            return;
        }

        // Aplicar atributos a todos los familiares
        for (AbstractSpellCastingPet familiar : summonedFamiliars) {
            applySharedAttributeModifiers(familiar, player, summonedFamiliars.size());
        }
    }

    /**
     * Remueve todos los atributos compartidos de todos los familiares de un jugador
     */
    public static void removeAllFamiliarAttributes(ServerPlayer player) {
        List<AbstractSpellCastingPet> summonedFamiliars = getSummonedFamiliars(player);

        for (AbstractSpellCastingPet familiar : summonedFamiliars) {
            removeAttributes(familiar);
        }
    }

    /**
     * Aplica los modificadores de atributos compartidos a un familiar específico
     */
    private static void applySharedAttributeModifiers(AbstractSpellCastingPet familiar, ServerPlayer player, int totalFamiliars) {
        for (Map.Entry<Holder<Attribute>, ResourceLocation> entry : SHARED_ATTRIBUTES.entrySet()) {
            Holder<Attribute> attributeHolder = entry.getKey();
            ResourceLocation modifierLocation = entry.getValue();

            // Obtener el valor del atributo del jugador
            AttributeInstance playerAttribute = player.getAttribute(attributeHolder);
            if (playerAttribute == null) continue;

            double playerValue = playerAttribute.getValue();
            double baseValue = playerAttribute.getBaseValue();

            // Calcular el valor adicional (valor del jugador - valor base)
            double additionalValue = playerValue - baseValue;

            if (additionalValue <= 0) continue;

            // Calcular la porción que le corresponde a cada familiar
            double familiarShare = additionalValue / totalFamiliars;

            // Aplicar el modificador al familiar
            AttributeInstance familiarAttribute = familiar.getAttribute(attributeHolder);
            if (familiarAttribute != null) {
                // Remover el modificador existente si existe
                familiarAttribute.removeModifier(modifierLocation);

                // Crear y agregar el nuevo modificador
                AttributeModifier modifier = new AttributeModifier(
                        modifierLocation,
                        familiarShare,
                        AttributeModifier.Operation.ADD_VALUE
                );
                familiarAttribute.addTransientModifier(modifier);
            }
        }
    }

    /**
     * Obtiene todos los familiares sumoneados de un jugador
     */
    private static List<AbstractSpellCastingPet> getSummonedFamiliars(ServerPlayer player) {
        List<AbstractSpellCastingPet> summonedFamiliars = new ArrayList<>();

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return summonedFamiliars;
        }

        PlayerFamiliarData familiarData = player.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        // Buscar todas las entidades familiares en el mundo que pertenezcan al jugador
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof AbstractSpellCastingPet familiar) {
                UUID ownerUUID = familiar.getOwnerUUID();
                if (ownerUUID != null && ownerUUID.equals(player.getUUID())) {
                    // Verificar que el familiar está en los datos del jugador
                    if (familiarData.hasFamiliar(familiar.getUUID())) {
                        // Verificar que no está en casa
                        if (!familiar.getIsInHouse()) {
                            summonedFamiliars.add(familiar);
                        }
                    }
                }
            }
        }

        return summonedFamiliars;
    }

    /**
     * Maneja el cambio de equipo del FamiliarSpellbook
     */
    public static void handleSpellbookEquipChange(ServerPlayer player, boolean equipped) {
        if (equipped) {
            recalculateAllFamiliarAttributes(player);
        } else {
            removeAllFamiliarAttributes(player);
        }
    }

    /**
     * Maneja cuando un familiar es sumoneado
     */
    public static void handleFamiliarSummoned(ServerPlayer player, AbstractSpellCastingPet familiar) {
        if (CurioUtils.isWearingFamiliarSpellbook(player)) {
            // Recalcular para todos los familiares (incluyendo el nuevo)
            recalculateAllFamiliarAttributes(player);
        }
    }

    /**
     * Maneja cuando un familiar es desumoneado
     */
    public static void handleFamiliarDismissed(ServerPlayer player, AbstractSpellCastingPet familiar) {
        // Remover atributos del familiar desumoneado
        removeAttributes(familiar);

        if (CurioUtils.isWearingFamiliarSpellbook(player)) {
            // Recalcular para los familiares restantes
            recalculateAllFamiliarAttributes(player);
        }
    }

    /**
     * Maneja cuando los atributos del jugador cambian
     */
    public static void handlePlayerAttributeChange(ServerPlayer player) {
        if (CurioUtils.isWearingFamiliarSpellbook(player)) {
            recalculateAllFamiliarAttributes(player);
        }
    }

    public static void debugFamiliarAttributes(AbstractSpellCastingPet familiar) {
        FamiliarsLib.LOGGER.info("Familiar {} attributes:", familiar.getUUID());
        for (Holder<Attribute> attributeHolder : SHARED_ATTRIBUTES.keySet()) {
            AttributeInstance instance = familiar.getAttribute(attributeHolder);
            if (instance != null) {
                FamiliarsLib.LOGGER.info("  {}: {} (base: {})",
                        attributeHolder.value().getDescriptionId(),
                        instance.getValue(),
                        instance.getBaseValue());
            }
        }
    }

    public static void applyHealthAttribute(AbstractSpellCastingPet pet) {
        int level = pet.getHealthStacks();

        // Usar un ResourceLocation único para el modificador de vida de familiares
        ResourceLocation healthModifierId = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_health_modifier");

        AttributeInstance health = pet.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            // Remover el modificador anterior si existe
            health.removeModifier(healthModifierId);

            // Solo aplicar modificador si hay stacks
            if (level > 0) {
                // Calcular el bonus total basado en los stacks
                double totalHealthBonus = level * 0.1; // 10% por stack

                AttributeModifier healthModifier = new AttributeModifier(
                        healthModifierId,
                        totalHealthBonus,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE
                );

                health.addPermanentModifier(healthModifier);

                FamiliarsLib.LOGGER.info("Applied health attribute to pet {}: {} stacks, modifier: {}%, new max health: {}",
                        pet.getUUID(), level, totalHealthBonus * 100, health.getValue());
            }

            // Si la vida actual es mayor que la nueva vida máxima, ajustarla
            if (pet.getHealth() > health.getValue()) {
                pet.setHealth((float) health.getValue());
            }
        }
    }

    public static void applyArmorAttribute(AbstractSpellCastingPet pet) {
        int level = pet.getArmorStacks();

        // Usar un ResourceLocation único para el modificador de armadura de familiares
        ResourceLocation armorModifierId = ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_armor_modifier");

        AttributeInstance armor = pet.getAttribute(Attributes.ARMOR);
        if (armor != null) {
            // Remover el modificador anterior si existe
            armor.removeModifier(armorModifierId);

            // Solo aplicar modificador si hay stacks
            if (level > 0) {
                // Calcular el bonus total basado en los stacks
                double totalArmorBonus = level * 1.0; // 1 punto de armadura por stack

                AttributeModifier armorModifier = new AttributeModifier(
                        armorModifierId,
                        totalArmorBonus,
                        AttributeModifier.Operation.ADD_VALUE
                );

                armor.addPermanentModifier(armorModifier);

                FamiliarsLib.LOGGER.info("Applied armor attribute to pet {}: {} stacks, new armor: {}",
                        pet.getUUID(), level, armor.getValue());
            }
        }
    }

    /**
     * Aplica todos los atributos consumibles al cargar el familiar
     */
    public static void applyAllConsumableAttributes(AbstractSpellCastingPet pet) {
        applyHealthAttribute(pet);
        applyArmorAttribute(pet);

        FamiliarsLib.LOGGER.info("Applied all consumable attributes to pet {}: {} health stacks, {} armor stacks",
                pet.getUUID(), pet.getHealthStacks(), pet.getArmorStacks());
    }
}
