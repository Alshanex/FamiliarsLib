package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.block.PetBedBlock;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class PetBedBlockItem extends BlockItem {

    public PetBedBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        ItemStack itemStack = context.getItemInHand();

        if (itemStack.has(ComponentRegistry.PET_BED_COLOR)) {
            String colorName = itemStack.get(ComponentRegistry.PET_BED_COLOR).getString("color");
            DyeColor color = DyeColor.byName(colorName, DyeColor.WHITE);

            if (state.hasProperty(PetBedBlock.COLOR)) {
                state = state.setValue(PetBedBlock.COLOR, color);
            }
        }

        return super.placeBlock(context, state);
    }

    public static ItemStack withColor(ItemStack stack, DyeColor color) {
        CompoundTag colorTag = new CompoundTag();
        colorTag.putString("color", color.getName());
        stack.set(ComponentRegistry.PET_BED_COLOR, colorTag);

        return stack;
    }
}
