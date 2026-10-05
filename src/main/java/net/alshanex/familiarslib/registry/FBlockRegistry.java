package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.FamiliarStorageBlock;
import net.alshanex.familiarslib.block.PetBedBlock;
import net.alshanex.familiarslib.block.ShrinkingStationBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Blocks shared by every familiar mod: the familiar bed, the familiar house and the shrinking station. */
public class FBlockRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FamiliarsLib.MODID);

    public static final Supplier<Block> PET_BED = BLOCKS.register("pet_bed",
            () -> new PetBedBlock(Block.Properties.of().mapColor(MapColor.METAL).strength(0.5F, 6.0F)));

    public static final Supplier<Block> FAMILIAR_STORAGE = BLOCKS.register("familiar_storage",
            () -> new FamiliarStorageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_WOOD).noOcclusion()));

    public static final Supplier<Block> SHRINKING_STATION = BLOCKS.register("shrinking_station",
            () -> new ShrinkingStationBlock(BlockBehaviour.Properties.of()));

    public static void register(IEventBus eventBus) {
        // These blocks used to be registered by Alshanex's Familiars: keep old worlds loading them
        FMigration.alias(BLOCKS, "pet_bed", "familiar_storage", "shrinking_station");
        BLOCKS.register(eventBus);
    }
}
