package net.alshanex.familiarslib.item;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellDataRegistryHolder;
import io.redspace.ironsspellbooks.item.UniqueSpellBook;
import io.redspace.ironsspellbooks.item.weapons.AttributeContainer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Generic class for Familiar Spellbook items
 */
public abstract class AbstractFamiliarSpellbookItem extends UniqueSpellBook {
    private static final Component DESCRIPTION = Component.translatable("item.familiarslib.familiar_spellbook.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);

    public AbstractFamiliarSpellbookItem() {
        super(SpellDataRegistryHolder.of(), 10);
        withSpellbookAttributes(getSpellbookAttributes());
    }

    protected AttributeContainer[] getSpellbookAttributes(){
        return new AttributeContainer[] {
                new AttributeContainer(AttributeRegistry.MAX_MANA, 200, AttributeModifier.Operation.ADD_VALUE),
                new AttributeContainer(AttributeRegistry.CAST_TIME_REDUCTION, 0.12, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                new AttributeContainer(AttributeRegistry.SPELL_POWER, 0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        };
    }

    @Override
    public void appendHoverText(@NotNull ItemStack itemStack, TooltipContext context, @NotNull List<Component> lines, @NotNull TooltipFlag flag) {
        super.appendHoverText(itemStack, context, lines, flag);
        lines.add(DESCRIPTION);
    }

    @Override
    public void initializeSpellContainer(ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }

        super.initializeSpellContainer(itemStack);
    }
}
