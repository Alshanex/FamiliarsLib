package net.alshanex.familiarslib;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class FamiliarsServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_FAMILIARS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_FAMILIARS = builder
                .comment("Maximum number of familiars a player can carry (familiars stored in houses don't count).")
                .defineInRange("maxFamiliars", 10, 1, 50);
        SPEC = builder.build();
    }

    private FamiliarsServerConfig() {}
}
