package net.alshanex.familiarslib.registry;

import io.redspace.ironsspellbooks.registries.CreativeTabRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class FCreativeTab {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FamiliarsLib.MODID);

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = TABS.register("familiars_main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + FamiliarsLib.MODID + ".main_tab"))
            .icon(() -> new ItemStack(ItemRegistry.SOUND_UPGRADE_ORB))
            .displayItems((enabledFeatures, entries) -> {
                entries.accept(ItemRegistry.SOUND_RUNE.get());
                entries.accept(ItemRegistry.SOUND_UPGRADE_ORB.get());
            })
            .withTabsBefore(CreativeTabRegistry.EQUIPMENT_TAB.getId())
            .build());
}
