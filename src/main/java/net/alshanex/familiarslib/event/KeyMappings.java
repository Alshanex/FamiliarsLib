package net.alshanex.familiarslib.event;

import com.mojang.blaze3d.platform.InputConstants;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@EventBusSubscriber(value = Dist.CLIENT, modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.MOD)
public class KeyMappings {
    public static final String KEY_BIND_SUMMONING_CATEGORY= "key.familiarslib.summoning_keys";
    public static final KeyMapping SUMMONING_KEYMAP = new KeyMapping(getResourceName("summoning_key"), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_X, KEY_BIND_SUMMONING_CATEGORY);
    public static final KeyMapping SCREEN_KEYMAP = new KeyMapping(getResourceName("screen_key"), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_H, KEY_BIND_SUMMONING_CATEGORY);

    private static String getResourceName(String name) {
        return String.format("key.familiarslib.%s", name);
    }

    @SubscribeEvent
    public static void onRegisterKeybinds(RegisterKeyMappingsEvent event) {
        event.register(SUMMONING_KEYMAP);
        event.register(SCREEN_KEYMAP);
    }
}
