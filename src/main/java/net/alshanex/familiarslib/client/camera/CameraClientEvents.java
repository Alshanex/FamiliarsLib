package net.alshanex.familiarslib.client.camera;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.camera.CameraTargeting;
import net.alshanex.familiarslib.item.camera.FamiliarCameraItem;
import net.alshanex.familiarslib.network.ServerboundTakePhotoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@EventBusSubscriber(modid = FamiliarsLib.MODID, value = Dist.CLIENT)
public final class CameraClientEvents {
    /** FOV multiplier while zoomed (the spyglass uses 0.1). */
    public static final float ZOOM_FOV = 0.2F;
    public static final int FLASH_TICKS = 6;

    @Nullable
    private static AbstractSpellCastingPet target;
    private static int flashTicks;

    private CameraClientEvents() {}

    // State

    /** Holding the camera in the main hand, or zooming with it from either hand. */
    public static boolean isCameraActive(Player player) {
        return !FamiliarCameraItem.activeCamera(player).isEmpty();
    }

    /** Zoomed in, first person: show the viewfinder. */
    public static boolean isZoomed(Player player) {
        return player.isUsingItem() && FamiliarCameraItem.isCamera(player.getUseItem())
                && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    @Nullable
    public static AbstractSpellCastingPet target() {
        return target != null && CameraTargeting.canBePhotographed(target) ? target : null;
    }

    public static int flashTicks() {
        return flashTicks;
    }

    // Left click

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (flashTicks > 0) flashTicks--;
        if (player == null || mc.level == null) {
            target = null;
            return;
        }

        boolean active = isCameraActive(player);
        // Aims at where familiars are drawn, not at their hitbox (a jumping familiar can be blocks above it)
        target = active ? CameraTargeting.findTarget(player, 1.0F, isZoomed(player), FamiliarRenderBounds::boxFor) : null;
        if (!active || mc.screen != null) {
            return;
        }

        boolean clicked = false;
        while (mc.options.keyAttack.consumeClick()) {
            clicked = true;
        }
        if (clicked) {
            takePhoto(mc, player);
        }
    }

    /** Holding left click with the camera must not mine blocks or hit mobs. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.isAttack() && player != null && isCameraActive(player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void takePhoto(Minecraft mc, LocalPlayer player) {
        ItemStack camera = FamiliarCameraItem.activeCamera(player);
        if (player.getCooldowns().isOnCooldown(camera.getItem())) {
            return;
        }

        AbstractSpellCastingPet subject = target();
        if (subject == null) {
            fail(mc, player, "message.familiarslib.camera.no_subject");
            return;
        }
        if (FamiliarCameraItem.paperCount(player) == 0) {
            fail(mc, player, "message.familiarslib.camera.no_paper");
            return;
        }

        Vector3f aimOffset = FamiliarRenderBounds.boxFor(subject).getCenter().subtract(subject.position()).toVector3f();
        PacketDistributor.sendToServer(new ServerboundTakePhotoPacket(subject.getId(), aimOffset, CameraPoseCapture.capture(subject)));
        flashTicks = FLASH_TICKS;
    }

    private static void fail(Minecraft mc, LocalPlayer player, String messageKey) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.6F, 0.4F));
        player.displayClientMessage(Component.translatable(messageKey), true);
    }

    // Zoom

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        if (isZoomed(event.getPlayer())) {
            event.setNewFovModifier(ZOOM_FOV);
        }
    }

    /** Slower mouse while zoomed, like the spyglass (about 1/8 of the normal turn speed). */
    @SubscribeEvent
    public static void onPlayerTurn(CalculatePlayerTurnEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && isZoomed(player)) {
            double base = (event.getMouseSensitivity() * 0.6D + 0.2D) * 0.5D;
            event.setMouseSensitivity(Math.max(0.0D, (base - 0.2D) / 0.6D));
        }
    }

    /** No hand or camera model in front of the viewfinder. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && isZoomed(player)) {
            event.setCanceled(true);
        }
    }

    /** The viewfinder draws its own focus marks instead of the crosshair. */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && event.getName().equals(VanillaGuiLayers.CROSSHAIR) && isZoomed(player)) {
            event.setCanceled(true);
        }
    }
}
