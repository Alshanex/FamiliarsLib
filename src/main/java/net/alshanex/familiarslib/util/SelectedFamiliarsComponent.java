package net.alshanex.familiarslib.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

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

    // No STREAM_CODEC needed - network sync handled via NBT in 1.20.1

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
