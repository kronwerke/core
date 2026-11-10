package de.kronwerke.core.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import de.kronwerke.core.net.JoinGrace;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;

/** Gives joining (and lagging) clients longer to answer keep-alives, see {@link JoinGrace}. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class KeepAliveGraceMixin {

    @Shadow private long keepAliveTime;

    @Unique
    private long kronwerke$created;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void kronwerke$remember(CallbackInfo ci) {
        this.kronwerke$created = Util.getMillis();
    }

    @WrapOperation(method = "keepConnectionAlive", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerCommonPacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"))
    private void kronwerke$graceful(ServerCommonPacketListenerImpl self, Component reason, Operation<Void> original) {
        if (JoinGrace.stillAllowed(this.kronwerke$created, this.keepAliveTime, Util.getMillis())) return;
        original.call(self, reason);
    }
}
