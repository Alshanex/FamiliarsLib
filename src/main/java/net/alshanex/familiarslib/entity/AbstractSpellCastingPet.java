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
import io.redspace.ironsspellbooks.entity.mobs.goals.*;
import io.redspace.ironsspellbooks.spells.ender.TeleportSpell;
import io.redspace.ironsspellbooks.spells.fire.BurningDashSpell;
import io.redspace.ironsspellbooks.util.OwnerHelper;
import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.block.entity.AbstractFamiliarStorageBlockEntity;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.registry.AttachmentRegistry;
import net.alshanex.familiarslib.registry.FParticleRegistry;
import net.alshanex.familiarslib.util.CurioUtils;
import net.alshanex.familiarslib.util.CylinderParticleManager;
import net.alshanex.familiarslib.util.ModTags;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
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

public abstract class AbstractSpellCastingPet extends PathfinderMob implements GeoEntity, IMagicEntity {
    private static final EntityDataAccessor<Boolean> DATA_CANCEL_CAST = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Boolean> DATA_IS_SITTING;
    protected static final EntityDataAccessor<Boolean> DATA_IS_HOUSE;
    protected final MagicData playerMagicData = new MagicData(true);

    static {
        DATA_ID_OWNER_UUID = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.OPTIONAL_UUID);
        DATA_BLOCKING = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_ENRAGED = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.INT);
        DATA_ARMOR = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.INT);
        DATA_HEALTH = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.INT);
        DATA_IS_SITTING = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_IS_HOUSE = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_IMPOSTOR = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
        DATA_TOTEM = SynchedEntityData.defineId(AbstractSpellCastingPet.class, EntityDataSerializers.BOOLEAN);
    }

    private static final EntityDataAccessor<Optional<UUID>> DATA_ID_OWNER_UUID;
    private static final EntityDataAccessor<Boolean> DATA_BLOCKING;
    private static final EntityDataAccessor<Integer> DATA_ENRAGED;
    private static final EntityDataAccessor<Integer> DATA_ARMOR;
    private static final EntityDataAccessor<Integer> DATA_HEALTH;
    private static final EntityDataAccessor<Boolean> DATA_IMPOSTOR;
    private static final EntityDataAccessor<Boolean> DATA_TOTEM;

    protected LivingEntity cachedSummoner;

    protected @Nullable SpellData castingSpell;
    public boolean hasUsedSingleAttack;
    protected boolean recreateSpell;

    protected boolean movementDisabled = false;

    protected static final float DEFAULT_ORIGINAL_MIN_QUALITY = 0.5f;
    protected static final float DEFAULT_ORIGINAL_MAX_QUALITY = 0.7f;
    protected static final float DEFAULT_TRINKET_MIN_QUALITY = 0.7f;
    protected static final float DEFAULT_TRINKET_MAX_QUALITY = 0.9f;

    private boolean lastTrinketState = false;
    private WizardAttackGoal currentAttackGoal;
    private boolean pendingGoalUpdate = false;
    private boolean pendingTrinketState = false;

    private boolean hasAttemptedMigration = false;

    public BlockPos housePosition = null;

    protected AbstractSpellCastingPet(EntityType<? extends PathfinderMob> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
        playerMagicData.setSyncedData(new SyncedSpellData(this));
        this.lookControl = createLookControl();
    }

    protected void setRandomImpostor(){
        Random rand = new Random();

        if(rand.nextFloat() <= 0.1){
            setIsImpostor(true);
            if(this.getIsImpostor()){
                this.goalSelector.addGoal(3, new FamiliarGoals.StealItemsWhenNotWatchedGoal(this, 3.0D));
            }
        }
    }

    public boolean getHasUsedSingleAttack() {
        return hasUsedSingleAttack;
    }

    @Override
    public void setHasUsedSingleAttack(boolean hasUsedSingleAttack) {
        this.hasUsedSingleAttack = hasUsedSingleAttack;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity pEntity) {
        return super.getPassengerRidingPosition(pEntity);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        if (this.getVehicle() instanceof PathfinderMob pathfindermob) {
            pathfindermob.yBodyRot = this.yBodyRot;
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(6, new FamiliarGoals.TeleportToOwnerGoal(this, this::getSummoner, 20F));
        this.goalSelector.addGoal(7, new FamiliarGoals.MovementAwareFollowOwnerGoal(this, this::getSummoner, 1.2f, 10, 3, false, Float.MAX_VALUE));
        this.goalSelector.addGoal(8, new FamiliarGoals.MovementAwareLookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new FamiliarGoals.MovementAwareRandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new FamiliarGoals.FindAndUsePetBedGoal(this, 10.0));

        this.targetSelector.addGoal(1, new GenericOwnerHurtByTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(2, new GenericOwnerHurtTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(3, new GenericCopyOwnerTargetGoal(this, this::getSummoner));
        this.targetSelector.addGoal(4, (new GenericHurtByTargetGoal(this, (entity) -> entity == getSummoner())).setAlertOthers());
        this.targetSelector.addGoal(5, new HurtByTargetGoal(this));
    }

    protected abstract WizardAttackGoal createAttackGoal(float min, float max);

    protected float[] getOriginalQualityValues() {
        return new float[]{DEFAULT_ORIGINAL_MIN_QUALITY, DEFAULT_ORIGINAL_MAX_QUALITY};
    }

    protected float[] getTrinketQualityValues() {
        return new float[]{DEFAULT_TRINKET_MIN_QUALITY, DEFAULT_TRINKET_MAX_QUALITY};
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

    protected abstract Item getValidTrinket();

    @Override
    public boolean isAlliedTo(Entity pEntity) {
        return super.isAlliedTo(pEntity) || this.isAlliedHelper(pEntity);
    }

    private boolean isAlliedHelper(Entity entity) {
        var owner = getSummoner();
        if (owner == null) {
            return false;
        }
        if (entity instanceof IMagicSummon magicSummon) {
            var otherOwner = magicSummon.getSummoner();
            return otherOwner != null && (owner == otherOwner || otherOwner.isAlliedTo(otherOwner));
        } else if (entity instanceof OwnableEntity tamableAnimal) {
            var otherOwner = tamableAnimal.getOwner();
            return otherOwner != null && (owner == otherOwner || otherOwner.isAlliedTo(otherOwner));
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

    public void setEnragedStacks(Integer level){
        this.entityData.set(DATA_ENRAGED, level);
    }

    public Integer getEnragedStacks() {
        return this.entityData
                .get(DATA_ENRAGED);
    }

    public void setArmorStacks(Integer level){
        this.entityData.set(DATA_ARMOR, level);
    }

    public Integer getArmorStacks() {
        return this.entityData
                .get(DATA_ARMOR);
    }

    public void setHealthStacks(Integer level){
        this.entityData.set(DATA_HEALTH, level);
    }

    public Integer getHealthStacks() {
        return this.entityData
                .get(DATA_HEALTH);
    }

    public void setIsBlocking(Boolean level){
        this.entityData.set(DATA_BLOCKING, level);
    }

    public Boolean getIsBlocking() {
        return this.entityData
                .get(DATA_BLOCKING);
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

            FamiliarsLib.LOGGER.info("Familiar {} entered house mode at {}", getUUID(), housePos);
        } else {
            clearHouseGoals();
            FamiliarsLib.LOGGER.info("Familiar {} exited house mode", getUUID());
        }
    }

    private void addHouseGoals() {
        if (housePosition == null) return;

        this.goalSelector.addGoal(1, new FamiliarGoals.WanderAroundHouseGoal(this, housePosition, 15.0, 1.0));

        this.goalSelector.addGoal(2, new FamiliarGoals.CasualLookAtPlayerGoal(this, Player.class, 8.0F));

        this.goalSelector.addGoal(3, new FamiliarGoals.CasualRandomLookGoal(this));

        this.goalSelector.addGoal(8, new FamiliarGoals.StayNearHouseGoal(this, housePosition, 20.0));

        this.goalSelector.addGoal(9, new FloatGoal(this));
    }

    private void clearHouseGoals() {
        // Remover todos los goals relacionados con casa
        this.goalSelector.getAvailableGoals().removeIf(goal ->
                goal.getGoal() instanceof FamiliarGoals.WanderAroundHouseGoal ||
                        goal.getGoal() instanceof FamiliarGoals.StayNearHouseGoal ||
                        goal.getGoal() instanceof FamiliarGoals.CasualLookAtPlayerGoal ||
                        goal.getGoal() instanceof FamiliarGoals.CasualRandomLookGoal);
    }

    protected void clearMovementGoals(){
        movementDisabled = true;
        this.navigation.stop();
    }

    protected void restoreMovementGoals(){
        movementDisabled = false;
        this.navigation.recomputePath();
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (this.isInvulnerableTo(pSource)) {
            return false;
        } else {
            if (!this.level().isClientSide) {
                if (wasPlayingSleepAnimation) {
                    triggerAnim("interact_controller", "interact");
                    wasPlayingSleepAnimation = false;
                    bedRegenTimer = 0;
                }
                this.setSitting(false);
            }
            return super.hurt(pSource, pAmount);
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.getEntity() != null && source.getEntity().is(this.getSummoner())) {
            return true;
        } else if (source.getEntity() != null && source.getEntity() instanceof AbstractSpellCastingPet pet
                    && pet.getSummoner() != null && pet.getSummoner().is(this.getSummoner())){
            return true;
        } else if (getIsInHouse()){
            return true;
        }  else {
            return super.isInvulnerableTo(source);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder pBuilder) {
        super.defineSynchedData(pBuilder);
        pBuilder.define(DATA_ID_OWNER_UUID, Optional.empty());
        pBuilder.define(DATA_BLOCKING, false);
        pBuilder.define(DATA_ENRAGED, 0);
        pBuilder.define(DATA_ARMOR, 0);
        pBuilder.define(DATA_HEALTH, 0);
        pBuilder.define(DATA_CANCEL_CAST, false);
        pBuilder.define(DATA_IS_SITTING, false);
        pBuilder.define(DATA_IS_HOUSE, false);
        pBuilder.define(DATA_IMPOSTOR, false);
        pBuilder.define(DATA_TOTEM, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        super.onSyncedDataUpdated(pKey);

        if (!level().isClientSide) {
            return;
        }

        if (pKey.id() == DATA_CANCEL_CAST.id()) {
            cancelCast();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        playerMagicData.getSyncedData().saveNBTData(pCompound, level().registryAccess());
        pCompound.putBoolean("usedSpecial", hasUsedSingleAttack);
        //pCompound.putUUID("ownerUUID", this.getOwnerUUID());

        if (getOwnerUUID() != null) {
            pCompound.putUUID("ownerUUID", getOwnerUUID());
        } else {
            pCompound.putString("ownerUUID", "null");
        }

        pCompound.putInt("enragedStacks", getEnragedStacks());
        pCompound.putBoolean("isBlocking", getIsBlocking());
        pCompound.putInt("armorStacks", getArmorStacks());
        pCompound.putInt("healthStacks", getHealthStacks());

        pCompound.putBoolean("Sitting", getIsSitting());

        pCompound.putBoolean("isImpostor", getIsImpostor());

        pCompound.putBoolean("hasTotem", getTotem());

        pCompound.putInt("bedRegenTimer", bedRegenTimer);
        pCompound.putBoolean("wasPlayingSleepAnimation", wasPlayingSleepAnimation);

        pCompound.putBoolean("lastTrinketState", lastTrinketState);
        pCompound.putBoolean("pendingGoalUpdate", pendingGoalUpdate);
        pCompound.putBoolean("pendingTrinketState", pendingTrinketState);
        pCompound.putBoolean("hasAttemptedMigration", hasAttemptedMigration);

        pCompound.putBoolean("isInHouse", getIsInHouse());
        if (housePosition != null) {
            pCompound.putLong("housePosition", housePosition.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        var syncedSpellData = new SyncedSpellData(this);
        syncedSpellData.loadNBTData(pCompound, level().registryAccess());
        if (syncedSpellData.isCasting()) {
            this.recreateSpell = true;
        }
        playerMagicData.setSyncedData(syncedSpellData);
        hasUsedSingleAttack = pCompound.getBoolean("usedSpecial");

        if (pCompound.contains("ownerUUID")) {
            String ownerUUIDString = pCompound.getString("ownerUUID");
            if (!"null".equals(ownerUUIDString)) {
                setOwnerUUID(pCompound.getUUID("ownerUUID"));
            } else {
                setOwnerUUID(null);
            }
        }

        // IMPORTANTE: Cargar los valores de stacks ANTES de aplicar atributos
        if(pCompound.contains("enragedStacks")){
            setEnragedStacks(pCompound.getInt("enragedStacks"));
        }

        if(pCompound.contains("isBlocking")){
            setIsBlocking(pCompound.getBoolean("isBlocking"));
        }

        if(pCompound.contains("armorStacks")){
            setArmorStacks(pCompound.getInt("armorStacks"));
        }

        if(pCompound.contains("healthStacks")){
            setHealthStacks(pCompound.getInt("healthStacks"));
        }

        if (!level().isClientSide) {
            FamiliarAttributesHelper.applyAllConsumableAttributes(this);

            if (pCompound.contains("currentHealth")) {
                float savedHealth = pCompound.getFloat("currentHealth");
                float maxHealth = getMaxHealth();

                setHealth(Math.min(savedHealth, maxHealth));

                FamiliarsLib.LOGGER.info("Loaded familiar {}: health stacks={}, max health={}, current health={}",
                        getUUID(), getHealthStacks(), maxHealth, getHealth());
            }
        }

        if(pCompound.contains("Sitting")){
            setSitting(pCompound.getBoolean("Sitting"));
            if(pCompound.getBoolean("Sitting")){
                movementDisabled = true;
            } else {
                movementDisabled = false;
            }
        }

        if(pCompound.contains("isImpostor")){
            setIsImpostor(pCompound.getBoolean("isImpostor"));
        }

        if(pCompound.contains("hasTotem")){
            setTotem(pCompound.getBoolean("hasTotem"));
        }

        if (pCompound.contains("bedRegenTimer")) {
            bedRegenTimer = pCompound.getInt("bedRegenTimer");
        }
        if (pCompound.contains("wasPlayingSleepAnimation")) {
            wasPlayingSleepAnimation = pCompound.getBoolean("wasPlayingSleepAnimation");
        }

        lastTrinketState = pCompound.getBoolean("lastTrinketState");
        pendingGoalUpdate = pCompound.getBoolean("pendingGoalUpdate");
        pendingTrinketState = pCompound.getBoolean("pendingTrinketState");

        if (pCompound.contains("hasAttemptedMigration")) {
            hasAttemptedMigration = pCompound.getBoolean("hasAttemptedMigration");
        }

        if (pCompound.contains("isInHouse")) {
            boolean inHouse = pCompound.getBoolean("isInHouse");
            BlockPos housePos = null;
            if (pCompound.contains("housePosition")) {
                housePos = BlockPos.of(pCompound.getLong("housePosition"));
            }
            setIsInHouse(inHouse, housePos);
        }
    }

    public LivingEntity getSummoner() {
        return OwnerHelper.getAndCacheOwner(level(), cachedSummoner, getOwnerUUID());
    }

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

    protected LookControl createLookControl() {
        return new LookControl(this) {
            @Override
            protected boolean resetXRotOnTick() {
                return getTarget() == null;
            }
        };
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

    public MagicData getMagicData() {
        return playerMagicData;
    }

    private int bedRegenTimer = 0;
    private boolean wasPlayingSleepAnimation = false;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            this.noCulling = this.isAnimating();
        }

        if (!level().isClientSide && getIsInHouse()) {
            FamiliarHelper.handleHouseBehavior(this);
        }

        // Check if familiar should despawn (if retrieved from storage but not summoned by player)
        if (!level().isClientSide && !getIsInHouse() && getSummoner() == null && this.tickCount % 100 == 0) {
            // This familiar was retrieved from storage but doesn't have an owner summoner
            // It should despawn after a short time
            if (this.tickCount > 1200) { // 1 minute
                this.remove(RemovalReason.DISCARDED);
                FamiliarsLib.LOGGER.info("Despawning familiar {} - no longer in house and no summoner", getUUID());
            }
        }

        if (!level().isClientSide && !hasAttemptedMigration && getSummoner() != null) {
            // Asegurarse de que tenemos un ServerPlayer válido
            if (getSummoner() instanceof ServerPlayer serverPlayer) {
                FamiliarHelper.attemptLegacyMigration(serverPlayer, this);
                hasAttemptedMigration = true;
            } else {
                // Si no es un ServerPlayer, buscar el jugador en el servidor por UUID
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

        if (this.pendingGoalUpdate && !level.isClientSide) {
            if (this.currentAttackGoal == null || !this.currentAttackGoal.isActing()) {
                updateGoalSafely(this.pendingTrinketState);
            }
        }

        if (this.tickCount % 20 == 0 && !level.isClientSide) {
            updateAttackGoal();
        }

        if (this.tickCount % 600 == 0 && !level.isClientSide) {
            if (getSummoner() != null || getOwnerUUID() != null) {
                this.setPersistenceRequired();
            }
        }

        if(getSummoner() != null && getTarget() != null && getTarget() instanceof AbstractSpellCastingPet familiar && this.tickCount % 10 == 0){
            if(familiar.getSummoner() != null && familiar.getSummoner().is(getSummoner())){
                setTarget(null);
            }
        }

        if(getTarget() != null && getIsInHouse() && this.tickCount % 10 == 0){
            setTarget(null);
        }

        handleBedRegeneration();

        if (getSummoner() != null && this.tickCount % 10 == 0 && getHealth() > 0) {
            if (CurioUtils.isWearingFamiliarSpellbook(getSummoner())) {
                FamiliarAttributesHelper.applyAttributes(this);
            } else {
                FamiliarAttributesHelper.removeAttributes(this);
            }
        }

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

    private void handleBedRegeneration() {
        boolean isOnValidBed = FamiliarBedHelper.isOnCompatibleBed(this);
        boolean shouldPlaySleepAnimation = getIsSitting() && isOnValidBed;

        if (shouldPlaySleepAnimation) {
            if (!wasPlayingSleepAnimation) {
                if (!level().isClientSide) {
                    FamiliarsLib.LOGGER.debug("Pet " + this.getUUID() + " starting sleep animation on compatible bed");
                }
            }

            if (!level().isClientSide) {
                bedRegenTimer++;
                if (bedRegenTimer >= 20) { // Every second
                    if (getHealth() < getMaxHealth()) {
                        heal(1.0F);
                        FamiliarsLib.LOGGER.debug("Pet " + this.getUUID() + " healed to " + getHealth() + "/" + getMaxHealth());
                        CylinderParticleManager.spawnParticlesAtBlockPos(this.level(), this.position(), 1, FParticleRegistry.SLEEP_PARTICLE.get(), CylinderParticleManager.ParticleDirection.UPWARD, 0.1, 0, .8);
                    } else {
                        // Full health, stop sleeping
                        setSitting(false);
                        FamiliarsLib.LOGGER.debug("Pet " + this.getUUID() + " fully healed, stopping sleep");
                    }
                    bedRegenTimer = 0;
                }
            }
        } else {
            // Not on a valid bed or not sitting
            if (wasPlayingSleepAnimation) {
                if (!level().isClientSide) {
                    FamiliarsLib.LOGGER.debug("Pet " + this.getUUID() + " stopping sleep animation - not on valid bed or not sitting");
                }
            }
            if (bedRegenTimer > 0) {
                bedRegenTimer = 0;
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

    @Override
    public void checkDespawn() {
        // Si tiene dueño o está en casa, nunca despawnear
        if (getSummoner() != null || getOwnerUUID() != null || getIsInHouse()) {
            this.setPersistenceRequired();
            return;
        }

        Player nearestPlayer = this.level().getNearestPlayer(this, 64.0D);

        if (nearestPlayer != null) {
            this.noActionTime = 0;
            return;
        }

        if (this.tickCount > 24000) { // 20 min
            if (this.random.nextInt(800) == 0) {
                this.discard();
            }
        }
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

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (getIsInHouse() && housePosition != null) {
            if (!(level().getBlockEntity(housePosition) instanceof AbstractFamiliarStorageBlockEntity storageEntity)) {
                return InteractionResult.FAIL;
            }

            if (!storageEntity.isOwner((ServerPlayer) player)) {
                return InteractionResult.FAIL;
            }

            // Owner can still feed the familiar
            if (!level().isClientSide) {
                if (isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                    this.heal(2.0F * 2.0f);
                    itemstack.consume(1, player);
                    FamiliarHelper.spawnEatingParticles(this);
                    this.gameEvent(GameEvent.EAT);
                    return InteractionResult.sidedSuccess(this.level().isClientSide());
                }
            }
            return InteractionResult.PASS;
        }

        if(getSummoner() != null && getSummoner().is(player) && itemstack.is(Items.STICK)){
            this.setSitting(!getIsSitting());
            return InteractionResult.SUCCESS;
        }

        if (!this.level().isClientSide) {
            if(getOwnerUUID() != null){
                if(getOwnerUUID().equals(player.getUUID())){
                    if (getSummoner() instanceof ServerPlayer serverPlayer) {
                        FamiliarHelper.attemptLegacyMigration(serverPlayer, this);
                    }
                    if (!wasPlayingSleepAnimation) {
                        triggerAnim("interact_controller", "interact");
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
                        this.heal(2.0F * 2.0f);
                        itemstack.consume(1, player);
                        FamiliarHelper.spawnEatingParticles(this);
                        this.gameEvent(GameEvent.EAT);
                        return InteractionResult.sidedSuccess(this.level().isClientSide());
                    }  else if(isHunterPet() && itemstack != null && itemstack != ItemStack.EMPTY){
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
                if (itemstack.is(ModTags.FAMILIAR_TAMING)) {
                    itemstack.consume(1, player);
                    this.tryToTame(player);
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return super.mobInteract(player, hand);
    }

    private EntityType<?> getModEntityType(String modId, String entityName) {
        if (!FamiliarsLib.isModLoaded(modId)) {
            return null;
        }

        ResourceLocation entityLocation = ResourceLocation.fromNamespaceAndPath(modId, entityName);
        return BuiltInRegistries.ENTITY_TYPE.get(entityLocation);
    }

    protected boolean isHunterPet(){
        return false;
    }

    protected void clericSetGoal(ServerPlayer player){

    }

    protected boolean isFood(ItemStack item){
        return item.is(Items.APPLE);
    }

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
            this.navigation.stop();
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

    protected void triggerAdvancement(ServerPlayer player){

    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        triggerAnim("spawn_controller", "spawn");

        if (!level().isClientSide && (getHealthStacks() > 0 || getArmorStacks() > 0)) {
            FamiliarAttributesHelper.applyAllConsumableAttributes(this);
            FamiliarsLib.LOGGER.info("Applied attributes on level join for familiar {}: {} health stacks, {} armor stacks",
                    getUUID(), getHealthStacks(), getArmorStacks());
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!level.isClientSide && getSummoner() instanceof ServerPlayer serverPlayer) {
            FamiliarAttributesHelper.handleFamiliarDismissed(serverPlayer, this);
        }

        if(!level.isClientSide){
            MagicManager.spawnParticles(level(), ParticleTypes.POOF, getX(), getY(), getZ(), 25, .4, .8, .4, .03, false);
        }
    }

    public boolean getMovementDisabled(){
        return this.movementDisabled;
    }

    public void cancelCast() {
        if (isCasting()) {
            if (level().isClientSide) {
                cancelCastAnimation = true;
            } else {
                //Need to ensure we pass a different value if we want the data to sync
                entityData.set(DATA_CANCEL_CAST, !entityData.get(DATA_CANCEL_CAST));
            }

            castComplete();
        }

    }

    public void castComplete() {
        if (!level().isClientSide) {
            if (castingSpell != null) {
                castingSpell.getSpell().onServerCastComplete(level(), castingSpell.getLevel(), this, playerMagicData, false);
            }
        } else {
            playerMagicData.resetCastingState();
        }

        castingSpell = null;
    }

    public void setSyncedSpellData(SyncedSpellData syncedSpellData) {
        if (!level().isClientSide) {
            return;
        }

        var isCasting = playerMagicData.isCasting();
        playerMagicData.setSyncedData(syncedSpellData);
        castingSpell = playerMagicData.getCastingSpell();

        if (castingSpell == null) {
            return;
        }

        if (!playerMagicData.isCasting() && isCasting) {
            castComplete();
        } else if (playerMagicData.isCasting() && !isCasting)/* if (syncedSpellData.getCastingSpellType().getCastType() == CastType.CONTINUOUS)*/ {
            var spell = playerMagicData.getCastingSpell().getSpell();

            initiateCastSpell(spell, playerMagicData.getCastingSpellLevel());

            if (castingSpell.getSpell().getCastType() == CastType.INSTANT) {
                instantCastSpellType = castingSpell.getSpell();
                castingSpell.getSpell().onClientPreCast(level(), castingSpell.getLevel(), this, InteractionHand.MAIN_HAND, playerMagicData);
                castComplete();
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (recreateSpell) {
            recreateSpell = false;
            var syncedSpellData = playerMagicData.getSyncedData();
            setSyncedSpellData(syncedSpellData);
        }

        if (castingSpell == null) {
            return;
        }

        playerMagicData.handleCastDuration();

        if (playerMagicData.isCasting()) {
            castingSpell.getSpell().onServerCastTick(level(), castingSpell.getLevel(), this, playerMagicData);
        }

        this.forceLookAtTarget(getTarget());

        if (playerMagicData.getCastDurationRemaining() <= 0) {

            if (castingSpell.getSpell().getCastType() == CastType.LONG || castingSpell.getSpell().getCastType() == CastType.INSTANT) {
                castingSpell.getSpell().onCast(level(), castingSpell.getLevel(), this, CastSource.MOB, playerMagicData);
            }
            castComplete();
        } else if (castingSpell.getSpell().getCastType() == CastType.CONTINUOUS) {
            if ((playerMagicData.getCastDurationRemaining() + 1) % 10 == 0) {
                castingSpell.getSpell().onCast(level(), castingSpell.getLevel(), this, CastSource.MOB, playerMagicData);
            }
        }
    }

    public void initiateCastSpell(AbstractSpell spell, int spellLevel) {
        if (spell == SpellRegistry.none()) {
            castingSpell = null;
            return;
        }

        if (level().isClientSide) {
            cancelCastAnimation = false;
        }

        castingSpell = new SpellData(spell, spellLevel);

        if (getTarget() != null) {
            forceLookAtTarget(getTarget());
        }

        if (!level().isClientSide && !castingSpell.getSpell().checkPreCastConditions(level(), spellLevel, this, playerMagicData)) {
            castingSpell = null;
            return;
        }

        if (spell == SpellRegistry.TELEPORT_SPELL.get() || spell == SpellRegistry.FROST_STEP_SPELL.get()) {
            setTeleportLocationBehindTarget(10);
        } else if (spell == SpellRegistry.BLOOD_STEP_SPELL.get()) {
            setTeleportLocationBehindTarget(3);
        } else if (spell == SpellRegistry.BURNING_DASH_SPELL.get()) {
            setBurningDashDirectionData();
        }

        playerMagicData.initiateCast(castingSpell.getSpell(), castingSpell.getLevel(), castingSpell.getSpell().getEffectiveCastTime(castingSpell.getLevel(), this), CastSource.MOB, SpellSelectionManager.MAINHAND);

        if (!level().isClientSide) {
            castingSpell.getSpell().onServerPreCast(level(), castingSpell.getLevel(), this, playerMagicData);
        }
    }

    public void notifyDangerousProjectile(Projectile projectile) {
    }

    public boolean isCasting() {
        return playerMagicData.isCasting();
    }

    public boolean setTeleportLocationBehindTarget(int distance) {
        var target = getTarget();
        boolean valid = false;
        if (target != null) {
            var rotation = target.getLookAngle().normalize().scale(-distance);
            var pos = target.position();
            var teleportPos = rotation.add(pos);

            for (int i = 0; i < 24; i++) {
                Vec3 randomness = Utils.getRandomVec3(.15f * i).multiply(1, 0, 1);
                teleportPos = Utils.moveToRelativeGroundLevel(level(), target.position().subtract(new Vec3(0, 0, distance / (float) (i / 7 + 1)).yRot(-(target.getYRot() + i * 45) * Mth.DEG_TO_RAD)).add(randomness), 5);
                teleportPos = new Vec3(teleportPos.x, teleportPos.y + .1f, teleportPos.z);
                var reposBB = this.getBoundingBox().move(teleportPos.subtract(this.position()));
                if (!level().collidesWithSuffocatingBlock(this, reposBB.inflate(-.05f))) {
                    valid = true;
                    break;
                }

            }
            if (valid) {
                playerMagicData.setAdditionalCastData(new TeleportSpell.TeleportData(teleportPos));
            } else {
                playerMagicData.setAdditionalCastData(new TeleportSpell.TeleportData(this.position()));

            }
        } else {
            playerMagicData.setAdditionalCastData(new TeleportSpell.TeleportData(this.position()));
        }
        return valid;
    }

    public void setBurningDashDirectionData() {
        playerMagicData.setAdditionalCastData(new BurningDashSpell.BurningDashDirectionOverrideCastData());
    }

    protected void forceLookAtTarget(LivingEntity target) {
        if (target != null) {
            double d0 = target.getX() - this.getX();
            double d2 = target.getZ() - this.getZ();
            double d1 = target.getEyeY() - this.getEyeY();

            double d3 = Math.sqrt(d0 * d0 + d2 * d2);
            float f = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
            float f1 = (float) (-(Mth.atan2(d1, d3) * (double) (180F / (float) Math.PI)));
            this.setXRot(f1 % 360);
            this.setYRot(f % 360);
        }
    }

    /**
     * GeckoLib Animations
     **/
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected AbstractSpell lastCastSpellType = SpellRegistry.none();
    protected AbstractSpell instantCastSpellType = SpellRegistry.none();
    protected boolean cancelCastAnimation = false;
    protected boolean animatingLegs = false;

    protected final RawAnimation idle = RawAnimation.begin().thenLoop("idle");
    protected final RawAnimation walk = RawAnimation.begin().thenLoop("walk");
    protected final RawAnimation attack = RawAnimation.begin().thenPlay("skill");
    protected final RawAnimation longCast = RawAnimation.begin().thenPlay("long_cast");
    protected final RawAnimation interact = RawAnimation.begin().thenPlay("interact");
    protected final RawAnimation stomp = RawAnimation.begin().thenPlay("stomp");
    protected final RawAnimation spawn = RawAnimation.begin().thenPlay("spawn");
    protected final RawAnimation sleep = RawAnimation.begin().thenPlayAndHold("sleep");

    protected final AnimationController animationControllerInstantCast = new AnimationController(this, "instant_casting", 0, this::instantCastingPredicate);
    protected final AnimationController animationControllerLongCast = new AnimationController(this, "long_casting", 0, this::longCastingPredicate);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(animationControllerInstantCast);
        controllerRegistrar.add(animationControllerLongCast);
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
        boolean isOnValidBed = FamiliarBedHelper.isOnCompatibleBed(this);
        boolean shouldPlaySleepAnimation = getIsSitting() && isOnValidBed;

        if (shouldPlaySleepAnimation) {
            // Si debe reproducir la animación de sleep
            if (!wasPlayingSleepAnimation) {
                // Primera vez que entra en sleep - iniciar la animación
                wasPlayingSleepAnimation = true;
                event.getController().setAnimation(sleep);
                FamiliarsLib.LOGGER.debug("Pet " + this.getUUID() + " starting sleep animation");
                return PlayState.CONTINUE;
            } else {
                // Ya estaba reproduciendo sleep - continuar
                return PlayState.CONTINUE;
            }
        } else {
            // No debe reproducir la animación de sleep
            if (wasPlayingSleepAnimation) {
                // Estaba reproduciendo sleep pero ahora debe parar
                wasPlayingSleepAnimation = false;
                FamiliarsLib.LOGGER.debug("Pet " + this.getUUID() + " stopping sleep animation");
                return PlayState.STOP;
            } else {
                // No estaba reproduciendo sleep y no debe reproducirla
                return PlayState.STOP;
            }
        }
    }

    protected PlayState instantCastingPredicate(AnimationState event) {
        if (cancelCastAnimation) {
            return PlayState.STOP;
        }

        var controller = event.getController();
        if (instantCastSpellType != SpellRegistry.none() && controller.getAnimationState() == AnimationController.State.STOPPED) {
            setStartAnimationFromSpell(controller, instantCastSpellType, castingSpell.getLevel());
            instantCastSpellType = SpellRegistry.none();
        }
        return PlayState.CONTINUE;
    }

    protected PlayState longCastingPredicate(AnimationState event) {
        var controller = event.getController();

        if (cancelCastAnimation || (controller.getAnimationState() == AnimationController.State.STOPPED && !(isCasting() && castingSpell != null && castingSpell.getSpell().getCastType() == CastType.LONG))) {
            return PlayState.STOP;
        }

        if (isCasting()) {
            if (controller.getAnimationState() == AnimationController.State.STOPPED) {
                setStartAnimationFromSpell(controller, castingSpell.getSpell(), castingSpell.getLevel());
            }
        }

        return PlayState.CONTINUE;
    }

    protected void setStartAnimationFromSpell(AnimationController controller, AbstractSpell spell, int spellLevel) {
        spell.getCastStartAnimation().getForMob().ifPresentOrElse(animationBuilder -> {
            controller.forceAnimationReset();
            if(FamiliarAnimationUtils.isLongAnimCast(spell, spellLevel)){
                controller.setAnimation(longCast);
            } else if (spell == SpellRegistry.STOMP_SPELL.get()) {
                controller.setAnimation(stomp);
            } else {
                controller.setAnimation(attack);
            }
            lastCastSpellType = spell;
            cancelCastAnimation = false;
            animatingLegs = false;
        }, () -> {
            cancelCastAnimation = true;
        });
    }

    public boolean isAnimating() {
        return isCasting()
                || (animationControllerLongCast.getAnimationState() != AnimationController.State.STOPPED)
                || (animationControllerInstantCast.getAnimationState() != AnimationController.State.STOPPED)
                || wasPlayingSleepAnimation;
    }

    public boolean shouldAlwaysAnimateHead() {
        return false;
    }

    public boolean shouldPointArmsWhileCasting() {
        return false;
    }

    public boolean shouldBeExtraAnimated() {
        return true;
    }

    public boolean shouldAlwaysAnimateLegs() {
        return !animatingLegs;
    }

    public boolean bobBodyWhileWalking() {
        return true;
    }

    public boolean shouldSheathSword() {
        return false;
    }
}
