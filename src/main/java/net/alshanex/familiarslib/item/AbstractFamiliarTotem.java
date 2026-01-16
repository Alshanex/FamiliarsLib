package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public abstract class AbstractFamiliarTotem extends Item {
    public AbstractFamiliarTotem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(ItemStack stack, Player playerIn, LivingEntity target, InteractionHand hand) {
        if (!isValidPet(target)) return InteractionResult.FAIL;
        if (target instanceof AbstractSpellCastingPet pet && pet.getTotem()) return InteractionResult.FAIL;
        if (!bind(stack, target, playerIn)) return InteractionResult.FAIL;
        playerIn.swing(hand);
        playerIn.setItemInHand(hand, stack);
        return InteractionResult.SUCCESS;
    }

    protected boolean isValidPet(LivingEntity target) {
        return target instanceof AbstractSpellCastingPet;
    }

    public boolean bind(ItemStack stack, LivingEntity target, Player player) {
        if (target.getCommandSenderWorld().isClientSide) return false;
        if (!(target instanceof AbstractSpellCastingPet pet && pet.getSummoner() != null && pet.getSummoner().is(player))) return false;

        if (ComponentRegistry.SOUL_LINK.has(stack)) {
            CompoundTag nbt = ComponentRegistry.SOUL_LINK.get(stack);
            UUID petUUID = nbt.getUUID("petUUID");

            ComponentRegistry.SOUL_LINK.remove(stack);

            if (!target.getUUID().equals(petUUID)) {
                CompoundTag nbtSwap = new CompoundTag();
                nbtSwap.putUUID("petUUID", target.getUUID());
                if (target.hasCustomName()) {
                    nbtSwap.putString("name", target.getCustomName().getString());
                }
                nbtSwap.putString("entity", EntityType.getKey(target.getType()).toString());
                target.saveWithoutId(nbtSwap);

                ComponentRegistry.SOUL_LINK.set(stack, nbtSwap);
            }

            if (target instanceof AbstractSpellCastingPet familiar) familiar.setTotem(true);
            return true;
        }

        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("petUUID", target.getUUID());
        if (target.hasCustomName()) {
            nbt.putString("name", target.getCustomName().getString());
        }
        nbt.putString("entity", EntityType.getKey(target.getType()).toString());
        target.saveWithoutId(nbt);

        ComponentRegistry.SOUL_LINK.set(stack, nbt);

        if (target instanceof AbstractSpellCastingPet familiar) familiar.setTotem(true);
        return true;
    }

    public String getID(ItemStack stack) {
        CompoundTag nbt = ComponentRegistry.SOUL_LINK.get(stack);
        if (nbt == null) return "";

        if (nbt.contains("name")) {
            return nbt.getString("name");
        }
        return nbt.getString("entity");
    }

    @Override
    public @NotNull Component getName(ItemStack stack) {
        if (!containsEntity(stack))
            return Component.translatable(super.getDescriptionId(stack));
        return Component.translatable(super.getDescriptionId(stack)).append(" (" + getID(stack) + ")");
    }

    public boolean containsEntity(ItemStack stack) {
        return !stack.isEmpty() && ComponentRegistry.SOUL_LINK.has(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.familiarslib.totem").withStyle(ChatFormatting.WHITE));
    }
}
