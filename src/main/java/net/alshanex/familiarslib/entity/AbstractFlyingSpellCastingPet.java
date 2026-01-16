package net.alshanex.familiarslib.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
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
    private int knockbackCooldown = 0;

    // Smooth movement interpolation
    private Vec3 smoothedVelocity = Vec3.ZERO;
    private float smoothedPitch = 0.0F;
    private float smoothedYaw = 0.0F;

    // Combat state tracking
    private int combatMovementTicks = 0;
    private Vec3 currentOrbitCenter = null;
    private double orbitAngle = 0.0;
    private boolean orbitClockwise = true;

    protected AbstractFlyingSpellCastingPet(EntityType<? extends AbstractSpellCastingPet> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.moveControl = new DynamicFlyingMoveControl(this);
        this.lookControl = new SmoothFlyingLookControl(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(10, new FlyingRandomStrollGoal(this, 1.0));
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
            float friction;
            float speed;

            if (this.isInWater()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8F));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                // Dynamic air friction based on combat state
                LivingEntity target = this.getTarget();
                boolean inCombat = target != null && target.isAlive();

                friction = inCombat ? 0.94F : 0.91F; // Less friction in combat = more responsive
                speed = this.getSpeed() * (inCombat ? 1.15F : 1.0F);

                this.moveRelative(speed, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(friction));
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

        // Update combat movement state
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            combatMovementTicks++;

            // Update orbit center to track target movement
            if (currentOrbitCenter == null || combatMovementTicks % 20 == 0) {
                currentOrbitCenter = target.position();
            }

            // Occasionally change orbit direction for unpredictability
            if (this.getRandom().nextInt(200) == 0) {
                orbitClockwise = !orbitClockwise;
            }
        } else {
            combatMovementTicks = 0;
            currentOrbitCenter = null;
        }

        // Apply smooth banking/tilting based on movement
        updateFlightVisuals();
    }

    /**
     * Updates visual flight characteristics like banking and pitch
     */
    private void updateFlightVisuals() {
        Vec3 movement = this.getDeltaMovement();

        // Calculate target pitch based on vertical movement
        float targetPitch = 0.0F;
        if (movement.lengthSqr() > 0.001) {
            targetPitch = (float) Math.toDegrees(Math.atan2(-movement.y, movement.horizontalDistance()));
            targetPitch = Mth.clamp(targetPitch, -45.0F, 45.0F);
        }

        // Smooth pitch interpolation
        smoothedPitch = Mth.lerp(0.1F, smoothedPitch, targetPitch);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        // Reduced knockback for flying entities
        super.knockback(strength * 0.6, x, z);
        this.knockbackCooldown = 15;

        // Add upward component to knockback for flying feel
        Vec3 current = this.getDeltaMovement();
        this.setDeltaMovement(current.x, current.y + strength * 0.3, current.z);
    }

    public boolean isInKnockback() {
        return knockbackCooldown > 0;
    }

    public float getSmoothedPitch() {
        return smoothedPitch;
    }

    public int getCombatMovementTicks() {
        return combatMovementTicks;
    }

    public Vec3 getCurrentOrbitCenter() {
        return currentOrbitCenter;
    }

    public double getOrbitAngle() {
        return orbitAngle;
    }

    public void setOrbitAngle(double angle) {
        this.orbitAngle = angle;
    }

    public boolean isOrbitClockwise() {
        return orbitClockwise;
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.FLYING_SPEED, 0.35F) // Slightly increased for responsiveness
                .add(Attributes.MOVEMENT_SPEED, 0.3);
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
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
    }

    // ==================== SMOOTH FLYING LOOK CONTROL ====================

    /**
     * Smooth look control that allows proper 3D targeting with fluid head movement
     */
    public static class SmoothFlyingLookControl extends LookControl {
        private final AbstractFlyingSpellCastingPet entity;
        private float currentYawVelocity = 0.0F;
        private float currentPitchVelocity = 0.0F;

        public SmoothFlyingLookControl(AbstractFlyingSpellCastingPet entity) {
            super(entity);
            this.entity = entity;
        }

        @Override
        public void tick() {
            if (entity.getIsSitting()) {
                return;
            }

            LivingEntity target = entity.getTarget();
            boolean inCombat = target != null && target.isAlive();

            if (this.lookAtCooldown > 0) {
                --this.lookAtCooldown;

                // Get target rotations
                this.getYRotD().ifPresent((targetYaw) -> {
                    // Use spring-damper for smooth rotation
                    float yawDiff = Mth.wrapDegrees(targetYaw - entity.yHeadRot);

                    // Faster tracking in combat
                    float springStrength = inCombat ? 0.15F : 0.08F;
                    float damping = 0.7F;

                    currentYawVelocity = currentYawVelocity * damping + yawDiff * springStrength;
                    currentYawVelocity = Mth.clamp(currentYawVelocity, -this.yMaxRotSpeed, this.yMaxRotSpeed);

                    entity.yHeadRot = entity.yHeadRot + currentYawVelocity;
                });

                this.getXRotD().ifPresent((targetPitch) -> {
                    float pitchDiff = targetPitch - entity.getXRot();

                    float springStrength = inCombat ? 0.12F : 0.06F;
                    float damping = 0.7F;

                    currentPitchVelocity = currentPitchVelocity * damping + pitchDiff * springStrength;
                    currentPitchVelocity = Mth.clamp(currentPitchVelocity, -this.xMaxRotAngle, this.xMaxRotAngle);

                    entity.setXRot(entity.getXRot() + currentPitchVelocity);
                });
            } else {
                // Gradually reset when not looking at anything
                currentYawVelocity *= 0.8F;
                currentPitchVelocity *= 0.8F;

                if (Math.abs(entity.getXRot()) > 1.0F) {
                    entity.setXRot(Mth.lerp(0.05F, entity.getXRot(), 0.0F));
                }

                // Smoothly align head with body
                float yawDiff = Mth.wrapDegrees(entity.yBodyRot - entity.yHeadRot);
                entity.yHeadRot = entity.yHeadRot + yawDiff * 0.1F;
            }

            // Limit head rotation relative to body
            float wrappedDifference = Mth.wrapDegrees(entity.yBodyRot - entity.yHeadRot);
            float maxHeadTurn = inCombat ? 90.0F : 75.0F;
            if (wrappedDifference < -maxHeadTurn) {
                entity.yHeadRot = entity.yBodyRot + maxHeadTurn;
            } else if (wrappedDifference > maxHeadTurn) {
                entity.yHeadRot = entity.yBodyRot - maxHeadTurn;
            }
        }
    }

    // ==================== DYNAMIC FLYING MOVE CONTROL ====================

    /**
     * Dynamic flying move control with combat awareness, smooth acceleration,
     * and natural flight patterns
     */
    public static class DynamicFlyingMoveControl extends FlyingMoveControl {
        private final AbstractFlyingSpellCastingPet entity;
        private Vec3 velocitySmoothing = Vec3.ZERO;
        private int idleHoverTicks = 0;
        private double hoverOffset = 0.0;

        public DynamicFlyingMoveControl(AbstractFlyingSpellCastingPet entity) {
            super(entity, 20, true);
            this.entity = entity;
        }

        @Override
        public void tick() {
            if (entity.getIsSitting()) {
                entity.setDeltaMovement(Vec3.ZERO);
                velocitySmoothing = Vec3.ZERO;
                return;
            }

            if (entity.isInKnockback()) {
                // Apply drag during knockback recovery
                Vec3 current = entity.getDeltaMovement();
                entity.setDeltaMovement(current.scale(0.92));
                velocitySmoothing = current;
                return;
            }

            LivingEntity target = entity.getTarget();
            boolean inCombat = target != null && target.isAlive();
            boolean isCasting = entity.isCasting();

            if (this.operation == Operation.MOVE_TO) {
                idleHoverTicks = 0;

                Vec3 targetPos = new Vec3(this.wantedX, this.wantedY, this.wantedZ);
                Vec3 currentPos = entity.position();
                Vec3 toTarget = targetPos.subtract(currentPos);
                double distance = toTarget.length();

                if (distance < 0.3) {
                    this.operation = Operation.WAIT;
                    entity.setDeltaMovement(entity.getDeltaMovement().scale(0.5));
                    velocitySmoothing = entity.getDeltaMovement();
                    return;
                }

                Vec3 direction = toTarget.normalize();
                double baseSpeed = entity.getAttributeValue(Attributes.FLYING_SPEED);

                // Calculate desired speed based on context
                double desiredSpeed;
                if (isCasting) {
                    // Slower, more stable movement while casting
                    desiredSpeed = baseSpeed * this.speedModifier * 0.4;
                } else if (inCombat) {
                    // Fast and responsive in combat
                    desiredSpeed = baseSpeed * this.speedModifier * 1.3;
                } else {
                    desiredSpeed = baseSpeed * this.speedModifier;
                }

                // Don't overshoot
                desiredSpeed = Math.min(desiredSpeed, distance * 0.5);

                Vec3 desiredVelocity = direction.scale(desiredSpeed);

                // Add natural flight variation when not in combat
                if (!inCombat && !isCasting) {
                    double wobble = Math.sin(entity.tickCount * 0.1) * 0.02;
                    desiredVelocity = desiredVelocity.add(0, wobble, 0);
                }

                // Smooth velocity interpolation
                // Faster response in combat, smoother otherwise
                double smoothFactor = inCombat ? 0.25 : (isCasting ? 0.08 : 0.15);
                velocitySmoothing = velocitySmoothing.lerp(desiredVelocity, smoothFactor);

                entity.setDeltaMovement(velocitySmoothing);

                // Update rotation
                updateRotation(direction, inCombat, target);

            } else {
                // Idle hovering behavior
                idleHoverTicks++;

                // Gentle bobbing motion
                hoverOffset = Math.sin(idleHoverTicks * 0.05) * 0.015;

                Vec3 hoverVelocity = new Vec3(0, hoverOffset, 0);

                // Add subtle random drift
                if (idleHoverTicks % 60 == 0) {
                    hoverVelocity = hoverVelocity.add(
                            (entity.getRandom().nextDouble() - 0.5) * 0.02,
                            0,
                            (entity.getRandom().nextDouble() - 0.5) * 0.02
                    );
                }

                // When in combat but waiting, maintain slight movement toward target
                if (inCombat && !isCasting) {
                    Vec3 toTarget = target.position().add(0, 2, 0).subtract(entity.position());
                    if (toTarget.lengthSqr() > 4) {
                        hoverVelocity = hoverVelocity.add(toTarget.normalize().scale(0.02));
                    }

                    // Look at target
                    entity.getLookControl().setLookAt(target, 30.0F, 30.0F);
                }

                // Apply with smoothing
                velocitySmoothing = velocitySmoothing.scale(0.85).add(hoverVelocity.scale(0.15));
                entity.setDeltaMovement(velocitySmoothing);
            }
        }

        private void updateRotation(Vec3 movementDirection, boolean inCombat, LivingEntity target) {
            float targetYaw;

            if (inCombat && target != null && entity.hasLineOfSight(target)) {
                // In combat: face toward target while moving
                Vec3 toTarget = target.position().subtract(entity.position());
                float combatYaw = (float)(Math.atan2(toTarget.z, toTarget.x) * (180.0 / Math.PI)) - 90.0F;
                float movementYaw = (float)(Math.atan2(movementDirection.z, movementDirection.x) * (180.0 / Math.PI)) - 90.0F;

                // Blend between movement direction and facing target
                // More weight to facing target when casting
                float blendFactor = entity.isCasting() ? 0.9F : 0.6F;
                float yawDiff = Mth.wrapDegrees(combatYaw - movementYaw);
                targetYaw = movementYaw + yawDiff * blendFactor;
            } else {
                // Out of combat: face movement direction
                targetYaw = (float)(Math.atan2(movementDirection.z, movementDirection.x) * (180.0 / Math.PI)) - 90.0F;
            }

            // Smooth rotation
            float rotSpeed = inCombat ? 12.0F : 6.0F;
            entity.setYRot(this.rotlerp(entity.getYRot(), targetYaw, rotSpeed));
        }
    }

    // ==================== FLYING RANDOM STROLL GOAL ====================

    /**
     * Improved random stroll for natural idle flight patterns
     */
    protected static class FlyingRandomStrollGoal extends Goal {
        private final AbstractFlyingSpellCastingPet mob;
        private final double speedModifier;
        private int nextStartTime;
        private Vec3 targetPos;

        public FlyingRandomStrollGoal(AbstractFlyingSpellCastingPet mob, double speedModifier) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.nextStartTime = 0;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getIsSitting() || mob.movementDisabled || mob.hasControllingPassenger()) {
                return false;
            }

            // Don't stroll when in combat
            if (mob.getTarget() != null && mob.getTarget().isAlive()) {
                return false;
            }

            if (--nextStartTime > 0) {
                return false;
            }

            nextStartTime = 80 + mob.getRandom().nextInt(100);

            Vec3 pos = getRandomPosition();
            if (pos == null) {
                return false;
            }

            this.targetPos = pos;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            if (mob.getIsSitting() || mob.movementDisabled || mob.hasControllingPassenger()) {
                return false;
            }

            if (mob.getTarget() != null && mob.getTarget().isAlive()) {
                return false;
            }

            if (this.targetPos == null) {
                return false;
            }

            return mob.position().distanceToSqr(this.targetPos) > 2.0;
        }

        @Override
        public void start() {
            if (this.targetPos != null && !mob.getIsSitting()) {
                mob.getMoveControl().setWantedPosition(
                        this.targetPos.x,
                        this.targetPos.y,
                        this.targetPos.z,
                        this.speedModifier
                );
            }
        }

        @Override
        public void stop() {
            this.targetPos = null;
        }

        @Nullable
        private Vec3 getRandomPosition() {
            Vec3 viewVector = mob.getViewVector(0.0F);

            // Try hover position first
            Vec3 hoverPos = HoverRandomPos.getPos(mob, 10, 8, viewVector.x, viewVector.z,
                    (float)(Math.PI / 2), 4, 2);
            if (hoverPos != null) {
                return hoverPos;
            }

            // Fallback to air position
            return AirAndWaterRandomPos.getPos(mob, 10, 6, -2, viewVector.x, viewVector.z,
                    (float)(Math.PI / 2));
        }
    }
}