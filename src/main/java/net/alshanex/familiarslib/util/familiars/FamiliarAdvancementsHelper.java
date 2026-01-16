package net.alshanex.familiarslib.util.familiars;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class FamiliarAdvancementsHelper {
    public static boolean hasCompletedTamingEvents(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            Advancement advancement = serverPlayer.getServer().getAdvancements().getAdvancement(new ResourceLocation("alshanex_familiars", "alshanex_familiars/anthropologist"));

            if (advancement != null) {
                AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
                return progress.isDone();
            }
            return false;
        }
        return false;
    }

    public static boolean hasRevivalAdvancement(ServerPlayer serverPlayer) {
        Advancement advancement = serverPlayer.getServer().getAdvancements().getAdvancement(new ResourceLocation("alshanex_familiars", "alshanex_familiars/learn_necromancy"));

        if (advancement != null) {
            AdvancementProgress progress = serverPlayer.getAdvancements().getOrStartProgress(advancement);
            return progress.isDone();
        }
        return false;
    }
}
