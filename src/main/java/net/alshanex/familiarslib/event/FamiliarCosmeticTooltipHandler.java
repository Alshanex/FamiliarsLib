package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmetics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Marks shrunk cosmetics in their tooltip and explains how to use them. */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FamiliarCosmeticTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!FamiliarCosmetics.isShrunkCosmetic(stack)) {
            return;
        }

        String kind = stack.is(FamiliarCosmetics.HATS) && stack.is(FamiliarCosmetics.WEAPONS) ? "hat_or_weapon"
                : stack.is(FamiliarCosmetics.HATS) ? "hat"
                : stack.is(FamiliarCosmetics.WEAPONS) ? "weapon"
                : null;
        if (kind == null) {
            return; // no longer in a cosmetic tag (e.g. a datapack removed it)
        }

        event.getToolTip().add(1, Component.translatable("tooltip.familiarslib.cosmetic." + kind).withStyle(ChatFormatting.LIGHT_PURPLE));
        event.getToolTip().add(2, Component.translatable("tooltip.familiarslib.cosmetic.usage").withStyle(ChatFormatting.GRAY));
    }
}
