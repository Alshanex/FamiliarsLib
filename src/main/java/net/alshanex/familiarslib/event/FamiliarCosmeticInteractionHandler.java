package net.alshanex.familiarslib.event;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmeticSlot;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmetics;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.alshanex.familiarslib.util.familiars.FamiliarSync;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Giving shrunk cosmetics to familiars and taking them back.
 * <ul>
 *     <li>Right-click your familiar with a shrunk cosmetic: it's put on, and whatever was in that slot
 *         goes back to you. Items that are both a hat and a weapon go on as a hat when sneaking.</li>
 *     <li>Sneak + right-click your familiar with an empty main hand: takes the weapon off,
 *         or the hat if it has no weapon.</li>
 * </ul>
 * The real item is stored in the familiar and always given back (still shrunk).
 */
@EventBusSubscriber(modid = FamiliarsLib.MODID, bus = EventBusSubscriber.Bus.GAME)
public class FamiliarCosmeticInteractionHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractSpellCastingPet familiar)) {
            return;
        }

        Player player = event.getEntity();
        UUID owner = familiar.getOwnerUUID();
        if (owner == null || !owner.equals(player.getUUID())) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (FamiliarCosmetics.isShrunkCosmetic(stack)) {
            equip(event, familiar, player, stack);
        } else if (stack.isEmpty() && event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()) {
            remove(event, familiar, player);
        }
    }

    private static void equip(PlayerInteractEvent.EntityInteract event, AbstractSpellCastingPet familiar, Player player, ItemStack stack) {
        boolean clientSide = player.level().isClientSide;
        FamiliarCosmeticSlot slot = FamiliarCosmetics.slotForEquip(familiar, stack, player.isShiftKeyDown());

        event.setCanceled(true);
        if (slot == null) {
            event.setCancellationResult(InteractionResult.FAIL);
            if (!clientSide) {
                player.displayClientMessage(Component.translatable("message.familiarslib.cosmetic.not_allowed"), true);
            }
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(clientSide));
        if (clientSide) {
            return;
        }

        ItemStack previous = familiar.getCosmetic(slot);
        familiar.setCosmetic(slot, stack.copyWithCount(1));
        stack.shrink(1);
        if (!previous.isEmpty()) {
            player.getInventory().placeItemBackInInventory(previous);
        }

        familiar.level().playSound(null, familiar, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        finish(familiar, player);
    }

    private static void remove(PlayerInteractEvent.EntityInteract event, AbstractSpellCastingPet familiar, Player player) {
        FamiliarCosmeticSlot slot = FamiliarCosmetics.slotForRemove(familiar);
        if (slot == null) {
            return; // nothing to take off: let the normal interaction happen
        }

        boolean clientSide = player.level().isClientSide;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(clientSide));
        if (clientSide) {
            return;
        }

        ItemStack removed = familiar.getCosmetic(slot);
        familiar.setCosmetic(slot, ItemStack.EMPTY);
        player.getInventory().placeItemBackInInventory(removed);

        familiar.level().playSound(null, familiar, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
        finish(familiar, player);
    }

    /** Server side: animation, then keep the roster snapshot (selection screen, resummoning) in sync. */
    private static void finish(AbstractSpellCastingPet familiar, Player player) {
        familiar.triggerAnim("interact_controller", "interact");
        FamiliarManager.updateFamiliarData(familiar);
        if (player instanceof ServerPlayer serverPlayer) {
            FamiliarSync.snapshot(serverPlayer, familiar.getUUID());
        }
    }
}
