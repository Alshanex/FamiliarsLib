package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default egg colors per familiar type, and which entity types are familiars.
 */
public final class FamiliarEggColors {
    /** Not a familiar. */
    private static final int NOT_A_FAMILIAR = Integer.MIN_VALUE;

    private static final Map<EntityType<?>, Integer> CACHE = new ConcurrentHashMap<>();

    private FamiliarEggColors() {}

    /** The default egg color of this familiar type (0xRRGGBB). The original beige if it can't be worked out yet. */
    public static int defaultColor(EntityType<?> type, @Nullable Level level) {
        int color = lookup(type, level);
        return color == NOT_A_FAMILIAR ? EggColor.DEFAULT_RGB : color;
    }

    /** True if this entity type is a familiar. */
    public static boolean isFamiliar(EntityType<?> type, @Nullable Level level) {
        Integer cached = CACHE.get(type);
        if (cached != null) return cached != NOT_A_FAMILIAR;
        return level != null && lookup(type, level) != NOT_A_FAMILIAR;
    }

    /** Every familiar type, in registry order. */
    public static List<EntityType<?>> familiarTypes(Level level) {
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.canSummon() && type.getCategory() != MobCategory.MISC && isFamiliar(type, level)) {
                types.add(type);
            }
        }
        return types;
    }

    private static int lookup(EntityType<?> type, @Nullable Level level) {
        Integer cached = CACHE.get(type);
        if (cached != null) return cached;
        if (level == null) return EggColor.DEFAULT_RGB; // not cached: try again once there is a level

        int color = NOT_A_FAMILIAR;
        try {
            Entity entity = type.create(level);
            if (entity instanceof AbstractSpellCastingPet familiar) {
                color = familiar.getEggColor().resolve(familiar);
            }
        } catch (Exception e) {
            FamiliarsLib.LOGGER.debug("Familiar eggs: skipping {}", BuiltInRegistries.ENTITY_TYPE.getKey(type), e);
        }
        CACHE.put(type, color);
        return color;
    }
}