package net.alshanex.familiarslib.setup;

import net.alshanex.familiarslib.item.FamiliarEggItem;
import net.alshanex.familiarslib.util.familiars.FamiliarEggColors;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;

/** Client only: one typed Familiar Egg per familiar in the game (this mod's and other mods'), for the creative tab. */
public final class FamiliarEggCreative {
    private FamiliarEggCreative() {}

    public static void addTypedEggs(CreativeModeTab.Output output) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return; // tabs are rebuilt in a world, where the familiars can be checked
        }
        for (EntityType<?> type : FamiliarEggColors.familiarTypes(level)) {
            output.accept(FamiliarEggItem.typedEgg(type));
        }
    }
}