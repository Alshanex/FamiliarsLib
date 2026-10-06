package net.alshanex.familiarslib.compendium;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A hand-written entry of the compendium's "Abilities" tab: something the familiar does that isn't a spell
 * (spells are listed separately, as icons, from {@code getCompendiumSpells}).
 */
public record CompendiumAbility(Component name, Component description, @Nullable ResourceLocation icon) {

    public static CompendiumAbility of(Component name, Component description) {
        return new CompendiumAbility(name, description, null);
    }

    public static CompendiumAbility of(Component name, Component description, ResourceLocation icon) {
        return new CompendiumAbility(name, description, icon);
    }
}