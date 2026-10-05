package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registry aliases for content that moved from Alshanex's Familiars into Familiars Lib.
 * <p>
 * An alias makes the old id ({@code alshanex_familiars:pet_bed}) resolve to the new one
 * ({@code familiarslib:pet_bed}), so blocks in existing worlds, items in inventories, block entity
 * data (including familiars stored in houses), item components and recipe types keep working.
 */
public final class FMigration {
    public static final String OLD_NAMESPACE = "alshanex_familiars";

    public static void alias(DeferredRegister<?> register, String... paths) {
        for (String path : paths) {
            register.addAlias(
                    ResourceLocation.fromNamespaceAndPath(OLD_NAMESPACE, path),
                    ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, path));
        }
    }

    private FMigration() {}
}
