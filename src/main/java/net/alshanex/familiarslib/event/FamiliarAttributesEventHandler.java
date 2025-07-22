package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.AbstractFamiliarSpellbookItem;
import net.alshanex.familiarslib.util.familiars.FamiliarAttributesHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Handles Familiar Spellbook attribute sharing
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FamiliarAttributesEventHandler {
    @SubscribeEvent
    public static void onCurioEquip(CurioChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            if (event.getTo().getItem() instanceof AbstractFamiliarSpellbookItem) {
                FamiliarAttributesHelper.handleSpellbookEquipChange(serverPlayer, true);
            }
        }
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            if (event.getFrom().getItem() instanceof AbstractFamiliarSpellbookItem) {
                FamiliarAttributesHelper.handleSpellbookEquipChange(serverPlayer, false);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.tickCount % 20 == 0) {
                FamiliarAttributesHelper.handlePlayerAttributeChange(serverPlayer);
            }
        }
    }
}
