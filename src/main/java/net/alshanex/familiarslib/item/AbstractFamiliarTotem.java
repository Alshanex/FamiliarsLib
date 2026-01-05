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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public abstract class AbstractFamiliarTotem extends Item {
    public AbstractFamiliarTotem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(ItemStack stack, Player playerIn, LivingEntity target, InteractionHand hand) {
        if(!isValidPet(target)) return InteractionResult.FAIL;
        if(target instanceof AbstractSpellCastingPet pet && pet.getTotem()) return InteractionResult.FAIL;
        if (!bind(stack, target, playerIn)) return InteractionResult.FAIL;
        playerIn.swing(hand);
        playerIn.setItemInHand(hand, stack);
        return InteractionResult.SUCCESS;
    }

    protected boolean isValidPet(LivingEntity target){
        return target instanceof AbstractSpellCastingPet;
    }

    public boolean bind(ItemStack stack, LivingEntity target, Player player) {
        if (target.getCommandSenderWorld().isClientSide) return false;
        if (!(target instanceof AbstractSpellCastingPet pet && pet.getSummoner() != null && pet.getSummoner().is(player))) return false;
        if(stack.has(ComponentRegistry.SOUL_LINK)){
            var nbt = stack.get(ComponentRegistry.SOUL_LINK);
            UUID petUUID = nbt.getUUID("petUUID");
            stack.remove(ComponentRegistry.SOUL_LINK);
            if(!target.getUUID().equals(petUUID)){
                CompoundTag nbtSwap = new CompoundTag();
                nbtSwap.putUUID("petUUID", target.getUUID());
                if(target.hasCustomName()){
                    nbtSwap.putString("name", target.getCustomName().getString());
                }
                nbtSwap.putString("entity", EntityType.getKey(target.getType()).toString());
                target.saveWithoutId(nbtSwap);
                stack.set(ComponentRegistry.SOUL_LINK, nbtSwap);
            }
            if(target instanceof AbstractSpellCastingPet familiar) familiar.setTotem(true);
            return true;
        }
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID("petUUID", target.getUUID());
        if(target.hasCustomName()){
            nbt.putString("name", target.getCustomName().getString());
        }
        nbt.putString("entity", EntityType.getKey(target.getType()).toString());
        target.saveWithoutId(nbt);
        stack.set(ComponentRegistry.SOUL_LINK, nbt);
        if(target instanceof AbstractSpellCastingPet familiar) familiar.setTotem(true);
        return true;
    }

    public String getID(ItemStack stack) {
        var nbt = stack.get(ComponentRegistry.SOUL_LINK);
        if(nbt.contains("name")){
            return nbt.getString("name");
        }
        return nbt.getString("entity");
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!containsEntity(stack))
            return Component.translatable(super.getDescriptionId(stack));
        return Component.translatable(super.getDescriptionId(stack)).append(" (" + getID(stack) + ")");
    }

    public boolean containsEntity(ItemStack stack) {
        return !stack.isEmpty() && stack.has(ComponentRegistry.SOUL_LINK);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        tooltipComponents.add(Component.translatable("tooltip.familiarslib.totem").withStyle(ChatFormatting.WHITE));
    }
}
