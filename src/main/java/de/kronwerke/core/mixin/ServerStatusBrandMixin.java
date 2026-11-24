package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import de.kronwerke.core.status.StatusBrand;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;

/** The server list entry carries the logo's crown and the name in the logo's colours. */
@Mixin(MinecraftServer.class)
public abstract class ServerStatusBrandMixin {

    @ModifyReturnValue(method = "buildServerStatus", at = @At("RETURN"))
    private ServerStatus kronwerke$brand(ServerStatus status) {
        return StatusBrand.apply(status);
    }
}
