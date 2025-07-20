package net.alshanex.familiarslib.util;

import net.alshanex.familiarslib.item.AbstractFamiliarSpellbookItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.Optional;

public class CurioUtils {
    /**
     * This is used to check curio inventory for specific curio
     * Credits to GameTech for sharing.
     */
    public static boolean isWearingCurio(LivingEntity entity, Item curioItem) {
        return CuriosApi.getCuriosInventory(entity).map(curios ->
                !curios.findCurios(item -> item != null && item.is(curioItem)).isEmpty()
        ).orElse(false);
    }

    public static boolean isWearingFamiliarSpellbook(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(curios ->
                !curios.findCurios(item -> item != null && item.getItem() instanceof AbstractFamiliarSpellbookItem).isEmpty()
        ).orElse(false);
    }

    public static void broadcastCurioBreakEvent(SlotContext slotContext) {
        CuriosApi.broadcastCurioBreakEvent(slotContext);
    }

    public static Optional<ICurio> getCurio(ItemStack stack) {
        return CuriosApi.getCurio(stack);
    }
}
