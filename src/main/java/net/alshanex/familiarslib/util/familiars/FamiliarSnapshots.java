package net.alshanex.familiarslib.util.familiars;

import net.minecraft.nbt.CompoundTag;

public final class FamiliarSnapshots {
    // World-state tags that are reset/overwritten when the familiar is summoned again
    private static final String[] VOLATILE_KEYS = {
            "Motion", "FallDistance", "Fire", "Air", "OnGround", "PortalCooldown",
            "HurtTime", "HurtByTimestamp", "DeathTime", "FallFlying", "TicksFrozen",
            "HasVisualFire", "Leash", "Passengers", "SleepingX", "SleepingY", "SleepingZ"
    };

    public static CompoundTag trim(CompoundTag nbt) {
        for (String key : VOLATILE_KEYS) {
            nbt.remove(key);
        }
        return nbt;
    }

    private FamiliarSnapshots() {}
}
