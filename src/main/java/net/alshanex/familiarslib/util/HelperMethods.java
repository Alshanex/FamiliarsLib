package net.alshanex.familiarslib.util;

import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class HelperMethods {
    public static float horizontalDistanceSqr(LivingEntity livingEntity, Vec3 vec3) {
        var dx = livingEntity.getX() - vec3.x;
        var dz = livingEntity.getZ() - vec3.z;
        return (float) (dx * dx + dz * dz);
    }

    //Util method to extract spells from your tags
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

    //Checks if a spell is inside a specific tag
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
