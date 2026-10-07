package de.kronwerke.core.portal;

import de.kronwerke.core.KronwerkeCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Tells the client what the portal does: charging, going, cancelled, arrived. */
public record TravelPayload(int phase, String label, int color) implements CustomPacketPayload {
    public static final int CHARGE = 0, GO = 1, CANCEL = 2, ARRIVE = 3;
    public static final Type<TravelPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "travel"));
    public static final StreamCodec<ByteBuf, TravelPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TravelPayload::phase,
            ByteBufCodecs.STRING_UTF8, TravelPayload::label,
            ByteBufCodecs.INT, TravelPayload::color,
            TravelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
