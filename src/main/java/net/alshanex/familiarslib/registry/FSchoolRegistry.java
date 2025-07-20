package net.alshanex.familiarslib.registry;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.datagen.FDamageTypes;
import net.alshanex.familiarslib.datagen.ItemTagProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class FSchoolRegistry {
    public static final ResourceLocation SOUND_RESOURCE = new ResourceLocation(FamiliarsLib.MODID, "sound");
    public static final DeferredRegister<SchoolType> SCHOOLS = DeferredRegister.create(SchoolRegistry.SCHOOL_REGISTRY_KEY, FamiliarsLib.MODID);


    public static final Supplier<SchoolType> SOUND = registerSchool(new SchoolType(
            SOUND_RESOURCE,
            ItemTagProvider.SOUND_FOCUS,
            Component.translatable("school.familiarslib.sound").withColor(0xCFFFD2),
            AttributeRegistry.SOUND_SPELL_POWER,
            AttributeRegistry.SOUND_MAGIC_RESIST,
            SoundRegistry.GUST_CAST,
            FDamageTypes.SOUND_MAGIC
    ));

    private static Supplier<SchoolType> registerSchool(SchoolType schoolType) {
        return SCHOOLS.register(schoolType.getId().getPath(), () -> schoolType);
    }
}
