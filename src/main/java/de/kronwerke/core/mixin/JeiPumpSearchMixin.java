package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pictures during JEI's start; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.search.ElementSearch", remap = false)
public abstract class JeiPumpSearchMixin {
    /** Once per item that goes into the search. */
    @Inject(method = "add", at = @At("HEAD"), require = 0)
    private void kronwerke$add(CallbackInfo ci) {
        LoadPump.pulse();
    }

    /** Once per word of it. */
    @Inject(method = "putIfNotBlank*", at = @At("HEAD"), require = 0)
    private static void kronwerke$put(CallbackInfo ci) {
        LoadPump.pulse();
    }
}
