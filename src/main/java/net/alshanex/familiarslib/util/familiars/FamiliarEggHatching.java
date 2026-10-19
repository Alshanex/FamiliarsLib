package net.alshanex.familiarslib.util.familiars;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.data.BoundFamiliar;
import net.alshanex.familiarslib.data.FamiliarRoster;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.item.FamiliarEggItem;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Familiar Eggs on Familiar Beds: cracking effects, and hatching once the owner can take the familiar.
 */
public final class FamiliarEggHatching {
    private static final double HINT_RANGE = 16.0;
    private static final int HINT_EVERY_CHECKS = 10; // with checks every second: one hint every 10 seconds

    private FamiliarEggHatching() {}

    public static void tick(AbstractFamiliarBedBlockEntity bed, ServerLevel level, int checkCount) {
        BoundFamiliar egg = bed.getEgg();
        if (egg == null) return;

        Vec3 eggPos = Vec3.atLowerCornerOf(bed.getBlockPos()).add(bed.getEggRenderOffset()).add(0, 0.25, 0);
        float progress = bed.getEggProgress(level.getGameTime());

        if (progress < 1F) {
            if (progress >= AbstractFamiliarBedBlockEntity.EGG_CRACK_STAGE_2) {
                level.playSound(null, eggPos.x, eggPos.y, eggPos.z, SoundEvents.TURTLE_EGG_CRACK, SoundSource.BLOCKS, 0.6F, 0.9F + level.random.nextFloat() * 0.2F);
                level.sendParticles(ParticleTypes.ENCHANT, eggPos.x, eggPos.y, eggPos.z, 6, 0.15, 0.15, 0.15, 0.3);
            } else if (progress >= AbstractFamiliarBedBlockEntity.EGG_CRACK_STAGE_1 && level.random.nextInt(4) == 0) {
                level.sendParticles(ParticleTypes.ENCHANT, eggPos.x, eggPos.y, eggPos.z, 3, 0.15, 0.15, 0.15, 0.3);
            }
            return;
        }

        // Ready: it only hatches when its owner can take it
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(egg.owner());
        if (owner == null || owner.serverLevel() != level) {
            return; // owner offline or in another dimension: keep waiting
        }

        FamiliarRoster roster = FamiliarRoster.of(owner);
        if (roster.hasFamiliar(egg.familiarId()) || level.getEntity(egg.familiarId()) != null) {
            // The familiar already exists somehow: give the egg back instead of creating a second copy
            FamiliarsLib.LOGGER.warn("Familiar egg at {} holds familiar {} which already exists, popping the egg", bed.getBlockPos(), egg.familiarId());
            popEgg(bed, level);
            return;
        }

        Component reason = null;
        if (!roster.canTameMoreFamiliars()) {
            reason = Component.translatable("message.familiarslib.familiar_egg.no_room", FamiliarRecall.familiarName(egg.data()));
        } else {
            FamiliarManager.refreshSummonedFamiliars(owner, roster);
            if (roster.getSummonedFamiliarCount() >= FamiliarManager.MAX_SUMMONED_FAMILIARS) {
                reason = Component.translatable("message.familiarslib.familiar_egg.no_summon_room", FamiliarRecall.familiarName(egg.data()));
            }
        }

        if (reason != null) {
            if (checkCount % HINT_EVERY_CHECKS == 0 && owner.position().distanceTo(eggPos) <= HINT_RANGE) {
                FamiliarRecall.actionBar(owner, reason.copy().withStyle(ChatFormatting.YELLOW));
            }
            level.sendParticles(ParticleTypes.ENCHANT, eggPos.x, eggPos.y, eggPos.z, 2, 0.15, 0.15, 0.15, 0.2);
            return;
        }

        hatch(bed, level, owner, roster, egg);
    }

    private static void hatch(AbstractFamiliarBedBlockEntity bed, ServerLevel level, ServerPlayer owner, FamiliarRoster roster, BoundFamiliar egg) {
        CompoundTag nbt = egg.data().copy();
        EntityType<?> type = EntityType.byString(nbt.getString("id")).orElse(null);
        Entity entity = type != null ? type.create(level) : null;
        if (!(entity instanceof AbstractSpellCastingPet familiar)) {
            FamiliarsLib.LOGGER.error("Familiar egg at {} holds an unknown familiar type '{}', popping the egg", bed.getBlockPos(), nbt.getString("id"));
            popEgg(bed, level);
            return;
        }

        familiar.load(nbt);
        familiar.setUUID(egg.familiarId());

        // Upgrades first, then born at half health
        FamiliarConsumableIntegration.loadConsumableData(familiar, nbt);
        FamiliarConsumableIntegration.applyConsumableModifiers(familiar);
        familiar.setHealth(familiar.getMaxHealth() * 0.5F);
        nbt.putFloat("currentHealth", familiar.getHealth());
        nbt.putFloat("Health", familiar.getHealth());

        if (!roster.tryAddTamedFamiliar(egg.familiarId(), nbt)) {
            return; // couldn't be added after all: keep waiting
        }

        // Free the bed first: the newborn is hurt, so its bed goal claims this bed and sleeps in it
        bed.takeEgg();

        Vec3 sleepPos = bed.getSleepPosition();
        familiar.moveTo(sleepPos.x, sleepPos.y, sleepPos.z, level.random.nextFloat() * 360F, 0F);

        roster.addSummonedFamiliar(egg.familiarId());
        if (roster.getSelectedFamiliarId() == null) {
            roster.setSelectedFamiliarId(egg.familiarId());
        }
        FamiliarManager.unmarkFamiliarAsDead(egg.familiarId()); // dead familiars are skipped by data updates otherwise

        level.addFreshEntity(familiar);
        FamiliarAttributesHelper.handleFamiliarSummoned(owner, familiar);

        FamiliarSync.snapshot(owner, egg.familiarId());
        FamiliarManager.syncFamiliarData(owner, roster);

        level.playSound(null, sleepPos.x, sleepPos.y, sleepPos.z, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, sleepPos.x, sleepPos.y + 0.4, sleepPos.z, 12, 0.3, 0.3, 0.3, 0.05);
        FamiliarRecall.actionBar(owner, Component.translatable("message.familiarslib.familiar_egg.hatched", FamiliarRecall.familiarName(nbt))
                .withStyle(ChatFormatting.GREEN));
    }

    /** Takes the egg off the bed and drops it, still holding its familiar. */
    public static void popEgg(AbstractFamiliarBedBlockEntity bed, ServerLevel level) {
        BoundFamiliar egg = bed.takeEgg();
        if (egg != null) {
            BlockPos pos = bed.getBlockPos();
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, FamiliarEggItem.boundEgg(egg));
        }
    }
}