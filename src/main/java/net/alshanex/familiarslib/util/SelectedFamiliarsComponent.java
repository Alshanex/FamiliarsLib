package net.alshanex.familiarslib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record SelectedFamiliarsComponent(Set<UUID> selectedFamiliars) {

    public static final Codec<SelectedFamiliarsComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.list(Codec.STRING.xmap(UUID::fromString, UUID::toString))
                    .xmap(HashSet::new, list -> list.stream().toList())
                    .fieldOf("selected_familiars")
                    .forGetter(component -> new HashSet<>(component.selectedFamiliars()))
    ).apply(instance, SelectedFamiliarsComponent::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectedFamiliarsComponent> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(HashSet::new, ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString))
                            .map(set -> (Set<UUID>) set, set -> new HashSet<>(set)),
                    SelectedFamiliarsComponent::selectedFamiliars,
                    SelectedFamiliarsComponent::new
            );

    public SelectedFamiliarsComponent() {
        this(new HashSet<>());
    }

    public Set<UUID> getSelectedFamiliars() {
        return new HashSet<>(selectedFamiliars);
    }

    public SelectedFamiliarsComponent withFamiliar(UUID familiarId) {
        Set<UUID> newSet = new HashSet<>(selectedFamiliars);
        newSet.add(familiarId);
        return new SelectedFamiliarsComponent(newSet);
    }

    public SelectedFamiliarsComponent withoutFamiliar(UUID familiarId) {
        Set<UUID> newSet = new HashSet<>(selectedFamiliars);
        newSet.remove(familiarId);
        return new SelectedFamiliarsComponent(newSet);
    }

    public boolean contains(UUID familiarId) {
        return selectedFamiliars.contains(familiarId);
    }

    public int size() {
        return selectedFamiliars.size();
    }

    public boolean canAddMore() {
        return selectedFamiliars.size() < 10;
    }
}
