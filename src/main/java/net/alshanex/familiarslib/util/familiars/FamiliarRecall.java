package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.FamiliarRoster;
import net.alshanex.familiarslib.data.RecallableFamiliarsData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.RecallShardItem;
import net.alshanex.familiarslib.network.ClientboundRecallListPacket;
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

/** Keeps released and dead familiars around per level, and brings them back with a Recall Shard. */
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

            // Comes back healthy, without the state of its death
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

    /** Recall Shard used: send the player what can be recalled in this level. */
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

    /** The player picked a familiar in the recall screen. Consumes one shard on success. */
    public static void recall(ServerPlayer player, UUID familiarId) {
        ItemStack shard = heldShard(player);
        if (shard.isEmpty()) {
            return; // shard no longer in hand
        }

        ServerLevel level = player.serverLevel();
        RecallableFamiliarsData data = RecallableFamiliarsData.get(level);
        RecallableFamiliarsData.Entry entry = data.find(player.getUUID(), familiarId).orElse(null);
        if (entry == null) {
            actionBar(player, Component.translatable("message.familiarslib.recall_shard.gone").withStyle(ChatFormatting.RED));
            return;
        }

        FamiliarRoster roster = FamiliarRoster.of(player);
        if (roster.hasFamiliar(familiarId)) {
            data.remove(player.getUUID(), familiarId); // already back somehow, drop the stale entry
            return;
        }
        if (!roster.canTameMoreFamiliars()) {
            actionBar(player, Component.translatable("message.familiarslib.limit_familiars", FamiliarRoster.maxFamiliars())
                    .withStyle(ChatFormatting.RED));
            return;
        }

        CompoundTag nbt = entry.data().copy();
        if (!roster.tryAddTamedFamiliar(familiarId, nbt)) {
            return;
        }

        data.remove(player.getUUID(), familiarId);
        FamiliarManager.unmarkFamiliarAsDead(familiarId); // dead familiars are skipped by data updates otherwise
        if (roster.getSelectedFamiliarId() == null) {
            roster.setSelectedFamiliarId(familiarId);
        }

        FamiliarSync.snapshot(player, familiarId);
        FamiliarManager.syncFamiliarData(player, roster);

        if (!player.getAbilities().instabuild) {
            shard.shrink(1);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
        actionBar(player, Component.translatable("message.familiarslib.recall_shard.recalled", familiarName(nbt))
                .withStyle(ChatFormatting.GREEN));
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

    private static ItemStack heldShard(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RecallShardItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void actionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}