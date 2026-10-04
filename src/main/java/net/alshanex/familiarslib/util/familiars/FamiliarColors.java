package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

/**
 * Spawn-time color helpers for familiars with color layers.
 */
public final class FamiliarColors {
    /** Chance that a naturally spawned dyeable familiar keeps its original colors. */
    public static final float NATURAL_DEFAULT_LOOK_CHANCE = 0.25F;

    /**
     * Gives a random dye color to {@link AbstractSpellCastingPet#DYE_LAYER_SLOT} for familiars that
     * spawn through the normal spawning paths (natural spawns, spawners, spawn eggs, /summon).
     */
    public static void rollNaturalDye(AbstractSpellCastingPet familiar, @Nullable MobSpawnType reason) {
        if (reason == null) {
            return;
        }
        RandomSource random = familiar.getRandom();
        if (random.nextFloat() < NATURAL_DEFAULT_LOOK_CHANCE) {
            return;
        }
        DyeColor[] colors = DyeColor.values();
        DyeColor color = colors[random.nextInt(colors.length)];
        familiar.setLayerColor(AbstractSpellCastingPet.DYE_LAYER_SLOT, color.getTextureDiffuseColor() & 0xFFFFFF);
    }

    private FamiliarColors() {}
}
