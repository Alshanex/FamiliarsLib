package net.alshanex.familiarslib.registry;

import io.redspace.ironsspellbooks.api.attribute.MagicPercentAttribute;
import io.redspace.ironsspellbooks.api.attribute.MagicRangedAttribute;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AttributeRegistry {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, FamiliarsLib.MODID);

    public static final DeferredHolder<Attribute, Attribute> FAMILIAR_DAMAGE = ATTRIBUTES.register("familiar_damage", () -> (new MagicPercentAttribute("attribute.familiarslib.familiar_damage", 1.0D, -100, 100.0D).setSyncable(true)));
    public static final DeferredHolder<Attribute, Attribute> FAMILIAR_RESIST = ATTRIBUTES.register("familiar_resist", () -> (new MagicPercentAttribute("attribute.familiarslib.familiar_resist", 1.0D, -100, 100.0D).setSyncable(true)));
}
