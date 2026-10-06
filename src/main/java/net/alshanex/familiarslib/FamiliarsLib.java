package net.alshanex.familiarslib;

import net.alshanex.familiarslib.block.ShrinkingStationBlock;
import net.alshanex.familiarslib.block.entity.ShrinkingStationBlockEntity;
import net.alshanex.familiarslib.registry.*;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(FamiliarsLib.MODID)
public class FamiliarsLib {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "familiarslib";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public FamiliarsLib(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        AttachmentRegistry.register(modEventBus);

        AttributeRegistry.ATTRIBUTES.register(modEventBus);

        CriteriaTriggersRegistry.TRIGGERS.register(modEventBus);

        FParticleRegistry.register(modEventBus);

        // Bed colors on items used to be an Alshanex's Familiars component
        FMigration.alias(ComponentRegistry.COMPONENTS, "pet_bed_color");
        ComponentRegistry.COMPONENTS.register(modEventBus);

        // Shared familiar blocks: bed, house and shrinking station
        FBlockRegistry.register(modEventBus);
        FItemRegistry.register(modEventBus);
        FBlockEntityRegistry.register(modEventBus);
        FRecipeRegistry.register(modEventBus);
        FCreativeTab.register(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(this::registerCapabilities);

        modContainer.registerConfig(ModConfig.Type.SERVER, FamiliarsServerConfig.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {

    }

    // The shared blocks go in the vanilla Functional Blocks tab; familiar mods can list them in their own tabs too
    private void addCreative(BuildCreativeModeTabContentsEvent event) {

    }

    // Lets hoppers and pipes use the shrinking station (top: input, bottom: output, sides: both)
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, direction) -> {
                    // Upper half: use the block entity of the lower half
                    if (state.getValue(ShrinkingStationBlock.HALF) == DoubleBlockHalf.UPPER) {
                        if (level.getBlockEntity(pos.below()) instanceof ShrinkingStationBlockEntity lowerBe) {
                            return lowerBe.getItemHandler(direction);
                        }
                    } else if (blockEntity instanceof ShrinkingStationBlockEntity shrinkingStation) {
                        return shrinkingStation.getItemHandler(direction);
                    }
                    return null;
                },
                FBlockRegistry.SHRINKING_STATION.get()
        );
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}