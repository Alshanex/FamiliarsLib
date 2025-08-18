package net.alshanex.familiarslib.util;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class ModTags {
    //Tag for familiar taming items
    public static final TagKey<Item> FAMILIAR_TAMING = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "familiar_taming"));

    //Tag for food that can drop memory fragments
    public static final TagKey<Item> BERRY = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "crystal_berry"));
    //Tag for memory fragments
    public static final TagKey<Item> FRAGMENTS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "fragments"));
    //Tag for entities that the hunter can not mark with its ability
    public static final TagKey<EntityType<?>> HUNTER_CANNOT_MARK = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FamiliarsLib.MODID, "hunter_cannot_mark"));
    //Tag for entities that the hunter can not attack while inside a familiar house
    public static final TagKey<EntityType<?>> HUNTER_CANNOT_ATTACK_IN_HOME = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FamiliarsLib.MODID, "hunter_cannot_attack_in_home"));
    //Tag for items that illusionist can steal when not watching
    public static final TagKey<Item> ILLUSIONIST_STEALS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "illusionist_steals"));

    //Spells
    public static TagKey<AbstractSpell> AOE_ATTACKS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "attack/aoe"));
    public static TagKey<AbstractSpell> SINGLE_TARGET_ATTACKS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "attack/single_target"));
    public static TagKey<AbstractSpell> CLOSE_RANGE_ATTACKS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "attack/close_range"));
    public static TagKey<AbstractSpell> MID_RANGE_ATTACKS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "attack/mid_range"));
    public static TagKey<AbstractSpell> LONG_RANGE_ATTACKS = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "attack/long_range"));

    public static TagKey<AbstractSpell> ATTACK_BACK_DEFENSE = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "defense/attack_back"));
    public static TagKey<AbstractSpell> SELF_BUFF_DEFENSE = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "defense/self_buff"));

    public static TagKey<AbstractSpell> CLOSE_DISTANCE_MOVEMENT = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "movement/close_distance"));
    public static TagKey<AbstractSpell> ESCAPE_MOVEMENT = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "movement/escape"));

    public static TagKey<AbstractSpell> DEBUFF_BUFFING = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "buffing/debuff"));
    public static TagKey<AbstractSpell> SAFE_BUFF_BUFFING = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "buffing/safe_buff"));
    public static TagKey<AbstractSpell> UNSAFE_BUFF_BUFFING = create(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "buffing/unsafe_buff"));

    public static TagKey<AbstractSpell> create(ResourceLocation name) {
        return new TagKey<AbstractSpell>(SpellRegistry.SPELL_REGISTRY_KEY, name);
    }
}
