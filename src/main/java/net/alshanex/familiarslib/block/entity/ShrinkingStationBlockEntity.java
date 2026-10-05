package net.alshanex.familiarslib.block.entity;

import net.alshanex.familiarslib.recipe.ShrinkingRecipe;
import net.alshanex.familiarslib.registry.FBlockEntityRegistry;
import net.alshanex.familiarslib.registry.FRecipeRegistry;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class ShrinkingStationBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final String NBT_ITEMS = "Items";
    private static final String NBT_IS_SHRINKABLE = "isShrinkable";
    private static final String NBT_COUNTER = "counter";

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int CONTAINER_SIZE = 2;

    private static final int[] SLOTS_FOR_UP = new int[]{INPUT_SLOT};
    private static final int[] SLOTS_FOR_DOWN = new int[]{OUTPUT_SLOT};
    private static final int[] SLOTS_FOR_SIDES = new int[]{INPUT_SLOT, OUTPUT_SLOT};

    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
    private boolean isShrinkable = false;
    private int counter = 0;

    // Cache the recipe to avoid looking it up every tick
    private RecipeHolder<ShrinkingRecipe> currentRecipe;

    private final IItemHandler[] itemHandlers = new IItemHandler[6];

    public ShrinkingStationBlockEntity(BlockPos pos, BlockState blockState) {
        super(FBlockEntityRegistry.SHRINKING_STATION.get(), pos, blockState);
    }

    public ItemStack getHeldItem() {
        ItemStack inputItem = items.get(INPUT_SLOT);
        if (!inputItem.isEmpty()) {
            return inputItem;
        }
        return items.get(OUTPUT_SLOT);
    }

    public ItemStack getInputItem() {
        return items.get(INPUT_SLOT);
    }

    public ItemStack getOutputItem() {
        return items.get(OUTPUT_SLOT);
    }

    public void setInputItem(ItemStack newItem) {
        items.set(INPUT_SLOT, newItem);
        updateShrinkableState();
        setChanged();
    }

    public void setOutputItem(ItemStack newItem) {
        items.set(OUTPUT_SLOT, newItem);
        setChanged();
    }

    private void updateShrinkableState() {
        if (level == null || level.isClientSide) return;

        ItemStack inputItem = items.get(INPUT_SLOT);
        ItemStack outputItem = items.get(OUTPUT_SLOT);

        this.counter = 0;

        if (inputItem.isEmpty() || !outputItem.isEmpty()) {
            this.isShrinkable = false;
            this.currentRecipe = null;
            return;
        }

        List<RecipeHolder<ShrinkingRecipe>> matches = level.getRecipeManager()
                .getRecipesFor(FRecipeRegistry.SHRINKING_RECIPE_TYPE.get(), new ShrinkingRecipe.Input(inputItem), level);

        this.currentRecipe = matches.stream()
                .max(java.util.Comparator.comparingInt((RecipeHolder<ShrinkingRecipe> holder) -> holder.value().priority()))
                .orElse(null);

        this.isShrinkable = (this.currentRecipe != null);
    }

    public boolean isShrinkable() {
        return isShrinkable;
    }

    public int getCounter() {
        return counter;
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case UP -> SLOTS_FOR_UP;
            case DOWN -> SLOTS_FOR_DOWN;
            default -> SLOTS_FOR_SIDES;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        if (index == INPUT_SLOT) {
            return items.get(INPUT_SLOT).isEmpty() && items.get(OUTPUT_SLOT).isEmpty();
        }
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction direction) {
        if (index == OUTPUT_SLOT) {
            return !items.get(OUTPUT_SLOT).isEmpty();
        }
        return false;
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack result = ContainerHelper.removeItem(this.items, index, count);
        if (!result.isEmpty()) {
            if (index == INPUT_SLOT) {
                updateShrinkableState();
            }
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack result = ContainerHelper.takeItem(this.items, index);
        if (index == INPUT_SLOT && !result.isEmpty()) {
            updateShrinkableState();
        }
        return result;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        this.items.set(index, stack);
        if (stack.getCount() > this.getMaxStackSize()) {
            stack.setCount(this.getMaxStackSize());
        }

        if (index == INPUT_SLOT) {
            updateShrinkableState();
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        this.items.clear();
        updateShrinkableState();
        setChanged();
    }

    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (side == null) return null;

        int index = side.ordinal();
        if (itemHandlers[index] == null) {
            itemHandlers[index] = new SidedInvWrapper(this, side);
        }
        return itemHandlers[index];
    }

    public void drops() {
        Containers.dropContents(this.level, this.worldPosition, this);
    }

    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        readNBT(pTag, pRegistries);
    }

    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, HolderLookup.Provider registryAccess) {
        writeNBT(tag, registryAccess);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        return saveWithoutMetadata(pRegistries);
    }

    private CompoundTag writeNBT(CompoundTag nbt, HolderLookup.Provider pRegistries) {
        ContainerHelper.saveAllItems(nbt, this.items, pRegistries);
        nbt.putBoolean(NBT_IS_SHRINKABLE, isShrinkable);
        nbt.putInt(NBT_COUNTER, counter);
        return nbt;
    }

    private CompoundTag readNBT(CompoundTag nbt, HolderLookup.Provider pRegistries) {
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(nbt, this.items, pRegistries);
        isShrinkable = nbt.getBoolean(NBT_IS_SHRINKABLE);
        counter = nbt.getInt(NBT_COUNTER);
        return nbt;
    }

    public void tick(Level pLevel, BlockPos pPos, BlockState pState) {
        if (pLevel.isClientSide()) {
            return;
        }

        // Ensure state is valid (e.g. after reload)
        if (!isShrinkable && !items.get(INPUT_SLOT).isEmpty() && items.get(OUTPUT_SLOT).isEmpty()) {
            updateShrinkableState();
        }

        if (isShrinkable && currentRecipe != null) {
            if (counter < 100) {
                counter++;
                setChanged();
            }

            if (counter >= 100) {
                completeShrink(pLevel, pPos, pState);
            }
        } else if (counter > 0) {
            // Reset if recipe becomes invalid mid-process
            counter = 0;
            setChanged();
        }
    }

    private void completeShrink(Level pLevel, BlockPos pPos, BlockState pState) {
        if (currentRecipe == null) return;

        ItemStack result = currentRecipe.value().assemble(new ShrinkingRecipe.Input(items.get(INPUT_SLOT)), pLevel.registryAccess());

        if (!result.isEmpty()) {
            MagicManager.spawnParticles(pLevel,
                    new BlastwaveParticleOptions(SchoolRegistry.EVOCATION.get().getTargetingColor(), 6),
                    pPos.getCenter().x, pPos.getCenter().y + .165f, pPos.getCenter().z,
                    1, 0, 0, 0, 0, true);

            items.set(OUTPUT_SLOT, result);
            items.get(INPUT_SLOT).shrink(1); // Consume input

            BlockState prevState = this.getBlockState();
            BlockState nextState = this.getBlockState();
            pLevel.sendBlockUpdated(pPos, prevState, nextState, Block.UPDATE_CLIENTS);
        }

        counter = 0;
        updateShrinkableState(); // Check if we can craft again
        setChanged();
    }
}