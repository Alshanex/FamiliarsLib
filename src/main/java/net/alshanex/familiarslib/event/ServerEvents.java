package net.alshanex.familiarslib.event;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractMeleeSpellCastingPet;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.AbstractMultiSelectionCurio;
import net.alshanex.familiarslib.util.CurioUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID)
public class ServerEvents {
    /**
     * Handles familiars attacks to not hurt their owner
     */
    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent event){
        if(event.getSource().getEntity() instanceof AbstractSpellCastingPet pet){
            if(pet.isAlliedTo(event.getEntity())){
                event.setAmount(0f);
            }
        }
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer) || serverPlayer.level().isClientSide) {
            return;
        }

        ItemStack fromStack = event.getFrom();

        // Solo procesar si se desequipó una Multi Selection curio
        if (!fromStack.isEmpty() && fromStack.getItem() instanceof AbstractMultiSelectionCurio) {
            FamiliarsLib.LOGGER.debug("Detected Multi Selection curio unequip for player {}",
                    serverPlayer.getName().getString());

            try {
                CurioUtils.handleMultiSelectionCurioUnequip(serverPlayer, fromStack);
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error handling Multi Selection curio unequip for player {}: ",
                        serverPlayer.getName().getString(), e);
            }
        }
    }
}
