package net.alshanex.familiarslib.item.camera;

import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.registry.FItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.List;

/**
 * Right click: zoom like a spyglass. Left click (zoomed or not): photograph the familiar closest to
 * the crosshair. Each photo uses one paper from the inventory (free in creative).
 */
public class FamiliarCameraItem extends Item {
    public static final int USE_DURATION = 1200;
    public static final int COOLDOWN_TICKS = 20;

    public FamiliarCameraItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    // Zoom (same behaviour as the spyglass)

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    /** Left click takes photos, it never breaks blocks. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.familiarslib.familiar_camera.usage_zoom").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.familiarslib.familiar_camera.usage_shoot").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.familiarslib.familiar_camera.usage_paper").withStyle(ChatFormatting.DARK_GRAY));
    }

    // Helpers

    public static boolean isCamera(ItemStack stack) {
        return stack.getItem() instanceof FamiliarCameraItem;
    }

    /** The camera the player is shooting with: the one being zoomed with, else the one in the main hand. */
    public static ItemStack activeCamera(Player player) {
        if (player.isUsingItem() && isCamera(player.getUseItem())) {
            return player.getUseItem();
        }
        ItemStack main = player.getMainHandItem();
        return isCamera(main) ? main : ItemStack.EMPTY;
    }

    public static boolean isPhotoPaper(ItemStack stack) {
        return stack.is(Items.PAPER);
    }

    /** Paper left for photos, or -1 for unlimited (creative). */
    public static int paperCount(Player player) {
        if (player.getAbilities().instabuild) {
            return -1;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (isPhotoPaper(stack)) count += stack.getCount();
        }
        return count;
    }

    private static boolean consumePaper(Player player) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isPhotoPaper(stack)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // Server side

    public static void takePhoto(ServerPlayer player, int entityId, Vector3f aimOffset, FamiliarPose pose) {
        ItemStack camera = activeCamera(player);
        if (camera.isEmpty() || player.getCooldowns().isOnCooldown(camera.getItem())) {
            return;
        }

        if (!(player.serverLevel().getEntity(entityId) instanceof AbstractSpellCastingPet familiar)
                || !CameraTargeting.isValidShot(player, familiar, CameraTargeting.seenBox(familiar, aimOffset))) {
            player.displayClientMessage(Component.translatable("message.familiarslib.camera.no_subject"), true);
            return;
        }

        if (!consumePaper(player)) {
            player.displayClientMessage(Component.translatable("message.familiarslib.camera.no_paper"), true);
            return;
        }

        ItemStack photo = new ItemStack(FItemRegistry.FAMILIAR_PHOTO.get());
        photo.set(ComponentRegistry.FAMILIAR_PHOTO.get(), FamiliarPhoto.capture(familiar, pose.sanitized(), player));
        if (!player.getInventory().add(photo)) {
            player.drop(photo, false);
        }

        player.getCooldowns().addCooldown(camera.getItem(), COOLDOWN_TICKS);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.3F);
    }
}
