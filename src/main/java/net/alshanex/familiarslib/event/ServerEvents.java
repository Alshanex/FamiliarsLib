package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

@EventBusSubscriber(modid = FamiliarsLib.MODID)
public class ServerEvents {
    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent.Pre event){
        if(event.getSource().getEntity() instanceof AbstractSpellCastingPet pet){
            if(pet.getSummoner() != null && event.getEntity().is(pet.getSummoner())){
                event.setNewDamage(0f);
            }
        }
    }
}
