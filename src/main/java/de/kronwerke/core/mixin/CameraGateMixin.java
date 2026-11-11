package de.kronwerke.core.mixin;

import java.net.SocketAddress;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import de.kronwerke.core.camera.Camera;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;

/** The camera passes the whitelist, bans and the start gate; see {@link Camera}. */
@Mixin(PlayerList.class)
public abstract class CameraGateMixin {
    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void kronwerke$cameraLogin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        if (Camera.is(profile)) cir.setReturnValue(null);
    }

    @Inject(method = "isWhiteListed", at = @At("HEAD"), cancellable = true)
    private void kronwerke$cameraListed(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (Camera.is(profile)) cir.setReturnValue(true);
    }
}
