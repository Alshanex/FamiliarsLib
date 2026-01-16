package net.alshanex.familiarslib.util;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.tags.ITag;
import net.minecraftforge.registries.tags.ITagManager;

import java.util.ArrayList;
import java.util.List;

public class HelperMethods {
    public static float horizontalDistanceSqr(LivingEntity livingEntity, Vec3 vec3) {
        var dx = livingEntity.getX() - vec3.x;
        var dz = livingEntity.getZ() - vec3.z;
        return (float) (dx * dx + dz * dz);
    }

    //Util method to extract spells from your tags
    public static List<AbstractSpell> getSpellsFromTag(TagKey<AbstractSpell> tagKey) {
        IForgeRegistry<AbstractSpell> registry = SpellRegistry.REGISTRY.get();

        if (registry == null) {
            FamiliarsLib.LOGGER.warn("SpellRegistry is null!");
            return new ArrayList<>();
        }

        ITagManager<AbstractSpell> tagManager = registry.tags();

        if (tagManager == null) {
            FamiliarsLib.LOGGER.warn("TagManager is null - tags not loaded yet!");
            return new ArrayList<>();
        }

        if (!tagManager.isKnownTagName(tagKey)) {
            FamiliarsLib.LOGGER.warn("Tag {} is not known/registered!", tagKey.location());
            return new ArrayList<>();
        }

        ITag<AbstractSpell> tag = tagManager.getTag(tagKey);
        List<AbstractSpell> result = tag.stream().toList();

        FamiliarsLib.LOGGER.debug("Tag {} returned {} spells", tagKey.location(), result.size());

        return result;
    }

    //Checks if a spell is inside a specific tag
    public static boolean isSpellInTag(AbstractSpell spell, TagKey<AbstractSpell> tagKey) {
        IForgeRegistry<AbstractSpell> registry = SpellRegistry.REGISTRY.get();
        ITagManager<AbstractSpell> tagManager = registry.tags();

        if (tagManager == null) {
            return false;
        }

        ITag<AbstractSpell> tag = tagManager.getTag(tagKey);
        return tag.contains(spell);
    }
}
