package de.kronwerke.core.queue;

import de.kronwerke.core.KronwerkeCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Where a joining player stands in the queue, sent while the connection is still in the
 * configuration phase: position 1 is next, 0 means it is their turn now.
 */
public record QueuePayload(int position, int waiting, int online, int limit, int etaSeconds) implements CustomPacketPayload {
    public static final Type<QueuePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "queue"));
    public static final StreamCodec<ByteBuf, QueuePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QueuePayload::position,
            ByteBufCodecs.VAR_INT, QueuePayload::waiting,
            ByteBufCodecs.VAR_INT, QueuePayload::online,
            ByteBufCodecs.VAR_INT, QueuePayload::limit,
            ByteBufCodecs.VAR_INT, QueuePayload::etaSeconds,
            QueuePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
