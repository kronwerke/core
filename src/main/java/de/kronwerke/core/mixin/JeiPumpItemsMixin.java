package de.kronwerke.core.mixin;

import de.kronwerke.core.client.LoadPump;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

/** A turn for the screen inside long work on the game's thread; see {@link LoadPump}. */
@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory", remap = false)
public abstract class JeiPumpItemsMixin {
    /** JEI's list of all items: once per item. */
    @Inject(method = "safeGetUid", at = @At("HEAD"), require = 0)
    private static void kronwerke$item(CallbackInfoReturnable<?> ci) {
        LoadPump.pulse();
    }
}
