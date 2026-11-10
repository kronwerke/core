package de.kronwerke.core.mixin;

import com.mojang.authlib.GameProfile;
import de.kronwerke.core.season.StartGate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

/**
 * The start gate answers on the login screen, after the ban and whitelist checks, before
 * the player is placed in the world: a player who is turned away never loads a chunk.
 */
@Mixin(PlayerList.class)
public abstract class StartGateMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void kronwerke$startGate(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) return;
        Component reason = de.kronwerke.core.link.NetworkSync.refuseLogin(server, profile);
        if (reason == null) reason = StartGate.refuse(server, profile);
        if (reason != null) cir.setReturnValue(reason);
    }
}
