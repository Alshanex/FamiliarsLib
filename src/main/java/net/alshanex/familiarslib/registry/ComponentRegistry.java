package net.alshanex.familiarslib.registry;

import com.mojang.serialization.Codec;
import net.alshanex.familiarslib.util.SelectedFamiliarsComponent;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableComponent;
import net.alshanex.familiarslib.util.consumables.FamiliarFoodComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Optional;

public class ComponentRegistry {
    private static final String COMPONENT_ROOT = "familiarslib_components";

    public static final ItemComponent<FamiliarConsumableComponent> FAMILIAR_CONSUMABLE =
            new ItemComponent<>("familiar_consumable", FamiliarConsumableComponent.CODEC);

    public static final ItemComponent<FamiliarFoodComponent> FAMILIAR_FOOD =
            new ItemComponent<>("familiar_food", FamiliarFoodComponent.CODEC);

    public static final ItemComponent<SelectedFamiliarsComponent> SELECTED_FAMILIARS =
            new ItemComponent<>("selected_familiars", SelectedFamiliarsComponent.CODEC);

    public static final ItemComponent<CompoundTag> SOUL_LINK =
            new ItemComponent<>("soul_link", CompoundTag.CODEC);

    public static class ItemComponent<T> {
        private final String id;
        private final Codec<T> codec;

        public ItemComponent(String id, Codec<T> codec) {
            this.id = id;
            this.codec = codec;
        }

        public String getId() {
            return id;
        }

        /**
         * Get the component value from an ItemStack
         */
        @Nullable
        public T get(ItemStack stack) {
            if (stack.isEmpty()) return null;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(COMPONENT_ROOT)) return null;

            CompoundTag components = tag.getCompound(COMPONENT_ROOT);
            if (!components.contains(id)) return null;

            return codec.parse(NbtOps.INSTANCE, components.get(id))
                    .result()
                    .orElse(null);
        }

        /**
         * Get the component value, or a default if not present
         */
        public T getOrDefault(ItemStack stack, T defaultValue) {
            T value = get(stack);
            return value != null ? value : defaultValue;
        }

        /**
         * Get the component value as an Optional
         */
        public Optional<T> getOptional(ItemStack stack) {
            return Optional.ofNullable(get(stack));
        }

        /**
         * Check if the component is present on the stack
         */
        public boolean has(ItemStack stack) {
            if (stack.isEmpty()) return false;
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(COMPONENT_ROOT)) return false;
            return tag.getCompound(COMPONENT_ROOT).contains(id);
        }

        /**
         * Set the component value on an ItemStack
         */
        public void set(ItemStack stack, T value) {
            if (stack.isEmpty()) return;

            CompoundTag tag = stack.getOrCreateTag();
            CompoundTag components = tag.contains(COMPONENT_ROOT)
                    ? tag.getCompound(COMPONENT_ROOT)
                    : new CompoundTag();

            codec.encodeStart(NbtOps.INSTANCE, value)
                    .result()
                    .ifPresent(nbt -> components.put(id, nbt));

            tag.put(COMPONENT_ROOT, components);
        }

        /**
         * Remove the component from an ItemStack
         */
        public void remove(ItemStack stack) {
            if (stack.isEmpty()) return;

            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains(COMPONENT_ROOT)) return;

            CompoundTag components = tag.getCompound(COMPONENT_ROOT);
            components.remove(id);

            // Clean up empty component root
            if (components.isEmpty()) {
                tag.remove(COMPONENT_ROOT);
            }
        }

        /**
         * Update the component value using a modifier function
         */
        public void update(ItemStack stack, T defaultValue, java.util.function.UnaryOperator<T> modifier) {
            T current = getOrDefault(stack, defaultValue);
            set(stack, modifier.apply(current));
        }
    }

    /**
     * Check if a stack has any familiar components
     */
    public static boolean hasAnyComponent(ItemStack stack) {
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        if (tag == null) return false;
        return tag.contains(COMPONENT_ROOT) && !tag.getCompound(COMPONENT_ROOT).isEmpty();
    }

    /**
     * Copy all components from one stack to another
     */
    public static void copyComponents(ItemStack from, ItemStack to) {
        if (from.isEmpty() || to.isEmpty()) return;

        CompoundTag fromTag = from.getTag();
        if (fromTag == null || !fromTag.contains(COMPONENT_ROOT)) return;

        CompoundTag toTag = to.getOrCreateTag();
        toTag.put(COMPONENT_ROOT, fromTag.getCompound(COMPONENT_ROOT).copy());
    }
}
