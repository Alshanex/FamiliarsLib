package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.PetBedBlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class FItemRegistry {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, FamiliarsLib.MODID);

    public static final DeferredHolder<Item, Item> PET_BED = ITEMS.register("pet_bed",
            () -> new PetBedBlockItem(FBlockRegistry.PET_BED.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> FAMILIAR_STORAGE = ITEMS.register("familiar_storage",
            () -> new BlockItem(FBlockRegistry.FAMILIAR_STORAGE.get(), new Item.Properties()));

    public static final DeferredHolder<Item, BlockItem> SHRINKING_STATION = ITEMS.register("shrinking_station",
            () -> new BlockItem(FBlockRegistry.SHRINKING_STATION.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        FMigration.alias(ITEMS, "pet_bed", "familiar_storage", "shrinking_station");
        ITEMS.register(eventBus);
    }
}
