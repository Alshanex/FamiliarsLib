package net.alshanex.familiarslib.util.armor_sets;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public class ArmorSetDefinition {
    private final String name;
    private final Supplier<? extends Item> helmet;
    private final Supplier<? extends Item> chestplate;
    private final Supplier<? extends Item> leggings;
    private final Supplier<? extends Item> boots;
    private final MutableComponent setTooltip;

    public ArmorSetDefinition(String name, Supplier<? extends Item> helmet, Supplier<? extends Item> chestplate,
                              Supplier<? extends Item> leggings, Supplier<? extends Item> boots,
                              MutableComponent setTooltip) {
        this.name = name;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.setTooltip = setTooltip;
    }

    public ArmorSetDefinition(String name, Supplier<? extends Item> helmet, Supplier<? extends Item> chestplate,
                              Supplier<? extends Item> leggings, Supplier<? extends Item> boots) {
        this(name, helmet, chestplate, leggings, boots, null);
    }

    public boolean isFullSetEquipped(Player player) {
        ItemStack headSlot = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chestSlot = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legsSlot = player.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feetSlot = player.getItemBySlot(EquipmentSlot.FEET);

        return !headSlot.isEmpty() && headSlot.getItem() == helmet.get()
                && !chestSlot.isEmpty() && chestSlot.getItem() == chestplate.get()
                && !legsSlot.isEmpty() && legsSlot.getItem() == leggings.get()
                && !feetSlot.isEmpty() && feetSlot.getItem() == boots.get();
    }

    public boolean isPartOfSet(Item item) {
        return item == helmet.get() || item == chestplate.get()
                || item == leggings.get() || item == boots.get();
    }

    public void addSetTooltip(Player player, List<Component> tooltipComponents) {
        if (setTooltip == null) return;

        if (isFullSetEquipped(player)) {
            tooltipComponents.add(setTooltip.copy().withStyle(ChatFormatting.YELLOW));
        } else {
            tooltipComponents.add(setTooltip.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    public String getName() {
        return name;
    }

    public MutableComponent getSetTooltip() {
        return setTooltip;
    }
}
