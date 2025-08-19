package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
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
                    exactSleepPosition = petBed.getSleepPosition();
                    hasSnappedToBed = false;
                    hasClaimedBed = false;
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " found available bed at " + targetBedPos +
                            ", exact sleep position: " + exactSleepPosition);
                    return true;
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
            hasClaimedBed = false;
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

            // Claim the bed when close enough
            if (distanceToSleepPos <= 2.0 && !hasClaimedBed) {
                claimBed();
            }

            // Snap to bed position when very close
            if (distanceToSleepPos <= 1.5 && !hasSnappedToBed && hasClaimedBed) {
                if (!pet.level().isClientSide) {
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " within snap range, teleporting to exact position");

                    pet.getNavigation().stop();
                    pet.setDeltaMovement(Vec3.ZERO);
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
            if (pet.getIsSitting()) {
                pet.setSitting(false);
                FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " stopped sitting when bed goal ended");
            }

            // Release the bed when stopping
            if (hasClaimedBed && targetBedPos != null) {
                releaseBed();
            }

            targetBedPos = null;
            exactSleepPosition = null;
            hasSnappedToBed = false;
            hasClaimedBed = false;
            pet.getNavigation().stop();
            cooldownTicks = SEARCH_COOLDOWN;

            // Reset sleep animation variables
            bedRegenTimer = 0;
            wasPlayingSleepAnimation = false;
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

        private void claimBed() {
            if (targetBedPos == null) return;

            BlockEntity be = pet.level().getBlockEntity(targetBedPos);
            if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                if (!petBed.isBedTaken()) {
                    petBed.setBedTaken(true);
                    hasClaimedBed = true;
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " claimed bed at " + targetBedPos);
                }
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

                            // Spawn healing particles
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
                    this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
                }
            }
        }

        private boolean hasOwnerTeleportedInstantly(LivingEntity owner) {
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
            ).stream().min(Comparator.comparingDouble(pet::distanceTo)).orElse(null);

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

    public static class FamiliarWizardAttackGoal extends Goal {

        protected LivingEntity target;
        protected final double speedModifier;
        protected final int spellAttackIntervalMin;
        protected final int spellAttackIntervalMax;
        protected float spellcastingRange;
        protected float spellcastingRangeSqr;
        protected boolean shortCircuitTemp = false;

        protected boolean hasLineOfSight;
        protected int seeTime = 0;
        protected int strafeTime;
        protected boolean strafingClockwise;
        protected int spellAttackDelay = -1;
        protected int projectileCount;

        protected AbstractSpell singleUseSpell = SpellRegistry.none();
        protected int singleUseDelay;
        protected int singleUseLevel;

        protected boolean isFlying;
        protected boolean allowFleeing;
        protected int fleeCooldown;
        protected int flyingMovementTimer;
        protected Vec3 flyingTarget;
        protected int lastHurtTime = -1;

        protected final ArrayList<AbstractSpell> attackSpells = new ArrayList<>();
        protected final ArrayList<AbstractSpell> defenseSpells = new ArrayList<>();
        protected final ArrayList<AbstractSpell> movementSpells = new ArrayList<>();
        protected final ArrayList<AbstractSpell> supportSpells = new ArrayList<>();
        protected ArrayList<AbstractSpell> lastSpellCategory = attackSpells;

        protected float minSpellQuality = .1f;
        protected float maxSpellQuality = .4f;

        protected boolean drinksPotions;
        protected final PathfinderMob mob;
        protected final IMagicEntity spellCastingMob;

        private Map<AbstractSpell, Holder<MobEffect>> buffs = Map.of(
                SpellRegistry.EVASION_SPELL.get(), MobEffectRegistry.EVASION,
                SpellRegistry.HEARTSTOP_SPELL.get(), MobEffectRegistry.HEARTSTOP,
                SpellRegistry.CHARGE_SPELL.get(), MobEffectRegistry.CHARGED,
                SpellRegistry.INVISIBILITY_SPELL.get(), MobEffectRegistry.TRUE_INVISIBILITY,
                SpellRegistry.OAKSKIN_SPELL.get(), MobEffectRegistry.OAKSKIN,
                SpellRegistry.HASTE_SPELL.get(), MobEffectRegistry.HASTENED,
                SpellRegistry.FROSTBITE_SPELL.get(), MobEffectRegistry.FROSTBITTEN_STRIKES
        );
        private Map<AbstractSpell, Holder<MobEffect>> debuffs = Map.of(
                SpellRegistry.BLIGHT_SPELL.get(), MobEffectRegistry.BLIGHT,
                SpellRegistry.SLOW_SPELL.get(), MobEffectRegistry.SLOWED
        );

        public FamiliarWizardAttackGoal(IMagicEntity abstractSpellCastingMob, double pSpeedModifier, int pAttackInterval) {
            this(abstractSpellCastingMob, pSpeedModifier, pAttackInterval, pAttackInterval);
        }

        public FamiliarWizardAttackGoal(IMagicEntity abstractSpellCastingMob, double pSpeedModifier, int pAttackIntervalMin, int pAttackIntervalMax) {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Flag.TARGET));
            this.spellCastingMob = abstractSpellCastingMob;
            if (abstractSpellCastingMob instanceof PathfinderMob m) {
                this.mob = m;
            } else
                throw new IllegalStateException("Unable to add " + this.getClass().getSimpleName() + "to entity, must extend PathfinderMob.");

            this.speedModifier = pSpeedModifier;
            this.spellAttackIntervalMin = pAttackIntervalMin;
            this.spellAttackIntervalMax = pAttackIntervalMax;
            this.spellcastingRange = 20;
            this.spellcastingRangeSqr = spellcastingRange * spellcastingRange;
            allowFleeing = true;
            flyingMovementTimer = 0;
        }

        public FamiliarWizardAttackGoal setSpells(List<AbstractSpell> attackSpells, List<AbstractSpell> defenseSpells, List<AbstractSpell> movementSpells, List<AbstractSpell> supportSpells) {
            this.attackSpells.clear();
            this.defenseSpells.clear();
            this.movementSpells.clear();
            this.supportSpells.clear();

            this.attackSpells.addAll(attackSpells);
            this.defenseSpells.addAll(defenseSpells);
            this.movementSpells.addAll(movementSpells);
            this.supportSpells.addAll(supportSpells);

            return this;
        }

        public FamiliarWizardAttackGoal setSpellQuality(float minSpellQuality, float maxSpellQuality) {
            this.minSpellQuality = minSpellQuality;
            this.maxSpellQuality = maxSpellQuality;
            return this;
        }

        public FamiliarWizardAttackGoal setSingleUseSpell(AbstractSpell abstractSpell, int minDelay, int maxDelay, int minLevel, int maxLevel) {
            this.singleUseSpell = abstractSpell;
            this.singleUseDelay = Utils.random.nextIntBetweenInclusive(minDelay, maxDelay);
            this.singleUseLevel = Utils.random.nextIntBetweenInclusive(minLevel, maxLevel);
            return this;
        }

        public FamiliarWizardAttackGoal setIsFlying() {
            isFlying = true;
            return this;
        }

        public FamiliarWizardAttackGoal setDrinksPotions() {
            drinksPotions = true;
            return this;
        }

        public FamiliarWizardAttackGoal setAllowFleeing(boolean allowFleeing) {
            this.allowFleeing = allowFleeing;
            return this;
        }

        public boolean canUse() {
            LivingEntity livingentity = this.mob.getTarget();
            if (livingentity != null && livingentity.isAlive()) {
                this.target = livingentity;
                return mob.canAttack(target);
            } else {
                return false;
            }
        }

        public boolean canContinueToUse() {
            return this.canUse();
        }

        public void stop() {
            this.target = null;
            this.seeTime = 0;
            this.spellAttackDelay = -1;
            this.mob.setAggressive(false);
            this.mob.getMoveControl().strafe(0, 0);
            this.mob.getNavigation().stop();
            this.flyingTarget = null;
            this.flyingMovementTimer = 0;
            this.lastHurtTime = -1;
        }

        public boolean requiresUpdateEveryTick() {
            return true;
        }

        public void tick() {
            if (target == null) {
                return;
            }

            if (target.isDeadOrDying()) {
                LivingEntity newTarget = findNearbyTarget();
                if (newTarget != null) {
                    this.target = newTarget;
                    this.mob.setTarget(newTarget);
                    this.seeTime = 0;
                    this.spellAttackDelay = Math.max(this.spellAttackDelay, 10);
                } else {
                    return;
                }
            }

            double distanceSquared = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
            hasLineOfSight = this.mob.getSensing().hasLineOfSight(this.target);
            if (hasLineOfSight) {
                this.seeTime++;
            } else {
                this.seeTime--;
            }

            //default mage movement
            doMovement(distanceSquared);

            //do attacks
            if (mob.getLastHurtByMobTimestamp() == mob.tickCount - 1) {
                spellAttackDelay = (int) (Mth.lerp(.6f, spellAttackDelay, 0) + 1);
                lastHurtTime = mob.tickCount;
            }

            //default attack timer
            handleAttackLogic(distanceSquared);

            singleUseDelay--;
            flyingMovementTimer--;
        }

        protected void handleAttackLogic(double distanceSquared) {
            if (seeTime < -50) {
                return;
            }
            if (--this.spellAttackDelay == 0) {
                resetSpellAttackTimer(distanceSquared);
                if (!spellCastingMob.isCasting() && !spellCastingMob.isDrinkingPotion()) {
                    doSpellAction();
                }

            } else if (this.spellAttackDelay < 0) {
                resetSpellAttackTimer(distanceSquared);
            }
            if (spellCastingMob.isCasting()) {
                var spellData = MagicData.getPlayerMagicData(mob).getCastingSpell();
                if (target.isDeadOrDying() || spellData.getSpell().shouldAIStopCasting(spellData.getLevel(), mob, target)) {
                    spellCastingMob.cancelCast();
                }
            }
        }

        public boolean isActing() {
            return spellCastingMob.isCasting() || spellCastingMob.isDrinkingPotion();
        }

        protected void resetSpellAttackTimer(double distanceSquared) {
            float f = (float) Math.sqrt(distanceSquared) / this.spellcastingRange;
            this.spellAttackDelay = Math.max(1, Mth.floor(f * (float) (this.spellAttackIntervalMax - this.spellAttackIntervalMin) + (float) this.spellAttackIntervalMin));
        }

        protected void doMovement(double distanceSquared) {
            double speed = (spellCastingMob.isCasting() ? .75f : 1f) * movementSpeed();

            if (target != null) {
                mob.lookAt(target, 30, 30);
                if (isFlying && spellCastingMob.isCasting()) {
                    forceLookAtTarget(target);
                }
            }

            if (isFlying) {
                doFlyingMovement(distanceSquared, speed);
            } else {
                doGroundMovement(distanceSquared, speed);
            }
        }

        protected void doFlyingMovement(double distanceSquared, double speed) {
            float fleeDist = .275f;

            // Fleeing movement
            if (allowFleeing && (!spellCastingMob.isCasting() && spellAttackDelay > 10) && --fleeCooldown <= 0 && distanceSquared < spellcastingRangeSqr * (fleeDist * fleeDist)) {
                Vec3 flee = DefaultRandomPos.getPosAway(this.mob, 16, 7, target.position());
                if (flee != null) {
                    flyingTarget = new Vec3(flee.x, flee.y + 3, flee.z);
                    flyingMovementTimer = 60;
                }
            }
            // In range movement
            else if (distanceSquared < spellcastingRangeSqr && seeTime >= 5) {
                boolean shouldGenerateNewTarget = !spellCastingMob.isCasting() &&
                        (flyingTarget == null || flyingMovementTimer <= 0 || mob.position().distanceTo(flyingTarget) < 2);

                if (spellCastingMob.isCasting() && mob.getRandom().nextInt(20) == 0) {
                    shouldGenerateNewTarget = true;
                }

                if (shouldGenerateNewTarget) {
                    double angle = mob.getRandom().nextDouble() * 2 * Math.PI;
                    double radius = 5 + mob.getRandom().nextDouble() * 10;
                    double x = target.getX() + Math.cos(angle) * radius;
                    double z = target.getZ() + Math.sin(angle) * radius;

                    double baseHeight = target.getY();
                    double heightVariation = (mob.getRandom().nextDouble() - 0.5) * 8;
                    double y = Math.max(baseHeight + 2, baseHeight + heightVariation + 3);

                    flyingTarget = new Vec3(x, y, z);
                    flyingMovementTimer = spellCastingMob.isCasting() ? 60 : 30 + mob.getRandom().nextInt(30);
                }

                if (flyingTarget != null) {
                    double flyingSpeed = spellCastingMob.isCasting() ? speed * 0.4 : speed;

                    if (mob.getMoveControl() instanceof AbstractFlyingSpellCastingPet.ImprovedFlyingMoveControl) {
                        mob.getMoveControl().setWantedPosition(flyingTarget.x, flyingTarget.y, flyingTarget.z, flyingSpeed);
                    } else {
                        Vec3 direction = flyingTarget.subtract(mob.position()).normalize();
                        Vec3 movement = direction.scale(flyingSpeed * 0.1);
                        mob.setDeltaMovement(movement);
                    }
                }
            }
            // Out of range movement
            else {
                if (mob.tickCount % 5 == 0 || flyingTarget == null) {
                    double targetY = target.getY() + 2 + mob.getRandom().nextDouble() * 3;
                    flyingTarget = new Vec3(target.getX(), targetY, target.getZ());
                    flyingMovementTimer = 20;
                }

                if (mob.getMoveControl() instanceof AbstractFlyingSpellCastingPet.ImprovedFlyingMoveControl) {
                    mob.getMoveControl().setWantedPosition(flyingTarget.x, flyingTarget.y, flyingTarget.z, speed);
                } else {
                    Vec3 direction = flyingTarget.subtract(mob.position()).normalize();
                    Vec3 movement = direction.scale(speed * 0.1);
                    mob.setDeltaMovement(movement);
                }
            }
        }

        protected void doGroundMovement(double distanceSquared, double speed) {
            // Default movement
            float fleeDist = .275f;
            float ss = getStrafeMultiplier();
            if (allowFleeing && (!spellCastingMob.isCasting() && spellAttackDelay > 10) && --fleeCooldown <= 0 && distanceSquared < spellcastingRangeSqr * (fleeDist * fleeDist)) {
                Vec3 flee = DefaultRandomPos.getPosAway(this.mob, 16, 7, target.position());
                if (flee != null) {
                    this.mob.getNavigation().moveTo(flee.x, flee.y, flee.z, speed * 1.5);
                } else {
                    mob.getMoveControl().strafe(-(float) speed * ss, (float) speed * ss);
                }
            } else if (distanceSquared < spellcastingRangeSqr && seeTime >= 5) {
                this.mob.getNavigation().stop();
                if (++strafeTime > 25) {
                    if (mob.getRandom().nextDouble() < .1) {
                        strafingClockwise = !strafingClockwise;
                        strafeTime = 0;
                    }
                }
                float strafeForward = (distanceSquared * 6 < spellcastingRangeSqr ? -1 : .5f) * .2f * (float) speedModifier;
                int strafeDir = strafingClockwise ? 1 : -1;
                mob.getMoveControl().strafe(strafeForward * ss, (float) speed * strafeDir * ss);
                if (mob.horizontalCollision && mob.getRandom().nextFloat() < .1f) {
                    tryJump();
                }
            } else {
                if (mob.tickCount % 5 == 0) {
                    this.mob.getNavigation().moveTo(this.target, speedModifier);
                }
            }
        }

        protected double movementSpeed() {
            return speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2;
        }

        protected void tryJump() {
            Vec3 nextBlock = new Vec3(mob.xxa, 0, mob.zza).normalize();
            BlockPos blockpos = BlockPos.containing(mob.position().add(nextBlock));
            BlockState blockstate = this.mob.level().getBlockState(blockpos);
            VoxelShape voxelshape = blockstate.getCollisionShape(this.mob.level(), blockpos);
            if (!voxelshape.isEmpty() && !blockstate.is(BlockTags.DOORS) && !blockstate.is(BlockTags.FENCES)) {
                BlockPos blockposAbove = blockpos.above();
                BlockState blockstateAbove = this.mob.level().getBlockState(blockposAbove);
                VoxelShape voxelshapeAbove = blockstateAbove.getCollisionShape(this.mob.level(), blockposAbove);
                if (voxelshapeAbove.isEmpty()) {
                    this.mob.getJumpControl().jump();
                    mob.setXxa(mob.xxa * 5);
                    mob.setZza(mob.zza * 5);
                }
            }
        }

        protected void doSpellAction() {
            if (!spellCastingMob.getHasUsedSingleAttack() && singleUseSpell != SpellRegistry.none() && singleUseDelay <= 0) {
                spellCastingMob.setHasUsedSingleAttack(true);
                spellCastingMob.initiateCastSpell(singleUseSpell, singleUseLevel);
                fleeCooldown = 7 + singleUseSpell.getCastTime(singleUseLevel);
            } else {
                var spell = getNextSpellType();
                int spellLevel = (int) (spell.getMaxLevel() * Mth.lerp(mob.getRandom().nextFloat(), minSpellQuality, maxSpellQuality));
                spellLevel = Math.max(spellLevel, 1);

                if (!spell.shouldAIStopCasting(spellLevel, mob, target)) {
                    spellCastingMob.initiateCastSpell(spell, spellLevel);
                    fleeCooldown = 7 + spell.getCastTime(spellLevel);
                } else {
                    spellAttackDelay = 5;
                }
            }
        }

        protected AbstractSpell getNextSpellType() {
            NavigableMap<Integer, ArrayList<AbstractSpell>> weightedSpells = new TreeMap<>();
            int attackWeight = getAttackWeight();
            int defenseWeight = getDefenseWeight() - (lastSpellCategory == defenseSpells ? 100 : 0);
            int movementWeight = getMovementWeight() - (lastSpellCategory == movementSpells ? 50 : 0);
            int supportWeight = getSupportWeight() - (lastSpellCategory == supportSpells ? 100 : 0);
            int total = 0;

            if (!attackSpells.isEmpty() && attackWeight > 0) {
                total += attackWeight;
                weightedSpells.put(total, getFilteredAttackSpells());
            }
            if (!defenseSpells.isEmpty() && defenseWeight > 0) {
                total += defenseWeight;
                weightedSpells.put(total, getFilteredDefenseSpells());
            }
            if (!movementSpells.isEmpty() && movementWeight > 0) {
                total += movementWeight;
                weightedSpells.put(total, getFilteredMovementSpells());
            }
            if ((!supportSpells.isEmpty() || drinksPotions) && supportWeight > 0) {
                total += supportWeight;
                weightedSpells.put(total, getFilteredSupportSpells());
            }

            if (total > 0) {
                int seed = mob.getRandom().nextInt(total);
                var spellList = weightedSpells.higherEntry(seed).getValue();
                lastSpellCategory = spellList;

                if (drinksPotions && spellList == supportSpells) {
                    if (supportSpells.isEmpty() || mob.getRandom().nextFloat() < .5f) {
                        spellCastingMob.startDrinkingPotion();
                        return SpellRegistry.none();
                    }
                }
                return spellList.get(mob.getRandom().nextInt(spellList.size()));
            } else {
                return SpellRegistry.none();
            }
        }

        protected ArrayList<AbstractSpell> getFilteredAttackSpells() {
            if (target == null) return new ArrayList<>(attackSpells);

            double distance = Math.sqrt(mob.distanceToSqr(target));

            // Choose priority
            List<AbstractSpell> rangeSpells = new ArrayList<>();
            if (distance <= 3) {
                rangeSpells = filterSpellsByTags(attackSpells, ModTags.CLOSE_RANGE_ATTACKS);
                if (rangeSpells.isEmpty()) {
                    rangeSpells = filterSpellsByTags(attackSpells, ModTags.MID_RANGE_ATTACKS);
                    if (rangeSpells.isEmpty()) {
                        rangeSpells = filterSpellsByTags(attackSpells, ModTags.LONG_RANGE_ATTACKS);
                    }
                }
            } else if (distance <= 6) {
                rangeSpells = filterSpellsByTags(attackSpells, ModTags.MID_RANGE_ATTACKS);
                if (rangeSpells.isEmpty()) {
                    rangeSpells = filterSpellsByTags(attackSpells, ModTags.LONG_RANGE_ATTACKS);
                    if (rangeSpells.isEmpty()) {
                        rangeSpells = filterSpellsByTags(attackSpells, ModTags.CLOSE_RANGE_ATTACKS);
                    }
                }
            } else {
                rangeSpells = filterSpellsByTags(attackSpells, ModTags.LONG_RANGE_ATTACKS);
                if (rangeSpells.isEmpty()) {
                    rangeSpells = filterSpellsByTags(attackSpells, ModTags.MID_RANGE_ATTACKS);
                    if (rangeSpells.isEmpty()) {
                        rangeSpells = filterSpellsByTags(attackSpells, ModTags.CLOSE_RANGE_ATTACKS);
                    }
                }
            }

            if (rangeSpells.isEmpty()) {
                rangeSpells = new ArrayList<>(attackSpells);
            }

            int entitiesNearTarget = getEntitiesNearTarget();
            List<AbstractSpell> finalSpells = new ArrayList<>();

            if (entitiesNearTarget >= 2) {
                // Priority AOE
                finalSpells = filterSpellsByTags(rangeSpells, ModTags.AOE_ATTACKS);
                if (finalSpells.isEmpty()) {
                    finalSpells = filterSpellsByTags(rangeSpells, ModTags.SINGLE_TARGET_ATTACKS);
                }
            } else {
                // Priority Single Target
                finalSpells = filterSpellsByTags(rangeSpells, ModTags.SINGLE_TARGET_ATTACKS);
                if (finalSpells.isEmpty()) {
                    finalSpells = filterSpellsByTags(rangeSpells, ModTags.AOE_ATTACKS);
                }
            }

            return finalSpells.isEmpty() ? new ArrayList<>(rangeSpells) : new ArrayList<>(finalSpells);
        }

        protected ArrayList<AbstractSpell> getFilteredDefenseSpells() {
            List<AbstractSpell> filteredSpells = new ArrayList<>();

            int timeSinceHurt = mob.tickCount - lastHurtTime;
            if (lastHurtTime == -1 || timeSinceHurt > 100) {
                return new ArrayList<>();
            }

            if (timeSinceHurt < 20) {
                return new ArrayList<>();
            }

            boolean hasCloseEnemies = hasEntitiesInRange(3);

            if (hasCloseEnemies) {
                filteredSpells = filterSpellsByTags(defenseSpells, ModTags.ATTACK_BACK_DEFENSE);
                if (filteredSpells.isEmpty()) {
                    List<AbstractSpell> selfBuffSpells = filterSpellsByTags(defenseSpells, ModTags.SELF_BUFF_DEFENSE);
                    List<AbstractSpell> availableSelfBuffs = filterSpellsWithoutExistingBuffs(selfBuffSpells, mob);

                    if (!availableSelfBuffs.isEmpty()) {
                        filteredSpells = availableSelfBuffs;
                    }
                }
            } else {
                List<AbstractSpell> selfBuffSpells = filterSpellsByTags(defenseSpells, ModTags.SELF_BUFF_DEFENSE);
                List<AbstractSpell> availableSelfBuffs = filterSpellsWithoutExistingBuffs(selfBuffSpells, mob);

                if (!availableSelfBuffs.isEmpty()) {
                    filteredSpells = availableSelfBuffs;
                } else {
                    filteredSpells = filterSpellsByTags(defenseSpells, ModTags.ATTACK_BACK_DEFENSE);
                }
            }

            return filteredSpells.isEmpty() ? new ArrayList<>() : new ArrayList<>(filteredSpells);
        }

        protected ArrayList<AbstractSpell> getFilteredMovementSpells() {
            if (target == null) return new ArrayList<>(movementSpells);

            double targetDistance = Math.sqrt(mob.distanceToSqr(target));
            boolean hasCloseHostiles = hasHostileEntitiesInRange(3);

            List<AbstractSpell> filteredSpells = new ArrayList<>();

            if (hasCloseHostiles) {
                filteredSpells = filterSpellsByTags(movementSpells, ModTags.ESCAPE_MOVEMENT);
                if (filteredSpells.isEmpty()) {
                    filteredSpells = filterSpellsByTags(movementSpells, ModTags.CLOSE_DISTANCE_MOVEMENT);
                }
            } else if (targetDistance > 5) {
                filteredSpells = filterSpellsByTags(movementSpells, ModTags.CLOSE_DISTANCE_MOVEMENT);
                if (filteredSpells.isEmpty()) {
                    filteredSpells = filterSpellsByTags(movementSpells, ModTags.ESCAPE_MOVEMENT);
                }
            } else {
                filteredSpells = filterSpellsByTags(movementSpells, ModTags.CLOSE_DISTANCE_MOVEMENT);
                if (filteredSpells.isEmpty()) {
                    filteredSpells = filterSpellsByTags(movementSpells, ModTags.ESCAPE_MOVEMENT);
                }
            }

            return filteredSpells.isEmpty() ? new ArrayList<>(movementSpells) : new ArrayList<>(filteredSpells);
        }

        protected ArrayList<AbstractSpell> getFilteredSupportSpells() {
            float healthPercentage = mob.getHealth() / mob.getMaxHealth();
            List<AbstractSpell> filteredSpells = new ArrayList<>();

            if (healthPercentage > 0.5f) {
                // More than 50% health
                List<AbstractSpell> safeBuffs = filterSpellsByTags(supportSpells, ModTags.SAFE_BUFF_BUFFING);
                List<AbstractSpell> availableSafeBuffs = filterSpellsWithoutExistingBuffs(safeBuffs, mob);

                List<AbstractSpell> debuffs = filterSpellsByTags(supportSpells, ModTags.DEBUFF_BUFFING);
                List<AbstractSpell> availableDebuffs = filterSpellsWithoutExistingDebuffs(debuffs, target);

                filteredSpells.addAll(availableSafeBuffs);
                filteredSpells.addAll(availableDebuffs);
            } else {
                // Less than 50% health
                List<AbstractSpell> unsafeBuffs = filterSpellsByTags(supportSpells, ModTags.UNSAFE_BUFF_BUFFING);
                List<AbstractSpell> availableUnsafeBuffs = filterSpellsWithoutExistingBuffs(unsafeBuffs, mob);

                List<AbstractSpell> debuffs = filterSpellsByTags(supportSpells, ModTags.DEBUFF_BUFFING);
                List<AbstractSpell> availableDebuffs = filterSpellsWithoutExistingDebuffs(debuffs, target);

                // Unsafe buffs have more chance (only if available)
                for (int i = 0; i < 3; i++) {
                    filteredSpells.addAll(availableUnsafeBuffs);
                }
                filteredSpells.addAll(availableDebuffs);
            }

            return filteredSpells.isEmpty() ? new ArrayList<>(supportSpells) : new ArrayList<>(filteredSpells);
        }

        protected List<AbstractSpell> filterSpellsByTags(List<AbstractSpell> spells, TagKey<AbstractSpell> tag) {
            var list = new ArrayList<AbstractSpell>();

            for (var spell : spells) {
                SpellRegistry.REGISTRY.getHolder(spell.getSpellResource()).ifPresent(a -> {
                    if (a.is(tag)) {
                        list.add(spell);
                    }
                });
            }

            return list;
        }

        protected List<AbstractSpell> filterSpellsWithoutExistingBuffs(List<AbstractSpell> spells, LivingEntity entity) {
            if (entity == null) return new ArrayList<>(spells);

            List<AbstractSpell> availableSpells = new ArrayList<>();

            for (AbstractSpell spell : spells) {
                Holder<MobEffect> effect = buffs.get(spell);
                if (effect == null || !entity.hasEffect(effect)) {
                    availableSpells.add(spell);
                }
            }

            return availableSpells;
        }

        protected List<AbstractSpell> filterSpellsWithoutExistingDebuffs(List<AbstractSpell> spells, LivingEntity targetEntity) {
            if (targetEntity == null) return new ArrayList<>(spells);

            List<AbstractSpell> availableSpells = new ArrayList<>();

            for (AbstractSpell spell : spells) {
                Holder<MobEffect> effect = debuffs.get(spell);
                if (effect == null || !targetEntity.hasEffect(effect)) {
                    availableSpells.add(spell);
                }
            }

            return availableSpells;
        }

        protected int getEntitiesNearTarget() {
            if (target == null) return 0;

            AABB area = target.getBoundingBox().inflate(3.0);
            return mob.level().getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != target && entity != mob && entity.isAlive()).size();
        }

        protected boolean hasEntitiesInRange(double range) {
            AABB area = mob.getBoundingBox().inflate(range);
            return !mob.level().getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity != mob && entity.isAlive()).isEmpty();
        }

        protected boolean hasHostileEntitiesInRange(double range) {
            AABB area = mob.getBoundingBox().inflate(range);
            return !mob.level().getEntitiesOfClass(Mob.class, area,
                    entity -> entity != mob && entity.isAlive() &&
                            (entity instanceof Enemy || entity.getTarget() == mob)).isEmpty();
        }

        @Override
        public void start() {
            super.start();
            this.mob.setAggressive(true);
        }

        protected int getAttackWeight() {
            int baseWeight = 80;
            if (!hasLineOfSight || target == null) {
                return 0;
            }

            float targetHealth = target.getHealth() / target.getMaxHealth();
            int targetHealthWeight = (int) ((1 - targetHealth) * baseWeight * .75f);

            double distanceSquared = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
            int distanceWeight = (int) (1 - (distanceSquared / spellcastingRangeSqr) * -60);

            return baseWeight + targetHealthWeight + distanceWeight;
        }

        protected int getDefenseWeight() {
            int baseWeight = -20;

            if (target == null) {
                return baseWeight;
            }

            int timeSinceHurt = mob.tickCount - lastHurtTime;
            if (lastHurtTime == -1 || timeSinceHurt > 100 || timeSinceHurt < 20) {
                return 0;
            }

            float x = mob.getHealth();
            float m = mob.getMaxHealth();
            int healthWeight = (int) (50 * (-(x * x * x) / (m * m * m) + 1));

            float targetHealth = target.getHealth() / target.getMaxHealth();
            int targetHealthWeight = (int) (1 - targetHealth) * -35;

            int threatWeight = projectileCount * 95;

            int recentAttackBonus = 150;

            return baseWeight + healthWeight + targetHealthWeight + threatWeight + recentAttackBonus;
        }

        protected int getMovementWeight() {
            if (target == null) {
                return 0;
            }

            double distanceSquared = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
            double distancePercent = Mth.clamp(distanceSquared / spellcastingRangeSqr, 0, 1);

            int distanceWeight = (int) ((distancePercent) * 50);
            int losWeight = hasLineOfSight ? 0 : 80;

            float healthInverted = 1 - mob.getHealth() / mob.getMaxHealth();
            float distanceInverted = (float) (1 - distancePercent);
            int runWeight = (int) (400 * healthInverted * healthInverted * distanceInverted * distanceInverted);

            return distanceWeight + losWeight + runWeight;
        }

        protected int getSupportWeight() {
            int baseWeight = -15;

            if (target == null) {
                return baseWeight;
            }

            float health = 1 - mob.getHealth() / mob.getMaxHealth();
            int healthWeight = (int) (200 * health);

            double distanceSquared = this.mob.distanceToSqr(this.target.getX(), this.target.getY(), this.target.getZ());
            double distancePercent = Mth.clamp(distanceSquared / spellcastingRangeSqr, 0, 1);
            int distanceWeight = (int) ((1 - distancePercent) * -75);

            return baseWeight + healthWeight + distanceWeight;
        }

        @Override
        public boolean isInterruptable() {
            return !isActing();
        }

        public float getStrafeMultiplier(){
            return 1f;
        }

        protected void forceLookAtTarget(LivingEntity target) {
            if (target != null) {
                double d0 = target.getX() - this.mob.getX();
                double d2 = target.getZ() - this.mob.getZ();
                double d1 = target.getEyeY() - this.mob.getEyeY();

                double d3 = Math.sqrt(d0 * d0 + d2 * d2);
                float f = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
                float f1 = (float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI)));
                this.mob.setXRot(f1 % 360);
                this.mob.setYRot(f % 360);
            }
        }

        protected LivingEntity findNearbyTarget() {
            if (mob == null) return null;

            AABB searchArea = mob.getBoundingBox().inflate(10.0);
            List<LivingEntity> nearbyHostiles = mob.level().getEntitiesOfClass(
                    LivingEntity.class,
                    searchArea,
                    entity -> isValidTargetForContinuation(entity)
            );

            if (nearbyHostiles.isEmpty()) {
                return null;
            }

            return findPriorityTarget(nearbyHostiles);
        }

        protected boolean isValidTargetForContinuation(LivingEntity entity) {
            if (entity == null || entity == mob || entity.isDeadOrDying()) {
                return false;
            }

            if (mob instanceof AbstractSpellCastingPet pet && pet.isAlliedTo(entity)) {
                return false;
            }

            if (entity instanceof Mob hostileMob) {
                LivingEntity hostileTarget = hostileMob.getTarget();
                if (hostileTarget == mob) {
                    return true;
                }
                if (mob instanceof AbstractSpellCastingPet pet && hostileTarget == pet.getSummoner()) {
                    return true;
                }
                return false;
            }

            return false;
        }

        protected LivingEntity findPriorityTarget(List<LivingEntity> potentialTargets) {
            if (mob instanceof AbstractSpellCastingPet pet) {
                LivingEntity owner = pet.getSummoner();

                for (LivingEntity entity : potentialTargets) {
                    if (entity instanceof Mob hostileMob) {
                        LivingEntity hostileTarget = hostileMob.getTarget();

                        if (hostileTarget == mob) {
                            return entity;
                        }

                        if (owner != null && hostileTarget == owner) {
                            return entity;
                        }
                    }
                }
            }
            return null;
        }
    }
}
