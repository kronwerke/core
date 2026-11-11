package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import de.kronwerke.core.camera.Camera;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/** The camera logs in without Mojang, only over loopback; see {@link Camera#offlineLogin}. */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class CameraLoginMixin {
    @Shadow
    @Final
    Connection connection;

    @WrapOperation(method = "handleHello", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;usesAuthentication()Z"))
    private boolean kronwerke$camera(MinecraftServer server, Operation<Boolean> original, @Local(argsOnly = true) ServerboundHelloPacket packet) {
        if (Camera.offlineLogin(connection.getRemoteAddress(), packet.name())) return false;
        return original.call(server);
    }
}
