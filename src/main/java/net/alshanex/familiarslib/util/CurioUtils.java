package net.alshanex.familiarslib.util;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.item.AbstractFamiliarSpellbookItem;
import net.alshanex.familiarslib.item.AbstractMultiSelectionCurio;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurio;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * This is used to check curio inventory for specific curio
 * Credits to GameTech for sharing.
 */
public class CurioUtils {
    public static boolean isWearingCurio(LivingEntity entity, Item curioItem) {
        return CuriosApi.getCuriosInventory(entity).map(curios ->
                !curios.findCurios(item -> item != null && item.is(curioItem)).isEmpty()
        ).orElse(false);
    }

    //Checks for a Familiar Spellbook in the curios inventory
    public static boolean isWearingFamiliarSpellbook(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(curios ->
                !curios.findCurios(item -> item != null && item.getItem() instanceof AbstractFamiliarSpellbookItem).isEmpty()
        ).orElse(false);
    }

    public static void broadcastCurioBreakEvent(SlotContext slotContext) {
        CuriosApi.broadcastCurioBreakEvent(slotContext);
    }

    public static LazyOptional<ICurio> getCurio(ItemStack stack) {
        return CuriosApi.getCurio(stack);
    }

    public static boolean isWearingMultiSelectionCurio(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(curios ->
                !curios.findCurios(item -> item != null && item.getItem() instanceof AbstractMultiSelectionCurio).isEmpty()
        ).orElse(false);
    }

    public static Optional<ItemStack> getEquippedMultiSelectionCurio(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity).map(curios ->
                curios.findFirstCurio(item -> item.getItem() instanceof AbstractMultiSelectionCurio)
                        .map(SlotResult::stack)
                        .orElse(ItemStack.EMPTY)
        ).filter(stack -> !stack.isEmpty());
    }

    public static Set<UUID> getSelectedFamiliarsFromEquipped(LivingEntity entity) {
        Optional<ItemStack> curio = getEquippedMultiSelectionCurio(entity);
        if (curio.isPresent()) {
            return AbstractMultiSelectionCurio.getSelectedFamiliars(curio.get());
        }
        return Set.of();
    }

    public static boolean updateSelectedFamiliarsInEquipped(LivingEntity entity, Set<UUID> selectedFamiliars) {
        Optional<ItemStack> curio = getEquippedMultiSelectionCurio(entity);
        if (curio.isPresent()) {
            AbstractMultiSelectionCurio.setSelectedFamiliars(curio.get(), selectedFamiliars);
            return true;
        }
        return false;
    }

    /**
     * Limpia los familiares seleccionados de un ItemStack específico de PandoraBox
     */
    public static void clearSelectedMultiSelectionCurio(ItemStack curio) {
        if (curio.getItem() instanceof AbstractMultiSelectionCurio) {
            FamiliarsLib.LOGGER.debug("Clearing selected familiars from PandoraBox ItemStack");
            AbstractMultiSelectionCurio.setSelectedFamiliars(curio, Set.of());
        }
    }

    /**
     * Busca y limpia todas las Multi Selection curio en el inventario del jugador y en el cursor
     */
    public static void findAndClearAllMultiSelectionCurio(ServerPlayer player) {
        int clearedCount = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof AbstractMultiSelectionCurio) {
                Set<UUID> selectedBefore = AbstractMultiSelectionCurio.getSelectedFamiliars(stack);
                if (!selectedBefore.isEmpty()) {
                    clearSelectedMultiSelectionCurio(stack);
                    clearedCount++;
                    FamiliarsLib.LOGGER.debug("Cleared Multi Selection curio in inventory slot {} (had {} selected)",
                            i, selectedBefore.size());
                }
            }
        }

        // 2. Buscar en el item que está siendo llevado por el cursor/ratón
        ItemStack carriedItem = player.containerMenu.getCarried();
        if (carriedItem.getItem() instanceof AbstractMultiSelectionCurio) {
            Set<UUID> selectedBefore = AbstractMultiSelectionCurio.getSelectedFamiliars(carriedItem);
            if (!selectedBefore.isEmpty()) {
                clearSelectedMultiSelectionCurio(carriedItem);
                // No necesitas setCarried porque estás modificando el ItemStack directamente
                clearedCount++;
                FamiliarsLib.LOGGER.debug("Cleared Multi Selection curio being carried by cursor (had {} selected)",
                        selectedBefore.size());
            }
        }

        // 3. Buscar en las manos (por si acaso)
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack handStack = player.getItemInHand(hand);
            if (handStack.getItem() instanceof AbstractMultiSelectionCurio) {
                Set<UUID> selectedBefore = AbstractMultiSelectionCurio.getSelectedFamiliars(handStack);
                if (!selectedBefore.isEmpty()) {
                    clearSelectedMultiSelectionCurio(handStack);
                    clearedCount++;
                    FamiliarsLib.LOGGER.debug("Cleared Multi Selection curio in {} hand (had {} selected)",
                            hand.name(), selectedBefore.size());
                }
            }
        }

        if (clearedCount > 0) {
            // Sincronizar inventario con el cliente
            player.inventoryMenu.broadcastChanges();
            FamiliarsLib.LOGGER.debug("Found and cleared {} Multi Selection curio for player {}",
                    clearedCount, player.getName().getString());
        } else {
            FamiliarsLib.LOGGER.debug("No Multi Selection curio with selections found for player {}",
                    player.getName().getString());
        }
    }

    /**
     * Maneja el evento de desequipar Multi Selection curio, limpiando automáticamente los familiares seleccionados
     */
    public static void handleMultiSelectionCurioUnequip(ServerPlayer player, ItemStack unequippedBox) {
        if (!(unequippedBox.getItem() instanceof AbstractMultiSelectionCurio)) {
            return;
        }

        Set<UUID> selectedFamiliars = AbstractMultiSelectionCurio.getSelectedFamiliars(unequippedBox);

        FamiliarsLib.LOGGER.debug("Player {} unequipped Multi Selection curio with {} selected familiars - searching all locations",
                player.getName().getString(), selectedFamiliars.size());

        // Buscar y limpiar TODAS las Multi Selection curio del jugador (inventario, cursor, manos)
        findAndClearAllMultiSelectionCurio(player);

        FamiliarsLib.LOGGER.debug("Completed Multi Selection curio cleanup for player {}",
                player.getName().getString());
    }

    /**
     * Verifica si un familiar específico está seleccionado en una Multi Selection curio equipada
     */
    public static boolean isFamiliarSelectedInEquippedMultiSelectionCurio(LivingEntity entity, UUID familiarId) {
        Set<UUID> selectedFamiliars = getSelectedFamiliarsFromEquipped(entity);
        return selectedFamiliars.contains(familiarId);
    }

    /**
     * Remueve un familiar específico de la selección de la Multi Selection curio equipada
     */
    public static boolean removeFamiliarFromEquippedMultiSelectionCurio(LivingEntity entity, UUID familiarId) {
        Optional<ItemStack> curio = getEquippedMultiSelectionCurio(entity);
        if (curio.isPresent()) {
            Set<UUID> currentSelection = AbstractMultiSelectionCurio.getSelectedFamiliars(curio.get());
            if (currentSelection.contains(familiarId)) {
                currentSelection.remove(familiarId);
                AbstractMultiSelectionCurio.setSelectedFamiliars(curio.get(), currentSelection);
                return true;
            }
        }
        return false;
    }

    public static void updateMultiSelectionCurio(ServerPlayer serverPlayer, Set<UUID> selectedFamiliars){
        // Primero intentar actualizar la equipada
        boolean updatedEquipped = updateSelectedFamiliarsInEquipped(serverPlayer, selectedFamiliars);

        if (!updatedEquipped) {
            // Si no hay equipada, buscar en las manos (compatibilidad)
            ItemStack heldItem = serverPlayer.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(heldItem.getItem() instanceof AbstractMultiSelectionCurio)) {
                heldItem = serverPlayer.getItemInHand(InteractionHand.OFF_HAND);
            }

            if (heldItem.getItem() instanceof AbstractMultiSelectionCurio) {
                AbstractMultiSelectionCurio.setSelectedFamiliars(heldItem, selectedFamiliars);
                updatedEquipped = true;
            }
        }

        if (updatedEquipped) {
            FamiliarsLib.LOGGER.debug("Updated Multi Selection curio with {} selected familiars for player {}",
                    selectedFamiliars.size(), serverPlayer.getName().getString());
        }
    }
}
