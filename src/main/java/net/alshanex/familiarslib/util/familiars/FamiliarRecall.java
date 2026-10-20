package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.BoundFamiliar;
import net.alshanex.familiarslib.data.RecallableFamiliarsData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.FamiliarEggItem;
import net.alshanex.familiarslib.network.ClientboundRecallListPacket;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Keeps released and dead familiars around per level, and binds them to Familiar Eggs.
 */
public final class FamiliarRecall {

    private FamiliarRecall() {}

    /** Called on release, after the familiar was desummoned and before it leaves the roster. */
    public static void recordRelease(ServerPlayer player, UUID familiarId, CompoundTag familiarNBT) {
        if (familiarNBT == null) return;
        ServerLevel level = player.serverLevel();
        RecallableFamiliarsData.get(level).add(player.getUUID(), familiarId, familiarNBT,
                RecallableFamiliarsData.Kind.RELEASED, level.getGameTime());
    }

    /** Called on death, before the familiar leaves the roster. Stored in the level where it died, at full health. */
    public static void recordDeath(AbstractSpellCastingPet familiar, ServerPlayer owner) {
        try {
            CompoundTag nbt = FamiliarManager.createFamiliarNBT(familiar);

            // Saved healthy and without the state of its death; hatching sets the newborn's health
            float maxHealth = familiar.getMaxHealth();
            nbt.putFloat("currentHealth", maxHealth);
            nbt.putFloat("Health", maxHealth);
            nbt.remove("DeathTime");
            nbt.remove("HurtTime");
            nbt.remove("HurtByTimestamp");
            nbt.remove("active_effects");
            nbt.remove("Fire");

            ServerLevel level = familiar.level() instanceof ServerLevel serverLevel ? serverLevel : owner.serverLevel();
            RecallableFamiliarsData.get(level).add(owner.getUUID(), familiar.getUUID(), nbt,
                    RecallableFamiliarsData.Kind.DEAD, level.getGameTime());
        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Failed to record dead familiar {} for recall", familiar.getUUID(), e);
        }
    }

    /** Empty egg used: send the player what can be bound in this level. */
    public static void openRecallList(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<RecallableFamiliarsData.Entry> entries = RecallableFamiliarsData.get(level).entriesFor(player.getUUID());
        if (entries.isEmpty()) {
            actionBar(player, Component.translatable("message.familiarslib.recall_shard.nothing").withStyle(ChatFormatting.YELLOW));
            return;
        }

        long now = level.getGameTime();
        List<ClientboundRecallListPacket.Entry> views = new ArrayList<>();
        for (RecallableFamiliarsData.Entry entry : entries) {
            views.add(new ClientboundRecallListPacket.Entry(entry.familiarId(), entry.data(),
                    entry.kind() == RecallableFamiliarsData.Kind.DEAD, Math.max(0, now - entry.gameTime())));
        }
        PacketDistributor.sendToPlayer(player, new ClientboundRecallListPacket(views));
    }

    /**
     * The player picked a familiar in the egg screen: move it out of the level's data and into the held empty egg.
     * The familiar now lives only in that egg.
     */
    public static void recall(ServerPlayer player, UUID familiarId) {
        ItemStack egg = heldEmptyEgg(player);
        if (egg.isEmpty()) {
            return; // egg no longer in hand
        }

        ServerLevel level = player.serverLevel();
        RecallableFamiliarsData data = RecallableFamiliarsData.get(level);
        RecallableFamiliarsData.Entry entry = data.find(player.getUUID(), familiarId).orElse(null);
        if (entry == null) {
            actionBar(player, Component.translatable("message.familiarslib.recall_shard.gone").withStyle(ChatFormatting.RED));
            return;
        }

        data.remove(player.getUUID(), familiarId);
        CompoundTag familiarData = entry.data().copy();
        egg.set(ComponentRegistry.BOUND_FAMILIAR,
                BoundFamiliar.recalled(familiarId, player.getUUID(), familiarData, eggColorOf(level, familiarData)));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.0F);
        actionBar(player, Component.translatable("message.familiarslib.familiar_egg.bound", familiarName(entry.data()))
                .withStyle(ChatFormatting.GREEN));
    }

    /**
     * The egg color for this saved familiar: loads it into a temporary entity (never added to the world)
     * so {@code getEggColor()} can read its layers.
     */
    public static int eggColorOf(ServerLevel level, CompoundTag familiarData) {
        try {
            EntityType<?> type = EntityType.byString(familiarData.getString("id")).orElse(null);
            if (type != null && type.create(level) instanceof AbstractSpellCastingPet familiar) {
                familiar.load(familiarData.copy());
                return familiar.getEggColor().resolve(familiar);
            }
        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Failed to work out the egg color of a {}", familiarData.getString("id"), e);
        }
        return EggColor.DEFAULT_RGB;
    }

    /** The familiar's custom name, or its type name. */
    public static Component familiarName(CompoundTag nbt) {
        if (nbt.contains("customName")) {
            return Component.literal(nbt.getString("customName"));
        }
        return EntityType.byString(nbt.getString("id"))
                .<Component>map(EntityType::getDescription)
                .orElse(Component.literal("?"));
    }

    private static ItemStack heldEmptyEgg(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof FamiliarEggItem && !FamiliarEggItem.isBound(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    static void actionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}