package net.alshanex.familiarslib.entity;

import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import io.redspace.ironsspellbooks.entity.mobs.IAnimatedAttacker;
import io.redspace.ironsspellbooks.entity.mobs.wizards.fire_boss.NotIdioticNavigation;
import net.alshanex.familiarslib.FamiliarsLib;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class AbstractMeleeSpellCastingPet extends AbstractTerrestrianSpellCastingPet implements IAnimatedAttacker {
    private static final UUID SPELL_POWER_DAMAGE_MODIFIER_UUID =
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

    protected AbstractMeleeSpellCastingPet(EntityType<? extends AbstractSpellCastingPet> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.lookControl = createLookControl();
        this.moveControl = createMoveControl();
    }

    protected LookControl createLookControl() {
        return new LookControl(this) {
            //This allows us to more rapidly turn towards our target. Helps to make sure his targets are aligned with his swing animations
            @Override
            protected float rotateTowards(float pFrom, float pTo, float pMaxDelta) {
                return super.rotateTowards(pFrom, pTo, pMaxDelta * 2.5f);
            }

            @Override
            protected boolean resetXRotOnTick() {
                return getTarget() == null;
            }
        };
    }

    protected MoveControl createMoveControl() {
        return new MoveControl(this) {
            //This fixes a bug where a mob tries to path into the block it's already standing, and spins around trying to look "forward"
            //We nullify our rotation calculation if we are close to block we are trying to get to
            @Override
            protected float rotlerp(float pSourceAngle, float pTargetAngle, float pMaximumChange) {
                double d0 = this.wantedX - this.mob.getX();
                double d1 = this.wantedZ - this.mob.getZ();
                if (d0 * d0 + d1 * d1 < .5f) {
                    return pSourceAngle;
                } else {
                    return super.rotlerp(pSourceAngle, pTargetAngle, pMaximumChange * .25f);
                }
            }
        };
    }

    @Override
    public @org.jetbrains.annotations.Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @org.jetbrains.annotations.Nullable SpawnGroupData pSpawnData, @org.jetbrains.annotations.Nullable CompoundTag pDataTag) {
        return super.finalizeSpawn(pLevel, pDifficulty, pReason, pSpawnData, pDataTag);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide && tickCount % 20 == 0) {
            updateSpellPowerDamageModifier();
        }
    }

    private void updateSpellPowerDamageModifier() {
        AttributeInstance attackDamage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackDamage == null) {
            return;
        }

        double spellPower = this.getAttributeValue(AttributeRegistry.SPELL_POWER.get());

        attackDamage.removeModifier(SPELL_POWER_DAMAGE_MODIFIER_UUID);

        double multiplier = spellPower - 1.0;

        if (multiplier > 0.0) {
            AttributeModifier modifier = new AttributeModifier(
                    SPELL_POWER_DAMAGE_MODIFIER_UUID,
                    "spell_power_damage_boost",
                    multiplier,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );

            attackDamage.addTransientModifier(modifier);
        }
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

    // Geckolib Animations

    RawAnimation animationToPlay = null;
    private final AnimationController<AbstractMeleeSpellCastingPet> meleeController = new AnimationController<>(this, "melee_animations", 0, this::predicate);

    @Override
    public void playAnimation(String animationId) {
        try {
            animationToPlay = RawAnimation.begin().thenPlay(animationId);
        } catch (Exception ignored) {
            FamiliarsLib.LOGGER.error("Entity {} Failed to play animation: {}", this, animationId);
        }
    }

    private PlayState predicate(AnimationState<AbstractMeleeSpellCastingPet> animationEvent) {
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

    @Override
    protected PathNavigation createNavigation(Level pLevel) {
        return new NotIdioticNavigation(this, pLevel);
    }
}
