package net.alshanex.familiarslib.util.familiars;

import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.block.pedestal.PedestalTile;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.item.Scroll;
import io.redspace.ironsspellbooks.particle.BlastwaveParticleOptions;
import io.redspace.ironsspellbooks.registries.BlockRegistry;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import io.redspace.ironsspellbooks.registries.MobEffectRegistry;
import io.redspace.ironsspellbooks.registries.ParticleRegistry;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.AbstractFamiliarBedBlock;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.BedLinkData;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.util.CylinderParticleManager;
import net.alshanex.familiarslib.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

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

            BlockPos foundBed = findLinkedBed();
            if (foundBed != null) {
                targetBedPos = foundBed;
                BlockEntity be = pet.level().getBlockEntity(targetBedPos);
                if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                    exactSleepPosition = petBed.getSleepPosition();
                    hasSnappedToBed = false;
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " found linked bed at " + targetBedPos +
                            ", exact sleep position: " + exactSleepPosition);
                    return true;
                }
            }

            cooldownTicks = SEARCH_COOLDOWN;
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return !pet.getIsSitting() && targetBedPos != null && exactSleepPosition != null &&
                    isValidLinkedBed(targetBedPos);
        }

        @Override
        public void start() {
            if (exactSleepPosition != null) {
                FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " starting to move to exact sleep position: " + exactSleepPosition);
                pet.getNavigation().moveTo(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z, 1.0);
            }
            hasSnappedToBed = false;
        }

        @Override
        public void tick() {
            if (targetBedPos == null || exactSleepPosition == null) {
                return;
            }

            Vec3 petPos = pet.position();
            double distanceToSleepPos = petPos.distanceTo(exactSleepPosition);

            if (distanceToSleepPos <= 1.5 && !hasSnappedToBed) {
                if (!pet.level().isClientSide) {
                    FamiliarsLib.LOGGER.debug("Pet " + pet.getUUID() + " within snap range, teleporting to exact position");

                    pet.getNavigation().stop();

                    pet.setDeltaMovement(Vec3.ZERO);

                    FamiliarBedHelper.snapToExactBedPosition(pet);

                    hasSnappedToBed = true;

                    try {
                        pet.setSitting(true);
                    } catch (Exception e) {
                        FamiliarsLib.LOGGER.error("Error setting pet to sitting: ", e);
                    }
                }
                return;
            }

            if (hasSnappedToBed && distanceToSleepPos <= 0.5) {
                if (!pet.level().isClientSide && !pet.getIsSitting()) {
                    try {
                        pet.setSitting(true);
                    } catch (Exception e) {
                        FamiliarsLib.LOGGER.error("Error setting pet to sitting after snap: ", e);
                    }
                }
                return;
            }

            if (!hasSnappedToBed && (pet.getNavigation().isDone() || distanceToSleepPos > 3.0)) {
                pet.getNavigation().moveTo(exactSleepPosition.x, exactSleepPosition.y, exactSleepPosition.z, 1.2);
            }
        }

        @Override
        public void stop() {
            targetBedPos = null;
            exactSleepPosition = null;
            hasSnappedToBed = false;
            pet.getNavigation().stop();
            cooldownTicks = SEARCH_COOLDOWN;
        }

        private BlockPos findLinkedBed() {
            if (pet.getSummoner() == null) {
                return null;
            }

            BlockPos petPos = pet.blockPosition();
            UUID petUUID = pet.getUUID();

            FamiliarsLib.LOGGER.debug("Pet " + petUUID + " searching for linked bed around " + petPos);

            if (!(pet.getSummoner() instanceof ServerPlayer serverPlayer)) {
                return null;
            }

            BedLinkData linkData = serverPlayer.getData(AttachmentRegistry.BED_LINK_DATA);
            BlockPos linkedBedPos = linkData.getLinkedBed(petUUID);

            if (linkedBedPos == null) {
                FamiliarsLib.LOGGER.debug("No linked bed found for pet " + petUUID);
                return null;
            }

            double distance = petPos.distSqr(linkedBedPos);
            if (distance > searchRadius * searchRadius) {
                FamiliarsLib.LOGGER.debug("Linked bed at " + linkedBedPos + " is too far (distance: " + Math.sqrt(distance) + ")");
                return null;
            }

            if (isValidLinkedBed(linkedBedPos)) {
                FamiliarsLib.LOGGER.debug("Found valid linked bed at " + linkedBedPos);
                return linkedBedPos;
            } else {
                FamiliarsLib.LOGGER.debug("Linked bed at " + linkedBedPos + " is no longer valid");
                linkData.unlinkBed(linkedBedPos);
                return null;
            }
        }

        private boolean isValidLinkedBed(BlockPos pos) {
            try {
                if (!(pet.level().getBlockState(pos).getBlock() instanceof AbstractFamiliarBedBlock)) {
                    return false;
                }

                BlockEntity be = pet.level().getBlockEntity(pos);
                if (!(be instanceof AbstractFamiliarBedBlockEntity petBed)) {
                    return false;
                }

                return petBed.isLinkedToPet(pet.getUUID());
            } catch (Exception e) {
                FamiliarsLib.LOGGER.error("Error checking if bed is valid: ", e);
                return false;
            }
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
            return canUse();
        }
    }

    public static class TeleportToOwnerGoal extends Goal {
        private final AbstractSpellCastingPet mob;
        @javax.annotation.Nullable
        private LivingEntity owner;
        private Supplier<LivingEntity> ownerGetter;
        private float teleportDistance;

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
            } else if (this.mob.distanceToSqr(livingentity) < (double) (this.teleportDistance * this.teleportDistance)) {
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
            return canUse();
        }

        @Override
        public void tick() {
            boolean flag = this.shouldTryTeleportToOwner();
            if (!flag) {
                this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
            } else {
                this.tryToTeleportToOwner();
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
            return livingentity != null && mob.distanceToSqr(livingentity) >= teleportDistance * teleportDistance;
        }

        private void teleportToAroundBlockPos(BlockPos pPos) {
            for (int i = 0; i < 10; i++) {
                int j = mob.getRandom().nextIntBetweenInclusive(-3, 3);
                int k = mob.getRandom().nextIntBetweenInclusive(-3, 3);
                if (Math.abs(j) >= 2 || Math.abs(k) >= 2) {
                    int l = mob.getRandom().nextIntBetweenInclusive(-1, 1);
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
                mob.moveTo((double) pX + 0.5, (double) pY, (double) pZ + 0.5, mob.getYRot(), mob.getXRot());
                return true;
            }
        }

        private boolean canTeleportTo(BlockPos pPos) {
            PathType pathtype = WalkNodeEvaluator.getPathTypeStatic(mob, pPos);
            if (pathtype != PathType.WALKABLE) {
                return false;
            } else {
                BlockState blockstate = mob.level().getBlockState(pPos.below());
                if (blockstate.getBlock() instanceof LeavesBlock) {
                    return false;
                } else {
                    BlockPos blockpos = pPos.subtract(mob.blockPosition());
                    return mob.level().noCollision(mob, mob.getBoundingBox().move(blockpos));
                }
            }
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
            this.creeper = pet.level().getEntitiesOfClass(
                    Creeper.class,
                    pet.getBoundingBox().inflate(15),
                    creeper -> !creeper.isPowered()
            ).stream().min(Comparator.comparingDouble(pet::distanceTo)).orElse(null);

            return this.creeper != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.creeper != null && this.creeper.isAlive() && !this.creeper.isPowered();
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
            this.skeleton = pet.level().getEntitiesOfClass(
                    Skeleton.class,
                    pet.getBoundingBox().inflate(15)
            ).stream().min(Comparator.comparingDouble(pet::distanceTo)).orElse(null);

            return this.skeleton != null;
        }

        @Override
        public boolean canContinueToUse() {
            return this.skeleton != null && this.skeleton.isAlive();
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
            return canUse();
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
}
