package de.kronwerke.core.mixin;

import de.kronwerke.core.spawn.ArrivalGrace;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The answer to the ping an arrival gets: their client is through loading, see {@link ArrivalGrace}. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ArrivalPongMixin {

    @Shadow @Final protected MinecraftServer server;

    @Inject(method = "handlePong", at = @At("HEAD"))
    private void kronwerke$pong(ServerboundPongPacket packet, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl game)) return;
        int id = packet.getId();
        this.server.execute(() -> ArrivalGrace.pong(game.player, id));
    }
}
