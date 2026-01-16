package net.alshanex.familiarslib.capabilities;

import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.alshanex.familiarslib.registry.CapabilityRegistry;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class PlayerFamiliarProvider implements ICapabilitySerializable<CompoundTag> {
    private final PlayerFamiliarData data = new PlayerFamiliarData();
    private final LazyOptional<PlayerFamiliarData> optional = LazyOptional.of(() -> data);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityRegistry.PLAYER_FAMILIAR_DATA) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        data.deserializeNBT(nbt);
    }

    public void invalidate() {
        optional.invalidate();
    }
}
