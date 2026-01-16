package net.alshanex.familiarslib.util.iron_spells;

import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import io.redspace.ironsspellbooks.item.weapons.IronsWeaponTier;
import net.alshanex.familiarslib.registry.AttributeRegistry;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public class StaffTier implements IronsWeaponTier {

    public static StaffTier SOUND_STAFF = new StaffTier(7, -3,
            new AttributeContainer(AttributeRegistry.SOUND_SPELL_POWER, .15, AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.CAST_TIME_REDUCTION, .15, AttributeModifier.Operation.MULTIPLY_BASE),
            new AttributeContainer(io.redspace.ironsspellbooks.api.registry.AttributeRegistry.SPELL_POWER, .05, AttributeModifier.Operation.MULTIPLY_BASE)
    );

    float damage;
    float speed;
    AttributeContainer[] attributes;

    public StaffTier(float damage, float speed, AttributeContainer... attributes) {
        this.damage = damage;
        this.speed = speed;
        this.attributes = attributes;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return damage;
    }

    public AttributeContainer[] getAdditionalAttributes() {
        return this.attributes;
    }
}
