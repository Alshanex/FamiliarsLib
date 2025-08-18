package net.alshanex.familiarslib.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
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
public abstract class AbstractFlyingSpellCastingPet extends AbstractSpellCastingPet{
    private int knockbackCooldown = 0;

    protected AbstractFlyingSpellCastingPet(EntityType<? extends AbstractSpellCastingPet> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.moveControl = new ImprovedFlyingMoveControl(this, 10, true);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(10, new FlyingRandomStrollGoal(this, 1.0));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation flyingpathnavigation = new FlyingPathNavigation(this, level);
        flyingpathnavigation.setCanOpenDoors(false);
        flyingpathnavigation.setCanFloat(true);
        flyingpathnavigation.setCanPassDoors(true);
        return flyingpathnavigation;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                float speed = this.getSpeed();
                this.moveRelative(speed, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.91F));
            }
        }

        this.calculateEntityAnimation(false);
    }

    @Override
    public void tick() {
        super.tick();

        if (knockbackCooldown > 0) {
            knockbackCooldown--;
        }

        LivingEntity target = this.getTarget();
        if (target != null) {
            this.getLookControl().setLookAt(target);
        }
    }

    @Override
    public void knockback(double strength, double x, double z) {
        super.knockback(strength, x, z);
        this.knockbackCooldown = 10;
    }

    public boolean isInKnockback() {
        return knockbackCooldown > 0;
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.FLYING_SPEED, .3F)
                .add(Attributes.MOVEMENT_SPEED, .3);
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

    public static class ImprovedFlyingMoveControl extends FlyingMoveControl {
        private final AbstractFlyingSpellCastingPet entity;

        public ImprovedFlyingMoveControl(AbstractFlyingSpellCastingPet entity, int maxTurn, boolean hoversInPlace) {
            super(entity, maxTurn, hoversInPlace);
            this.entity = entity;
        }

        @Override
        public void tick() {
            if (entity.isInKnockback()) {
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.85));
                return;
            }

            if (this.operation == Operation.MOVE_TO) {
                Vec3 targetPos = new Vec3(this.wantedX, this.wantedY, this.wantedZ);
                Vec3 currentPos = entity.position();
                Vec3 direction = targetPos.subtract(currentPos);

                double distance = direction.length();
                if (distance < 0.5) {
                    this.operation = Operation.WAIT;
                    entity.setDeltaMovement(entity.getDeltaMovement().scale(0.6));
                } else {
                    direction = direction.normalize();
                    double speed = Math.min(this.speedModifier * entity.getAttributeValue(Attributes.FLYING_SPEED), distance);

                    if (!entity.isCasting()) {
                        direction = direction.add(
                                (entity.getRandom().nextDouble() - 0.5) * 0.1,
                                (entity.getRandom().nextDouble() - 0.5) * 0.05,
                                (entity.getRandom().nextDouble() - 0.5) * 0.1
                        ).normalize();
                    }

                    Vec3 movement = direction.scale(speed);
                    Vec3 currentMovement = entity.getDeltaMovement();
                    Vec3 blendedMovement = currentMovement.scale(0.2).add(movement.scale(0.8));
                    entity.setDeltaMovement(blendedMovement);

                    float targetYaw = (float)(Math.atan2(direction.z, direction.x) * (180D / Math.PI)) - 90.0F;
                    entity.setYRot(this.rotlerp(entity.getYRot(), targetYaw, 4.0F));
                }
            } else {
                if (!entity.isCasting() && entity.getRandom().nextInt(40) == 0) {
                    Vec3 hover = new Vec3(
                            (entity.getRandom().nextDouble() - 0.5) * 0.02,
                            (entity.getRandom().nextDouble() - 0.5) * 0.01,
                            (entity.getRandom().nextDouble() - 0.5) * 0.02
                    );
                    entity.setDeltaMovement(entity.getDeltaMovement().add(hover));
                }
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.9));
            }
        }
    }

    protected static class FlyingRandomStrollGoal extends Goal {
        private final AbstractFlyingSpellCastingPet mob;
        private final double speedModifier;
        private int interval;
        private Vec3 targetPos;

        public FlyingRandomStrollGoal(AbstractFlyingSpellCastingPet mob, double speedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.interval = 120;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.movementDisabled || mob.hasControllingPassenger()) {
                return false;
            }

            if (mob.getRandom().nextInt(this.interval) != 0) {
                return false;
            }

            Vec3 pos = this.getRandomPosition();
            if (pos == null) {
                return false;
            }

            this.targetPos = pos;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (mob.movementDisabled || mob.hasControllingPassenger()) {
                return false;
            }

            if (this.targetPos == null) {
                return false;
            }

            double distanceToTarget = mob.position().distanceTo(this.targetPos);
            return distanceToTarget > 2.0;
        }

        @Override
        public void start() {
            if (this.targetPos != null) {
                mob.getMoveControl().setWantedPosition(this.targetPos.x, this.targetPos.y, this.targetPos.z, this.speedModifier);
            }
        }

        @Override
        public void stop() {
            this.targetPos = null;
        }

        @Nullable
        private Vec3 getRandomPosition() {
            Vec3 viewVector = mob.getViewVector(0.0F);

            Vec3 hoverPos = HoverRandomPos.getPos(mob, 8, 7, viewVector.x, viewVector.z, (float)(Math.PI / 2), 3, 1);
            if (hoverPos != null) {
                return hoverPos;
            }

            return AirAndWaterRandomPos.getPos(mob, 8, 4, -2, viewVector.x, viewVector.z, (float)(Math.PI / 2));
        }
    }
}
