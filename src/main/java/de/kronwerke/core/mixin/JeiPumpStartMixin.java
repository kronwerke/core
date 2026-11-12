package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pictures during JEI's start; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.library.startup.JeiStarter", remap = false)
public abstract class JeiPumpStartMixin {
    @Inject(method = "start", at = @At("HEAD"), require = 0)
    private void kronwerke$begin(CallbackInfo ci) {
        LoadPump.begin();
    }

    @Inject(method = "start", at = @At("RETURN"), require = 0)
    private void kronwerke$end(CallbackInfo ci) {
        LoadPump.end();
    }
}
