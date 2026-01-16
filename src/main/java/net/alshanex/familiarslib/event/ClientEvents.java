package net.alshanex.familiarslib.event;

import io.redspace.ironsspellbooks.player.KeyState;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.network.RequestFamiliarSelectionPacket;
import net.alshanex.familiarslib.network.SummonPetPackage;
import net.alshanex.familiarslib.setup.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

import static net.alshanex.familiarslib.event.KeyMappings.SCREEN_KEYMAP;
import static net.alshanex.familiarslib.event.KeyMappings.SUMMONING_KEYMAP;

@Mod.EventBusSubscriber(modid = FamiliarsLib.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    private static final ArrayList<KeyState> KEY_STATES = new ArrayList<>();
    private static final KeyState SUMMON_STATE = register(SUMMONING_KEYMAP);
    private static final KeyState SCREEN_STATE = register(SCREEN_KEYMAP);

    private static KeyState register(KeyMapping key) {
        var k = new KeyState(key);
        KEY_STATES.add(k);
        return k;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        handleInputEvent(event.getKey(), event.getAction());
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        handleInputEvent(event.getButton(), event.getAction());
    }

    private static void handleInputEvent(int button, int action) {
        var minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        if (SUMMON_STATE.wasPressed() && minecraft.screen == null) {
            NetworkHandler.sendToServer(new SummonPetPackage());
        }
        if (SCREEN_STATE.wasPressed() && minecraft.screen == null) {
            NetworkHandler.sendToServer(new RequestFamiliarSelectionPacket());
        }
        update();
    }

    private static void update() {
        for (KeyState k : KEY_STATES) {
            k.update();
        }
    }
}
