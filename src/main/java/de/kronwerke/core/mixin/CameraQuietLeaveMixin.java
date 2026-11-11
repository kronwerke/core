package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import de.kronwerke.core.camera.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;

/** No "KronwerkeCam left the game" either. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CameraQuietLeaveMixin {
    @Shadow
    public ServerPlayer player;

    @WrapOperation(method = "removePlayerFromWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"), require = 0)
    private void kronwerke$quietLeave(PlayerList list, Component msg, boolean overlay, Operation<Void> original) {
        if (!Camera.is(player) && !de.kronwerke.core.portal.Travel.quietLeave(player.getUUID())) original.call(list, msg, overlay);
    }
}
