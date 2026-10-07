package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.familiars.FamiliarDyes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Marks shrunk dyes in their tooltip and explains what they do. */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FamiliarDyeTooltipHandler {

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!FamiliarDyes.isShrunkDye(event.getItemStack())) {
            return;
        }
        event.getToolTip().add(1, Component.translatable("tooltip.familiarslib.shrunk_dye").withStyle(ChatFormatting.LIGHT_PURPLE));
        event.getToolTip().add(2, Component.translatable("tooltip.familiarslib.shrunk_dye.usage").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(3, Component.translatable("tooltip.familiarslib.shrunk_dye.sneak").withStyle(ChatFormatting.DARK_GRAY));
    }
}