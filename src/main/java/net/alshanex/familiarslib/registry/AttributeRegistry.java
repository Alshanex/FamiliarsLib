package net.alshanex.familiarslib.registry;

import io.redspace.ironsspellbooks.api.attribute.MagicPercentAttribute;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class AttributeRegistry {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, FamiliarsLib.MODID);

    public static final RegistryObject<Attribute> SOUND_MAGIC_RESIST = newResistanceAttribute("sound");
    public static final RegistryObject<Attribute> SOUND_SPELL_POWER = newPowerAttribute("sound");

    private static RegistryObject<Attribute> newResistanceAttribute(String id) {
        return ATTRIBUTES.register(id + "_magic_resist", () -> (new MagicPercentAttribute("attribute.familiarslib." + id + "_magic_resist", 1.0D, -100, 100).setSyncable(true)));
    }

    private static RegistryObject<Attribute> newPowerAttribute(String id) {
        return ATTRIBUTES.register(id + "_spell_power", () -> (new MagicPercentAttribute("attribute.familiarslib." + id + "_spell_power", 1.0D, -100, 100).setSyncable(true)));
    }
}
