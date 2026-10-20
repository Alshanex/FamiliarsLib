package net.alshanex.familiarslib.item;

import net.alshanex.familiarslib.block.entity.AbstractFamiliarBedBlockEntity;
import net.alshanex.familiarslib.data.BoundFamiliar;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.registry.FItemRegistry;
import net.alshanex.familiarslib.util.familiars.EggColor;
import net.alshanex.familiarslib.util.familiars.FamiliarEggColors;
import net.alshanex.familiarslib.util.familiars.FamiliarRecall;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Familiar Egg. Empty: right-click to pick a released or fallen familiar to bind to it (it can't be changed afterwards).
 * Bound: right-click a free Familiar Bed to place it there and let it hatch.
 */
public class FamiliarEggItem extends Item {

    public FamiliarEggItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
    }

    public static boolean isBound(ItemStack stack) {
        return stack.has(ComponentRegistry.BOUND_FAMILIAR);
    }

    /** The familiar type of a typed egg, if it is one. */
    public static Optional<EntityType<?>> familiarType(ItemStack stack) {
        ResourceLocation id = stack.get(ComponentRegistry.FAMILIAR_TYPE);
        return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
    }

    /** An egg that hatches a new familiar of this type. */
    public static ItemStack typedEgg(EntityType<?> type) {
        ItemStack stack = new ItemStack(FItemRegistry.FAMILIAR_EGG.get());
        stack.set(ComponentRegistry.FAMILIAR_TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(type));
        return stack;
    }

    /**
     * The egg that comes back when an egg leaves a bed unhatched. A new familiar's egg goes back to being a
     * plain typed egg, so whoever places it next becomes the owner.
     */
    public static ItemStack boundEgg(BoundFamiliar familiar) {
        if (familiar.fresh()) {
            ResourceLocation type = familiar.type();
            Optional<EntityType<?>> entityType = type == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(type);
            if (entityType.isPresent()) {
                return typedEgg(entityType.get());
            }
        }
        ItemStack stack = new ItemStack(FItemRegistry.FAMILIAR_EGG.get());
        stack.set(ComponentRegistry.BOUND_FAMILIAR, familiar);
        return stack;
    }

    /**
     * Tint of this egg (0xRRGGBB): the bound familiar's color, the default color of a typed egg's familiar,
     * or the original beige for an empty egg. {@code level} is needed to work out a type's color the first time.
     */
    public static int eggColor(ItemStack stack, @Nullable Level level) {
        BoundFamiliar bound = stack.get(ComponentRegistry.BOUND_FAMILIAR);
        if (bound != null) {
            return bound.color();
        }
        return familiarType(stack).map(type -> FamiliarEggColors.defaultColor(type, level)).orElse(EggColor.DEFAULT_RGB);
    }

    // Use

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isBound(stack) || familiarType(stack).isPresent()) {
            if (player instanceof ServerPlayer serverPlayer) {
                actionBar(serverPlayer, Component.translatable("message.familiarslib.familiar_egg.place_on_bed").withStyle(ChatFormatting.YELLOW));
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            FamiliarRecall.openRecallList(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        Player player = context.getPlayer();
        boolean bound = isBound(stack);
        Optional<EntityType<?>> type = familiarType(stack);

        if ((!bound && type.isEmpty()) || player == null
                || !(level.getBlockEntity(context.getClickedPos()) instanceof AbstractFamiliarBedBlockEntity bed)) {
            return InteractionResult.PASS; // empty eggs open the selection screen through use()
        }

        if (bed.isBedTaken()) {
            if (player instanceof ServerPlayer serverPlayer) {
                actionBar(serverPlayer, Component.translatable("message.familiarslib.familiar_egg.bed_taken").withStyle(ChatFormatting.RED));
            }
            return InteractionResult.FAIL;
        }

        if (!level.isClientSide) {
            BoundFamiliar egg = bound
                    ? stack.get(ComponentRegistry.BOUND_FAMILIAR)
                    // A typed egg hatches for whoever places it
                    : BoundFamiliar.fresh(BuiltInRegistries.ENTITY_TYPE.getKey(type.get()), player.getUUID(),
                    FamiliarEggColors.defaultColor(type.get(), level));
            bed.placeEgg(egg, level.getGameTime());
            stack.shrink(1);
            level.playSound(null, context.getClickedPos(), SoundEvents.SNIFFER_EGG_PLOP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // Display

    @Override
    public Component getName(ItemStack stack) {
        Optional<EntityType<?>> type = familiarType(stack);
        if (type.isPresent()) {
            return Component.translatable("item.familiarslib.familiar_egg.typed", type.get().getDescription());
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BoundFamiliar bound = stack.get(ComponentRegistry.BOUND_FAMILIAR);
        if (bound != null) {
            tooltip.add(Component.translatable("item.familiarslib.familiar_egg.holds", FamiliarRecall.familiarName(bound.data()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            tooltip.add(Component.translatable("item.familiarslib.familiar_egg.usage_bound").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Optional<EntityType<?>> type = familiarType(stack);
        if (type.isPresent()) {
            tooltip.add(Component.translatable("item.familiarslib.familiar_egg.typed_desc", type.get().getDescription())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.familiarslib.familiar_egg.usage_bound").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.familiarslib.familiar_egg.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.familiarslib.familiar_egg.usage").withStyle(ChatFormatting.DARK_GRAY));
    }

    // A bound egg is the familiar itself: keep it from despawning or being destroyed on the ground

    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return isBound(stack) ? Integer.MAX_VALUE : super.getEntityLifespan(stack, level);
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        if (isBound(stack)) {
            return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY); // only the void
        }
        return super.canBeHurtBy(stack, source);
    }

    private static void actionBar(ServerPlayer player, Component message) {
        player.connection.send(new ClientboundSetActionBarTextPacket(message));
    }
}