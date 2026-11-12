package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pictures during JEI's start; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.ListElementInfo", remap = false)
public abstract class JeiPumpElementMixin {
    /** Once per item of the list on the right, when its names and tooltip are read. */
    @Inject(method = "create", at = @At("HEAD"), require = 0)
    private static void kronwerke$create(CallbackInfoReturnable<?> ci) {
        LoadPump.pulse();
    }
}
