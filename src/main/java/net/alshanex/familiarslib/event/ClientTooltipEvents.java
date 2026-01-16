package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.consumables.ConsumableTooltipHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Automatically adds consumable tooltips to ALL items that have the familiar consumable component
 */
@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientTooltipEvents {

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // Automatically add consumable tooltips to any item that has the component
        ConsumableTooltipHelper.addConsumableTooltip(
                event.getItemStack(),
                event.getToolTip(),
                event.getFlags()
        );
    }
}
