package de.kronwerke.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.kronwerke.core.portal.Crossing;

/**
 * Create lays a track through a Nether or End portal into the dimension on the other side.
 * When that dimension is played on another server, the track would end in this server's own
 * copy of it, which nobody can visit, and a train would vanish there with its cargo. No exit
 * then: the track does not connect, as at a portal Create cannot use.
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.trains.track.AllPortalTracks", remap = false)
public abstract class CreatePortalTrackMixin {
    @Inject(method = "fromPortal", at = @At("RETURN"), cancellable = true, require = 0)
    private static void kronwerke$noExitElsewhere(CallbackInfoReturnable<Object> cir) {
        if (Crossing.foreignExit(cir.getReturnValue())) cir.setReturnValue(null);
    }
}
