package net.alshanex.familiarslib.registry;

import io.redspace.ironsspellbooks.item.armor.UpgradeOrbType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import static io.redspace.ironsspellbooks.registries.UpgradeOrbTypeRegistry.UPGRADE_ORB_REGISTRY_KEY;

public class FUpgradeOrbRegistry {
    public static ResourceKey<UpgradeOrbType> SOUND_SPELL_POWER = ResourceKey.create(UPGRADE_ORB_REGISTRY_KEY, new ResourceLocation("familiarslib", "sound_power"));
}
