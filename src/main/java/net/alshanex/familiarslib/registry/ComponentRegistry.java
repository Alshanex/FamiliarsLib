package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableComponent;
import net.alshanex.familiarslib.util.consumables.FamiliarFoodComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

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
}