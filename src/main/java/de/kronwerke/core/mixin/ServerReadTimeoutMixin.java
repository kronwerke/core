package de.kronwerke.core.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import de.kronwerke.core.net.JoinGrace;

/** The server closes a silent connection after two minutes instead of 30 seconds, see {@link JoinGrace}. */
@Mixin(targets = "net.minecraft.server.network.ServerConnectionListener$1")
public abstract class ServerReadTimeoutMixin {

    @Redirect(method = "initChannel", require = 0, at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerConnectionListener;READ_TIMEOUT:I", opcode = Opcodes.GETSTATIC))
    private int kronwerke$longer() {
        return Math.max(JoinGrace.READ_TIMEOUT_S, Integer.getInteger("neoforge.readTimeout", 30));
    }
}
