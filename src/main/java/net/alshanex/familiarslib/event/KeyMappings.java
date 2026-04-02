package net.alshanex.familiarslib.event;

import com.mojang.blaze3d.platform.InputConstants;
import io.redspace.ironsspellbooks.player.ExtendedKeyMapping;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(value = Dist.CLIENT, modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.MOD)
public class KeyMappings {
    public static final String KEY_BIND_SUMMONING_CATEGORY= "key.familiarslib.summoning_keys";
    public static final String KEY_BIND_QUICK_SUMMON_CATEGORY = "key.familiarslib.quick_summoning_keys";

    public static final ExtendedKeyMapping SUMMONING_KEYMAP = new ExtendedKeyMapping(getResourceName("summoning_key"), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_X, KEY_BIND_SUMMONING_CATEGORY);
    public static final ExtendedKeyMapping SCREEN_KEYMAP = new ExtendedKeyMapping(getResourceName("screen_key"), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_H, KEY_BIND_SUMMONING_CATEGORY);
    public static final List<ExtendedKeyMapping> QUICK_SUMMONING_MAPPINGS = createQuickSummonKeybinds();

    private static String getResourceName(String name) {
        return String.format("key.familiarslib.%s", name);
    }

    @SubscribeEvent
    public static void onRegisterKeybinds(RegisterKeyMappingsEvent event) {
        event.register(SUMMONING_KEYMAP);
        event.register(SCREEN_KEYMAP);

        QUICK_SUMMONING_MAPPINGS.forEach(event::register);
    }

    private static List<ExtendedKeyMapping> createQuickSummonKeybinds() {
        var qcm = new ArrayList<ExtendedKeyMapping>();
        for (int i = 1; i <= 10; i++) {
            qcm.add(new ExtendedKeyMapping(getResourceName(String.format("familiar_quick_summon_%d", i)), KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEY_BIND_QUICK_SUMMON_CATEGORY));
        }
        return qcm;
    }
}
