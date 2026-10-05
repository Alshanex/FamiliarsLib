package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Item tags and rules for familiar cosmetics.
 * <p>
 * An item can be given to a familiar as a cosmetic when it's in {@link #HATS} or {@link #WEAPONS}
 * <b>and</b> it has been shrunk, which marks it with the {@code familiarslib:familiar_cosmetic} component.
 */
public final class FamiliarCosmetics {
    /** Items that can be shrunk and worn as a hat. */
    public static final TagKey<Item> HATS = tag("familiar_hats");
    /**
     * Items that can be shrunk into a weapon cosmetic. Which familiar can hold which weapon is decided
     * by each familiar's own weapon tag (see {@link #weaponTagFor}); add those tags to this one so
     * their items can be shrunk.
     */
    public static final TagKey<Item> WEAPONS = tag("familiar_weapons");

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, name));
    }

    /**
     * The default per-familiar weapon tag: {@code <entity namespace>:familiar_weapons/<entity path>}.
     * For example {@code alshanex_familiars:familiar_weapons/hunter_pet}.
     */
    public static TagKey<Item> weaponTagFor(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "familiar_weapons/" + id.getPath()));
    }

    /** True if the item is in either cosmetic tag (so it can be shrunk into a cosmetic). */
    public static boolean canBecomeCosmetic(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(HATS) || stack.is(WEAPONS));
    }

    /** True if the item has been shrunk into a familiar cosmetic. */
    public static boolean isShrunkCosmetic(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ComponentRegistry.FAMILIAR_COSMETIC.get());
    }

    /** Returns a single shrunk copy of the item, marked as a familiar cosmetic. */
    public static ItemStack shrink(ItemStack stack) {
        ItemStack result = stack.copyWithCount(1);
        result.set(ComponentRegistry.FAMILIAR_COSMETIC.get(), Unit.INSTANCE);
        return result;
    }

    /**
     * Which slot a shrunk cosmetic goes in on this familiar, or null if it can't be used.
     * Items in both tags go in the hat slot when sneaking, otherwise the weapon slot.
     */
    @Nullable
    public static FamiliarCosmeticSlot slotForEquip(AbstractSpellCastingPet familiar, ItemStack stack, boolean sneaking) {
        if (!isShrunkCosmetic(stack)) {
            return null;
        }
        boolean hat = familiar.canWearCosmeticHat(stack);
        boolean weapon = familiar.canHoldCosmeticWeapon(stack);
        if (hat && weapon) {
            return sneaking ? FamiliarCosmeticSlot.HAT : FamiliarCosmeticSlot.WEAPON;
        }
        return hat ? FamiliarCosmeticSlot.HAT : weapon ? FamiliarCosmeticSlot.WEAPON : null;
    }

    /** Which slot to empty when taking a cosmetic off: the weapon first, then the hat. Null if both are empty. */
    @Nullable
    public static FamiliarCosmeticSlot slotForRemove(AbstractSpellCastingPet familiar) {
        if (!familiar.getCosmetic(FamiliarCosmeticSlot.WEAPON).isEmpty()) {
            return FamiliarCosmeticSlot.WEAPON;
        }
        if (!familiar.getCosmetic(FamiliarCosmeticSlot.HAT).isEmpty()) {
            return FamiliarCosmeticSlot.HAT;
        }
        return null;
    }

    private FamiliarCosmetics() {}
}