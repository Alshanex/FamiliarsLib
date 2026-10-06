package net.alshanex.familiarslib.client.compendium;

import net.minecraft.client.Minecraft;

/** Client-only entry point, called from common code only when running on the client. */
public final class CompendiumClientAccess {
    public static void openScreen() {
        Minecraft.getInstance().setScreen(new CompendiumScreen());
    }

    private CompendiumClientAccess() {}
}
