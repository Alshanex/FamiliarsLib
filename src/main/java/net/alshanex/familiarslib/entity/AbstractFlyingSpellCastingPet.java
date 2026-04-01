package net.alshanex.familiarslib.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.AirAndWaterRandomPos;
import net.minecraft.world.entity.ai.util.HoverRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Generic class for flying familiars
 */
public abstract class AbstractFlyingSpellCastingPet extends AbstractSpellCastingPet {

    protected AbstractFlyingSpellCastingPet(EntityType<? extends AbstractSpellCastingPet> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.moveControl = new VexStyleFlyingMoveControl(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(10, new OwnerOrbitStrollGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.91));
            }
        }

        this.calculateEntityAnimation(false);
    }

    @Override
    public boolean shouldAlwaysAnimateLegs() {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isDrinkingPotion() {
        return false;
    }

    @Override
    public void startDrinkingPotion() {
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder pBuilder) {
        super.defineSynchedData(pBuilder);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
    }

    // ==================== VEX-STYLE FLYING MOVE CONTROL ====================

    /**
     * Acceleration-based move control inspired by the Vex.
     * Smoothly accelerates toward the wanted position and rotates to face either the combat target or the movement direction.
     * Only handles MOVE_TO operations — no strafing.
     */
    public static class VexStyleFlyingMoveControl extends MoveControl {
        private final AbstractFlyingSpellCastingPet entity;

        public VexStyleFlyingMoveControl(AbstractFlyingSpellCastingPet entity) {
            super(entity);
            this.entity = entity;
        }

        @Override
        public void tick() {
            if (entity.getIsSitting()) {
                entity.setDeltaMovement(Vec3.ZERO);
                return;
            }

            if (this.operation == Operation.MOVE_TO) {
                Vec3 direction = new Vec3(
                        this.wantedX - entity.getX(),
                        this.wantedY - entity.getY(),
                        this.wantedZ - entity.getZ()
                );
                double distance = direction.length();

                if (distance < entity.getBoundingBox().getSize()) {
                    this.operation = Operation.WAIT;
                    entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5));
                } else {
                    // Accelerate toward the wanted position
                    entity.setDeltaMovement(
                            entity.getDeltaMovement().add(direction.scale(this.speedModifier * 0.05 / distance))
                    );

                    // Face the combat target if in combat, otherwise face movement direction
                    if (entity.getTarget() == null) {
                        Vec3 vel = entity.getDeltaMovement();
                        entity.setYRot(-((float) Mth.atan2(vel.x, vel.z)) * (180F / (float) Math.PI));
                    } else {
                        double dx = entity.getTarget().getX() - entity.getX();
                        double dz = entity.getTarget().getZ() - entity.getZ();
                        entity.setYRot(-((float) Mth.atan2(dx, dz)) * (180F / (float) Math.PI));
                    }
                    entity.yBodyRot = entity.getYRot();
                }
            }
        }
    }

    // ==================== OWNER ORBIT STROLL GOAL ====================

    /**
     * When idle (no combat), the entity floats randomly around the owner's position.
     * Falls back to its own position if the owner is not available.
     */
    protected static class OwnerOrbitStrollGoal extends Goal {
        private final AbstractFlyingSpellCastingPet mob;

        public OwnerOrbitStrollGoal(AbstractFlyingSpellCastingPet mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getIsSitting() || mob.movementDisabled) {
                return false;
            }
            return !mob.getMoveControl().hasWanted()
                    && mob.getRandom().nextInt(reducedTickDelay(7)) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void tick() {
            BlockPos origin;
            LivingEntity owner = mob.getSummoner();
            if (owner != null) {
                origin = owner.blockPosition();
            } else {
                origin = mob.blockPosition();
            }

            for (int i = 0; i < 3; ++i) {
                BlockPos target = origin.offset(
                        mob.getRandom().nextInt(15) - 7,
                        mob.getRandom().nextInt(11) - 5,
                        mob.getRandom().nextInt(15) - 7
                );
                if (mob.level().isEmptyBlock(target)) {
                    mob.getMoveControl().setWantedPosition(
                            target.getX() + 0.5,
                            target.getY() + 0.5,
                            target.getZ() + 0.5,
                            0.25
                    );
                    if (mob.getTarget() == null) {
                        mob.getLookControl().setLookAt(
                                target.getX() + 0.5,
                                target.getY() + 0.5,
                                target.getZ() + 0.5,
                                180.0F, 20.0F
                        );
                    }
                    break;
                }
            }
        }
    }
}