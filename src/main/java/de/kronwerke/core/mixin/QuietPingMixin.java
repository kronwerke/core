package de.kronwerke.core.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server list scanners open a status ping and drop the socket halfway. Vanilla then tries to
 * send a disconnect packet that the status protocol does not have and logs sixty lines of
 * stack trace for it. A connection that never got past the server list is just closed.
 */
@Mixin(Connection.class)
public abstract class QuietPingMixin {

    @Shadow
    public abstract PacketListener getPacketListener();

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void kronwerke$quietPing(ChannelHandlerContext ctx, Throwable ex, CallbackInfo ci) {
        PacketListener listener = getPacketListener();
        if (listener instanceof ServerStatusPacketListenerImpl || listener instanceof ServerHandshakePacketListenerImpl) {
            ctx.close();
            ci.cancel();
        }
    }
}
