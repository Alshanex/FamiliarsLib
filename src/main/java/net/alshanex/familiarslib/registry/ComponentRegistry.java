package net.alshanex.familiarslib.registry;

import com.google.common.base.Suppliers;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.SelectedFamiliarsComponent;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableComponent;
import net.alshanex.familiarslib.util.consumables.FamiliarFoodComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Registry for data components
 */
public class ComponentRegistry {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, FamiliarsLib.MODID);

    public static final Supplier<DataComponentType<FamiliarConsumableComponent>> FAMILIAR_CONSUMABLE =
            COMPONENTS.register("familiar_consumable", () ->
                    DataComponentType.<FamiliarConsumableComponent>builder()
                            .persistent(FamiliarConsumableComponent.CODEC)
                            .networkSynchronized(FamiliarConsumableComponent.STREAM_CODEC)
                            .build()
            );

    public static final Supplier<DataComponentType<FamiliarFoodComponent>> FAMILIAR_FOOD =
            COMPONENTS.register("familiar_food", () ->
                    DataComponentType.<FamiliarFoodComponent>builder()
                            .persistent(FamiliarFoodComponent.CODEC)
                            .networkSynchronized(FamiliarFoodComponent.STREAM_CODEC)
                            .build()
            );

    public static final Supplier<DataComponentType<SelectedFamiliarsComponent>> SELECTED_FAMILIARS = COMPONENTS.register(
            "selected_familiars",
            () -> DataComponentType.<SelectedFamiliarsComponent>builder()
                    .persistent(SelectedFamiliarsComponent.CODEC)
                    .networkSynchronized(SelectedFamiliarsComponent.STREAM_CODEC)
                    .build()
    );

    public static final Supplier<DataComponentType<CompoundTag>> SOUL_LINK = register("soul_link", CompoundTag::new, op -> op.persistent(CompoundTag.CODEC));

    private static <T> ComponentSupplier<T> register(String name, Supplier<T> defaultVal, UnaryOperator<DataComponentType.Builder<T>> op) {
        var registered = COMPONENTS.register(name, () -> op.apply(DataComponentType.builder()).build());
        return new ComponentSupplier<>(registered, defaultVal);
    }

    public static class ComponentSupplier<T> implements Supplier<DataComponentType<T>> {
        private final Supplier<DataComponentType<T>> type;
        private final Supplier<T> defaultSupplier;

        public ComponentSupplier(Supplier<DataComponentType<T>> type, Supplier<T> defaultSupplier) {
            this.type = type;
            this.defaultSupplier = Suppliers.memoize(defaultSupplier::get);
        }

        public T get(ItemStack stack) {
            return stack.getOrDefault(type, defaultSupplier.get());
        }

        @Override
        public DataComponentType<T> get() {
            return type.get();
        }
    }
}