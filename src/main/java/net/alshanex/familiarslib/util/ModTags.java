package net.alshanex.familiarslib.util;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

public class ModTags {
    public static final TagKey<Item> FAMILIAR_TAMING = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "familiar_taming"));

    public static final TagKey<Item> BERRY = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "crystal_berry"));
    public static final TagKey<Item> FRAGMENTS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "fragments"));

    public static final TagKey<EntityType<?>> HUNTER_CANNOT_MARK = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(FamiliarsLib.MODID, "hunter_cannot_mark"));

    public static final TagKey<Item> ILLUSIONIST_STEALS = TagKey.create(Registries.ITEM, new ResourceLocation(FamiliarsLib.MODID, "illusionist_steals"));

}
