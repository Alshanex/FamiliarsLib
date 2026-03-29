package net.alshanex.familiarslib.mixin;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class FamiliarAlliedMixin {
    @Inject(method = "isAlliedTo(Lnet/minecraft/world/entity/Entity;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void isAlliedTo(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Entity self = ((Entity) (Object) this);
        if (entity instanceof AbstractSpellCastingPet familiar && familiar.getSummoner() != null)
            cir.setReturnValue(self.isAlliedTo(familiar.getSummoner()) || self.equals(familiar.getSummoner()));

    }
}
