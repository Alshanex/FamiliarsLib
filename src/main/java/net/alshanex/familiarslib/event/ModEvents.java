package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.registry.AttributeRegistry;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEvents {
    @SubscribeEvent
    public static void modifyEntityAttributes(EntityAttributeModificationEvent e) {
        e.getTypes().forEach(entity -> AttributeRegistry.ATTRIBUTES.getEntries().forEach(attribute -> e.add(entity, attribute.get())));
    }
}
