package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import de.kronwerke.core.camera.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

/** No "KronwerkeCam joined the game" in chat (and from there in Discord) when the camera reconnects. */
@Mixin(PlayerList.class)
public abstract class CameraQuietMixin {
    @WrapOperation(method = "placeNewPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"))
    private void kronwerke$quietJoin(PlayerList list, Component msg, boolean overlay, Operation<Void> original,
                                     @Local(argsOnly = true) ServerPlayer player) {
        if (!Camera.is(player)) original.call(list, msg, overlay);
    }
}
