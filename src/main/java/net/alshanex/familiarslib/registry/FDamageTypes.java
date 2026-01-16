package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class FDamageTypes {
    public static ResourceKey<DamageType> register(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(FamiliarsLib.MODID, name));
    }

    public static final ResourceKey<DamageType> SOUND_MAGIC = register("sound_magic");
}
