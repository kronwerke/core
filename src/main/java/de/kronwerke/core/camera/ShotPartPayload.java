package de.kronwerke.core.camera;

import de.kronwerke.core.KronwerkeCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Camera to server: one piece of the PNG (a serverbound payload holds at most 32 KiB). */
public record ShotPartPayload(int id, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final int MAX = 30_000;
    public static final Type<ShotPartPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "camera_part"));
    public static final StreamCodec<ByteBuf, ShotPartPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShotPartPayload::id,
            ByteBufCodecs.VAR_INT, ShotPartPayload::index,
            ByteBufCodecs.VAR_INT, ShotPartPayload::total,
            ByteBufCodecs.byteArray(MAX), ShotPartPayload::data,
            ShotPartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
