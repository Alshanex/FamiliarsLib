package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarColorOverrides;
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
            return;
        }

        if (!player.level().isClientSide) {
            Optional<FamiliarColorOverrides.Rule> rule = FamiliarColorOverrides.INSTANCE.find(familiar, stack);
            if (rule.isPresent()) {
                applyRule(event, familiar, player, stack, rule.get());
                return;
            }
        }

        applyDye(event, familiar, player, stack);
    }

    private static void applyRule(PlayerInteractEvent.EntityInteract event, AbstractSpellCastingPet familiar,
                                      Player player, ItemStack stack, FamiliarColorOverrides.Rule rule) {
        if (!rule.wouldChange(familiar)) {
            return;
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

    private static void applyDye(PlayerInteractEvent.EntityInteract event, AbstractSpellCastingPet familiar,
                                 Player player, ItemStack stack) {
        if (!familiar.canBeDyed() || !(stack.getItem() instanceof DyeItem dyeItem)) {
            return;
        }

        int rgb = dyeItem.getDyeColor().getTextureDiffuseColor() & 0xFFFFFF;
        if (familiar.getLayerColor(AbstractSpellCastingPet.DYE_LAYER_SLOT) == rgb) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (player.level().isClientSide) {
            return;
        }

        familiar.setLayerColor(AbstractSpellCastingPet.DYE_LAYER_SLOT, rgb);
        stack.consume(1, player);
        finish(familiar, player, SoundEvents.DYE_USE);
    }

    /** Feedback, then keep the roster snapshot (selection screen, resummoning) in sync. */
    private static void finish(AbstractSpellCastingPet familiar, Player player, SoundEvent sound) {
        familiar.level().playSound(null, familiar, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        familiar.triggerAnim("interact_controller", "interact");

        FamiliarManager.updateFamiliarData(familiar);
        if (player instanceof ServerPlayer serverPlayer) {
            FamiliarSync.snapshot(serverPlayer, familiar.getUUID());
        }
    }
}