package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.consumables.ConsumableTooltipHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Automatically adds consumable tooltips to ALL items that have the familiar consumable component
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
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
