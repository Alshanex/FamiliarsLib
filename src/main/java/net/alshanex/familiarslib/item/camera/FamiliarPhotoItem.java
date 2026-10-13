package net.alshanex.familiarslib.item.camera;

import net.alshanex.familiarslib.client.compendium.ClientCompendium;
import net.alshanex.familiarslib.compendium.CompendiumCatalog;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.registry.ComponentRegistry;
import net.alshanex.familiarslib.render.LayerColorHolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A printed photo of a familiar.
 */
public class FamiliarPhotoItem extends Item {

    public FamiliarPhotoItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    /** "Photo of Mage Familiar" / "Photo of Fluffy". */
    @Override
    public Component getName(ItemStack stack) {
        FamiliarPhoto photo = stack.get(ComponentRegistry.FAMILIAR_PHOTO.get());
        return photo == null ? super.getName(stack)
                : Component.translatable("item.familiarslib.familiar_photo.of", photo.displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FamiliarPhoto photo = stack.get(ComponentRegistry.FAMILIAR_PHOTO.get());
        if (photo == null) {
            tooltip.add(Component.translatable("tooltip.familiarslib.photo.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        FamiliarPhoto.Info info = photo.info();

        // Who is in it
        if (info.customName().isPresent()) {
            tooltip.add(Component.translatable("tooltip.familiarslib.photo.familiar", photo.typeName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        info.ownerName().ifPresent(owner -> tooltip.add(
                label("tooltip.familiarslib.photo.owner").append(Component.literal(owner).withStyle(ChatFormatting.WHITE))));

        // Colors
        addColorLines(photo, tooltip);

        // Cosmetics
        FamiliarPhoto.Appearance look = photo.appearance();
        if (!look.hat().isEmpty()) {
            tooltip.add(label("tooltip.familiarslib.photo.hat")
                    .append(look.hat().getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        }
        if (!look.weapon().isEmpty()) {
            tooltip.add(label("tooltip.familiarslib.photo.weapon")
                    .append(look.weapon().getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        }

        // Pose
        FamiliarPose pose = photo.pose();
        Component poseName = pose.sitting()
                ? Component.translatable("tooltip.familiarslib.photo.pose.sitting")
                : pose.mainAnimation().<Component>map(name -> Component.literal(prettify(name)))
                .orElse(Component.translatable("tooltip.familiarslib.photo.pose.unknown"));
        tooltip.add(label("tooltip.familiarslib.photo.pose").append(poseName.copy().withStyle(ChatFormatting.WHITE)));

        if (Screen.hasShiftDown()) {
            addPoseDetails(pose, tooltip);
        }

        // Credits
        tooltip.add(Component.translatable("tooltip.familiarslib.photo.taken_by", info.photographer(), info.day())
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (!Screen.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.familiarslib.photo.shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * One line per layer. Layers the compendium knows about are always listed (with "Original" when untinted),
     * in the compendium's order; tinted layers it doesn't know get "Layer N".
     */
    private static void addColorLines(FamiliarPhoto photo, List<Component> tooltip) {
        Map<Integer, Component> layers = new LinkedHashMap<>();
        for (CompendiumCatalog.Section section : ClientCompendium.catalog().sections()) {
            if (section.entityType().equals(photo.entityType())) {
                for (CompendiumCatalog.LayerOptions layer : section.layers()) {
                    layers.put(layer.slot(), Component.translatable(layer.nameKey()));
                }
            }
        }
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            if (photo.appearance().color(slot) != LayerColorHolder.NO_LAYER_COLOR) {
                layers.putIfAbsent(slot, Component.translatable("item.familiarslib.identity_vessel.layer", slot + 1));
            }
        }
        if (layers.isEmpty()) {
            return;
        }

        tooltip.add(Component.translatable("tooltip.familiarslib.photo.colors").withStyle(ChatFormatting.GRAY));
        layers.forEach((slot, name) -> {
            int rgb = photo.appearance().color(slot);
            MutableComponent line = Component.literal("  ").append(name).append(": ").withStyle(ChatFormatting.GRAY);
            if (rgb == LayerColorHolder.NO_LAYER_COLOR) {
                line.append(Component.translatable("tooltip.familiarslib.photo.original").withStyle(ChatFormatting.WHITE));
            } else {
                line.append(Component.literal("■ ").withStyle(style -> style.withColor(TextColor.fromRgb(rgb))))
                        .append(Component.literal(String.format("#%06X", rgb)).withStyle(ChatFormatting.WHITE));
            }
            tooltip.add(line);
        });
    }

    private static void addPoseDetails(FamiliarPose pose, List<Component> tooltip) {
        for (String animation : pose.animations()) {
            tooltip.add(Component.literal("  " + animation).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.familiarslib.photo.pose_details",
                        pose.bones().size(), Math.round(pose.headYaw()), Math.round(pose.headPitch()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent label(String key) {
        return Component.translatable(key).append(": ").withStyle(ChatFormatting.GRAY);
    }

    /** "idle_sit" / "animation.mage.idle" -> "Idle Sit" / "Idle". */
    private static String prettify(String animation) {
        String name = animation.contains(".") ? animation.substring(animation.lastIndexOf('.') + 1) : animation;
        StringBuilder out = new StringBuilder();
        for (String word : name.split("[_\\-\\s]+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? animation : out.toString();
    }
}