package de.kronwerke.core.mixin;

import de.kronwerke.core.compat.EmiLocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** EMI derives the list it shows from the whole one: the stage locks cut it afterwards (EmiLocks). Nothing without EMI. */
@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiStackList", remap = false)
public abstract class EmiStackListMixin {

    @Inject(method = "bakeFiltered", at = @At("TAIL"), require = 0)
    private static void kronwerke$stageLocks(CallbackInfo ci) {
        EmiLocks.filter();
    }
}
