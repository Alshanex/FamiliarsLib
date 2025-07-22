package net.alshanex.familiarslib.util.familiars;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Helper to check the acquisition of important advancements
 */
public class FamiliarAdvancementsHelper {
    public static boolean hasCompletedTamingEvents(Player player){
        if(player instanceof ServerPlayer serverPlayer){
            AdvancementHolder advancement = serverPlayer.getServer().getAdvancements().get(new ResourceLocation("alshanex_familiars", "alshanex_familiars/anthropologist"));

            if (advancement != null) {
                AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);

                return progress.isDone();
            }
            return false;
        }
        return false;
    }

    public static boolean hasRevivalAdvancement(ServerPlayer serverPlayer){
        AdvancementHolder advancement = serverPlayer.getServer().getAdvancements().get(new ResourceLocation("alshanex_familiars", "alshanex_familiars/learn_necromancy"));

        if (advancement != null) {
            AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);

            return progress.isDone();
        }
        return false;
    }
}
