package net.alshanex.familiarslib.entity;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.mobs.IAnimatedAttacker;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Abstract class for flying melee familiars with Vex-style movement.
 */
public abstract class AbstractFlyingMeleeSpellCastingPet extends AbstractFlyingSpellCastingPet implements IAnimatedAttacker {

    private static final ResourceLocation SPELL_POWER_DAMAGE_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "spell_power_damage_boost");

    protected AbstractFlyingMeleeSpellCastingPet(EntityType<? extends AbstractSpellCastingPet> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.moveControl = new VexStyleMoveControl(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && tickCount % 20 == 0) {
            updateSpellPowerDamageModifier();
        }
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
                // Apply slight drag so the entity decelerates smoothly
                this.setDeltaMovement(this.getDeltaMovement().scale(0.91));
            }
        }

        this.calculateEntityAnimation(false);
    }

    private void updateSpellPowerDamageModifier() {
        AttributeInstance attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        double spellPower = this.getAttributeValue(AttributeRegistry.SPELL_POWER);
        attackDamage.removeModifier(SPELL_POWER_DAMAGE_MODIFIER_ID);

        double multiplier = spellPower - 1.0;
        if (multiplier > 0.0) {
            AttributeModifier modifier = new AttributeModifier(
                    SPELL_POWER_DAMAGE_MODIFIER_ID,
                    multiplier,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            );
            attackDamage.addTransientModifier(modifier);
        }
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

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData) {
        return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData);
    }

    RawAnimation animationToPlay = null;
    private final AnimationController<AbstractFlyingMeleeSpellCastingPet> meleeController =
            new AnimationController<>(this, "melee_animations", 0, this::meleePredicate);

    @Override
    public void playAnimation(String animationId) {
        try {
            animationToPlay = RawAnimation.begin().thenPlay(animationId);
        } catch (Exception ignored) {
            FamiliarsLib.LOGGER.error("Entity {} Failed to play animation: {}", this, animationId);
        }
    }

    private PlayState meleePredicate(AnimationState<AbstractFlyingMeleeSpellCastingPet> animationEvent) {
        var controller = animationEvent.getController();
        if (this.animationToPlay != null) {
            controller.forceAnimationReset();
            controller.setAnimation(animationToPlay);
            animationToPlay = null;
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(meleeController);
        super.registerControllers(controllerRegistrar);
    }

    @Override
    public boolean isAnimating() {
        return meleeController.getAnimationState() != AnimationController.State.STOPPED || super.isAnimating();
    }

    /**
     * Move control that mimics the Vex's floating movement style.
     * The entity accelerates toward the wanted position and smoothly rotates
     * to face either the target (in combat) or the movement direction.
     */
    public static class VexStyleMoveControl extends MoveControl {
        private final AbstractFlyingMeleeSpellCastingPet entity;

        public VexStyleMoveControl(AbstractFlyingMeleeSpellCastingPet entity) {
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
                    // Accelerate toward target, scaled by speed modifier
                    entity.setDeltaMovement(
                            entity.getDeltaMovement().add(direction.scale(this.speedModifier * 0.05 / distance))
                    );

                    // Rotation: face the target entity if in combat, otherwise face movement direction
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
}
