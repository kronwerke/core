package de.kronwerke.core.mixin;

import de.kronwerke.core.client.JeiKeep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JEI stays up across a move between the servers of the network; see {@link JeiKeep}. */
@Pseudo
@Mixin(targets = "mezz.jei.neoforge.startup.StartEventObserver", remap = false)
public abstract class JeiKeepMixin {
    @Inject(method = "transitionState", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$keep(@Coerce Enum<?> next, CallbackInfo ci) {
        if (next.name().equals("LISTENING")) {
            if (JeiKeep.keep()) ci.cancel();
        } else {
            JeiKeep.started();
        }
    }

    @Inject(method = "restart", at = @At("HEAD"), cancellable = true, require = 0)
    private void kronwerke$sameRecipes(CallbackInfo ci) {
        if (JeiKeep.same()) ci.cancel();
    }
}
