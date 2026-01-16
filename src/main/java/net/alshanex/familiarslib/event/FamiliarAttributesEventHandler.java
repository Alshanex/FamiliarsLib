package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.AbstractFamiliarSpellbookItem;
import net.alshanex.familiarslib.util.familiars.FamiliarAttributesHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

/**
 * Handles Familiar Spellbook attribute sharing
 */
@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.tickCount % 20 == 0) {
                FamiliarAttributesHelper.handlePlayerAttributeChange(serverPlayer);
            }
        }
    }
}
