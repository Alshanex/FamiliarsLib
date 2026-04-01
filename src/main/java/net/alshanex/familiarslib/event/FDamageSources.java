package net.alshanex.familiarslib.event;

import io.redspace.ironsspellbooks.api.util.Utils;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttributeRegistry;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber
public class FDamageSources {
    @SubscribeEvent
    public static void preHitEffects(LivingIncomingDamageEvent event) {
        var damageSource = event.getSource();

        AbstractSpellCastingPet fromFamiliar = damageSource.getDirectEntity() instanceof AbstractSpellCastingPet familiar ? familiar : damageSource.getEntity() instanceof AbstractSpellCastingPet familiar ? familiar : null;
        if (fromFamiliar != null) {
            var summoner = fromFamiliar.getSummoner();
            if (summoner != null && summoner.getUUID().equals(event.getEntity().getUUID())) {
                event.setCanceled(true);
                return;
            }
            if (summoner instanceof LivingEntity livingSummoner) {
                event.setAmount(event.getAmount() * (float) livingSummoner.getAttributeValue(AttributeRegistry.FAMILIAR_DAMAGE));
            }
        }

        LivingEntity target = event.getEntity();
        if (target instanceof AbstractSpellCastingPet familiarTarget) {
            var summoner = familiarTarget.getSummoner();
            if (summoner instanceof LivingEntity livingSummoner) {
                event.setAmount(event.getAmount() * getFamiliarsResist(livingSummoner));
            }
        }
    }

    public static float getFamiliarsResist(LivingEntity summoner){
        return 2 - (float) Utils.softCapFormula(summoner.getAttributeValue(AttributeRegistry.FAMILIAR_RESIST));
    }
}
