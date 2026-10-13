package net.alshanex.familiarslib.item.camera;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.alshanex.familiarslib.entity.AbstractSpellCastingPet;
import net.alshanex.familiarslib.render.LayerColorHolder;
import net.alshanex.familiarslib.util.familiars.FamiliarCosmeticSlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Everything a photo remembers about a familiar: its type, its look (layer colors, hat, weapon),
 * its pose, and who/when/whose.
 */
public record FamiliarPhoto(ResourceLocation entityType, Appearance appearance, FamiliarPose pose, Info info) {

    public static final Codec<FamiliarPhoto> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(FamiliarPhoto::entityType),
            Appearance.CODEC.fieldOf("appearance").forGetter(FamiliarPhoto::appearance),
            FamiliarPose.CODEC.optionalFieldOf("pose", FamiliarPose.EMPTY).forGetter(FamiliarPhoto::pose),
            Info.CODEC.fieldOf("info").forGetter(FamiliarPhoto::info)
    ).apply(instance, FamiliarPhoto::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarPhoto> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FamiliarPhoto::entityType,
            Appearance.STREAM_CODEC, FamiliarPhoto::appearance,
            FamiliarPose.STREAM_CODEC, FamiliarPhoto::pose,
            Info.STREAM_CODEC, FamiliarPhoto::info,
            FamiliarPhoto::new);

    /** Server side: reads the look straight from the familiar, the pose comes from the photographer's client. */
    public static FamiliarPhoto capture(AbstractSpellCastingPet familiar, FamiliarPose pose, ServerPlayer photographer) {
        List<Integer> colors = new ArrayList<>(AbstractSpellCastingPet.LAYER_COLOR_SLOTS);
        for (int slot = 0; slot < AbstractSpellCastingPet.LAYER_COLOR_SLOTS; slot++) {
            colors.add(familiar.getLayerColor(slot));
        }

        Appearance appearance = new Appearance(colors,
                familiar.getCosmetic(FamiliarCosmeticSlot.HAT),
                familiar.getCosmetic(FamiliarCosmeticSlot.WEAPON));

        Info info = new Info(
                familiar.hasCustomName() ? Optional.of(familiar.getCustomName().getString()) : Optional.empty(),
                ownerName(familiar, photographer.getServer()),
                photographer.getGameProfile().getName(),
                photographer.level().getDayTime() / 24000L + 1L);

        return new FamiliarPhoto(BuiltInRegistries.ENTITY_TYPE.getKey(familiar.getType()), appearance, pose, info);
    }

    private static Optional<String> ownerName(AbstractSpellCastingPet familiar, MinecraftServer server) {
        UUID ownerId = familiar.getOwnerUUID();
        if (ownerId == null || server == null) {
            return Optional.empty();
        }
        ServerPlayer online = server.getPlayerList().getPlayer(ownerId);
        if (online != null) {
            return Optional.of(online.getGameProfile().getName());
        }
        GameProfileCache cache = server.getProfileCache();
        return cache == null ? Optional.empty() : cache.get(ownerId).map(GameProfile::getName);
    }

    public Optional<EntityType<?>> type() {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityType);
    }

    /** "Mage Familiar", or the raw id if the familiar's mod is gone. */
    public Component typeName() {
        return type().map(EntityType::getDescription).orElseGet(() -> Component.literal(entityType.toString()));
    }

    /** The custom name if it had one, otherwise the type name. */
    public Component displayName() {
        return info.customName().<Component>map(Component::literal).orElseGet(this::typeName);
    }

    /** Layer colors (one per slot, {@link LayerColorHolder#NO_LAYER_COLOR} = original look) and cosmetics. */
    public record Appearance(List<Integer> colors, ItemStack hat, ItemStack weapon) {
        public static final Codec<Appearance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.listOf().fieldOf("colors").forGetter(Appearance::colors),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("hat", ItemStack.EMPTY).forGetter(Appearance::hat),
                ItemStack.OPTIONAL_CODEC.optionalFieldOf("weapon", ItemStack.EMPTY).forGetter(Appearance::weapon)
        ).apply(instance, Appearance::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Appearance> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT.apply(ByteBufCodecs.list(16)), Appearance::colors,
                ItemStack.OPTIONAL_STREAM_CODEC, Appearance::hat,
                ItemStack.OPTIONAL_STREAM_CODEC, Appearance::weapon,
                Appearance::new);

        public Appearance {
            colors = List.copyOf(colors);
            hat = hat.isEmpty() ? ItemStack.EMPTY : hat.copyWithCount(1);
            weapon = weapon.isEmpty() ? ItemStack.EMPTY : weapon.copyWithCount(1);
        }

        public int color(int slot) {
            return slot >= 0 && slot < colors.size() ? colors.get(slot) : LayerColorHolder.NO_LAYER_COLOR;
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Appearance that
                    && colors.equals(that.colors)
                    && ItemStack.matches(hat, that.hat)
                    && ItemStack.matches(weapon, that.weapon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(colors, ItemStack.hashItemAndComponents(hat), ItemStack.hashItemAndComponents(weapon));
        }
    }

    /** Custom name and owner at the time of the photo, who took it, and on which in-game day. */
    public record Info(Optional<String> customName, Optional<String> ownerName, String photographer, long day) {
        public static final Codec<Info> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("custom_name").forGetter(Info::customName),
                Codec.STRING.optionalFieldOf("owner").forGetter(Info::ownerName),
                Codec.STRING.fieldOf("photographer").forGetter(Info::photographer),
                Codec.LONG.fieldOf("day").forGetter(Info::day)
        ).apply(instance, Info::new));

        public static final StreamCodec<ByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), Info::customName,
                ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), Info::ownerName,
                ByteBufCodecs.STRING_UTF8, Info::photographer,
                ByteBufCodecs.VAR_LONG, Info::day,
                Info::new);
    }
}