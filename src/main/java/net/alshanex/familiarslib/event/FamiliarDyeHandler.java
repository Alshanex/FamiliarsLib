package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.alshanex.familiarslib.util.familiars.FamiliarSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Right-clicking your own tamed familiar with a dye recolors its dyeable layer.
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FamiliarDyeHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractSpellCastingPet familiar) || !familiar.canBeDyed()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof DyeItem dyeItem)) {
            return;
        }

        Player player = event.getEntity();
        UUID owner = familiar.getOwnerUUID();
        if (owner == null || !owner.equals(player.getUUID())) {
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
        familiar.level().playSound(null, familiar, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
        familiar.triggerAnim("interact_controller", "interact");

        // Keep the roster snapshot (selection screen, resummoning) in sync with the new color
        FamiliarManager.updateFamiliarData(familiar);
        if (player instanceof ServerPlayer serverPlayer) {
            FamiliarSync.snapshot(serverPlayer, familiar.getUUID());
        }
    }
}
