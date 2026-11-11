package de.kronwerke.core.camera;

import de.kronwerke.core.KronwerkeCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to camera: take a picture now, of what the camera sees, at this size and field of view. */
public record ShotPayload(int id, int width, int height, int fov, int settleTicks) implements CustomPacketPayload {
    public static final Type<ShotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(KronwerkeCore.MOD_ID, "camera_shot"));
    public static final StreamCodec<ByteBuf, ShotPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShotPayload::id,
            ByteBufCodecs.VAR_INT, ShotPayload::width,
            ByteBufCodecs.VAR_INT, ShotPayload::height,
            ByteBufCodecs.VAR_INT, ShotPayload::fov,
            ByteBufCodecs.VAR_INT, ShotPayload::settleTicks,
            ShotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
