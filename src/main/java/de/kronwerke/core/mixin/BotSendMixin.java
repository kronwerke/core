package de.kronwerke.core.mixin;

import de.kronwerke.core.loadtest.BotConnection;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A load test bot has no client: whatever the server sends it is dropped before NeoForge
 * checks the payload against channels the bot never negotiated. The server still builds
 * every packet, which is part of the load being measured.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class BotSendMixin {

    @Shadow
    @Final
    protected Connection connection;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), cancellable = true)
    private void kronwerke$dropForBots(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (connection instanceof BotConnection) ci.cancel();
    }
}
