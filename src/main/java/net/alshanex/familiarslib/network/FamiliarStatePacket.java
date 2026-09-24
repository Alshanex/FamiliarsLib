package net.alshanex.familiarslib.network;

import net.alshanex.familiarslib.FamiliarsLib;
import net.alshanex.familiarslib.data.ClientFamiliarData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Small, frequent: IDs + selection/summon state + cap. No snapshots. */
public class FamiliarStatePacket implements CustomPacketPayload {
    public static final Type<FamiliarStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FamiliarsLib.MODID, "familiar_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FamiliarStatePacket> STREAM_CODEC =
            CustomPacketPayload.codec(FamiliarStatePacket::write, FamiliarStatePacket::new);

    private final List<UUID> order;
    @Nullable private final UUID selectedId;
    @Nullable private final UUID currentSummonedId;
    private final Set<UUID> summonedIds;
    private final int maxFamiliars;

    public FamiliarStatePacket(List<UUID> order, @Nullable UUID selectedId, @Nullable UUID currentSummonedId,
                               Set<UUID> summonedIds, int maxFamiliars) {
        this.order = order;
        this.selectedId = selectedId;
        this.currentSummonedId = currentSummonedId;
        this.summonedIds = summonedIds;
        this.maxFamiliars = maxFamiliars;
    }

    public FamiliarStatePacket(FriendlyByteBuf buf) {
        this.order = buf.readList(UUIDUtil.STREAM_CODEC);
        this.selectedId = buf.readNullable(UUIDUtil.STREAM_CODEC);
        this.currentSummonedId = buf.readNullable(UUIDUtil.STREAM_CODEC);
        this.summonedIds = buf.readCollection(HashSet::new, UUIDUtil.STREAM_CODEC);
        this.maxFamiliars = buf.readVarInt();
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeCollection(order, UUIDUtil.STREAM_CODEC);
        buf.writeNullable(selectedId, UUIDUtil.STREAM_CODEC);
        buf.writeNullable(currentSummonedId, UUIDUtil.STREAM_CODEC);
        buf.writeCollection(summonedIds, UUIDUtil.STREAM_CODEC);
        buf.writeVarInt(maxFamiliars);
    }

    public static void handle(FamiliarStatePacket p, IPayloadContext context) {
        context.enqueueWork(() -> ClientFamiliarData.get()
                .applyState(p.order, p.selectedId, p.currentSummonedId, p.summonedIds, p.maxFamiliars));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
