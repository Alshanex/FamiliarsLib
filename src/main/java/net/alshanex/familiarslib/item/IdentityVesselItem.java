package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.client.compendium.ClientCompendium;
import net.alshanex.familiarslib.compendium.CompendiumCatalog;
import net.alshanex.familiarslib.data.FamiliarIdentity;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Takes a familiar's identity (its layer colors) and gives it to another familiar of the same type.
 */
public class IdentityVesselItem extends Item {

    public IdentityVesselItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ComponentRegistry.FAMILIAR_IDENTITY);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FamiliarIdentity identity = stack.get(ComponentRegistry.FAMILIAR_IDENTITY);
        if (identity == null) {
            tooltip.add(Component.translatable("item.familiarslib.identity_vessel.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.familiarslib.identity_vessel.usage_empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        Component familiarName = BuiltInRegistries.ENTITY_TYPE.get(identity.entityType()).getDescription();
        tooltip.add(Component.translatable("item.familiarslib.identity_vessel.contains", familiarName).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            int rgb = identity.color(slot);
            if (rgb < 0) continue;
            tooltip.add(Component.literal(" ").append(layerName(identity, slot)).append(": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("■ ").withStyle(style -> style.withColor(TextColor.fromRgb(rgb))))
                    .append(Component.literal(String.format("#%06X", rgb)).withStyle(ChatFormatting.WHITE)));
        }
        tooltip.add(Component.translatable("item.familiarslib.identity_vessel.usage_filled", familiarName).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The layer's name from the compendium (e.g. "Wood"), or "Layer N" if the familiar has no compendium colors. */
    private static Component layerName(FamiliarIdentity identity, int slot) {
        for (CompendiumCatalog.Section section : ClientCompendium.catalog().sections()) {
            if (section.entityType().equals(identity.entityType())) {
                for (CompendiumCatalog.LayerOptions layer : section.layers()) {
                    if (layer.slot() == slot) return Component.translatable(layer.nameKey());
                }
            }
        }
        return Component.translatable("item.familiarslib.identity_vessel.layer", slot + 1);
    }
}