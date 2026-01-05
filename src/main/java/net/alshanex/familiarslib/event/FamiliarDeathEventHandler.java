package net.alshanex.familiarslib.event;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.AbstractFamiliarTotem;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.util.familiars.FamiliarAdvancementsHelper;
import net.alshanex.familiarslib.util.familiars.FamiliarManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = FamiliarsLib.MODID)
public class FamiliarDeathEventHandler {

    private static final Set<UUID> processedDeaths = new HashSet<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        if (event.getEntity() instanceof AbstractSpellCastingPet familiar) {

            if (familiar.getSummoner() != null && familiar.getSummoner() instanceof ServerPlayer serverPlayer) {

                if (FamiliarAdvancementsHelper.hasRevivalAdvancement(serverPlayer)) {
                    FamiliarsLib.LOGGER.debug("Player {} has revival advancement", serverPlayer.getName().getString());

                    MagicData magicData = MagicData.getPlayerMagicData(serverPlayer);

                    if (magicData.getMana() >= 80) {
                        familiar.setHealth(familiar.getMaxHealth() / 2);

                        var newMana = Math.max(magicData.getMana() - 80, 0);
                        magicData.setMana(newMana);
                        PacketDistributor.sendToPlayer(serverPlayer, new SyncManaPacket(magicData));

                        event.setCanceled(true);
                        return;
                    } else {
                        ItemStack boundSoulLink = findBoundSoulLink(serverPlayer, familiar);
                        if (boundSoulLink != null) {
                            FamiliarsLib.LOGGER.debug("Found Tiny Totem, reviving familiar");
                            familiar.setHealth(familiar.getMaxHealth() / 2);
                            event.setCanceled(true);
                            boundSoulLink.shrink(1);
                            familiar.setTotem(false);
                            triggerTotem(familiar);
                            return;
                        }
                    }
                } else {
                    FamiliarsLib.LOGGER.debug("Player {} does not have revival advancement", serverPlayer.getName().getString());

                    ItemStack boundSoulLink = findBoundSoulLink(serverPlayer, familiar);
                    if (boundSoulLink != null) {
                        FamiliarsLib.LOGGER.debug("Found Tiny Totem, reviving familiar");
                        familiar.setHealth(familiar.getMaxHealth() / 2);
                        event.setCanceled(true);
                        boundSoulLink.shrink(1);
                        familiar.setTotem(false);
                        triggerTotem(familiar);
                        return;
                    }
                }
            }

            handleDeath(familiar);
        }
    }

    private static void triggerTotem(AbstractSpellCastingPet pet){
        pet.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
        pet.level().broadcastEntityEvent(pet, (byte)35);
    }

    private static ItemStack findBoundSoulLink(Player player, AbstractSpellCastingPet pet) {
        for (ItemStack stack : player.getInventory().items) {
            if (isSoulLinkBoundToPet(stack, pet)) {
                return stack;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isSoulLinkBoundToPet(stack, pet)) {
                return stack;
            }
        }

        for (ItemStack stack : player.getInventory().armor) {
            if (isSoulLinkBoundToPet(stack, pet)) {
                return stack;
            }
        }

        ItemStack offhandStack = player.getInventory().offhand.get(0);
        if (isSoulLinkBoundToPet(offhandStack, pet)) {
            return offhandStack;
        }

        return null;
    }

    private static boolean isSoulLinkBoundToPet(ItemStack stack, AbstractSpellCastingPet pet) {
        if (!(stack.getItem() instanceof AbstractFamiliarTotem tinyTotem)) {
            return false;
        }

        if (!tinyTotem.containsEntity(stack)) {
            return false;
        }

        var nbt = stack.get(ComponentRegistry.SOUL_LINK);
        if (nbt == null || !nbt.contains("petUUID")) {
            return false;
        }

        return nbt.getUUID("petUUID").equals(pet.getUUID());
    }

    private static void handleDeath(AbstractSpellCastingPet familiar){
        UUID familiarId = familiar.getUUID();

        if (processedDeaths.contains(familiarId)) {
            FamiliarsLib.LOGGER.debug("Death already processed for familiar {}", familiarId);
            return;
        }

        FamiliarsLib.LOGGER.debug("Processing death event for familiar {} with health {} (BEFORE die() method)",
                familiarId, familiar.getHealth());

        // Marcar como procesado inmediatamente
        processedDeaths.add(familiarId);

        if (familiar.getSummoner() instanceof ServerPlayer serverPlayer) {
            FamiliarsLib.LOGGER.debug("Found owner {} for dying familiar {}",
                    serverPlayer.getName().getString(), familiarId);

            try {
                // Procesar la muerte ANTES de que die() sea llamado
                FamiliarManager.handleFamiliarDeath(familiar, serverPlayer);
                FamiliarsLib.LOGGER.debug("Successfully processed death for familiar {} - death packet sent", familiarId);
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error processing death for familiar {}: ", familiarId, e);
            }
        } else {
            FamiliarsLib.LOGGER.warn("Dying familiar {} has no valid owner", familiarId);
        }

        // Limpiar después de un breve delay
        familiar.level().getServer().execute(() -> {
            familiar.level().getServer().execute(() -> {
                processedDeaths.remove(familiarId);
                FamiliarsLib.LOGGER.debug("Cleaned up processed death entry for familiar {}", familiarId);
            });
        });
    }

    public static void clearProcessedDeaths() {
        processedDeaths.clear();
        FamiliarsLib.LOGGER.debug("Cleared all processed deaths");
    }

    public static boolean wasDeathProcessed(UUID familiarId) {
        return processedDeaths.contains(familiarId);
    }

    public static int getProcessedDeathsCount() {
        return processedDeaths.size();
    }
}
