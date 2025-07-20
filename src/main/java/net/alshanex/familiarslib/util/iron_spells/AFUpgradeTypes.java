package net.alshanex.familiarslib.util.iron_spells;

import io.redspace.ironsspellbooks.item.armor.UpgradeType;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.registry.AttributeRegistry;
import net.alshanex.familiarslib.registry.ItemRegistry;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;

import java.util.Optional;

public enum AFUpgradeTypes implements UpgradeType {
    SOUND_SPELL_POWER("sound_power", ItemRegistry.SOUND_UPGRADE_ORB, AttributeRegistry.SOUND_SPELL_POWER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, .05f);

    final Holder<Attribute> attribute;
    final AttributeModifier.Operation operation;
    final float amountPerUpgrade;
    final ResourceLocation id;
    final Optional<Holder<Item>> containerItem;

    AFUpgradeTypes(String key, Holder<Item> containerItem, Holder<Attribute> attribute, AttributeModifier.Operation operation, float amountPerUpgrade) {
        this(key, Optional.of(containerItem), attribute, operation, amountPerUpgrade);
    }

    AFUpgradeTypes(String key, Optional<Holder<Item>> containerItem, Holder<Attribute> attribute, AttributeModifier.Operation operation, float amountPerUpgrade) {
        this.id = new ResourceLocation(FamiliarsLib.MODID, key);
        this.attribute = attribute;
        this.operation = operation;
        this.amountPerUpgrade = amountPerUpgrade;
        this.containerItem = containerItem;
        UpgradeType.registerUpgrade(this);
    }

    @Override
    public Holder<Attribute> getAttribute() {
        return attribute;
    }

    @Override
    public AttributeModifier.Operation getOperation() {
        return operation;
    }

    @Override
    public float getAmountPerUpgrade() {
        return amountPerUpgrade;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public Optional<Holder<Item>> getContainerItem() {
        return containerItem;
    }
}
