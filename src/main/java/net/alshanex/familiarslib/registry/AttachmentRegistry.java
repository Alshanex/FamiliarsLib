package net.alshanex.familiarslib.registry;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.PlayerFamiliarData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class AttachmentRegistry {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FamiliarsLib.MODID);

    public static final Supplier<AttachmentType<PlayerFamiliarData>> PLAYER_FAMILIAR_DATA =
            ATTACHMENT_TYPES.register("player_familiar_data", () ->
                    AttachmentType.serializable(PlayerFamiliarData::new).build());

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
