package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import de.kronwerke.core.queue.JoinQueue;
import net.minecraft.server.MinecraftServer;

/**
 * The server list shows the network: everybody on main and the side worlds out of the queue's
 * limit, not main's players out of vanilla's max-players.
 */
@Mixin(MinecraftServer.class)
public abstract class ServerStatusQueueMixin {

    @WrapOperation(method = "buildPlayerStatus", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getMaxPlayers()I"))
    private int kronwerke$limit(MinecraftServer self, Operation<Integer> original) {
        return JoinQueue.on() ? JoinQueue.limit() : original.call(self);
    }

    @ModifyArg(method = "buildPlayerStatus", require = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/status/ServerStatus$Players;<init>(IILjava/util/List;)V"), index = 1)
    private int kronwerke$online(int mainPlayers) {
        return JoinQueue.on() ? Math.max(mainPlayers, JoinQueue.online()) : mainPlayers;
    }
}
