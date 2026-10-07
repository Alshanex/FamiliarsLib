package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.compendium.FamiliarCompendium;
import net.alshanex.familiarslib.data.FamiliarIdentity;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.IdentityVesselItem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.alshanex.familiarslib.util.familiars.FamiliarSync;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Using an {@link IdentityVesselItem} on a familiar. It only works on familiars tamed by the player using it.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FamiliarIdentityHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractSpellCastingPet familiar)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof IdentityVesselItem)) {
            return;
        }
        Player player = event.getEntity();
        event.setCanceled(true); // a vessel never does anything else on a familiar

        // Only works on familiars tamed by this player: wild ones and other players' familiars are refused
        UUID owner = familiar.getOwnerUUID();
        if (owner == null || !owner.equals(player.getUUID())) {
            event.setCancellationResult(InteractionResult.FAIL);
            if (!player.level().isClientSide) {
                player.displayClientMessage(Component.translatable("message.familiarslib.identity_vessel.not_yours"), true);
            }
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        FamiliarIdentity identity = stack.get(ComponentRegistry.FAMILIAR_IDENTITY);
        if (identity == null) {
            extract(serverPlayer, familiar, stack);
        } else {
            pour(serverPlayer, familiar, stack, identity);
        }
    }

    /** Empty vessel: take the familiar's colors into it and reset the familiar. */
    private static void extract(ServerPlayer player, AbstractSpellCastingPet familiar, ItemStack stack) {
        List<Integer> colors = new ArrayList<>();
        boolean anyColor = false;
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            int rgb = familiar.getLayerColor(slot);
            colors.add(rgb);
            anyColor |= rgb >= 0;
        }
        if (!anyColor) {
            player.displayClientMessage(Component.translatable("message.familiarslib.identity_vessel.nothing_to_take"), true);
            return;
        }

        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(familiar.getType());
        stack.set(ComponentRegistry.FAMILIAR_IDENTITY, new FamiliarIdentity(typeId, colors));
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            familiar.setLayerColor(slot, AbstractSpellCastingPet.NO_LAYER_COLOR);
        }
        finish(player, familiar, SoundEvents.BOTTLE_FILL);
    }

    /** Filled vessel: give its colors to a familiar of the same type, mixing with colors it already has. */
    private static void pour(ServerPlayer player, AbstractSpellCastingPet familiar, ItemStack stack, FamiliarIdentity identity) {
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(familiar.getType());
        if (!identity.entityType().equals(typeId)) {
            player.displayClientMessage(Component.translatable("message.familiarslib.identity_vessel.wrong_familiar",
                    BuiltInRegistries.ENTITY_TYPE.get(identity.entityType()).getDescription()), true);
            return;
        }

        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            int incoming = identity.color(slot);
            if (incoming < 0) {
                continue; // that layer was in its original look: leave the receiver's layer alone
            }
            int current = familiar.getLayerColor(slot);
            familiar.setLayerColor(slot, current < 0 ? incoming : mix(current, incoming));
        }
        stack.remove(ComponentRegistry.FAMILIAR_IDENTITY);
        finish(player, familiar, SoundEvents.BOTTLE_EMPTY);
    }

    /**
     * Mixes two colors the way vanilla mixes dyes on leather armor: averages the channels, then restores the
     * average brightness, so mixing doesn't make colors muddy and dark.
     */
    static int mix(int a, int b) {
        int r = ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2;
        int g = ((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        float averageBrightness = (max(a) + max(b)) / 2F;
        float mixedBrightness = Math.max(r, Math.max(g, bl));
        if (mixedBrightness > 0) {
            float scale = averageBrightness / mixedBrightness;
            r = Math.min(255, Math.round(r * scale));
            g = Math.min(255, Math.round(g * scale));
            bl = Math.min(255, Math.round(bl * scale));
        }
        return (r << 16) | (g << 8) | bl;
    }

    private static int max(int rgb) {
        return Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF));
    }

    /** Feedback, then keep the roster snapshot in sync, and unlock any compendium colors the familiar now has. */
    private static void finish(ServerPlayer player, AbstractSpellCastingPet familiar, SoundEvent sound) {
        familiar.level().playSound(null, familiar, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        familiar.triggerAnim("interact_controller", "interact");
        FamiliarManager.updateFamiliarData(familiar);
        FamiliarSync.snapshot(player, familiar.getUUID());
        FamiliarCompendium.discover(player, familiar);
    }
}