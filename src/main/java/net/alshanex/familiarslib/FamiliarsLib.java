package net.alshanex.familiarslib;

import com.mojang.logging.LogUtils;
import net.alshanex.familiarslib.registry.*;
import net.alshanex.familiarslib.setup.ModSetup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(FamiliarsLib.MODID)
public class FamiliarsLib
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "familiarslib";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public FamiliarsLib(FMLJavaModLoadingContext context)
    {
        ModSetup.setup();

        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);


        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        AttributeRegistry.ATTRIBUTES.register(modEventBus);

        CriteriaTriggersRegistry.register();

        FParticleRegistry.register(modEventBus);

        FSchoolRegistry.SCHOOLS.register(modEventBus);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        modEventBus.addListener(CapabilityRegistry::register);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        //context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {

    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {

    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {

    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {

        }
    }
}
