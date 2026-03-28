package net.alshanex.familiarslib.util.armor_sets;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public class ArmorSetDefinition {
    private final String name;
    private final Supplier<? extends Item> helmet;
    private final Supplier<? extends Item> chestplate;
    private final Supplier<? extends Item> leggings;
    private final Supplier<? extends Item> boots;

    public ArmorSetDefinition(String name, Supplier<? extends Item> helmet, Supplier<? extends Item> chestplate,
                              Supplier<? extends Item> leggings, Supplier<? extends Item> boots) {
        this.name = name;
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
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

    public String getName() {
        return name;
    }
}
