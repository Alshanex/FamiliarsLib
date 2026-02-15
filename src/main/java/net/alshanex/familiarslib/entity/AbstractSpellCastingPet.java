package net.alshanex.familiarslib.entity;

import io.redspace.ironsspellbooks.api.entity.IMagicEntity;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.entity.mobs.IMagicSummon;
import io.redspace.ironsspellbooks.entity.mobs.abstract_spell_casting_mob.AbstractSpellCastingMob;
import io.redspace.ironsspellbooks.entity.mobs.goals.*;
import io.redspace.ironsspellbooks.entity.spells.AoeEntity;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.fire.BurningDashSpell;
import io.redspace.ironsspellbooks.util.OwnerHelper;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.AbstractFamiliarBedBlock;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
import net.alshanex.familiarslib.util.CylinderParticleManager;
import net.alshanex.familiarslib.util.ModTags;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration;
import net.alshanex.familiarslib.util.consumables.FamiliarConsumableSystem;
import net.alshanex.familiarslib.util.familiars.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Generic class with the main methods of all familiars
 */
public abstract class AbstractSpellCastingPet extends AbstractSpellCastingMob {
    protected static final EntityDataAccessor<Boolean> DATA_IS_SITTING;
    protected static final EntityDataAccessor<Boolean> DATA_IS_HOUSE;

    static {
        DATA_ID_OWNER_UUID = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.OPTIONAL_UUID);
        DATA_IS_SITTING = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_IS_HOUSE = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_IMPOSTOR = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_TOTEM = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
    }

    private static final EntityDataAccessor<Optional<UUID>> DATA_ID_OWNER_UUID;
    private static final EntityDataAccessor<Boolean> DATA_IMPOSTOR;
    private static final EntityDataAccessor<Boolean> DATA_TOTEM;

    protected LivingEntity cachedSummoner;

    protected boolean movementDisabled = false;

    private boolean lastTrinketState = false;
    private WizardAttackGoal currentAttackGoal;
    private boolean pendingGoalUpdate = false;
    private boolean pendingTrinketState = false;

    private boolean hasAttemptedMigration = false;
    private boolean hasAttemptedConsumableMigration = false;
    private boolean hasInitializedHealth = false;

    public BlockPos housePosition = null;

    protected AbstractSpellCastingPet(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    //Sets the chance of the familiar being a disguised illusionist
    protected void setRandomImpostor(){
        Random rand = new Random();

        if(rand.nextFloat() <= 0.1){
            setIsImpostor(true);
            if(this.getIsImpostor()){
                this.goalSelector.addGoal(3, new FamiliarGoals.StealItemsWhenNotWatchedGoal(this, 3.0D));
            }
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(6, new FamiliarGoals.TeleportToOwnerGoal(this, this::getSummoner, 20F));
        this.goalSelector.addGoal(7, new FamiliarGoals.MovementAwareFollowOwnerGoal(this, this::getSummoner, 1.2f, 10, 3, false, Float.MAX_VALUE));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        //this.goalSelector.addGoal(9, new FamiliarGoals.MovementAwareRandomLookAroundGoal(this));
        this.goalSelector.addGoal(10, new FamiliarGoals.FindAndUsePetBedGoal(this, 10.0));

        this.targetSelector.addGoal(1, new GenericOwnerHurtByTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(2, new GenericOwnerHurtTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(3, new GenericCopyOwnerTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(4, (new GenericHurtByTargetGoal(this, (entity) -> entity == getSummoner())).setAlertOthers());
        this.targetSelector.addGoal(5, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(5, new FamiliarGoals.FamiliarHurtByTargetGoal(this));
        this.targetSelector.addGoal(6, new FamiliarGoals.AlliedFamiliarDefenseGoal(this));
    }

    public float getBaseMaxHealth() {
        AttributeInstance healthAttribute = getAttribute(Attributes.MAX_HEALTH);
        if (healthAttribute != null) {
            return (float) healthAttribute.getBaseValue();
        }
        return 50.0f; // Fallback value
    }

    //This section handles the trinket power boost
    protected abstract WizardAttackGoal createAttackGoal(float min, float max);

    protected float[] getOriginalQualityValues() {
        return new float[]{0.1f, 0.3f};
    }

    protected float[] getTrinketQualityValues() {
        return new float[]{0.4f, 0.6f};
    }

    protected void initializeAttackGoal(int goalPriority) {
        float[] originalValues = getOriginalQualityValues();
        this.currentAttackGoal = createAttackGoal(originalValues[0], originalValues[1]);
        this.goalSelector.addGoal(goalPriority, this.currentAttackGoal);
    }

    private void updateAttackGoal() {
        LivingEntity summoner = getSummoner();
        if (summoner == null) return;

        boolean hasTrinket = CurioUtils.isWearingCurio(summoner, this.getValidTrinket());

        if (hasTrinket != lastTrinketState) {
            if (this.currentAttackGoal != null && this.currentAttackGoal.isActing()) {
                this.pendingGoalUpdate = true;
                this.pendingTrinketState = hasTrinket;
                return;
            }

            updateGoalSafely(hasTrinket);
        }
    }

    private void updateGoalSafely(boolean hasTrinket) {
        if (this.currentAttackGoal != null) {
            this.goalSelector.removeGoal(this.currentAttackGoal);
        }

        if (hasTrinket) {
            float[] trinketValues = getTrinketQualityValues();
            this.currentAttackGoal = createAttackGoal(trinketValues[0], trinketValues[1]);
        } else {
            float[] originalValues = getOriginalQualityValues();
            this.currentAttackGoal = createAttackGoal(originalValues[0], originalValues[1]);
        }

        this.goalSelector.addGoal(2, this.currentAttackGoal);

        lastTrinketState = hasTrinket;
        this.pendingGoalUpdate = false;
    }

    //Used to set the valid trinket that will give the boost to the familiar
    protected abstract Item getValidTrinket();

    //Generic owner and allay methods
    @Override
    public boolean isAlliedTo(Entity pEntity) {
        return super.isAlliedTo(pEntity) || this.isAlliedHelper(pEntity);
    }

    private boolean isAlliedHelper(Entity entity) {
        var owner = getSummoner();
        if (owner == null) {
            return false;
        }
        if (entity.is(getSummoner())){ return true;}
        if (entity instanceof IMagicSummon magicSummon) {
            var otherOwner = magicSummon.getSummoner();
            return otherOwner != null && (owner == otherOwner || otherOwner.isAlliedTo(otherOwner));
        } else if (entity instanceof OwnableEntity tamableAnimal) {
            var otherOwner = tamableAnimal.getOwner();
            return otherOwner != null && (owner == otherOwner || otherOwner.isAlliedTo(otherOwner));
        } else if (entity instanceof AbstractSpellCastingPet pet) {
            var otherOwner = pet.getSummoner();
            return otherOwner != null && (owner == otherOwner || otherOwner.isAlliedTo(otherOwner));
        } else if (entity instanceof AoeEntity zone) {
            var otherOwner = zone.getOwner();
            return otherOwner != null && (owner == otherOwner || otherOwner.isAlliedTo(otherOwner) || otherOwner.isAlliedTo(owner));
        }
        return false;
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(DATA_ID_OWNER_UUID, Optional.ofNullable(uuid));
    }

    public UUID getOwnerUUID() {
        return this.entityData
                .get(DATA_ID_OWNER_UUID)
                .orElseGet(() -> this.entityData.get(DATA_ID_OWNER_UUID).orElse(null));
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    public net.minecraft.world.entity.ai.goal.GoalSelector getGoalSelector() {
        return this.goalSelector;
    }

    //Getters and setters for the familiars data, like consumables, impostor mark, house mark, etc.
    public void setEnragedStacks(Integer level) {
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(this);
        data.setValue(FamiliarConsumableSystem.ConsumableType.ENRAGED, level);
        updateConsumableData(data);
    }

    public Integer getEnragedStacks() {
        return FamiliarConsumableIntegration.getCurrentBonus(this, FamiliarConsumableSystem.ConsumableType.ENRAGED);
    }

    public void setArmorStacks(Integer level) {
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(this);
        data.setValue(FamiliarConsumableSystem.ConsumableType.ARMOR, level);
        updateConsumableData(data);
    }

    public Integer getArmorStacks() {
        return FamiliarConsumableIntegration.getCurrentBonus(this, FamiliarConsumableSystem.ConsumableType.ARMOR);
    }

    public void setHealthStacks(Integer level) {
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(this);
        data.setValue(FamiliarConsumableSystem.ConsumableType.HEALTH, level);
        updateConsumableData(data);
    }

    public Integer getHealthStacks() {
        return FamiliarConsumableIntegration.getCurrentBonus(this, FamiliarConsumableSystem.ConsumableType.HEALTH);
    }

    public void setIsBlocking(Boolean level) {
        FamiliarConsumableSystem.ConsumableData data = FamiliarConsumableIntegration.getConsumableData(this);
        data.setValue(FamiliarConsumableSystem.ConsumableType.BLOCKING, level ? 1 : 0);
        updateConsumableData(data);
    }

    private void updateConsumableData(FamiliarConsumableSystem.ConsumableData data) {
        if (!level().isClientSide) {
            FamiliarConsumableSystem.applyAttributeModifiers(this, data);
        }
    }

    public Boolean getIsBlocking() {
        return FamiliarConsumableIntegration.canFamiliarBlock(this);
    }

    public void setTotem(Boolean level){
        this.entityData.set(DATA_TOTEM, level);
    }

    public Boolean getTotem() {
        return this.entityData
                .get(DATA_TOTEM);
    }

    public void setIsImpostor(Boolean level){
        this.entityData.set(DATA_IMPOSTOR, level);
    }

    public Boolean getIsImpostor() {
        return this.entityData
                .get(DATA_IMPOSTOR);
    }

    public void setSitting(Boolean level){
        try {
            if(level){
                FamiliarBedHelper.snapToExactBedPosition(this);
                clearMovementGoals();
                this.navigation.stop();
                this.setTarget(null);

                // Special handling for flying pets
                if (this instanceof AbstractFlyingSpellCastingPet) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
                }
            } else {
                restoreMovementGoals();
                this.navigation.recomputePath();
            }
            this.entityData.set(DATA_IS_SITTING, level);
        } catch (Exception e) {
            FamiliarsLib.LOGGER.error("Error in setSitting: ", e);
        }
    }

    public Boolean getIsSitting() {
        return this.entityData
                .get(DATA_IS_SITTING);
    }

    public Boolean getIsInHouse() {
        return this.entityData
                .get(DATA_IS_HOUSE);
    }

    public void setIsInHouse(Boolean level, BlockPos housePos) {
        this.entityData.set(DATA_IS_HOUSE, level);
        this.housePosition = level ? housePos : null;

        if (level && housePos != null) {
            clearHouseGoals();
            addHouseGoals();

            FamiliarsLib.LOGGER.debug("Familiar {} entered house mode at {}", getUUID(), housePos);
        } else {
            clearHouseGoals();
            FamiliarsLib.LOGGER.debug("Familiar {} exited house mode", getUUID());
        }
    }

    public boolean hasAtteptedConsumableMigration(){
        return this.hasAttemptedConsumableMigration;
    }

    public void setHasAttemptedConsumableMigration(boolean bool){
        this.hasAttemptedConsumableMigration = bool;
    }

    //Goals set to familiars inside storage blocks
    private void addHouseGoals() {
        if (housePosition == null) return;

        this.goalSelector.addGoal(1, new FamiliarGoals.WanderAroundHouseGoal(this, housePosition, 1.0));

        this.goalSelector.addGoal(2, new FamiliarGoals.CasualLookAtPlayerGoal(this, Player.class, 8.0F));

        this.goalSelector.addGoal(3, new FamiliarGoals.CasualRandomLookGoal(this));

        this.goalSelector.addGoal(8, new FamiliarGoals.StayNearHouseGoal(this, housePosition));

        this.goalSelector.addGoal(9, new FloatGoal(this));
    }

    private void clearHouseGoals() {
        this.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof FamiliarGoals.WanderAroundHouseGoal ||
                        goal.getGoal() instanceof FamiliarGoals.StayNearHouseGoal ||
                        goal.getGoal() instanceof FamiliarGoals.CasualLookAtPlayerGoal ||
                        goal.getGoal() instanceof FamiliarGoals.CasualRandomLookGoal);
    }

    public int getHouseMaxDistance() {
        if (housePosition == null || level() == null) {
            return 25;
        }
        BlockEntity blockEntity = level().getBlockEntity(housePosition);
        if (blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity) {
            return storageEntity.getMaxDistance();
        }
        return 25;
    }

    //Handles sitting logic
    protected void clearMovementGoals(){
        movementDisabled = true;
        this.navigation.stop();

        // Additional handling for flying pets
        if (this instanceof AbstractFlyingSpellCastingPet) {
            this.setDeltaMovement(Vec3.ZERO);
            this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
        }
    }

    protected void restoreMovementGoals(){
        movementDisabled = false;
        this.navigation.recomputePath();

        // Clear any forced position for flying pets
        if (this instanceof AbstractFlyingSpellCastingPet) {
            this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0);
        }
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (this.isInvulnerableTo(pSource)) {
            return false;
        } else {
            this.setSitting(false);
            return super.hurt(pSource, pAmount);
        }
    }

    //Invulnerable to the owner and owner's other familiars, also invulnerable when inside a storage block
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.getEntity() != null && this.isAlliedTo(source.getEntity())) {
            return true;
        }  else {
            return super.isInvulnerableTo(source);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder pBuilder) {
        super.defineSynchedData(pBuilder);
        pBuilder.define(DATA_ID_OWNER_UUID, Optional.empty());
        pBuilder.define(DATA_IS_SITTING, false);
        pBuilder.define(DATA_IS_HOUSE, false);
        pBuilder.define(DATA_IMPOSTOR, false);
        pBuilder.define(DATA_TOTEM, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);

        if (getOwnerUUID() != null) {
            pCompound.putUUID("ownerUUID", getOwnerUUID());
        } else {
            pCompound.putString("ownerUUID", "null");
        }

        float currentHealth = getHealth();
        pCompound.putFloat("currentHealth", currentHealth);
        pCompound.putFloat("baseMaxHealth", getBaseMaxHealth()); // Keep for reference

        FamiliarsLib.LOGGER.debug("Saving health for familiar {}: current={}, max={}, base={}",
                getUUID(), currentHealth, getMaxHealth(), getBaseMaxHealth());

        pCompound.putBoolean("Sitting", getIsSitting());
        pCompound.putBoolean("isImpostor", getIsImpostor());
        pCompound.putBoolean("hasTotem", getTotem());
        pCompound.putBoolean("lastTrinketState", lastTrinketState);
        pCompound.putBoolean("pendingGoalUpdate", pendingGoalUpdate);
        pCompound.putBoolean("pendingTrinketState", pendingTrinketState);
        pCompound.putBoolean("hasAttemptedMigration", hasAttemptedMigration);
        pCompound.putBoolean("hasAttemptedConsumableMigration", hasAttemptedConsumableMigration);
        pCompound.putBoolean("hasInitializedHealth", hasInitializedHealth);

        pCompound.putBoolean("isInHouse", getIsInHouse());
        if (housePosition != null) {
            pCompound.putLong("housePosition", housePosition.asLong());
        }

        FamiliarConsumableIntegration.saveConsumableData(this, pCompound);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound); // 1. Loads vanilla "Health" here

        if (pCompound.contains("ownerUUID")) {
            String ownerUUIDString = pCompound.getString("ownerUUID");
            if (!"null".equals(ownerUUIDString)) {
                setOwnerUUID(pCompound.getUUID("ownerUUID"));
            } else {
                setOwnerUUID(null);
            }
        }

        // Load New System Data
        FamiliarConsumableIntegration.loadConsumableData(this, pCompound);

        if (pCompound.isEmpty() || (!pCompound.contains("currentHealth") && !pCompound.contains("ownerUUID") && !pCompound.contains("Health"))) {
            // This is a fresh command spawn or empty data
            hasInitializedHealth = false;
        } else {
            hasInitializedHealth = pCompound.getBoolean("hasInitializedHealth");

            float savedHealth;
            // Check if we have the NEW custom tag
            if (pCompound.contains("currentHealth")) {
                savedHealth = pCompound.getFloat("currentHealth");
            } else {
                // MIGRATION FALLBACK: Use the vanilla health loaded by super.readAdditionalSaveData()
                savedHealth = this.getHealth();

                // Safety net: If they were dead or glitched to 0, revive them to Max during migration
                if (savedHealth <= 0) {
                    savedHealth = getMaxHealth();
                }
            }

            FamiliarsLib.LOGGER.debug("Loading health for familiar {}: saved health = {}", getUUID(), savedHealth);

            if (!level().isClientSide) {
                // Recalculate max health based on the migrated stacks
                FamiliarConsumableIntegration.applyConsumableModifiers(this);

                float newMaxHealth = getMaxHealth();
                float restoredHealth = Math.min(savedHealth, newMaxHealth);

                setHealth(restoredHealth);
                hasInitializedHealth = true;

                FamiliarsLib.LOGGER.debug("Restored health: {}/{}", restoredHealth, newMaxHealth);
            } else {
                getPersistentData().putFloat("pendingHealth", savedHealth);
            }
        }

        // Remaining Flags
        if(pCompound.contains("Sitting")){
            setSitting(pCompound.getBoolean("Sitting"));
            movementDisabled = pCompound.getBoolean("Sitting");
        }

        if(pCompound.contains("isImpostor")) setIsImpostor(pCompound.getBoolean("isImpostor"));
        if(pCompound.contains("hasTotem")) setTotem(pCompound.getBoolean("hasTotem"));

        lastTrinketState = pCompound.getBoolean("lastTrinketState");
        pendingGoalUpdate = pCompound.getBoolean("pendingGoalUpdate");
        pendingTrinketState = pCompound.getBoolean("pendingTrinketState");
        if (pCompound.contains("hasAttemptedMigration")) hasAttemptedMigration = pCompound.getBoolean("hasAttemptedMigration");

        if (pCompound.contains("isInHouse")) {
            boolean inHouse = pCompound.getBoolean("isInHouse");
            BlockPos housePos = null;
            if (pCompound.contains("housePosition")) {
                housePos = BlockPos.of(pCompound.getLong("housePosition"));
            }
            setIsInHouse(inHouse, housePos);
        }
    }

    //Owner getter
    public LivingEntity getSummoner() {
        return OwnerHelper.getAndCacheOwner(level(), cachedSummoner, getOwnerUUID());
    }

    //Handle death logic
    @Override
    public void die(DamageSource pDamageSource) {
        if (!level().isClientSide) {
            this.onDeathHelper();
        }

        if (getIsInHouse() && housePosition != null) {
            FamiliarDeathStorageHandler.notifyFamiliarDeath(this);
        }

        super.die(pDamageSource);
    }

    private void onDeathHelper() {
        if (this instanceof LivingEntity entity) {
            Level level = entity.level;
            var deathMessage = entity.getCombatTracker().getDeathMessage();

            if (!level.isClientSide && level.getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES) && getSummoner() instanceof ServerPlayer player) {
                player.sendSystemMessage(deathMessage);
            }
        }
    }

    protected MoveControl createMoveControl() {
        return new MoveControl(this) {
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
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            this.noCulling = this.isAnimating();
        }

        if (!level().isClientSide && getIsInHouse()) {
            FamiliarHelper.handleHouseBehavior(this);
        }

        //Auto-migration to the new system
        if (!level().isClientSide && !hasAttemptedMigration && getSummoner() != null) {
            if (getSummoner() instanceof ServerPlayer serverPlayer) {
                FamiliarHelper.attemptLegacyMigration(serverPlayer, this);
                hasAttemptedMigration = true;
            } else {
                UUID ownerUUID = getOwnerUUID();
                if (ownerUUID != null && level() instanceof ServerLevel serverLevel) {
                    ServerPlayer serverPlayer = serverLevel.getServer().getPlayerList().getPlayer(ownerUUID);
                    if (serverPlayer != null) {
                        FamiliarHelper.attemptLegacyMigration(serverPlayer, this);
                        hasAttemptedMigration = true;
                    }
                }
            }
        }

        //Goal update for the trinket boost
        if (this.pendingGoalUpdate && !level.isClientSide) {
            if (this.currentAttackGoal == null || !this.currentAttackGoal.isActing()) {
                updateGoalSafely(this.pendingTrinketState);
            }
        }

        if (this.tickCount % 20 == 0 && !level.isClientSide) {
            updateAttackGoal();
        }

        if (this.tickCount % 10 == 0 && !level.isClientSide) {
            if((this.isOnBed() || this.getIsInHouse() || this.getMovementDisabled())
                    && this.currentAttackGoal.isActing()){
                this.currentAttackGoal.stop();
            }
        }

        //Check to not despawn when tamed
        if (this.tickCount % 600 == 0 && !level.isClientSide) {
            if (getSummoner() != null || getOwnerUUID() != null) {
                this.setPersistenceRequired();
            }
        }

        //Stop targeting owner's other familiars
        if(getSummoner() != null && getTarget() != null && getTarget() instanceof AbstractSpellCastingPet familiar && this.tickCount % 10 == 0){
            if(familiar.getSummoner() != null && familiar.getSummoner().is(getSummoner())){
                setTarget(null);
            }
        }

        //Familiars can't attack anyone when inside storage blocks
        if(getTarget() != null && getIsInHouse() && this.tickCount % 10 == 0){
            if(isHunterPet()){
                if(getTarget() instanceof Mob mob){
                    if(mob.getTarget() != null && this.isAlliedTo(mob.getTarget()) && !mob.getTarget().getType().is(ModTags.HUNTER_CANNOT_ATTACK_IN_HOME)){
                        this.setTarget(mob);
                    } else {
                        setTarget(null);
                    }
                } else {
                    setTarget(null);
                }
            } else {
                setTarget(null);
            }
        }

        //Handles Familiar Spellbook attribute sharing
        if (getSummoner() != null && this.tickCount % 10 == 0 && getHealth() > 0) {
            if (CurioUtils.isWearingFamiliarSpellbook(getSummoner())) {
                FamiliarAttributesHelper.applyAttributes(this);
            } else {
                FamiliarAttributesHelper.removeAttributes(this);
            }
        }

        //Periodic status update for the player
        if (getSummoner() != null && this.tickCount % 40 == 0) {
            if (!level().isClientSide && getSummoner() instanceof ServerPlayer serverPlayer) {
                PlayerFamiliarData familiarData = serverPlayer.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);
                if (familiarData.hasFamiliar(getUUID())) {
                    FamiliarManager.updateFamiliarData(this);
                } else {
                    FamiliarsLib.LOGGER.debug("Familiar {} not in player data, skipping update", getUUID());
                }
            }
        }
    }

    public static AttributeSupplier.Builder prepareAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.MOVEMENT_SPEED, .25);
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    //Prevents despawn of tamed familiars
    @Override
    public void checkDespawn() {
        if (getSummoner() != null || getOwnerUUID() != null) {
            this.setPersistenceRequired();
            return;
        }
        super.checkDespawn();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        if (getSummoner() != null || getOwnerUUID() != null) {
            return false;
        }
        return super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return getSummoner() != null || getOwnerUUID() != null;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    //Taming, feeding and other interaction logic
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        //Interactions with familiars inside storage blocks
        if (getIsInHouse() && housePosition != null) {
            if (!(level().getBlockEntity(housePosition) instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
                return InteractionResult.FAIL;
            }

            if (!storageEntity.isOwner((ServerPlayer) player)) {
                return InteractionResult.FAIL;
            }

            // Owner can still feed the familiar
            if (!level().isClientSide) {
                InteractionResult consumableResult = FamiliarConsumableIntegration.handleConsumableInteraction(this, player, itemstack);
                if (consumableResult != InteractionResult.PASS) {
                    return consumableResult;
                }

                if (isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                    float healAmount = 4f;
                    if(itemstack.has(ComponentRegistry.FAMILIAR_FOOD)){
                        healAmount = itemstack.get(ComponentRegistry.FAMILIAR_FOOD).healing();
                    }
                    this.heal(healAmount);
                    itemstack.consume(1, player);
                    FamiliarHelper.spawnEatingParticles(this);
                    this.gameEvent(GameEvent.EAT);
                    return InteractionResult.sidedSuccess(this.level().isClientSide());
                }
            }
            return InteractionResult.PASS;
        }

        //Sitting interaction
        if(getSummoner() != null && getSummoner().is(player) && itemstack.is(Items.STICK)){
            this.setSitting(!getIsSitting());
            return InteractionResult.SUCCESS;
        }

        //Taming and feeding interaction
        if (!this.level().isClientSide) {
            if(getOwnerUUID() != null){
                if(getOwnerUUID().equals(player.getUUID())){
                    if (getSummoner() instanceof ServerPlayer serverPlayer) {
                        FamiliarHelper.attemptLegacyMigration(serverPlayer, this);
                    }

                    triggerAnim("interact_controller", "interact");

                    InteractionResult consumableResult = FamiliarConsumableIntegration.handleConsumableInteraction(this, player, itemstack);
                    if (consumableResult != InteractionResult.PASS) {
                        return consumableResult;
                    }

                    if(itemstack.is(ModTags.BERRY)){
                        itemstack.shrink(1);

                        Optional<Item> firstItem = BuiltInRegistries.ITEM.getTag(ModTags.FRAGMENTS)
                                .map(holders -> holders.iterator().next().value())
                                .or(() -> Optional.empty());
                        ItemStack itemStack = firstItem.map(item -> new ItemStack(item, 1))
                                .orElse(ItemStack.EMPTY);

                        this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemStack));
                        if(this.getHealth() < this.getMaxHealth()){
                            this.heal(2.0F * 2.0f);
                            this.gameEvent(GameEvent.EAT);
                        }
                        return InteractionResult.sidedSuccess(this.level().isClientSide());
                    }
                    if(isFood(itemstack) && this.getHealth() < this.getMaxHealth()){
                        float healAmount = 4f;
                        if(itemstack.has(ComponentRegistry.FAMILIAR_FOOD)){
                            healAmount = itemstack.get(ComponentRegistry.FAMILIAR_FOOD).healing();
                        }
                        this.heal(healAmount);
                        itemstack.consume(1, player);
                        FamiliarHelper.spawnEatingParticles(this);
                        this.gameEvent(GameEvent.EAT);
                        return InteractionResult.sidedSuccess(this.level().isClientSide());
                    }  else if(isHunterPet() && itemstack != null && itemstack != ItemStack.EMPTY){
                        //Hunter ability interaction

                        Item heldItem = itemstack.getItem();

                        AABB searchArea = this.getBoundingBox().inflate(20.0D);
                        List<Entity> nearbyEntities = this.level().getEntities(
                                this,
                                searchArea,
                                entity -> entity instanceof LivingEntity && FamiliarHelper.canDropItem((ServerLevel) this.level(), entity, heldItem) && !entity.getType().is(ModTags.HUNTER_CANNOT_MARK)
                        );

                        for (Entity entity : nearbyEntities) {
                            if (entity instanceof LivingEntity livingEntity) {
                                livingEntity.addEffect(new MobEffectInstance(
                                        MobEffects.GLOWING, 200, 0, false, true)
                                );
                            }
                        }
                        return super.mobInteract(player, hand);
                    }
                }
            } else {
                //Taming interaction

                if (itemstack.is(ModTags.FAMILIAR_TAMING)) {
                    itemstack.consume(1, player);
                    this.tryToTame(player);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    //Hunter ability check
    protected boolean isHunterPet(){
        return false;
    }

    //Cleric check for special support goal
    protected void clericSetGoal(ServerPlayer player){

    }

    //Placeholder food, can be changed in the familiar's class
    protected boolean isFood(ItemStack item){
        return item.has(ComponentRegistry.FAMILIAR_FOOD);
    }

    //Taming logic
    protected void tryToTame(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        PlayerFamiliarData familiarData = serverPlayer.getData(AttachmentRegistry.PLAYER_FAMILIAR_DATA);

        if (!familiarData.canTameMoreFamiliars()) {
            serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.limit_familiars", PlayerFamiliarData.MAX_FAMILIAR_LIMIT).withStyle(ChatFormatting.RED)));
            return;
        }

        if (this.random.nextInt(10) <= 3) {
            FamiliarHelper.spawnTamingParticles(true, this);
            this.setTarget(null);
            this.setOwnerUUID(player.getUUID());

            boolean success = FamiliarManager.handleFamiliarTaming(this, serverPlayer);

            if (success) {
                this.setPersistenceRequired();
                triggerAdvancement(serverPlayer);

                int remainingSlots = familiarData.getRemainingFamiliarSlots();
                serverPlayer.connection.send(new ClientboundSetActionBarTextPacket(Component.translatable("message.familiarslib.tamed_successfully", remainingSlots).withStyle(ChatFormatting.WHITE)));

                serverPlayer.level().playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 2.0F);

                clericSetGoal(serverPlayer);
            } else {
                FamiliarHelper.spawnTamingParticles(false, this);
            }
        } else {
            FamiliarHelper.spawnTamingParticles(false, this);
        }
    }

    //Handles advancement trigger if needed, overriden in familiar classes
    protected void triggerAdvancement(ServerPlayer player){

    }

    //Spawning logic and effects
    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        triggerAnim("spawn_controller", "spawn");
    }

    //Despawning logic and effects
    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level.isClientSide && getSummoner() instanceof ServerPlayer serverPlayer) {
            FamiliarAttributesHelper.handleFamiliarDismissed(serverPlayer, this);
        }

        if (!level.isClientSide) {
            FamiliarConsumableIntegration.removeConsumableModifiers(this);
        }

        if(!level.isClientSide){
            MagicManager.spawnParticles(level(), ParticleTypes.POOF, getX(), getY(), getZ(), 25, .4, .8, .4, .03, false);
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData);

        // Handle command-spawned entities
        if (reason == MobSpawnType.COMMAND) {
            FamiliarsLib.LOGGER.debug("Finalizing spawn for command-summoned familiar {}", getUUID());

            // Initialize consumable system for command-spawned entities
            if (!level.isClientSide()) {
                FamiliarConsumableIntegration.applyConsumableModifiers(this);

                // Ensure health is properly set
                if (getHealth() <= 0) {
                    setHealth(getMaxHealth());
                    FamiliarsLib.LOGGER.debug("Set health for command-spawned familiar {}: {}/{}",
                            getUUID(), getHealth(), getMaxHealth());
                }

                hasInitializedHealth = true;

                // Mark as persistent so it doesn't despawn
                setPersistenceRequired();
            }
        }

        return result;
    }

    public int getConsumableBonus(FamiliarConsumableSystem.ConsumableType type) {
        return FamiliarConsumableIntegration.getCurrentBonus(this, type);
    }

    public int getMaxUsableConsumableTier(FamiliarConsumableSystem.ConsumableType type) {
        return FamiliarConsumableIntegration.getMaxUsableTier(this, type);
    }

    //Helper method to check if familiars can execute goals in wander mode of storage blocks
    public boolean canExecuteGoalsInHouse() {
        if (!getIsInHouse() || housePosition == null) {
            return true;
        }

        if (level() == null || level().isClientSide) {
            return false;
        }

        BlockEntity blockEntity = level().getBlockEntity(housePosition);
        if (!(blockEntity instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
            return false;
        }

        return !storageEntity.isStoreMode() && storageEntity.canFamiliarsUseGoals();
    }

    public boolean getMovementDisabled(){
        return this.movementDisabled;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
    }

    @Override
    public void setSyncedSpellData(SyncedSpellData syncedSpellData) {
        if (!level().isClientSide) {
            return;
        }

        var playerMagicData = this.getMagicData();
        var wasCasting = playerMagicData.isCasting();
        playerMagicData.setSyncedData(syncedSpellData);

        SpellData castingSpell = playerMagicData.getCastingSpell();

        if (castingSpell == null) {
            return;
        }

        // Handle transition from not-casting to casting
        if (playerMagicData.isCasting() && !wasCasting) {
            var spell = castingSpell.getSpell();
            initiateCastSpell(spell, playerMagicData.getCastingSpellLevel());

            if (castingSpell.getSpell().getCastType() == CastType.INSTANT) {
                this.instantCastSpellType = castingSpell.getSpell();
                castingSpell.getSpell().onClientPreCast(level(),
                        castingSpell.getLevel(), this, InteractionHand.MAIN_HAND, playerMagicData);
                castComplete();
            }
        }
        // Handle transition from casting to not-casting
        else if (!playerMagicData.isCasting() && wasCasting) {
            castComplete();
        }
    }

    /**
     * GeckoLib Animations
     **/
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected AbstractSpell lastCastSpellType = SpellRegistry.none();
    public AbstractSpell instantCastSpellType = SpellRegistry.none();
    protected boolean cancelCastAnimation = false;
    protected boolean animatingLegs = false;

    protected final RawAnimation idle = RawAnimation.begin().thenLoop("idle");
    protected final RawAnimation walk = RawAnimation.begin().thenLoop("walk");
    protected final RawAnimation attack = RawAnimation.begin().thenPlay("skill");
    protected final RawAnimation longCast = RawAnimation.begin().thenPlay("long_cast");
    protected final RawAnimation continuousCast = RawAnimation.begin().thenLoop("continuous");
    protected final RawAnimation interact = RawAnimation.begin().thenPlay("interact");
    protected final RawAnimation stomp = RawAnimation.begin().thenPlay("stomp");
    protected final RawAnimation spawn = RawAnimation.begin().thenPlay("spawn");
    protected final RawAnimation sleep = RawAnimation.begin().thenPlayAndHold("sleep");

    protected final AnimationController animationControllerInstantCast = new AnimationController(this, "instant_casting", 0, this::instantCastingPredicate);
    protected final AnimationController animationControllerLongCast = new AnimationController(this, "long_casting", 0, this::longCastingPredicate);
    protected final AnimationController animationControllerOtherCast = new AnimationController(this, "other_casting", 0, this::otherCastingPredicate);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(animationControllerInstantCast);
        controllerRegistrar.add(animationControllerLongCast);
        controllerRegistrar.add(animationControllerOtherCast);
        controllerRegistrar.add(new AnimationController(this, "idle", 0, this::idlePredicate));
        controllerRegistrar.add(new AnimationController(this, "sleep", 0, this::sleepPredicate));
        controllerRegistrar.add(new AnimationController<>(this, "interact_controller", state -> PlayState.STOP)
                .triggerableAnim("interact", interact));
        controllerRegistrar.add(new AnimationController<>(this, "spawn_controller", state -> PlayState.STOP)
                .triggerableAnim("spawn", spawn));
        controllerRegistrar.add(new AnimationController<>(this, "block_controller", state -> PlayState.STOP)
                .triggerableAnim("block", attack));
    }

    protected PlayState idlePredicate(AnimationState event) {
        if (isAnimating()) {
            return PlayState.STOP;
        }
        if (event.isMoving()) {
            event.getController().setAnimation(walk);
            return PlayState.CONTINUE;
        } else if (!event.isMoving()){
            event.getController().setAnimation(idle);
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    protected PlayState sleepPredicate(AnimationState event) {
        boolean shouldPlaySleepAnimation = getIsSitting() && isOnBed();

        if (shouldPlaySleepAnimation) {
            event.getController().setAnimation(sleep);
            return PlayState.CONTINUE;
        } else {
            return PlayState.STOP;
        }
    }

    public boolean isOnBed() {
        BlockPos petPos = blockPosition();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 0; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos checkPos = petPos.offset(x, y, z);

                    if (level().getBlockState(checkPos).getBlock() instanceof AbstractFamiliarBedBlock) {
                        BlockEntity be = level().getBlockEntity(checkPos);
                        if (be instanceof AbstractFamiliarBedBlockEntity petBed) {
                            return petBed.isPositionCorrectForSleeping(position());
                        }
                    }
                }
            }
        }
        return false;
    }

    protected PlayState instantCastingPredicate(AnimationState event) {
        if (cancelCastAnimation) {
            return PlayState.STOP;
        }

        var controller = event.getController();
        if (instantCastSpellType != SpellRegistry.none() &&
                controller.getAnimationState() == AnimationController.State.STOPPED) {
            setStartAnimationFromSpell(controller, instantCastSpellType);
            instantCastSpellType = SpellRegistry.none();
        }
        return PlayState.CONTINUE;
    }

    protected PlayState longCastingPredicate(AnimationState event) {
        var controller = event.getController();

        // Get current casting spell
        AbstractSpell castingSpell = null;
        if (isCasting()) {
            MagicData magicData = this.getMagicData();
            SpellData spellData = magicData.getCastingSpell();
            if (spellData != null) {
                castingSpell = spellData.getSpell();
            }
        }

        // Stop conditions
        if (cancelCastAnimation ||
                (controller.getAnimationState() == AnimationController.State.STOPPED &&
                        !(isCasting() && castingSpell != null && castingSpell.getCastType() == CastType.LONG))) {
            return PlayState.STOP;
        }

        // Start animation if casting and controller stopped
        if (isCasting() && controller.getAnimationState() == AnimationController.State.STOPPED) {
            if (castingSpell != null) {
                setStartAnimationFromSpell(controller, castingSpell);
            }
        }
        // Play finish animation if just finished a long cast
        else if (!isCasting() && lastCastSpellType.getCastType() == CastType.LONG) {
            setFinishAnimationFromSpell(controller, lastCastSpellType);
        }

        return PlayState.CONTINUE;
    }

    protected PlayState otherCastingPredicate(AnimationState event) {
        if (cancelCastAnimation) {
            return PlayState.STOP;
        }

        var controller = event.getController();

        // Get current casting spell
        AbstractSpell castingSpell = null;
        if (isCasting()) {
            MagicData magicData = this.getMagicData();
            SpellData spellData = magicData.getCastingSpell();
            if (spellData != null) {
                castingSpell = spellData.getSpell();
            }
        }

        // Start continuous cast animation if needed
        if (isCasting() && castingSpell != null &&
                controller.getAnimationState() == AnimationController.State.STOPPED) {
            if (castingSpell.getCastType() == CastType.CONTINUOUS) {
                setStartAnimationFromSpell(controller, castingSpell);
            }
            return PlayState.CONTINUE;
        }

        // Continue or stop based on casting state
        if (isCasting()) {
            return PlayState.CONTINUE;
        } else {
            return PlayState.STOP;
        }
    }

    protected void setFinishAnimationFromSpell(AnimationController controller, AbstractSpell spell) {
        controller.forceAnimationReset();
        lastCastSpellType = SpellRegistry.none();
    }

    // Handles spell animations
    protected void setStartAnimationFromSpell(AnimationController controller, AbstractSpell spell) {
        controller.forceAnimationReset();

        if (spell.getCastType() == CastType.CONTINUOUS){
            controller.setAnimation(continuousCast);
        } else if (spell == SpellRegistry.STOMP_SPELL.get()) {
            controller.setAnimation(stomp);
        } else if(spell.getCastTime(spell.getMinLevel()) > 15){
            controller.setAnimation(longCast);
        } else {
            controller.setAnimation(attack);
        }
        lastCastSpellType = spell;
        cancelCastAnimation = false;
        animatingLegs = false;
    }

    public boolean isAnimating() {
        boolean isSleeping = getIsSitting() && isOnBed();

        return isCasting()
                || (animationControllerLongCast.getAnimationState() != AnimationController.State.STOPPED)
                || (animationControllerInstantCast.getAnimationState() != AnimationController.State.STOPPED)
                || isSleeping;
    }

    @Override
    public boolean shouldAlwaysAnimateHead() {
        return false;
    }

    @Override
    public boolean shouldPointArmsWhileCasting() {
        return false;
    }

    @Override
    public boolean shouldBeExtraAnimated() {
        return true;
    }

    @Override
    public boolean shouldAlwaysAnimateLegs() {
        return !animatingLegs;
    }

    @Override
    public boolean bobBodyWhileWalking() {
        return true;
    }

    @Override
    public boolean shouldSheathSword() {
        return false;
    }
}
