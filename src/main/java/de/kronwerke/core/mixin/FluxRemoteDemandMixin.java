package de.kronwerke.core.mixin;

import de.kronwerke.core.share.flux.FluxLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sonar.fluxnetworks.common.connection.FluxNetwork;

/**
 * A Flux network's plugs take in energy for the points and storages of the same network on the
 * other servers too, see {@link FluxLayer#remoteWant}.
 */
@Pseudo
@Mixin(targets = "sonar.fluxnetworks.common.connection.ServerFluxNetwork", remap = false)
public abstract class FluxRemoteDemandMixin {
    @Shadow
    private long mBufferLimiter;

    @Inject(method = "onEndServerTick", at = @At("RETURN"), require = 0)
    private void kronwerke$remoteDemand(CallbackInfo ci) {
        long more = FluxLayer.remoteWant(((FluxNetwork) (Object) this).getNetworkID());
        if (more <= 0) return;
        long sum = mBufferLimiter + more;
        mBufferLimiter = sum < 0 ? Long.MAX_VALUE / 4 : sum;
    }
}
