package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.compendium.FamiliarCompendium;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarColorOverrides;
import net.alshanex.familiarslib.util.familiars.FamiliarDyes;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.alshanex.familiarslib.util.familiars.FamiliarSync;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Recolors a tamed familiar when its owner right-clicks it with an item.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FamiliarColorInteractionHandler {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(FamiliarColorOverrides.INSTANCE);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractSpellCastingPet familiar)) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        Player player = event.getEntity();
        UUID owner = familiar.getOwnerUUID();
        if (owner == null || !owner.equals(player.getUUID())) {
            return; // only the owner of a tamed familiar can recolor it
        }

        if (!player.level().isClientSide) {
            Optional<FamiliarColorOverrides.Rule> rule = FamiliarColorOverrides.INSTANCE.find(familiar, stack);
            if (rule.isPresent()) {
                applyRule(event, familiar, player, stack, rule.get());
                return; // an override for this item always takes priority over the dye behavior
            }
        }

        applyDye(event, familiar, player, stack);
    }

    private static void applyRule(PlayerInteractEvent.EntityInteract event, AbstractSpellCastingPet familiar,
                                  Player player, ItemStack stack, FamiliarColorOverrides.Rule rule) {
        if (!rule.wouldChange(familiar)) {
            return; // already these colors: don't waste the item, let other interactions run
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        rule.layers().forEach(familiar::setLayerColor);
        if (rule.consume()) {
            stack.consume(1, player);
        }

        SoundEvent sound = rule.sound().map(BuiltInRegistries.SOUND_EVENT::get).orElse(null);
        finish(familiar, player, sound != null ? sound : SoundEvents.DYE_USE);
    }

    /**
     * Dyes: a normal dye colors the dye slot (clothes) of dyeable familiars. A shrunk dye colors the eyes
     * ({@code getEyeDyeLayerSlot}); while sneaking, familiars with a second pair of eyes
     * ({@code getSneakingEyeDyeLayerSlot}, e.g. the hunter's dog) get those colored instead.
     */
    private static void applyDye(PlayerInteractEvent.EntityInteract event, AbstractSpellCastingPet familiar,
                                 Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof DyeItem dyeItem)) {
            return;
        }

        int slot;
        if (FamiliarDyes.isShrunkDye(stack)) {
            int sneakingSlot = familiar.getSneakingEyeDyeLayerSlot();
            slot = player.isShiftKeyDown() && sneakingSlot >= 0 ? sneakingSlot : familiar.getEyeDyeLayerSlot();
        } else {
            slot = familiar.canBeDyed() ? AbstractSpellCastingPet.DYE_LAYER_SLOT : -1;
        }
        if (slot < 0) {
            return;
        }

        int rgb = dyeItem.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
        if (familiar.getLayerColor(slot) == rgb) {
            return; // already this color: don't waste the dye
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) {
            return;
        }

        familiar.setLayerColor(slot, rgb);
        stack.consume(1, player);
        finish(familiar, player, SoundEvents.DYE_USE);
    }

    /** Server side: feedback, then keep the roster snapshot (selection screen, resummoning) in sync. */
    private static void finish(AbstractSpellCastingPet familiar, Player player, SoundEvent sound) {
        familiar.level().playSound(null, familiar, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        familiar.triggerAnim("interact_controller", "interact");

        FamiliarManager.updateFamiliarData(familiar);
        if (player instanceof ServerPlayer serverPlayer) {
            FamiliarSync.snapshot(serverPlayer, familiar.getUUID());
            // Recoloring an owned familiar into a look unlocks its compendium entry
            FamiliarCompendium.discover(serverPlayer, familiar);
        }
    }
}