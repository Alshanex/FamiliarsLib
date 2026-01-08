package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.mobs.IAnimatedAttacker;
import io.redspace.ironsspellbooks.entity.mobs.SummonedSkeleton;
import io.redspace.ironsspellbooks.entity.mobs.goals.WizardAttackGoal;
import io.redspace.ironsspellbooks.entity.mobs.wizards.GenericAnimatedWarlockAttackGoal;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.registries.ParticleRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.AbstractFamiliarBedBlock;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.entity.AbstractFlyingSpellCastingPet;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.alshanex.familiarslib.util.CylinderParticleManager;
import net.alshanex.familiarslib.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Goals which can be added to any familiar
 */
public class FamiliarGoals {

    public static class FindAndUsePetBedGoal extends Goal {
        private final AbstractSpellCastingPet pet;
        private final double searchRadius;
        private BlockPos targetBedPos;
        private Vec3 exactSleepPosition;
        private int cooldownTicks;
        private static final int SEARCH_COOLDOWN = 60;
        private boolean hasSnappedToBed = false;
        private boolean hasClaimedBed = false;

        // Sleep animation and regeneration variables
        private int bedRegenTimer = 0;
        private boolean wasPlayingSleepAnimation = false;

        public FindAndUsePetBedGoal(AbstractSpellCastingPet pet, double searchRadius) {
            this.pet = pet;
            this.searchRadius = searchRadius;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!pet.level().isClientSide && pet.tickCount % 100 == 0) {
                FamiliarsLib.LOGGER.debug("FindAndUsePetBedGoal check - Pet: " + pet.getUUID() +
                        ", Sitting: " + pet.getIsSitting() +
                        ", Health: " + pet.getHealth() + "/" + pet.getMaxHealth() +
                        ", Cooldown: " + cooldownTicks);
            }

            if (pet.getIsSitting()) {
                return false;
            }

            if (cooldownTicks > 0) {
                cooldownTicks--;
                return false;
            }

            if (pet.getHealth() >= pet.getMaxHealth()) {
                cooldownTicks = SEARCH_COOLDOWN;
                return false;
            }

            BlockPos foundBed = findAvailableBed();
            if (foundBed != null) {
                targetBedPos = foundBed;
                BlockEntity be = pet.level().getBlockEntity(targetBedPos);
                if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                    // Claim the bed immediately
                    if (!petBed.isBedTaken()) {
                        petBed.setBedTaken(true);
                        hasClaimedBed = true;
                        FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " immediately claimed bed at " + targetBedPos);

                        exactSleepPosition = petBed.getSleepPosition();
                        hasSnappedToBed = false;
                        FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " found available bed at " + targetBedPos +
                                ", exact sleep position: " + exactSleepPosition);
                        return true;
                    } else {
                        // Bed was taken between finding it and trying to claim it
                        FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " failed to claim bed at " + targetBedPos + " - already taken");
                    }
                }
            }

            cooldownTicks = SEARCH_COOLDOWN;
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (pet.getHealth() >= pet.getMaxHealth()) {
                return false;
            }

            if (pet.getIsSitting() && hasSnappedToBed && targetBedPos != null) {
                return isValidBed(targetBedPos);
            }

            return !pet.getIsSitting() && targetBedPos != null && exactSleepPosition != null &&
                    isValidBed(targetBedPos);
        }

        @Override
        public void start() {
            if (exactSleepPosition != null) {
                FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " starting to move to exact sleep position: " + exactSleepPosition);
                pet.getNavigation().moveTo(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z, 1.0);
            }
            hasSnappedToBed = false;
            bedRegenTimer = 0;
            wasPlayingSleepAnimation = false;
        }

        @Override
        public void tick() {
            if (targetBedPos == null || exactSleepPosition == null) {
                return;
            }

            Vec3 petPos = pet.position();
            double distanceToSleepPos = petPos.distanceTo(exactSleepPosition);

            // Snap to bed position when very close
            if (distanceToSleepPos <= 1.5 && !hasSnappedToBed && hasClaimedBed) {
                if (!pet.level().isClientSide) {
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " within snap range, teleporting to exact position");

                    pet.getNavigation().stop();

                    // Special handling for flying pets
                    if (pet instanceof AbstractFlyingSpellCastingPet) {
                        // Stop all movement and hover controls for flying pets
                        pet.setDeltaMovement(Vec3.ZERO);
                        pet.getMoveControl().setWantedPosition(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z, 0.0);
                    } else {
                        pet.setDeltaMovement(Vec3.ZERO);
                    }

                    pet.setPos(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z);
                    hasSnappedToBed = true;

                    try {
                        pet.setSitting(true);
                    } catch (Exception e) {
                        FamiliarsLib.LOGGER.error("Error setting pet to sitting: ", e);
                    }
                }
                return;
            }

            // Handle sleep animation and regeneration when snapped and sitting
            if (hasSnappedToBed && pet.getIsSitting()) {
                // Ensure flying pets stay in position while sleeping
                if (pet instanceof AbstractFlyingSpellCastingPet) {
                    double currentDistance = pet.position().distanceTo(exactSleepPosition);
                    if (currentDistance > 0.5) {
                        pet.setPos(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z);
                        pet.setDeltaMovement(Vec3.ZERO);
                    }
                }

                handleSleepAndRegeneration();
                return;
            }

            // Continue moving to bed if not snapped yet
            if (!hasSnappedToBed && (pet.getNavigation().isDone() || distanceToSleepPos > 3.0)) {
                pet.getNavigation().moveTo(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z, 1.2);
            }
        }

        @Override
        public void stop() {
            try {
                if (pet.getIsSitting()) {
                    pet.setSitting(false);
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " stopped sitting when bed goal ended");
                }

                // Move pet one block above the bed when they finish sleeping
                if (hasSnappedToBed && targetBedPos != null && exactSleepPosition != null) {
                    // Calculate exit position (one block above the bed)
                    Vec3 exitPosition = new Vec3(
                            exactSleepPosition.x,
                            exactSleepPosition.y + 1.0, // One block above
                            exactSleepPosition.z
                    );

                    // Check if the exit position is safe
                    BlockPos exitBlockPos = BlockPos.containing(exitPosition);
                    boolean isSafeToExit = pet.level().getBlockState(exitBlockPos).isAir() ||
                            pet.level().getBlockState(exitBlockPos).canBeReplaced();

                    if (isSafeToExit) {
                        pet.setPos(exitPosition.x, exitPosition.y, exitPosition.z);
                        FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " moved to exit position: " + exitPosition);
                    } else {
                        // If one block above isn't safe, try to find a safe position nearby
                        Vec3 safeExitPosition = findSafeExitPosition(exactSleepPosition);
                        if (safeExitPosition != null) {
                            pet.setPos(safeExitPosition.x, safeExitPosition.y, safeExitPosition.z);
                            FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " moved to safe exit position: " + safeExitPosition);
                        }
                    }
                }

                // Release the bed when stopping
                if (hasClaimedBed && targetBedPos != null) {
                    releaseBed();
                }

                resetBedState();
                pet.getNavigation().stop();
                cooldownTicks = SEARCH_COOLDOWN;

                // Reset sleep animation variables
                bedRegenTimer = 0;
                wasPlayingSleepAnimation = false;

            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error in FindAndUsePetBedGoal.stop(): ", e);
                // Force cleanup even if there's an error
                forceCleanup();
            }
        }

        // Add this helper method to the FindAndUsePetBedGoal class
        private Vec3 findSafeExitPosition(Vec3 bedPosition) {
            // Try positions around the bed if directly above isn't safe
            double[][] offsets = {
                    {0, 1, 0},    // Directly above (already tried, but include for completeness)
                    {1, 1, 0},    // One block to the side and up
                    {-1, 1, 0},   // Other side and up
                    {0, 1, 1},    // Forward and up
                    {0, 1, -1},   // Backward and up
                    {1, 0, 0},    // Just to the side (same level)
                    {-1, 0, 0},   // Other side (same level)
                    {0, 0, 1},    // Forward (same level)
                    {0, 0, -1},   // Backward (same level)
                    {0, 2, 0}     // Two blocks above
            };

            for (double[] offset : offsets) {
                Vec3 testPosition = bedPosition.add(offset[0], offset[1], offset[2]);
                BlockPos testBlockPos = BlockPos.containing(testPosition);

                // Check if position is safe (air and not colliding)
                boolean isAir = pet.level().getBlockState(testBlockPos).isAir() ||
                        pet.level().getBlockState(testBlockPos).canBeReplaced();
                boolean hasGroundNearby = hasGroundNearby(testBlockPos);

                if (isAir && hasGroundNearby) {
                    return testPosition;
                }
            }

            return null; // No safe position found
        }

        // Add this helper method to check if there's ground nearby
        private boolean hasGroundNearby(BlockPos pos) {
            // Check if there's solid ground within 3 blocks below
            for (int y = 0; y <= 3; y++) {
                BlockPos groundCheck = pos.offset(0, -y, 0);
                if (pet.level().getBlockState(groundCheck).isSolid()) {
                    return true;
                }
            }
            return false;
        }

        // Add helper methods for better state management
        private void resetBedState() {
            targetBedPos = null;
            exactSleepPosition = null;
            hasSnappedToBed = false;
            hasClaimedBed = false;
        }

        private void forceCleanup() {
            try {
                if (targetBedPos != null) {
                    BlockEntity be = pet.level().getBlockEntity(targetBedPos);
                    if (be instanceof AbstractFamiliarBedBlockEntity petBed && petBed.isBedTaken()) {
                        petBed.setBedTaken(false);
                        FamiliarsLib.LOGGER.debug("Force-released bed at {} for pet {}", targetBedPos, pet.getUUID());
                    }
                }
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error in force cleanup: ", e);
            } finally {
                resetBedState();
            }
        }

        public BlockPos getTargetBedPos() {
            return targetBedPos;
        }

        public boolean hasClaimedBed() {
            return hasClaimedBed;
        }

        public boolean isActuallyRunning() {
            return targetBedPos != null && hasClaimedBed;
        }

        private BlockPos findAvailableBed() {
            BlockPos petPos = pet.blockPosition();

            FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " searching for available bed around " + petPos);

            // Search in a radius around the pet
            int searchRange = (int) searchRadius;
            for (int x = -searchRange; x <= searchRange; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -searchRange; z <= searchRange; z++) {
                        BlockPos checkPos = petPos.offset(x, y, z);

                        // Check distance
                        double distance = petPos.distSqr(checkPos);
                        if (distance > searchRadius * searchRadius) {
                            continue;
                        }

                        if (pet.level().getBlockState(checkPos).getBlock() instanceof AbstractFamiliarBedBlock) {
                            BlockEntity be = pet.level().getBlockEntity(checkPos);
                            if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                                // Check if bed is available (not taken)
                                if (!petBed.isBedTaken()) {
                                    FamiliarsLib.LOGGER.debug("Found available bed at " + checkPos);
                                    return checkPos;
                                }
                            }
                        }
                    }
                }
            }

            FamiliarsLib.LOGGER.debug("No available bed found for pet " + pet.getUUID());
            return null;
        }

        private boolean isValidBed(BlockPos pos) {
            try {
                if (!(pet.level().getBlockState(pos).getBlock() instanceof AbstractFamiliarBedBlock)) {
                    return false;
                }

                BlockEntity be = pet.level().getBlockEntity(pos);
                return be instanceof AbstractFamiliarBedBlockEntity;
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error checking if bed is valid: ", e);
                return false;
            }
        }

        private void releaseBed() {
            if (targetBedPos == null) return;

            BlockEntity be = pet.level().getBlockEntity(targetBedPos);
            if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                petBed.setBedTaken(false);
                hasClaimedBed = false;
                FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " released bed at " + targetBedPos);
            }
        }

        private void handleSleepAndRegeneration() {
            boolean isOnValidBed = isOnCompatibleBed();
            boolean shouldPlaySleepAnimation = pet.getIsSitting() && isOnValidBed;

            if (shouldPlaySleepAnimation) {
                if (!wasPlayingSleepAnimation) {
                    if (!pet.level().isClientSide) {
                        FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " starting sleep animation on compatible bed");
                    }
                    wasPlayingSleepAnimation = true;
                }

                if (!pet.level().isClientSide) {
                    bedRegenTimer++;
                    if (bedRegenTimer >= 20) { // Every second
                        if (pet.getHealth() < pet.getMaxHealth()) {
                            pet.heal(1.0F);
                            FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " healed to " + pet.getHealth() + "/" + pet.getMaxHealth());

                            // Spawn sleeping particles
                            try {
                                CylinderParticleManager.spawnParticlesAtBlockPos(
                                        pet.level(),
                                        pet.position(),
                                        1,
                                        FParticleRegistry.SLEEP_PARTICLE.get(),
                                        CylinderParticleManager.ParticleDirection.UPWARD,
                                        0.1,
                                        0,
                                        .8
                                );
                            } catch (Exception e) {
                                FamiliarsLib.LOGGER.error("Error spawning sleep particles: ", e);
                            }
                        } else {
                            // Full health
                            FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " fully healed, goal will stop");
                        }
                        bedRegenTimer = 0;
                    }
                }
            } else {
                // Not on a valid bed or not sitting
                if (wasPlayingSleepAnimation) {
                    if (!pet.level().isClientSide) {
                        FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " stopping sleep animation - not on valid bed or not sitting");
                    }
                    wasPlayingSleepAnimation = false;
                }
                if (bedRegenTimer > 0) {
                    bedRegenTimer = 0;
                }
            }
        }

        private boolean isOnCompatibleBed() {
            if (targetBedPos == null) return false;

            BlockPos petPos = pet.blockPosition();

            // Check if pet is on or very close to the target bed
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 0; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos checkPos = petPos.offset(x, y, z);

                        if (checkPos.equals(targetBedPos)) {
                            if (pet.level().getBlockState(checkPos).getBlock() instanceof AbstractFamiliarBedBlock) {
                                BlockEntity be = pet.level().getBlockEntity(checkPos);
                                if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                                    return petBed.isPositionCorrectForSleeping(pet.position());
                                }
                            }
                        }
                    }
                }
            }

            return false;
        }
    }

    public static class ApplyRandomEffectGoal extends Goal {
        private final AbstractSpellCastingPet entity;
        private final Supplier<LivingEntity> targetEntity;
        private final List<MobEffectInstance> effectHolders;
        private final int interval;
        private int tickCounter;

        public ApplyRandomEffectGoal(AbstractSpellCastingPet entity, Supplier<LivingEntity> targetEntity, List<MobEffectInstance> effectHolders, int interval) {
            this.entity = entity;
            this.targetEntity = targetEntity;
            this.effectHolders = effectHolders;
            this.interval = interval;
            this.tickCounter = 0;

            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.targetEntity.get();
            if(owner == null){ return false;}
            if (!entity.canExecuteGoalsInHouse()) {
                return false;
            }
            if(horizontalDistanceSqr(owner, this.entity.position()) > 400){ return false;}
            return ++tickCounter >= interval;
        }

        private float horizontalDistanceSqr(LivingEntity livingEntity, Vec3 vec3) {
            var dx = livingEntity.getX() - vec3.x;
            var dz = livingEntity.getZ() - vec3.z;
            return (float) (dx * dx + dz * dz);
        }

        @Override
        public void start() {
            tickCounter = 0;
            this.entity.triggerAnim("interact_controller", "interact");

            LivingEntity owner = this.targetEntity.get();
            int randomIndex = ThreadLocalRandom.current().nextInt(effectHolders.size());
            this.entity.triggerAnim("interact_controller", "interact");
            owner.addEffect(effectHolders.get(randomIndex));
        }

        @Override
        public boolean canContinueToUse() {
            if (!entity.canExecuteGoalsInHouse()) {
                return false;
            }
            return canUse();
        }
    }

    public static class TeleportToOwnerGoal extends Goal {
        private final AbstractSpellCastingPet mob;
        @javax.annotation.Nullable
        private LivingEntity owner;
        private Supplier<LivingEntity> ownerGetter;
        private float teleportDistance;
        private int teleportDelay;

        private Vec3 lastOwnerPosition = Vec3.ZERO;
        private int ticksSinceLastCheck = 0;
        private static final int CHECK_INTERVAL = 10;
        private static final double INSTANT_TELEPORT_THRESHOLD = 20.0;

        private static final int SAFE_POSITION_SEARCH_RADIUS = 16;

        public TeleportToOwnerGoal(AbstractSpellCastingPet pTamable, Supplier<LivingEntity> ownerGetter, float teleportDistance) {
            this.mob = pTamable;
            this.ownerGetter = ownerGetter;
            this.teleportDistance = teleportDistance;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public void start() {
            this.teleportDelay = 0;
        }

        @Override
        public boolean canUse() {
            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity == null) {
                return false;
            }

            if (hasOwnerTeleportedInstantly(livingentity)) {
                this.owner = livingentity;
                return true;
            }

            if (this.mob.distanceToSqr(livingentity) < (double) (this.teleportDistance * this.teleportDistance)) {
                return false;
            } else if (this.mob.getIsSitting()){
                return false;
            } else if (this.mob.getIsInHouse()) {
                return false;
            } else {
                this.owner = livingentity;
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity == null) {
                return false;
            }

            if (this.teleportDelay > 0) {
                return false;
            }

            if (hasOwnerTeleportedInstantly(livingentity) || shouldTryTeleportToOwner()) {
                return true;
            }

            return canUse();
        }

        @Override
        public void tick() {
            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity == null) return;

            updateOwnerPositionTracking(livingentity);

            boolean needsTeleport = this.shouldTryTeleportToOwner() || hasOwnerTeleportedInstantly(livingentity);

            if (!needsTeleport) {
                this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
            } else {
                if (!this.tryToTeleportToOwnerSafely()) {
                    this.teleportDelay = 10;
                    this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
                }
            }
        }

        private boolean hasOwnerTeleportedInstantly(LivingEntity owner) {
            if(this.mob.getIsInHouse()) return false;
            ticksSinceLastCheck++;

            if (ticksSinceLastCheck >= CHECK_INTERVAL) {
                Vec3 currentOwnerPos = owner.position();

                if (lastOwnerPosition.equals(Vec3.ZERO)) {
                    lastOwnerPosition = currentOwnerPos;
                    ticksSinceLastCheck = 0;
                    return false;
                }

                double distanceMoved = lastOwnerPosition.distanceTo(currentOwnerPos);

                boolean hasTeleported = distanceMoved > INSTANT_TELEPORT_THRESHOLD;

                lastOwnerPosition = currentOwnerPos;
                ticksSinceLastCheck = 0;

                return hasTeleported;
            }

            return false;
        }

        private void updateOwnerPositionTracking(LivingEntity owner) {
            if (ticksSinceLastCheck == 0) {
                lastOwnerPosition = owner.position();
            }
        }

        public boolean tryToTeleportToOwnerSafely() {
            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity != null) {
                BlockPos safePos = findSafeTeleportPosition(livingentity.blockPosition(), mob.level());
                if (safePos != null && !safePos.equals(livingentity.blockPosition())) {
                    mob.moveTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, mob.getYRot(), mob.getXRot());
                    return true;
                }
            }
            return false;
        }

        private BlockPos findSafeTeleportPosition(BlockPos ownerPos, Level level) {
            BlockPos nearbyPos = findSafeTeleportPositionAtLevel(ownerPos, level, 4);
            if (nearbyPos != null) return nearbyPos;

            for (int yOffset = -1; yOffset >= -32; yOffset--) {
                BlockPos lowerPos = ownerPos.offset(0, yOffset, 0);
                if (level.getBlockState(lowerPos.below()).isSolid()) {
                    BlockPos safePos = findSafeTeleportPositionAtLevel(lowerPos, level, 4);
                    if (safePos != null) return safePos;
                }
            }

            return findSafeTeleportPositionExtended(ownerPos, level);
        }

        private BlockPos findSafeTeleportPositionAtLevel(BlockPos centerPos, Level level, int radius) {
            for (int i = 0; i < 10; i++) {
                int j = mob.getRandom().nextIntBetweenInclusive(-radius, radius);
                int k = mob.getRandom().nextIntBetweenInclusive(-radius, radius);
                if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                    BlockPos testPos = new BlockPos(centerPos.getX() + j, centerPos.getY(), centerPos.getZ() + k);
                    if (this.canTeleportTo(testPos)) {
                        return testPos;
                    }
                }
            }

            for (int r = 1; r <= radius; r++) {
                for (int angle = 0; angle < 8; angle++) {
                    double radians = (Math.PI * 2 * angle) / 8;
                    int x = centerPos.getX() + (int)(Math.cos(radians) * r);
                    int z = centerPos.getZ() + (int)(Math.sin(radians) * r);

                    BlockPos testPos = new BlockPos(x, centerPos.getY(), z);
                    if (canTeleportTo(testPos)) {
                        return testPos;
                    }
                }
            }

            return null;
        }

        private BlockPos findSafeTeleportPositionExtended(BlockPos centerPos, Level level) {
            for (int radius = 1; radius <= SAFE_POSITION_SEARCH_RADIUS; radius++) {
                for (int angle = 0; angle < 16; angle++) {
                    double radians = (Math.PI * 2 * angle) / 16;
                    int x = centerPos.getX() + (int)(Math.cos(radians) * radius);
                    int z = centerPos.getZ() + (int)(Math.sin(radians) * radius);

                    for (int yOffset = 10; yOffset >= -10; yOffset--) {
                        BlockPos testPos = new BlockPos(x, centerPos.getY() + yOffset, z);
                        if (canTeleportTo(testPos)) {
                            return testPos;
                        }
                    }
                }
            }
            return null;
        }

        public boolean shouldTryTeleportToOwner() {
            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity == null) return false;
            if(this.mob.getIsInHouse()) return false;

            return mob.distanceToSqr(livingentity) >= teleportDistance * teleportDistance;
        }

        private boolean canTeleportTo(BlockPos pPos) {
            Level level = mob.level();

            if (!level.isLoaded(pPos)) return false;

            if (!level.getBlockState(pPos).isAir() || !level.getBlockState(pPos.above()).isAir()) {
                return false;
            }

            BlockState blockstate = level.getBlockState(pPos.below());
            if (!blockstate.isSolid() || blockstate.getBlock() instanceof LeavesBlock) {
                return false;
            }

            PathType pathtype = WalkNodeEvaluator.getPathTypeStatic(mob, pPos);
            if (pathtype != PathType.WALKABLE) {
                return false;
            }

            BlockPos blockpos = pPos.subtract(mob.blockPosition());
            if (!level.noCollision(mob, mob.getBoundingBox().move(blockpos))) {
                return false;
            }

            return true;
        }
    }

    public static class StealItemsWhenNotWatchedGoal extends Goal {
        private final net.alshanex.familiarslib.entity.AbstractSpellCastingPet mob;
        private final double searchRadius;
        private ItemEntity targetItem;
        private int stealingTime;
        private final int STEALING_DURATION = 20;

        public StealItemsWhenNotWatchedGoal(net.alshanex.familiarslib.entity.AbstractSpellCastingPet mob, double searchRadius) {
            this.mob = mob;
            this.searchRadius = searchRadius;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (mob.getIsSitting()) {
                return false;
            }

            if(mob.getMovementDisabled()){return false;}

            List<ItemEntity> nearbyItems = mob.level().getEntitiesOfClass(
                    ItemEntity.class,
                    mob.getBoundingBox().inflate(searchRadius),
                    this::isDesirableItem
            );

            if (nearbyItems.isEmpty()) {
                return false;
            }

            if (isAnyPlayerWatching()) {
                return false;
            }

            targetItem = nearbyItems.get(0);
            double closestDistance = mob.distanceToSqr(targetItem);

            for (int i = 1; i < nearbyItems.size(); i++) {
                ItemEntity item = nearbyItems.get(i);
                double distance = mob.distanceToSqr(item);

                if (distance < closestDistance) {
                    targetItem = item;
                    closestDistance = distance;
                }
            }

            return true;
        }

        @Override
        public void start() {
            stealingTime = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return targetItem != null
                    && targetItem.isAlive()
                    && !isAnyPlayerWatching()
                    && stealingTime < STEALING_DURATION;
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(
                    targetItem.getX(),
                    targetItem.getY(),
                    targetItem.getZ()
            );

            stealingTime++;

            if (stealingTime >= STEALING_DURATION) {
                mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.0F);

                targetItem.discard();
                targetItem = null;
            }
        }

        @Override
        public void stop() {
            targetItem = null;
            stealingTime = 0;
        }

        private boolean isDesirableItem(ItemEntity itemEntity) {
            return itemEntity != null
                    && itemEntity.isAlive()
                    && itemEntity.getItem().is(ModTags.ILLUSIONIST_STEALS);
        }

        private boolean isAnyPlayerWatching() {
            List<Player> nearbyPlayers = mob.level().getEntitiesOfClass(
                    Player.class,
                    new AABB(
                            mob.getX() - 16.0D,
                            mob.getY() - 16.0D,
                            mob.getZ() - 16.0D,
                            mob.getX() + 16.0D,
                            mob.getY() + 16.0D,
                            mob.getZ() + 16.0D
                    )
            );

            for (Player player : nearbyPlayers) {
                if (!player.isAlive() || player.isSpectator()) {
                    continue;
                }

                if (isInPlayerFieldOfView(player)) {
                    return true;
                }
            }

            return false;
        }

        private boolean isInPlayerFieldOfView(Player player) {
            Vec3 playerViewVector = player.getViewVector(1.0F).normalize();
            Vec3 playerToMobVector = new Vec3(
                    mob.getX() - player.getX(),
                    mob.getEyeY() - player.getEyeY(),
                    mob.getZ() - player.getZ()
            );

            double distanceSquared = playerToMobVector.lengthSqr();
            playerToMobVector = playerToMobVector.normalize();

            if (distanceSquared < 4.0D) {
                return true;
            }

            double dotProduct = playerViewVector.dot(playerToMobVector);

            if (dotProduct > -0.1D && player.hasLineOfSight(mob)) {
                return true;
            }

            return false;
        }
    }

    public static class MovementAwareFollowOwnerGoal extends Goal {
        private final AbstractSpellCastingPet pet;
        @Nullable
        private LivingEntity owner;
        private Supplier<LivingEntity> ownerGetter;
        private final double speedModifier;
        private final PathNavigation navigation;
        private int timeToRecalcPath;
        private final float stopDistance;
        private final float startDistance;
        private float oldWaterCost;
        private float teleportDistance;
        private boolean canFly;

        public MovementAwareFollowOwnerGoal(AbstractSpellCastingPet pet, Supplier<LivingEntity> ownerGetter, double speedModifier, float startDistance, float stopDistance, boolean canFly, float teleportDistance) {
            this.pet = pet;
            this.ownerGetter = ownerGetter;
            this.speedModifier = speedModifier;
            this.navigation = pet.getNavigation();
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.teleportDistance = teleportDistance;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
            this.canFly = canFly;
        }

        @Override
        public boolean canUse() {
            if (pet.getMovementDisabled()) {
                return false;
            }

            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity == null) {
                return false;
            } else if (this.pet.distanceToSqr(livingentity) < (double) (this.startDistance * this.startDistance)) {
                return false;
            } else if (this.pet.getIsInHouse()) {
                return false;
            }  else {
                this.owner = livingentity;
                return true;
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (pet.getMovementDisabled()) {
                return false;
            }

            if (this.navigation.isDone()) {
                return false;
            } else {
                return !(this.pet.distanceToSqr(this.owner) <= (double) (this.stopDistance * this.stopDistance));
            }
        }

        @Override
        public void start() {
            this.timeToRecalcPath = 0;
            this.oldWaterCost = this.pet.getPathfindingMalus(PathType.WATER);
            this.pet.setPathfindingMalus(PathType.WATER, 0.0F);
        }

        @Override
        public void stop() {
            this.owner = null;
            this.navigation.stop();
            this.pet.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
        }

        @Override
        public void tick() {
            boolean flag = this.shouldTryTeleportToOwner();
            if (!flag) {
                this.pet.getLookControl().setLookAt(this.owner, 10.0F, (float) this.pet.getMaxHeadXRot());
            }

            if (--this.timeToRecalcPath <= 0) {
                this.timeToRecalcPath = this.adjustedTickDelay(10);
                if (flag) {
                    this.tryToTeleportToOwner();
                } else {
                    if (false && canFly && !pet.onGround()) {
                        Vec3 vec3 = owner.position();
                        this.pet.getMoveControl().setWantedPosition(vec3.x, vec3.y + 2, vec3.z, this.speedModifier);
                    } else {
                        this.navigation.moveTo(this.owner, this.speedModifier);
                    }
                }
            }
        }

        public void tryToTeleportToOwner() {
            LivingEntity livingentity = this.ownerGetter.get();
            if (livingentity != null) {
                this.teleportToAroundBlockPos(livingentity.blockPosition());
            }
        }

        public boolean shouldTryTeleportToOwner() {
            LivingEntity livingentity = this.ownerGetter.get();
            return livingentity != null && pet.distanceToSqr(livingentity) >= teleportDistance * teleportDistance;
        }

        private void teleportToAroundBlockPos(BlockPos pPos) {
            for (int i = 0; i < 10; i++) {
                int j = pet.getRandom().nextIntBetweenInclusive(-3, 3);
                int k = pet.getRandom().nextIntBetweenInclusive(-3, 3);
                if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                    int l = pet.getRandom().nextIntBetweenInclusive(-1, 1);
                    if (this.maybeTeleportTo(pPos.getX() + j, pPos.getY() + l, pPos.getZ() + k)) {
                        return;
                    }
                }
            }
        }

        private boolean maybeTeleportTo(int pX, int pY, int pZ) {
            if (!this.canTeleportTo(new BlockPos(pX, pY, pZ))) {
                return false;
            } else {
                pet.moveTo((double) pX + 0.5, (double) pY, (double) pZ + 0.5, pet.getYRot(), pet.getXRot());
                this.navigation.stop();
                return true;
            }
        }

        private boolean canTeleportTo(BlockPos pPos) {
            PathType pathtype = WalkNodeEvaluator.getPathTypeStatic(pet, pPos);
            if (pathtype != PathType.WALKABLE) {
                return false;
            } else {
                BlockState blockstate = pet.level().getBlockState(pPos.below());
                if (!this.canFly && blockstate.getBlock() instanceof LeavesBlock) {
                    return false;
                } else {
                    BlockPos blockpos = pPos.subtract(pet.blockPosition());
                    return pet.level().noCollision(pet, pet.getBoundingBox().move(blockpos));
                }
            }
        }
    }

    public static class MovementAwareLookAtPlayerGoal extends Goal {
        public static final float DEFAULT_PROBABILITY = 0.02F;
        private final AbstractSpellCastingPet pet;
        @Nullable
        protected Entity lookAt;
        protected final float lookDistance;
        private int lookTime;
        protected final float probability;
        private final boolean onlyHorizontal;
        protected final Class<? extends LivingEntity> lookAtType;
        protected final TargetingConditions lookAtContext;

        public MovementAwareLookAtPlayerGoal(AbstractSpellCastingPet pet, Class<? extends LivingEntity> lookAtType, float lookDistance) {
            this(pet, lookAtType, lookDistance, DEFAULT_PROBABILITY);
        }

        public MovementAwareLookAtPlayerGoal(AbstractSpellCastingPet pet, Class<? extends LivingEntity> lookAtType, float lookDistance, float probability) {
            this(pet, lookAtType, lookDistance, probability, false);
        }

        public MovementAwareLookAtPlayerGoal(AbstractSpellCastingPet pet, Class<? extends LivingEntity> lookAtType, float lookDistance, float probability, boolean onlyHorizontal) {
            this.pet = pet;
            this.lookAtType = lookAtType;
            this.lookDistance = lookDistance;
            this.probability = probability;
            this.onlyHorizontal = onlyHorizontal;
            this.setFlags(EnumSet.of(Flag.LOOK));
            if (lookAtType == Player.class) {
                this.lookAtContext = TargetingConditions.forNonCombat()
                        .range((double)lookDistance)
                        .selector(p_25531_ -> EntitySelector.notRiding(pet).test(p_25531_));
            } else {
                this.lookAtContext = TargetingConditions.forNonCombat().range((double)lookDistance);
            }
        }

        @Override
        public boolean canUse() {
            if (pet.getMovementDisabled()) {
                return false;
            }

            if (this.pet.getRandom().nextFloat() >= this.probability) {
                return false;
            } else {
                if (this.pet.getTarget() != null) {
                    this.lookAt = this.pet.getTarget();
                }

                if (this.lookAtType == Player.class) {
                    this.lookAt = this.pet.level().getNearestPlayer(this.lookAtContext, this.pet, this.pet.getX(), this.pet.getEyeY(), this.pet.getZ());
                } else {
                    this.lookAt = this.pet
                            .level()
                            .getNearestEntity(
                                    this.pet
                                            .level()
                                            .getEntitiesOfClass(
                                                    this.lookAtType,
                                                    this.pet.getBoundingBox().inflate((double)this.lookDistance, 3.0, (double)this.lookDistance),
                                                    p_148124_ -> true
                                            ),
                                    this.lookAtContext,
                                    this.pet,
                                    this.pet.getX(),
                                    this.pet.getEyeY(),
                                    this.pet.getZ()
                            );
                }

                return this.lookAt != null;
            }
        }

        @Override
        public boolean canContinueToUse() {
            if (pet.getMovementDisabled()) {
                return false;
            }

            if (!this.lookAt.isAlive()) {
                return false;
            } else {
                return this.pet.distanceToSqr(this.lookAt) > (double)(this.lookDistance * this.lookDistance) ? false : this.lookTime > 0;
            }
        }

        @Override
        public void start() {
            this.lookTime = this.adjustedTickDelay(40 + this.pet.getRandom().nextInt(40));
        }

        @Override
        public void stop() {
            this.lookAt = null;
        }

        @Override
        public void tick() {
            if (this.lookAt.isAlive()) {
                double d0 = this.onlyHorizontal ? this.pet.getEyeY() : this.lookAt.getEyeY();
                this.pet.getLookControl().setLookAt(this.lookAt.getX(), d0, this.lookAt.getZ());
                this.lookTime--;
            }
        }
    }

    public static class MovementAwareRandomLookAroundGoal extends Goal {
        private final AbstractSpellCastingPet pet;
        private double relX;
        private double relZ;
        private int lookTime;

        public MovementAwareRandomLookAroundGoal(AbstractSpellCastingPet pet) {
            this.pet = pet;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (pet.getMovementDisabled()) {
                return false;
            }

            return this.pet.getRandom().nextFloat() < 0.02F;
        }

        @Override
        public boolean canContinueToUse() {
            if (pet.getMovementDisabled()) {
                return false;
            }

            return this.lookTime >= 0;
        }

        @Override
        public void start() {
            double d0 = (Math.PI * 2) * this.pet.getRandom().nextDouble();
            this.relX = Math.cos(d0);
            this.relZ = Math.sin(d0);
            this.lookTime = 20 + this.pet.getRandom().nextInt(20);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            this.lookTime--;
            this.pet.getLookControl().setLookAt(this.pet.getX() + this.relX, this.pet.getEyeY(), this.pet.getZ() + this.relZ);
        }
    }

    public static class TargetAttackerOfPlayersGoal extends TargetGoal {
        private final AbstractSpellCastingPet pet;
        private final Supplier<LivingEntity> owner;
        private LivingEntity target;

        public TargetAttackerOfPlayersGoal(AbstractSpellCastingPet pet, Supplier<LivingEntity> owner) {
            super(pet, true);
            this.pet = pet;
            this.owner = owner;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity getOwner = this.owner.get();
            if(getOwner == null){ return false;}
            if(pet.getMovementDisabled()){ return false;}
            if (!pet.canExecuteGoalsInHouse()) {
                return false;
            }

            Level level = pet.level();

            List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
                    LivingEntity.class,
                    pet.getBoundingBox().inflate(15),
                    entity -> entity instanceof Mob mob && mob.getTarget() == getOwner
            );

            if(!nearbyEntities.isEmpty()){
                this.target = nearbyEntities.getFirst();
                return  true;
            }

            return false;
        }

        public void start() {
            this.pet.setTarget(this.target);
            this.pet.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, this.target, 200L);
            super.start();
        }
    }

    public static class ThunderstrikeGoal extends Goal {
        private final AbstractSpellCastingPet pet;
        private Creeper creeper;

        public ThunderstrikeGoal(AbstractSpellCastingPet pet) {
            this.pet = pet;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if(this.creeper != null){ return false;}
            if(pet.getMovementDisabled()){ return false;}
            if (!pet.canExecuteGoalsInHouse()) {
                return false;
            }
            this.creeper = pet.level().getEntitiesOfClass(
                    Creeper.class,
                    pet.getBoundingBox().inflate(15),
                    creeper -> !creeper.isPowered()
            ).stream().min(Comparator.comparingDouble(pet::distanceTo)).orElse(null);

            return this.creeper != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.creeper != null && this.creeper.isAlive() && !this.creeper.isPowered() && pet.canExecuteGoalsInHouse();
        }

        @Override
        public void start() {
            pet.getNavigation().moveTo(this.creeper, 1.0);
        }

        @Override
        public void tick() {
            if (creeper == null) return;

            if (pet.distanceTo(creeper) < 8.0) {
                pet.getLookControl().setLookAt(creeper, 10.0F, 10.0F);
                if (!creeper.isPowered() && pet.level instanceof ServerLevel serverLevel) {
                    pet.triggerAnim("interact_controller", "interact");

                    LightningBolt lightningBolt = EntityType.LIGHTNING_BOLT.create(pet.level);
                    lightningBolt.setVisualOnly(true);
                    lightningBolt.setDamage(0);
                    lightningBolt.setPos(creeper.getX(), creeper.getY(), creeper.getZ());
                    pet.level.addFreshEntity(lightningBolt);
                    creeper.thunderHit(serverLevel, lightningBolt);
                }
            } else {
                pet.getNavigation().moveTo(creeper, 1.0);
            }
        }

        @Override
        public void stop() {
            this.creeper = null;
        }
    }

    public static class WitherifyGoal extends Goal {
        private final AbstractSpellCastingPet pet;
        private Skeleton skeleton;

        public WitherifyGoal(AbstractSpellCastingPet pet) {
            this.pet = pet;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if(this.skeleton != null){ return false;}
            if(pet.getMovementDisabled()){ return false;}
            if (!pet.canExecuteGoalsInHouse()) {
                return false;
            }
            this.skeleton = pet.level().getEntitiesOfClass(
                    Skeleton.class,
                    pet.getBoundingBox().inflate(15)
            ).stream().filter(entity -> !(entity instanceof SummonedSkeleton)).min(Comparator.comparingDouble(pet::distanceTo)).orElse(null);

            return this.skeleton != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.skeleton != null && this.skeleton.isAlive() && pet.canExecuteGoalsInHouse();
        }

        @Override
        public void start() {
            pet.getNavigation().moveTo(this.skeleton, 1.0);
        }

        @Override
        public void tick() {
            if (skeleton == null) return;

            if (pet.distanceTo(skeleton) < 3.0) {
                pet.getLookControl().setLookAt(skeleton, 10.0F, 10.0F);
                pet.triggerAnim("interact_controller", "interact");

                WitherSkeleton witherSkeleton = new WitherSkeleton(EntityType.WITHER_SKELETON, pet.level());
                witherSkeleton.setPos(skeleton.getX(), skeleton.getY(), skeleton.getZ());
                witherSkeleton.finalizeSpawn((ServerLevel) pet.level(), pet.level.getCurrentDifficultyAt(skeleton.getOnPos()), MobSpawnType.MOB_SUMMONED, null);
                pet.level().addFreshEntity(witherSkeleton);

                MagicManager.spawnParticles(pet.level, new BlastwaveParticleOptions(SchoolRegistry.ELDRITCH.get().getTargetingColor(), 2), this.skeleton.getX(), this.skeleton.getY() + .165f, this.skeleton.getZ(), 1, 0, 0, 0, 0, true);
                skeleton.remove(Entity.RemovalReason.DISCARDED);
            } else {
                pet.getNavigation().moveTo(skeleton, 1.0);
            }
        }

        @Override
        public void stop() {
            this.skeleton = null;
        }
    }

    public static class DisableSculkShriekerGoal extends Goal {
        private final AbstractSpellCastingPet controllerEntity;
        private final Supplier<LivingEntity> targetEntity;
        private final int radius;

        public DisableSculkShriekerGoal(AbstractSpellCastingPet controllerEntity, Supplier<LivingEntity> targetEntity, int radius) {
            this.controllerEntity = controllerEntity;
            this.targetEntity = targetEntity;
            this.radius = radius;
            this.setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = this.targetEntity.get();
            if(owner == null){ return false;}
            if (!controllerEntity.canExecuteGoalsInHouse()) {
                return false;
            }
            return controllerEntity.isAlive() && owner.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity owner = targetEntity.get();
            BlockPos controllerPos = owner.blockPosition();

            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        BlockPos currentPos = controllerPos.offset(x, y, z);

                        BlockState blockState = controllerEntity.level().getBlockState(currentPos);
                        if (blockState.is(Blocks.SCULK_SHRIEKER) && blockState.getValue(SculkShriekerBlock.SHRIEKING)) {
                            controllerEntity.triggerAnim("interact_controller", "interact");
                            CylinderParticleManager.spawnParticlesAtBlockPos(owner.level(), currentPos.getCenter(), 50, ParticleRegistry.FIREFLY_PARTICLE.get(), CylinderParticleManager.ParticleDirection.UPWARD, 0.5, 0, 0);
                            controllerEntity.level().setBlock(currentPos, blockState.setValue(SculkShriekerBlock.SHRIEKING, false), 3);
                        }
                    }
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && controllerEntity.canExecuteGoalsInHouse();
        }
    }

    public static class WanderAroundHouseGoal extends Goal {
        private final AbstractSpellCastingPet familiar;
        private final BlockPos housePos;
        private final double wanderRadius;
        private final double speedModifier;
        private Vec3 targetPos;
        private int cooldown = 0;

        public WanderAroundHouseGoal(AbstractSpellCastingPet familiar, BlockPos housePos, double wanderRadius, double speedModifier) {
            this.familiar = familiar;
            this.housePos = housePos;
            this.wanderRadius = wanderRadius;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            BlockEntity blockEntity = familiar.level().getBlockEntity(housePos);
            if (!(blockEntity instanceof AbstractFamiliarStorageBlockEntity)) {
                return false;
            }

            if (cooldown > 0) {
                cooldown--;
                return false;
            }

            return !familiar.getNavigation().isInProgress() &&
                    familiar.getRandom().nextInt(60) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            double distanceToHouse = familiar.position().distanceTo(Vec3.atCenterOf(housePos));
            if (distanceToHouse > wanderRadius + 5) {
                return false;
            }

            return familiar.getNavigation().isInProgress() && targetPos != null;
        }

        @Override
        public void start() {
            Vec3 houseCenter = Vec3.atCenterOf(housePos);
            Vec3 currentPos = familiar.position();

            for (int attempt = 0; attempt < 10; attempt++) {
                double angle = familiar.getRandom().nextDouble() * 2 * Math.PI;
                double distance = 3 + familiar.getRandom().nextDouble() * (wanderRadius - 3);

                double offsetX = Math.cos(angle) * distance;
                double offsetZ = Math.sin(angle) * distance;

                targetPos = houseCenter.add(offsetX, 0, offsetZ);

                if (currentPos.distanceTo(targetPos) > 2.0) {
                    break;
                }
            }

            if (targetPos != null) {
                targetPos = findGroundPosition(targetPos);

                PathNavigation navigation = familiar.getNavigation();
                boolean success = navigation.moveTo(targetPos.x, targetPos.y, targetPos.z, speedModifier);

                if (!success) {
                    Vec3 closerPos = houseCenter.add(
                            (targetPos.x - houseCenter.x) * 0.5,
                            0,
                            (targetPos.z - houseCenter.z) * 0.5
                    );
                    closerPos = findGroundPosition(closerPos);
                    navigation.moveTo(closerPos.x, closerPos.y, closerPos.z, speedModifier);
                }
            }

            cooldown = 40 + familiar.getRandom().nextInt(40);
        }

        @Override
        public void stop() {
            targetPos = null;
            familiar.getNavigation().stop();
        }

        private Vec3 findGroundPosition(Vec3 pos) {
            BlockPos blockPos = BlockPos.containing(pos);

            for (int y = 0; y <= 5; y++) {
                BlockPos checkPos = blockPos.offset(0, -y, 0);
                if (familiar.level().getBlockState(checkPos).isSolid() &&
                        familiar.level().getBlockState(checkPos.above()).isAir()) {
                    return Vec3.atBottomCenterOf(checkPos.above());
                }
            }

            for (int y = 1; y <= 3; y++) {
                BlockPos checkPos = blockPos.offset(0, y, 0);
                if (familiar.level().getBlockState(checkPos).isSolid() &&
                        familiar.level().getBlockState(checkPos.above()).isAir()) {
                    return Vec3.atBottomCenterOf(checkPos.above());
                }
            }

            return pos;
        }
    }

    public static class CasualLookAtPlayerGoal extends Goal {
        private final AbstractSpellCastingPet familiar;
        private final Class<? extends LivingEntity> lookAtType;
        private final float lookDistance;
        private LivingEntity lookAt;
        private int lookTime;

        public CasualLookAtPlayerGoal(AbstractSpellCastingPet familiar, Class<? extends LivingEntity> lookAtType, float lookDistance) {
            this.familiar = familiar;
            this.lookAtType = lookAtType;
            this.lookDistance = lookDistance;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            if (familiar.getRandom().nextInt(80) != 0) {
                return false;
            }

            this.lookAt = familiar.level().getNearestEntity(
                    this.lookAtType,
                    TargetingConditions.forNonCombat().range(this.lookDistance),
                    familiar,
                    familiar.getX(),
                    familiar.getEyeY(),
                    familiar.getZ(),
                    familiar.getBoundingBox().inflate(this.lookDistance, 3.0, this.lookDistance)
            );

            return this.lookAt != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            if (!this.lookAt.isAlive()) {
                return false;
            }

            if (familiar.distanceToSqr(this.lookAt) > (double)(this.lookDistance * this.lookDistance)) {
                return false;
            }

            return this.lookTime > 0;
        }

        @Override
        public void start() {
            this.lookTime = this.adjustedTickDelay(40 + familiar.getRandom().nextInt(40)); // 2-4 segundos
        }

        @Override
        public void stop() {
            this.lookAt = null;
        }

        @Override
        public void tick() {
            if (this.lookAt != null) {
                familiar.getLookControl().setLookAt(
                        this.lookAt.getX(),
                        this.lookAt.getEyeY(),
                        this.lookAt.getZ()
                );
            }
            this.lookTime--;
        }
    }

    public static class CasualRandomLookGoal extends Goal {
        private final AbstractSpellCastingPet familiar;
        private double relX;
        private double relZ;
        private int lookTime;

        public CasualRandomLookGoal(AbstractSpellCastingPet familiar) {
            this.familiar = familiar;
            this.setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            return familiar.getRandom().nextFloat() < 0.02F; // 2% chance por tick
        }

        @Override
        public boolean canContinueToUse() {
            return familiar.getIsInHouse() && this.lookTime >= 0;
        }

        @Override
        public void start() {
            double angle = Math.random() * 2 * Math.PI;
            this.relX = Math.cos(angle);
            this.relZ = Math.sin(angle);
            this.lookTime = 20 + familiar.getRandom().nextInt(20);
        }

        @Override
        public void tick() {
            this.lookTime--;
            familiar.getLookControl().setLookAt(
                    familiar.getX() + this.relX,
                    familiar.getEyeY(),
                    familiar.getZ() + this.relZ
            );
        }
    }

    public static class StayNearHouseGoal extends Goal {
        private final AbstractSpellCastingPet familiar;
        private final BlockPos housePos;
        private final double maxDistance;

        public StayNearHouseGoal(AbstractSpellCastingPet familiar, BlockPos housePos, double maxDistance) {
            this.familiar = familiar;
            this.housePos = housePos;
            this.maxDistance = maxDistance;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            BlockEntity blockEntity = familiar.level().getBlockEntity(housePos);
            if (!(blockEntity instanceof AbstractFamiliarStorageBlockEntity)) {
                return false;
            }

            double distance = familiar.position().distanceTo(Vec3.atCenterOf(housePos));
            return distance > maxDistance;
        }

        @Override
        public boolean canContinueToUse() {
            if (!familiar.getIsInHouse()) {
                return false;
            }

            double distance = familiar.position().distanceTo(Vec3.atCenterOf(housePos));
            return distance > maxDistance * 0.8 && familiar.getNavigation().isInProgress();
        }

        @Override
        public void start() {
            Vec3 houseCenter = Vec3.atCenterOf(housePos);
            Vec3 currentPos = familiar.position();
            Vec3 direction = houseCenter.subtract(currentPos).normalize();

            double offsetX = (familiar.getRandom().nextDouble() - 0.5) * 6;
            double offsetZ = (familiar.getRandom().nextDouble() - 0.5) * 6;

            Vec3 targetPos = currentPos.add(direction.scale(8.0)).add(offsetX, 0, offsetZ);

            PathNavigation navigation = familiar.getNavigation();
            navigation.moveTo(targetPos.x, targetPos.y, targetPos.z, 1.2);
        }
    }

    public static class AlliedFamiliarDefenseGoal extends TargetGoal {
        private final AbstractSpellCastingPet familiar;
        private LivingEntity targetMob;
        private int timestamp;
        private static final double HELP_RANGE = 16.0D;

        public AlliedFamiliarDefenseGoal(AbstractSpellCastingPet familiar) {
            super(familiar, false);
            this.familiar = familiar;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            // Don't help if we already have a target or are in house mode
            if (familiar.getTarget() != null || familiar.getIsInHouse()) {
                return false;
            }

            // Don't help if we're sitting
            if (familiar.getIsSitting()) {
                return false;
            }

            // Don't help if movement is disabled
            if (familiar.getMovementDisabled()) {
                return false;
            }

            // Don't help if we can't execute goals in house (for storage blocks)
            if (!familiar.canExecuteGoalsInHouse()) {
                return false;
            }

            // Look for allied familiars in combat within range
            AABB searchArea = familiar.getBoundingBox().inflate(HELP_RANGE);
            List<AbstractSpellCastingPet> nearbyFamiliars = familiar.level().getEntitiesOfClass(
                    AbstractSpellCastingPet.class,
                    searchArea,
                    alliedFamiliar -> alliedFamiliar != familiar && // Not ourselves
                            familiar.isAlliedTo(alliedFamiliar) && // Allied to us
                            alliedFamiliar.getTarget() != null && // Has a target
                            alliedFamiliar.getTarget().isAlive() && // Target is alive
                            !alliedFamiliar.getIsInHouse() && // Not in house mode
                            !alliedFamiliar.getIsSitting() && // Not sitting
                            canHelpAgainst(alliedFamiliar.getTarget()) // We can help against this target
            );

            // Find the closest allied familiar that needs help
            AbstractSpellCastingPet closestAlliedFamiliar = null;
            double closestDistance = Double.MAX_VALUE;

            for (AbstractSpellCastingPet alliedFamiliar : nearbyFamiliars) {
                double distance = familiar.distanceToSqr(alliedFamiliar);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestAlliedFamiliar = alliedFamiliar;
                }
            }

            if (closestAlliedFamiliar != null && closestAlliedFamiliar.getTarget() != null) {
                this.targetMob = closestAlliedFamiliar.getTarget();
                return true;
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            // Stop if we're now sitting or in house mode
            if (familiar.getIsSitting() || familiar.getIsInHouse()) {
                return false;
            }

            // Stop if movement is disabled
            if (familiar.getMovementDisabled()) {
                return false;
            }

            // Stop if we can't execute goals in house
            if (!familiar.canExecuteGoalsInHouse()) {
                return false;
            }

            // Stop if target is no longer valid
            if (targetMob == null || !targetMob.isAlive()) {
                return false;
            }

            // Stop if we can no longer help against this target
            if (!canHelpAgainst(targetMob)) {
                return false;
            }

            // Stop if target is too far away
            if (familiar.distanceToSqr(targetMob) > HELP_RANGE * HELP_RANGE * 2) {
                return false;
            }

            // Stop if no allied familiar is still fighting this target
            AABB searchArea = familiar.getBoundingBox().inflate(HELP_RANGE * 1.5);
            List<AbstractSpellCastingPet> alliedFamiliars = familiar.level().getEntitiesOfClass(
                    AbstractSpellCastingPet.class,
                    searchArea,
                    alliedFamiliar -> alliedFamiliar != familiar &&
                            familiar.isAlliedTo(alliedFamiliar) &&
                            alliedFamiliar.getTarget() == targetMob
            );

            return !alliedFamiliars.isEmpty();
        }

        @Override
        public void start() {
            familiar.setTarget(this.targetMob);
            this.timestamp = familiar.tickCount;
            super.start();
        }

        @Override
        public void stop() {
            familiar.setTarget(null);
            this.targetMob = null;
            super.stop();
        }

        /**
         * Check if this familiar can help against the given target
         */
        private boolean canHelpAgainst(LivingEntity target) {
            // Don't attack allied entities
            if (familiar.isAlliedTo(target)) {
                return false;
            }

            // Don't attack entities we can't see
            if (!familiar.hasLineOfSight(target)) {
                return false;
            }

            // Use the same targeting conditions as other combat goals
            TargetingConditions conditions = TargetingConditions.forCombat()
                    .range(HELP_RANGE)
                    .selector(entity -> entity instanceof LivingEntity &&
                            !familiar.isAlliedTo(entity) &&
                            entity.isAlive());

            return conditions.test(familiar, target);
        }
    }

    public static class FamiliarHurtByTargetGoal extends HurtByTargetGoal {
        private final AbstractSpellCastingPet familiar;
        private static final double ALERT_RANGE = 20.0D;

        public FamiliarHurtByTargetGoal(AbstractSpellCastingPet familiar) {
            super(familiar);
            this.familiar = familiar;
        }

        @Override
        public void start() {
            super.start();

            // Alert nearby allied familiars when we get hurt
            if (familiar.getLastHurtByMob() != null) {
                alertAlliedFamiliars(familiar.getLastHurtByMob());
            }
        }

        /**
         * Alert all allied familiars in the area about the attacker
         */
        private void alertAlliedFamiliars(LivingEntity attacker) {
            if (attacker == null || !attacker.isAlive()) {
                return;
            }

            // Don't alert if we're in house mode
            if (familiar.getIsInHouse()) {
                return;
            }

            AABB searchArea = familiar.getBoundingBox().inflate(ALERT_RANGE);
            List<AbstractSpellCastingPet> nearbyFamiliars = familiar.level().getEntitiesOfClass(
                    AbstractSpellCastingPet.class,
                    searchArea,
                    alliedFamiliar -> alliedFamiliar != familiar && // Not ourselves
                            familiar.isAlliedTo(alliedFamiliar) && // Allied to us
                            !alliedFamiliar.getIsInHouse() && // Not in house mode
                            !alliedFamiliar.getIsSitting() && // Not sitting
                            alliedFamiliar.canExecuteGoalsInHouse() && // Can act
                            (alliedFamiliar.getTarget() == null || // No current target
                                    alliedFamiliar.getTarget() == attacker) // Or already targeting this attacker
            );

            for (AbstractSpellCastingPet alliedFamiliar : nearbyFamiliars) {
                // Only set target if they can attack this entity
                if (!alliedFamiliar.isAlliedTo(attacker) &&
                        alliedFamiliar.hasLineOfSight(attacker) &&
                        alliedFamiliar.getTarget() == null) {

                    alliedFamiliar.setTarget(attacker);

                    // Also set their last hurt by mob so they know who to be angry at
                    if (alliedFamiliar.getLastHurtByMob() == null) {
                        alliedFamiliar.setLastHurtByMob(attacker);
                    }
                }
            }
        }
    }

    public static class FlyingWizardAttackGoal extends WizardAttackGoal {

        // Flight configuration
        protected float preferredHeight = 4.0F;
        protected float optimalCombatRange = 12.0F;
        protected float minCombatRange = 6.0F;
        protected float maxCombatRange = 20.0F;

        // Movement state
        protected MovementPattern currentPattern = MovementPattern.ORBIT;
        protected int patternTicks = 0;
        protected int patternDuration = 100;
        protected double orbitAngle = 0.0;
        protected boolean orbitClockwise = true;

        public enum MovementPattern {
            ORBIT,      // Circle around target
            STRAFE,     // Side-to-side movement
            APPROACH,   // Move closer to target
            RETREAT,    // Move away from target
            DIVE,       // Quick descent toward target
            ASCEND,     // Gain altitude
            HOVER       // Hold position (during casting)
        }

        public FlyingWizardAttackGoal(IMagicEntity abstractSpellCastingMob, double speedModifier, int attackInterval) {
            this(abstractSpellCastingMob, speedModifier, attackInterval, attackInterval);
        }

        public FlyingWizardAttackGoal(IMagicEntity abstractSpellCastingMob, double speedModifier, int attackIntervalMin, int attackIntervalMax) {
            super(abstractSpellCastingMob, speedModifier, attackIntervalMin, attackIntervalMax);
            this.isFlying = true;
            this.allowFleeing = false; // We handle our own retreat logic
        }

        // ==================== CONFIGURATION ====================

        public FlyingWizardAttackGoal setPreferredHeight(float height) {
            this.preferredHeight = height;
            return this;
        }

        public FlyingWizardAttackGoal setCombatRanges(float min, float optimal, float max) {
            this.minCombatRange = min;
            this.optimalCombatRange = optimal;
            this.maxCombatRange = max;
            this.spellcastingRange = max;
            this.spellcastingRangeSqr = max * max;
            return this;
        }

        // ==================== LIFECYCLE OVERRIDES ====================

        @Override
        public void start() {
            super.start();
            this.orbitAngle = mob.getRandom().nextDouble() * Math.PI * 2;
            this.orbitClockwise = mob.getRandom().nextBoolean();
            selectNewPattern();
        }

        @Override
        public void stop() {
            super.stop();
            this.currentPattern = MovementPattern.HOVER;
            this.patternTicks = 0;
        }

        // ==================== MAIN MOVEMENT OVERRIDE ====================

        @Override
        protected void doMovement(double distanceSquared) {
            if (target == null) {
                return;
            }

            // Always look at target
            mob.lookAt(target, 30, 30);

            // Handle pattern transitions
            patternTicks++;
            if (patternTicks >= patternDuration) {
                selectNewPattern();
            }

            // Execute the current movement pattern
            double distance = Math.sqrt(distanceSquared);
            executeMovement(distance);
        }

        // ==================== PATTERN SELECTION ====================

        protected void selectNewPattern() {
            patternTicks = 0;

            if (target == null) {
                currentPattern = MovementPattern.HOVER;
                patternDuration = 60;
                return;
            }

            double distance = mob.distanceTo(target);
            float healthPercent = mob.getHealth() / mob.getMaxHealth();
            boolean isCasting = spellCastingMob.isCasting();
            boolean isDrinking = spellCastingMob.isDrinkingPotion();

            // While casting or drinking, prefer stable positioning
            if (isCasting || isDrinking) {
                if (distance < minCombatRange) {
                    currentPattern = MovementPattern.RETREAT;
                    patternDuration = 40;
                } else {
                    currentPattern = mob.getRandom().nextFloat() < 0.7F ? MovementPattern.HOVER : MovementPattern.ORBIT;
                    patternDuration = 60 + mob.getRandom().nextInt(40);
                }
                return;
            }

            // Dynamic pattern selection based on combat state
            float roll = mob.getRandom().nextFloat();

            if (!hasLineOfSight) {
                // No line of sight - approach to regain
                currentPattern = MovementPattern.APPROACH;
                patternDuration = 80;
            } else if (distance > maxCombatRange) {
                // Too far - approach
                currentPattern = MovementPattern.APPROACH;
                patternDuration = 60 + mob.getRandom().nextInt(40);
            } else if (distance < minCombatRange) {
                // Too close - retreat or ascend
                currentPattern = roll < 0.6F ? MovementPattern.RETREAT : MovementPattern.ASCEND;
                patternDuration = 40 + mob.getRandom().nextInt(30);
            } else if (healthPercent < 0.3F && roll < 0.4F) {
                // Low health - be defensive
                currentPattern = MovementPattern.RETREAT;
                patternDuration = 60;
            } else if (roll < 0.5F) {
                // Most common - orbit
                currentPattern = MovementPattern.ORBIT;
                patternDuration = 80 + mob.getRandom().nextInt(60);
                // Occasionally switch orbit direction
                if (mob.getRandom().nextFloat() < 0.3F) {
                    orbitClockwise = !orbitClockwise;
                }
            } else if (roll < 0.7F) {
                // Strafe
                currentPattern = MovementPattern.STRAFE;
                patternDuration = 40 + mob.getRandom().nextInt(30);
            } else if (roll < 0.85F) {
                // Height changes
                double heightDiff = mob.getY() - target.getY();
                if (heightDiff < preferredHeight) {
                    currentPattern = MovementPattern.ASCEND;
                } else {
                    currentPattern = MovementPattern.DIVE;
                }
                patternDuration = 30 + mob.getRandom().nextInt(20);
            } else {
                // Brief hover
                currentPattern = MovementPattern.HOVER;
                patternDuration = 30 + mob.getRandom().nextInt(30);
            }
        }

        // ==================== MOVEMENT EXECUTION ====================

        protected void executeMovement(double distance) {
            double speed = getFlightSpeed();
            Vec3 targetPos = target.position();
            Vec3 mobPos = mob.position();

            switch (currentPattern) {
                case ORBIT -> executeOrbit(targetPos, mobPos, speed);
                case STRAFE -> executeStrafe(targetPos, mobPos, speed);
                case APPROACH -> executeApproach(targetPos, mobPos, speed);
                case RETREAT -> executeRetreat(targetPos, mobPos, speed);
                case DIVE -> executeDive(targetPos, mobPos, speed);
                case ASCEND -> executeAscend(targetPos, mobPos, speed);
                case HOVER -> executeHover(targetPos, mobPos);
            }
        }

        protected double getFlightSpeed() {
            double baseSpeed = speedModifier;

            if (spellCastingMob.isCasting()) {
                return baseSpeed * 0.5;
            }

            if (spellCastingMob.isDrinkingPotion()) {
                return baseSpeed * 0.3;
            }

            return baseSpeed;
        }

        protected void executeOrbit(Vec3 targetPos, Vec3 mobPos, double speed) {
            double orbitRadius = optimalCombatRange;
            double orbitSpeed = 0.03 * (orbitClockwise ? 1 : -1);
            orbitAngle += orbitSpeed;

            // Target position on orbit circle with vertical bobbing
            double targetX = targetPos.x + Math.cos(orbitAngle) * orbitRadius;
            double targetZ = targetPos.z + Math.sin(orbitAngle) * orbitRadius;
            double targetY = targetPos.y + preferredHeight + Math.sin(orbitAngle * 2) * 1.5;

            // Add randomness for natural feel
            if (mob.getRandom().nextInt(20) == 0) {
                targetX += (mob.getRandom().nextDouble() - 0.5) * 2;
                targetZ += (mob.getRandom().nextDouble() - 0.5) * 2;
            }

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed);
        }

        protected void executeStrafe(Vec3 targetPos, Vec3 mobPos, double speed) {
            Vec3 toTarget = targetPos.subtract(mobPos).normalize();
            Vec3 strafeDir = new Vec3(-toTarget.z, 0, toTarget.x); // Perpendicular

            if (!orbitClockwise) {
                strafeDir = strafeDir.scale(-1);
            }

            // Maintain distance while strafing
            double currentDist = mobPos.distanceTo(targetPos);
            Vec3 adjustedDir = strafeDir;

            if (currentDist < minCombatRange) {
                adjustedDir = adjustedDir.subtract(toTarget.scale(0.5));
            } else if (currentDist > maxCombatRange) {
                adjustedDir = adjustedDir.add(toTarget.scale(0.5));
            }

            adjustedDir = adjustedDir.normalize();

            double targetX = mobPos.x + adjustedDir.x * 6;
            double targetZ = mobPos.z + adjustedDir.z * 6;
            double targetY = targetPos.y + preferredHeight;

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed * 1.2);
        }

        protected void executeApproach(Vec3 targetPos, Vec3 mobPos, double speed) {
            Vec3 toTarget = targetPos.subtract(mobPos);
            double dist = toTarget.horizontalDistance();

            Vec3 direction = toTarget.normalize();
            double desiredDist = optimalCombatRange * 0.8;

            double targetX, targetZ;
            if (dist > desiredDist) {
                targetX = targetPos.x - direction.x * desiredDist;
                targetZ = targetPos.z - direction.z * desiredDist;
            } else {
                targetX = mobPos.x;
                targetZ = mobPos.z;
            }

            double targetY = targetPos.y + preferredHeight;

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed * 1.3);
        }

        protected void executeRetreat(Vec3 targetPos, Vec3 mobPos, double speed) {
            Vec3 awayFromTarget = mobPos.subtract(targetPos).normalize();

            double retreatDist = 8.0;
            double targetX = mobPos.x + awayFromTarget.x * retreatDist;
            double targetZ = mobPos.z + awayFromTarget.z * retreatDist;
            double targetY = mobPos.y + 2.0; // Gain height while retreating

            // Add sideways movement for unpredictability
            double sideOffset = (mob.getRandom().nextDouble() - 0.5) * 4;
            targetX += -awayFromTarget.z * sideOffset;
            targetZ += awayFromTarget.x * sideOffset;

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed * 1.4);
        }

        protected void executeDive(Vec3 targetPos, Vec3 mobPos, double speed) {
            Vec3 toTarget = targetPos.subtract(mobPos).normalize();

            double targetX = targetPos.x - toTarget.x * optimalCombatRange * 0.7;
            double targetZ = targetPos.z - toTarget.z * optimalCombatRange * 0.7;
            double targetY = targetPos.y + 2.0; // Stay above target

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed * 1.5);
        }

        protected void executeAscend(Vec3 targetPos, Vec3 mobPos, double speed) {
            double horizontalDist = mobPos.subtract(targetPos).horizontalDistance();

            double targetX = mobPos.x;
            double targetZ = mobPos.z;

            // If too close, also move away horizontally
            if (horizontalDist < optimalCombatRange) {
                Vec3 away = mobPos.subtract(targetPos).normalize();
                targetX = mobPos.x + away.x * 4;
                targetZ = mobPos.z + away.z * 4;
            }

            double targetY = mobPos.y + 6.0;

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, speed);
        }

        protected void executeHover(Vec3 targetPos, Vec3 mobPos) {
            double currentDist = mobPos.distanceTo(targetPos);

            double targetX = mobPos.x;
            double targetZ = mobPos.z;
            double targetY = targetPos.y + preferredHeight;

            // Gentle adjustment toward optimal range
            if (currentDist < minCombatRange - 1) {
                Vec3 away = mobPos.subtract(targetPos).normalize();
                targetX = mobPos.x + away.x * 2;
                targetZ = mobPos.z + away.z * 2;
            } else if (currentDist > maxCombatRange + 2) {
                Vec3 toward = targetPos.subtract(mobPos).normalize();
                targetX = mobPos.x + toward.x * 2;
                targetZ = mobPos.z + toward.z * 2;
            }

            // Small bobbing motion
            targetY += Math.sin(mob.tickCount * 0.08) * 0.3;

            mob.getMoveControl().setWantedPosition(targetX, targetY, targetZ, 0.5);
        }

        // ==================== OVERRIDE STRAFE MULTIPLIER ====================

        @Override
        public float getStrafeMultiplier() {
            // Flying entities don't use ground strafing
            return 0.0F;
        }

        // ==================== UTILITY FOR PATTERN CHANGES ON CAST ====================

        @Override
        protected void doSpellAction() {
            super.doSpellAction();

            // Switch to stable pattern when starting to cast
            if (spellCastingMob.isCasting() || spellCastingMob.isDrinkingPotion()) {
                currentPattern = mob.getRandom().nextFloat() < 0.7F ? MovementPattern.HOVER : MovementPattern.ORBIT;
                patternTicks = 0;
                patternDuration = 80;
            }
        }
    }

    public static class MeleeSpellCastWarlockAttackGoal<T extends PathfinderMob & IAnimatedAttacker & IMagicEntity> extends GenericAnimatedWarlockAttackGoal<T> {

        @Nullable
        private AbstractSpell gapCloserSpell;
        private int gapCloserSpellLevel;
        private double gapCloserDistance = 3.0;
        private int spellCooldown = 0;
        private static final int SPELL_COOLDOWN_TICKS = 100; // 5 seconds between casts
        private boolean hasAttemptedGapClose = false;

        @Nullable
        private LivingEntity lastTarget = null;

        public MeleeSpellCastWarlockAttackGoal(T mob, float speedModifier, int attackIntervalMin, int attackIntervalMax) {
            super(mob, speedModifier, attackIntervalMin, attackIntervalMax);
        }

        public MeleeSpellCastWarlockAttackGoal<T> setGapCloserSpell(@Nullable AbstractSpell spell, int level) {
            this.gapCloserSpell = spell;
            this.gapCloserSpellLevel = level;
            return this;
        }

        public MeleeSpellCastWarlockAttackGoal<T> setGapCloserDistance(double distance) {
            this.gapCloserDistance = distance;
            return this;
        }

        @Override
        public void start() {
            super.start();
            hasAttemptedGapClose = false;
        }

        @Override
        public void stop() {
            super.stop();
            hasAttemptedGapClose = false;
        }

        @Override
        public void tick() {
            super.tick();

            // Check for target change
            LivingEntity currentTarget = mob.getTarget();
            if (currentTarget != lastTarget) {
                hasAttemptedGapClose = false;
                lastTarget = currentTarget;
            }

            // Decrement spell cooldown
            if (spellCooldown > 0) {
                spellCooldown--;
            }

            // Check if we should cast the gap closer spell
            if (shouldCastGapCloserSpell()) {
                castGapCloserSpell();
            }
        }

        private boolean shouldCastGapCloserSpell() {
            // Don't cast if no spell configured
            if (gapCloserSpell == null) {
                return false;
            }

            // Don't cast if spell is on cooldown
            if (spellCooldown > 0) {
                return false;
            }

            // Don't cast if already attempted for this target
            if (hasAttemptedGapClose) {
                return false;
            }

            // Don't cast if no target
            LivingEntity target = mob.getTarget();
            if (target == null) {
                return false;
            }

            // Don't cast if already casting
            MagicData magicData = MagicData.getPlayerMagicData(mob);
            if (magicData.isCasting()) {
                return false;
            }

            // Don't cast if currently meleeing (mid-animation)
            if (isMeleeing()) {
                return false;
            }

            // Check if target is far enough away to warrant gap closing
            double distanceToTarget = mob.distanceTo(target);

            if (distanceToTarget < gapCloserDistance) {
                return false;
            }

            return true;
        }

        private void castGapCloserSpell() {
            if (gapCloserSpell == null || mob.getTarget() == null) {
                return;
            }

            MagicData magicData = MagicData.getPlayerMagicData(mob);

            // Get the spell level (use entity's spell power or default to 1)
            int spellLevel = this.gapCloserSpellLevel;

            // Initiate spell casting
            mob.initiateCastSpell(gapCloserSpell, spellLevel);

            // Mark as attempted and set cooldown
            hasAttemptedGapClose = true;

            spellCooldown = SPELL_COOLDOWN_TICKS;
        }

        @Nullable
        public AbstractSpell getGapCloserSpell() {
            return gapCloserSpell;
        }

        public double getGapCloserDistance() {
            return gapCloserDistance;
        }

        public int getSpellCooldown() {
            return spellCooldown;
        }

        public boolean hasAttemptedGapClose() {
            return hasAttemptedGapClose;
        }
    }
}
