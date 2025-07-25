package net.alshanex.familiarslib.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * Generic class for terrestrian familiars
 */
public abstract class AbstractTerrestrianSpellCastingPet extends AbstractSpellCastingPet{
    protected AbstractTerrestrianSpellCastingPet(EntityType<? extends AbstractSpellCastingPet> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        this.moveControl = createMoveControl();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(10, new MovementAwareRandomStrollGoal(this, 1, 60));
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
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
    public boolean isDrinkingPotion() {
        return false;
    }

    @Override
    public void startDrinkingPotion() {

    }

    @Override
    public boolean shouldAlwaysAnimateLegs() {
        return true;
    }

    protected class MovementAwareRandomStrollGoal extends Goal {
        public static final int DEFAULT_INTERVAL = 120;
        protected final PathfinderMob mob;
        protected double wantedX;
        protected double wantedY;
        protected double wantedZ;
        protected final double speedModifier;
        protected int interval;
        protected boolean forceTrigger;
        private final boolean checkNoActionTime;

        public MovementAwareRandomStrollGoal(PathfinderMob mob, double speedModifier) {
            this(mob, speedModifier, 120);
        }

        public MovementAwareRandomStrollGoal(PathfinderMob mob, double speedModifier, int interval) {
            this(mob, speedModifier, interval, true);
        }

        public MovementAwareRandomStrollGoal(PathfinderMob mob, double speedModifier, int interval, boolean checkNoActionTime) {
            this.mob = mob;
            this.speedModifier = speedModifier;
            this.interval = interval;
            this.checkNoActionTime = checkNoActionTime;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if(movementDisabled){ return false;}
            if (this.mob.hasControllingPassenger()) {
                return false;
            } else {
                if (!this.forceTrigger) {
                    if (this.checkNoActionTime && this.mob.getNoActionTime() >= 100) {
                        return false;
                    }

                    if (this.mob.getRandom().nextInt(reducedTickDelay(this.interval)) != 0) {
                        return false;
                    }
                }

                Vec3 vec3 = this.getPosition();
                if (vec3 == null) {
                    return false;
                } else {
                    this.wantedX = vec3.x;
                    this.wantedY = vec3.y;
                    this.wantedZ = vec3.z;
                    this.forceTrigger = false;
                    return true;
                }
            }
        }

        @Nullable
        protected Vec3 getPosition() {
            return DefaultRandomPos.getPos(this.mob, 10, 7);
        }

        @Override
        public boolean canContinueToUse() {
            return !this.mob.getNavigation().isDone() && !this.mob.hasControllingPassenger();
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.wantedX, this.wantedY, this.wantedZ, this.speedModifier);
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
            super.stop();
        }

        public void trigger() {
            this.forceTrigger = true;
        }

        public void setInterval(int newchance) {
            this.interval = newchance;
        }
    }
}
