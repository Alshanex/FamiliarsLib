package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.FamiliarStorageBlockEntity;
import net.alshanex.familiarslib.block.entity.PetBedBlockEntity;
import net.alshanex.familiarslib.block.entity.ShrinkingStationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class FBlockEntityRegistry {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FamiliarsLib.MODID);

    public static final Supplier<BlockEntityType<PetBedBlockEntity>> PET_BED = BLOCK_ENTITIES.register("pet_bed",
            () -> BlockEntityType.Builder.of(PetBedBlockEntity::new, FBlockRegistry.PET_BED.get()).build(null));

    public static final Supplier<BlockEntityType<FamiliarStorageBlockEntity>> FAMILIAR_STORAGE = BLOCK_ENTITIES.register("familiar_storage",
            () -> BlockEntityType.Builder.of(FamiliarStorageBlockEntity::new, FBlockRegistry.FAMILIAR_STORAGE.get()).build(null));

    public static final Supplier<BlockEntityType<ShrinkingStationBlockEntity>> SHRINKING_STATION = BLOCK_ENTITIES.register("shrinking_station",
            () -> BlockEntityType.Builder.of(ShrinkingStationBlockEntity::new, FBlockRegistry.SHRINKING_STATION.get()).build(null));

    public static void register(IEventBus eventBus) {
        FMigration.alias(BLOCK_ENTITIES, "pet_bed", "familiar_storage", "shrinking_station");
        BLOCK_ENTITIES.register(eventBus);
    }
}
