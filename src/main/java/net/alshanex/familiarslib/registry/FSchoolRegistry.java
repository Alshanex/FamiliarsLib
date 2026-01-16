package net.alshanex.familiarslib.registry;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.registries.SoundRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class FSchoolRegistry {
    public static final ResourceLocation SOUND_RESOURCE = new ResourceLocation(FamiliarsLib.MODID, "sound");
    public static final DeferredRegister<SchoolType> SCHOOLS = DeferredRegister.create(SchoolRegistry.SCHOOL_REGISTRY_KEY, FamiliarsLib.MODID);


    public static final Supplier<SchoolType> SOUND = registerSchool(new SchoolType(
            SOUND_RESOURCE,
            ModTags.SOUND_FOCUS,
            Component.translatable("school.familiarslib.sound").withStyle(Style.EMPTY.withColor(0xCFFFD2)),
            AttributeRegistry.SOUND_SPELL_POWER,
            AttributeRegistry.SOUND_MAGIC_RESIST,
            SoundRegistry.GUST_CAST,
            FDamageTypes.SOUND_MAGIC
    ));

    private static Supplier<SchoolType> registerSchool(SchoolType schoolType) {
        return SCHOOLS.register(schoolType.getId().getPath(), () -> schoolType);
    }
}
