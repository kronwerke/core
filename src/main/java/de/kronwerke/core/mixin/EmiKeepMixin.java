package de.kronwerke.core.mixin;

import de.kronwerke.core.client.EmiKeep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EMI keeps its lists across a move between the network's servers, see EmiKeep. Nothing without EMI. */
@Pseudo
@Mixin(targets = "dev.emi.emi.runtime.EmiReloadManager", remap = false)
public abstract class EmiKeepMixin {

    @Inject(method = "clear", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$keep(CallbackInfo ci) {
        if (EmiKeep.keep()) ci.cancel();
    }

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true, require = 0)
    private static void kronwerke$same(CallbackInfo ci) {
        if (EmiKeep.same()) ci.cancel();
    }
}
