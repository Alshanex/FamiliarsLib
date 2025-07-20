package net.alshanex.familiarslib.util;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

public class ModTags {
    public static final TagKey<Item> FAMILIAR_TAMING = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "familiar_taming"));
    public static final TagKey<EntityType<?>> NECROMANCER_BLACKLIST = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FamiliarsLib.MODID, "necromancer_ability_blacklist"));
    public static final TagKey<Block> DRUID_GROWABLES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "druid_growables"));

    public static final TagKey<Item> BERRY = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "crystal_berry"));
    public static final TagKey<Item> FRAGMENTS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "fragments"));

    public static final TagKey<Item> ILLUSIONIST_STEALS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "illusionist_steals"));

    public static TagKey<AbstractSpell> ARCHMAGE_CANNOT_CREATE = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage_cannot_create"));
    public static TagKey<AbstractSpell> ARCHMAGE_CANNOT_REROLL = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage_cannot_reroll"));

    public static final TagKey<EntityType<?>> HUNTER_CANNOT_MARK = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FamiliarsLib.MODID, "hunter_cannot_mark"));
    public static final TagKey<EntityType<?>> DRUID_CANNOT_BREED = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FamiliarsLib.MODID, "druid_cannot_breed"));

    public static final TagKey<Block> MAGE_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "mage_spawnable_on"));
    public static final TagKey<Block> ARCHMAGE_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage_spawnable_on"));
    public static final TagKey<Block> SUMMONER_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "summoner_spawnable_on"));
    public static final TagKey<Block> NECROMANCER_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "necromancer_spawnable_on"));
    public static final TagKey<Block> HUNTER_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "hunter_spawnable_on"));
    public static final TagKey<Block> DRUID_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "druid_spawnable_on"));
    public static final TagKey<Block> SCORCHER_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "scorcher_spawnable_on"));
    public static final TagKey<Block> FROSTLING_SPAWNABLE_ON = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "frostling_spawnable_on"));

    public static TagKey<AbstractSpell> create(ResourceLocation name) {
        return new TagKey<AbstractSpell>(SpellRegistry.SPELL_REGISTRY_KEY, name);
    }

    //Archmage
    public static TagKey<AbstractSpell> ARCHMAGE_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage/attack"));
    public static TagKey<AbstractSpell> ARCHMAGE_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage/defense"));
    public static TagKey<AbstractSpell> ARCHMAGE_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage/movement"));
    public static TagKey<AbstractSpell> ARCHMAGE_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "archmage/support"));

    //Mage
    public static TagKey<AbstractSpell> MAGE_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "mage/attack"));
    public static TagKey<AbstractSpell> MAGE_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "mage/defense"));
    public static TagKey<AbstractSpell> MAGE_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "mage/movement"));
    public static TagKey<AbstractSpell> MAGE_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "mage/support"));

    //Bard
    public static TagKey<AbstractSpell> BARD_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "bard/attack"));
    public static TagKey<AbstractSpell> BARD_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "bard/defense"));
    public static TagKey<AbstractSpell> BARD_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "bard/movement"));
    public static TagKey<AbstractSpell> BARD_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "bard/support"));

    //Cleric
    public static TagKey<AbstractSpell> CLERIC_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "cleric/attack"));
    public static TagKey<AbstractSpell> CLERIC_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "cleric/defense"));
    public static TagKey<AbstractSpell> CLERIC_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "cleric/movement"));
    public static TagKey<AbstractSpell> CLERIC_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "cleric/support"));
    public static TagKey<AbstractSpell> CLERIC_HEALING_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "cleric/healing"));
    public static TagKey<AbstractSpell> CLERIC_BUFFING_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "cleric/buffing"));

    //Druid
    public static TagKey<AbstractSpell> DRUID_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "druid/attack"));
    public static TagKey<AbstractSpell> DRUID_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "druid/defense"));
    public static TagKey<AbstractSpell> DRUID_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "druid/movement"));
    public static TagKey<AbstractSpell> DRUID_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "druid/support"));

    //Hunter
    public static TagKey<AbstractSpell> HUNTER_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "hunter/attack"));
    public static TagKey<AbstractSpell> HUNTER_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "hunter/defense"));
    public static TagKey<AbstractSpell> HUNTER_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "hunter/movement"));
    public static TagKey<AbstractSpell> HUNTER_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "hunter/support"));

    //Illusionist
    public static TagKey<AbstractSpell> ILLUSIONIST_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "illusionist/attack"));
    public static TagKey<AbstractSpell> ILLUSIONIST_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "illusionist/defense"));
    public static TagKey<AbstractSpell> ILLUSIONIST_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "illusionist/movement"));
    public static TagKey<AbstractSpell> ILLUSIONIST_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "illusionist/support"));

    //Frostling
    public static TagKey<AbstractSpell> FROSTLING_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "frostling/attack"));
    public static TagKey<AbstractSpell> FROSTLING_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "frostling/defense"));
    public static TagKey<AbstractSpell> FROSTLING_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "frostling/movement"));
    public static TagKey<AbstractSpell> FROSTLING_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "frostling/support"));

    //Necromancer
    public static TagKey<AbstractSpell> NECROMANCER_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "necromancer/attack"));
    public static TagKey<AbstractSpell> NECROMANCER_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "necromancer/defense"));
    public static TagKey<AbstractSpell> NECROMANCER_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "necromancer/movement"));
    public static TagKey<AbstractSpell> NECROMANCER_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "necromancer/support"));

    //Plague
    public static TagKey<AbstractSpell> PLAGUE_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "plague/attack"));
    public static TagKey<AbstractSpell> PLAGUE_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "plague/defense"));
    public static TagKey<AbstractSpell> PLAGUE_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "plague/movement"));
    public static TagKey<AbstractSpell> PLAGUE_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "plague/support"));

    //Scorcher
    public static TagKey<AbstractSpell> SCORCHER_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "scorcher/attack"));
    public static TagKey<AbstractSpell> SCORCHER_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "scorcher/defense"));
    public static TagKey<AbstractSpell> SCORCHER_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "scorcher/movement"));
    public static TagKey<AbstractSpell> SCORCHER_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "scorcher/support"));

    //Summoner
    public static TagKey<AbstractSpell> SUMMONER_ATTACK_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "summoner/attack"));
    public static TagKey<AbstractSpell> SUMMONER_DEFENSE_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "summoner/defense"));
    public static TagKey<AbstractSpell> SUMMONER_MOVEMENT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "summoner/movement"));
    public static TagKey<AbstractSpell> SUMMONER_SUPPORT_SPELLS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "summoner/support"));

    public static List<AbstractSpell> getSpellsFromTag(TagKey<AbstractSpell> tag) {
        var list = new ArrayList<AbstractSpell>();

        for (var spell : SpellRegistry.getEnabledSpells()) {
            SpellRegistry.REGISTRY.getHolder(spell.getSpellResource()).ifPresent(a -> {
                if (a.is(tag)) {
                    list.add(spell);
                }
            });
        }

        return list;
    }

    public static boolean isSpellInTag(AbstractSpell spell, TagKey<AbstractSpell> tag){
        var list = new ArrayList<AbstractSpell>();

        SpellRegistry.REGISTRY.getHolder(spell.getSpellResource()).ifPresent(a -> {
            if (a.is(tag)) {
                list.add(spell);
            }
        });

        return !list.isEmpty();
    }
}
